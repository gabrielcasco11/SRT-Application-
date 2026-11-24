package com.gabriel.srtmonitor.srt

import android.util.Log
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import io.github.thibaultbee.srtdroid.core.enums.ErrorType
import io.github.thibaultbee.srtdroid.core.enums.SockOpt
import io.github.thibaultbee.srtdroid.core.enums.SockStatus
import io.github.thibaultbee.srtdroid.core.enums.Transtype
import io.github.thibaultbee.srtdroid.core.models.SrtError
import io.github.thibaultbee.srtdroid.core.models.SrtSocket
import io.github.thibaultbee.srtdroid.core.models.Stats
import io.github.thibaultbee.srtdroid.core.models.rejectreason.InternalRejectReason
import io.github.thibaultbee.srtdroid.core.models.rejectreason.PredefinedRejectReason
import io.github.thibaultbee.srtdroid.core.models.rejectreason.UserDefinedRejectReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InterruptedIOException
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Opens one SRT connection (caller or listener) on its own thread and queues the
 * received MPEG-TS payloads (normally 7 x 188 = 1316 bytes each) for the player.
 * One instance = one connection attempt. Create a new one to reconnect.
 */
class SrtReceiver(private val config: StreamConfig) {

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Listening : State
        data class Connected(val peer: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isConnected: Boolean get() = _state.value is State.Connected

    // ~13 MB of TS at most; at 20 Mb/s that is ~5 s, far more than the player keeps.
    private val queue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)

