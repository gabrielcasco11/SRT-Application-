package com.gabriel.srtmonitor.srt

import android.util.Log
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import io.github.thibaultbee.srtdroid.core.enums.ErrorType
import io.github.thibaultbee.srtdroid.core.enums.SockOpt
import io.github.thibaultbee.srtdroid.core.enums.Transtype
import io.github.thibaultbee.srtdroid.core.models.SrtError
import io.github.thibaultbee.srtdroid.core.models.SrtSocket
import io.github.thibaultbee.srtdroid.core.models.Stats
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
                socket.connect(config.host.trim(), config.port)
                connected = socket
                peer = "${config.host.trim()}:${config.port}"
            } else {
                _state.value = State.Listening
                listenSocket = socket
                socket.setSockFlag(SockOpt.REUSEADDR, true)
                socket.bind("0.0.0.0", config.port)
                socket.listen(1)
                val (client, address) = socket.accept()
                listenSocket = null
                closeQuietly(socket)
                if (!running) {
                    closeQuietly(client)
                    return
                }
                dataSocket = client
                connected = client
                peer = address?.let { "${it.address?.hostAddress ?: it.hostString}:${it.port}" } ?: "remote caller"
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
        } catch (e: InterruptedIOException) {
            if (running) fail("Connection closed by remote")
        } catch (t: Throwable) {
            if (running) fail(describe(t))
        } finally {
            closeQuietly(listenSocket)
            closeQuietly(dataSocket)
        }
    }

    private fun configure(s: SrtSocket) {
        s.setSockFlag(SockOpt.TRANSTYPE, Transtype.LIVE)
        s.setSockFlag(SockOpt.LATENCY, config.latencyMs)
        s.setSockFlag(SockOpt.CONNTIMEO, config.connectTimeoutSec * 1000)
        if (config.passphrase.isNotEmpty()) {
            s.setSockFlag(SockOpt.PASSPHRASE, config.passphrase)
            if (config.keyLength in listOf(16, 24, 32)) s.setSockFlag(SockOpt.PBKEYLEN, config.keyLength)
        }
        if (config.streamId.isNotBlank()) s.setSockFlag(SockOpt.STREAMID, config.streamId.trim())
    }

    private fun fail(reason: String) {
        Log.w(TAG, "SRT failed: $reason")
        _state.value = State.Failed(reason)
    }

    private fun describe(t: Throwable): String {
        val err = runCatching { SrtError.lastError }.getOrNull()
        val friendly = when (err) {
            ErrorType.ENOSERVER -> "No answer from ${config.host}:${config.port} (is the source in Listener mode?)"
            ErrorType.ECONNREJ -> "Connection rejected (check passphrase / stream ID / mode)"
            ErrorType.ECONNLOST -> "Connection lost"
            ErrorType.ESECFAIL -> "Encryption handshake failed (check passphrase)"
            ErrorType.ECONNSETUP -> if (config.mode == SrtMode.LISTENER) "Can't listen on port ${config.port}" else null
            else -> null
        }
        return friendly ?: (t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
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