    @Volatile private var running = false
    @Volatile private var dataSocket: SrtSocket? = null
    @Volatile private var listenSocket: SrtSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::run, "srt-receiver").also { it.start() }
    }

    /** Blocking (up to ~1.5 s). Call off the main thread. */
    fun stop() {
        running = false
        closeQuietly(listenSocket)
        closeQuietly(dataSocket)
        listenSocket = null
        dataSocket = null
        thread?.interrupt()
        thread?.join(1500)
        thread = null
        queue.clear()
        if (_state.value !is State.Failed) _state.value = State.Idle
    }

    /** Next TS chunk, or null after [timeoutMs]. */
    fun poll(timeoutMs: Long): ByteArray? = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /** Interval stats since the previous call (totals are cumulative). */
    fun stats(): Stats? = try {
        dataSocket?.takeIf { isConnected }?.bistats(true, true)
    } catch (t: Throwable) {
        null
    }

    private fun run() {
        try {
            val socket = SrtSocket()
            configure(socket)
            val connected: SrtSocket
            val peer: String
            if (config.mode == SrtMode.CALLER) {
                _state.value = State.Connecting
                dataSocket = socket
                Log.i(TAG, "Calling ${config.host.trim()}:${config.port} latency=${config.latencyMs} " +
                    "encrypted=${config.passphrase.isNotEmpty()} streamId='${config.streamId}' timeout=${config.connectTimeoutSec}s")
                connectNonBlocking(socket)
                connected = socket
                peer = "${config.host.trim()}:${config.port}"
            } else {
                _state.value = State.Listening
                listenSocket = socket
                socket.setSockFlag(SockOpt.REUSEADDR, true)
                socket.bind("0.0.0.0", config.port)
                socket.listen(1)
                val accepted = socket.accept()
                val client: SrtSocket = accepted.first
                val address: java.net.InetSocketAddress? = accepted.second
                listenSocket = null
                closeQuietly(socket)
                if (!running) {
                    closeQuietly(client)
                    return
                }
                dataSocket = client
                connected = client
                peer = address?.let { a -> "${a.address?.hostAddress ?: a.hostString}:${a.port}" } ?: "remote caller"
            }
            // Wake up regularly so a silent stream / stop() is noticed.
            connected.setSockFlag(SockOpt.RCVTIMEO, 1000)
            queue.clear()
            _state.value = State.Connected(peer)
            Log.i(TAG, "Connected to $peer")

            val buffer = ByteArray(1500)
            var silentSeconds = 0
            while (running) {
                val n = try {
                    connected.recv(buffer, 0, buffer.size)
                } catch (e: SocketException) {
                    val err = SrtError.lastError
                    if (err == ErrorType.EASYNCRCV || err == ErrorType.ETIMEOUT) {
                        silentSeconds++
                        if (silentSeconds >= NO_DATA_TIMEOUT_S) throw SocketException("No data for $NO_DATA_TIMEOUT_S s")
                        continue
                    }
                    throw e
                }
                silentSeconds = 0
                val chunk = buffer.copyOf(n)
                if (!queue.offer(chunk)) {
                    queue.poll() // drop oldest if the player stalls
                    queue.offer(chunk)
                }
            }
        } catch (e: ConnectFailure) {
            if (running) fail(e.message ?: "Connection failed")
        } catch (e: InterruptedIOException) {
            if (running) fail("Connection closed by remote")
        } catch (t: Throwable) {
            if (running) fail(describe(t))
        } finally {
            closeQuietly(listenSocket)
            closeQuietly(dataSocket)
        }
    }

    private class ConnectFailure(message: String) : Exception(message)

    /**
     * Connects the way VLC and ffmpeg do (non-blocking + poll). libsrt's blocking connect adds an
     * extra ISN "security check" that some listeners (e.g. Nevion Virtuoso) don't satisfy, which
     * aborts an otherwise good connection with SRT_ESECFAIL.
     */
    private fun connectNonBlocking(s: SrtSocket) {
        s.setSockFlag(SockOpt.RCVSYN, false)
        s.connect(config.host.trim(), config.port)
        val deadline = System.currentTimeMillis() + config.connectTimeoutSec * 1000L + 1000L
        while (running) {
            when (s.sockState) {
                SockStatus.CONNECTED -> {
                    s.setSockFlag(SockOpt.RCVSYN, true) // back to blocking reads
                    return
                }
                SockStatus.BROKEN, SockStatus.CLOSED, SockStatus.CLOSING, SockStatus.NONEXIST -> {
                    throw ConnectFailure(rejectText(s))
                }
                else -> Unit
            }
            // A failed non-blocking connect stays CONNECTING; the reject reason tells us it failed.
            val rejected = runCatching { s.rejectReason }.getOrNull()
            val rejectedCode = (rejected as? InternalRejectReason)?.code?.name
            if (rejected != null && rejectedCode != "UNKNOWN") throw ConnectFailure(rejectText(s))
            if (System.currentTimeMillis() > deadline) {
                throw ConnectFailure("No answer from ${config.host}:${config.port} (is the source in Listener mode?)")
            }
            Thread.sleep(20)
        }
    }

    private fun rejectText(s: SrtSocket): String {
        val r = runCatching { s.rejectReason }.getOrNull()
        val code = (r as? InternalRejectReason)?.code?.name
        val friendly = when (code) {
            "TIMEOUT" -> "No answer from ${config.host}:${config.port} (is the source in Listener mode?)"
            "BADSECRET" -> "Wrong passphrase"
            "UNSECURE" -> "The source requires a passphrase (Advanced tab)"
            "PEER" -> "Connection refused by the source"
            "BACKLOG" -> "The source is busy (already serving another caller?)"
            "VERSION" -> "SRT version not accepted by the source"
            else -> "Connection failed"
        }
        val detail = when (r) {
            is InternalRejectReason -> "reject: ${r.code.name}"
            is PredefinedRejectReason -> "reject: code ${r.code}"
            is UserDefinedRejectReason -> "reject: code ${r.code}"
            else -> null
        }
        return if (detail == null) friendly else "$friendly\n[$detail]"
    }

    private fun configure(s: SrtSocket) {
        s.setSockFlag(SockOpt.TRANSTYPE, Transtype.LIVE)
        s.setSockFlag(SockOpt.LATENCY, config.latencyMs)
        s.setSockFlag(SockOpt.CONNTIMEO, config.connectTimeoutSec * 1000)
        if (config.passphrase.isNotEmpty()) {
            s.setSockFlag(SockOpt.PASSPHRASE, config.passphrase)
            if (config.keyLength in listOf(16, 24, 32)) s.setSockFlag(SockOpt.PBKEYLEN, config.keyLength)
        } else {
            // Behave like older SRT builds (e.g. VLC's): don't refuse the connection just
            // because the other side has encryption configured.
            runCatching { s.setSockFlag(SockOpt.ENFORCEDENCRYPTION, false) }
        }
        if (config.streamId.isNotBlank()) s.setSockFlag(SockOpt.STREAMID, config.streamId.trim())
    }

    private fun fail(reason: String) {
        Log.w(TAG, "SRT failed: $reason | ${runCatching { SrtError.lastErrorMessage }.getOrNull()}")
        _state.value = State.Failed(reason)
    }

    private fun describe(t: Throwable): String {
        val err = runCatching { SrtError.lastError }.getOrNull()
        val friendly = when (err) {
            ErrorType.ENOSERVER -> "No answer from ${config.host}:${config.port} (is the source in Listener mode?)"
            ErrorType.ECONNREJ -> "Connection rejected (check passphrase / stream ID / mode)"
            ErrorType.ECONNLOST -> "Connection lost"
            ErrorType.ESECFAIL -> "The source answered, but its handshake reply didn't match this connection"
            ErrorType.ECONNSETUP -> if (config.mode == SrtMode.LISTENER) "Can't listen on port ${config.port}" else null
            else -> null
        }
        val base = friendly ?: (t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
        val reason = runCatching {
            when (val r = (dataSocket ?: listenSocket)?.rejectReason) {
                is InternalRejectReason -> r.code.name.takeIf { it != "UNKNOWN" }
                is PredefinedRejectReason -> "code ${r.code}"
                is UserDefinedRejectReason -> "code ${r.code}"
                else -> null
            }
        }.getOrNull()
        val code = err?.name?.let { "SRT $it" }
        val detail = listOfNotNull(code, reason?.let { "reject: $it" }).joinToString(" · ")
        return if (detail.isEmpty()) base else "$base\n[$detail]"
    }

    private fun closeQuietly(s: SrtSocket?) {
        try {
            s?.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "SrtReceiver"
        private const val QUEUE_CAPACITY = 10_000
        private const val NO_DATA_TIMEOUT_S = 6
    }
}
