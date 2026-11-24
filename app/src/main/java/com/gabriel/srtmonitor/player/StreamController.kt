package com.gabriel.srtmonitor.player

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.gabriel.srtmonitor.model.DecoderMode
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.gabriel.srtmonitor.model.SrtMode
import com.gabriel.srtmonitor.model.StreamConfig
import com.gabriel.srtmonitor.net.WifiMonitor
import com.gabriel.srtmonitor.srt.SrtDataSource
import com.gabriel.srtmonitor.srt.SrtReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Runs one stream: SRT connection -> Media3 player, with automatic reconnect
 * and once-a-second statistics for the UI.
 */
@UnstableApi
class StreamController(
    context: Context,
    private val config: StreamConfig,
    private val wifi: WifiMonitor,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(LiveUi())
    val ui: StateFlow<LiveUi> = _ui.asStateFlow()

    var textureView: TextureView? = null
        set(value) {
            field = value
            player?.setVideoTextureView(value)
        }

    private var player: ExoPlayer? = null
    private var receiver: SrtReceiver? = null
    private var sessionJob: Job? = null
    private var statsJob: Job? = null
    private val playerError = MutableStateFlow<String?>(null)
    private var boundToWifi = false

    /** True once we've decided to decode in software (FFmpeg) for this session. */
    private var useSoftware = config.decoderMode == DecoderMode.SOFTWARE

    private var retransTotal = 0L
    private var lastRendered = -1
    private var firstFrameAt = 0L
    private var lastThumbAt = 0L

    fun start() {
        if (sessionJob?.isActive == true) return
        bindToWifi()
        _ui.value = LiveUi()
        retransTotal = 0
        sessionJob = scope.launch { runSessions() }
        statsJob = scope.launch { pollStats() }
    }

    fun stop() {
        saveThumbnail()
        statsJob?.cancel()
        sessionJob?.cancel()
        statsJob = null
        sessionJob = null
        releasePlayer()
        receiver?.let { rx -> Thread { rx.stop() }.start() }
        receiver = null
        unbindWifi()
        _ui.update { it.copy(phase = Phase.IDLE) }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    // ---- session loop -------------------------------------------------------------------

    private suspend fun runSessions() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val rx = SrtReceiver(config)
            receiver = rx
            _ui.update {
                it.copy(
                    phase = if (config.mode == SrtMode.CALLER) Phase.CONNECTING else Phase.LISTENING,
                    connectedAtMs = null,
                )
            }
            rx.start()
            try {
                val st = rx.state.first { it is SrtReceiver.State.Connected || it is SrtReceiver.State.Failed }
                if (st is SrtReceiver.State.Connected) {
                    attempt = 0
                    _ui.update {
                        it.copy(
                            phase = Phase.CONNECTED,
                            peer = st.peer,
                            message = "",
                            connectedAtMs = SystemClock.elapsedRealtime(),
                        )
                    }
                    playerError.value = null
                    startPlayer(rx)
                    val reason = merge(
                        rx.state.filterIsInstance<SrtReceiver.State.Failed>().map { it.reason },
                        playerError.filterNotNull(),
                    ).first()
                    saveThumbnail()
                    _ui.update { it.copy(phase = Phase.RECONNECTING, message = reason, connectedAtMs = null) }
                } else if (st is SrtReceiver.State.Failed) {
                    _ui.update { it.copy(phase = Phase.ERROR, message = st.reason) }
                }
            } finally {
                releasePlayer()
                withContext(NonCancellable + Dispatchers.IO) { rx.stop() }
                if (receiver === rx) receiver = null
            }
            attempt++
            val switched = _ui.value.message.startsWith("Phone hardware")
            delay(if (switched || config.mode == SrtMode.LISTENER) 300L else min(1000L * attempt, 3000L))
        }
    }

    private fun startPlayer(rx: SrtReceiver) {
        val mode = when {
            useSoftware -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            config.decoderMode == DecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }
        val renderers = NextRenderersFactory(appContext)
            .setExtensionRendererMode(mode)
            .setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 3000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(appContext, renderers)
            .setLoadControl(loadControl)
            .build()
        p.addListener(playerListener)
        p.addAnalyticsListener(analyticsListener)
        p.setVideoTextureView(textureView)

        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        val extractors = ExtractorsFactory {
            arrayOf<Extractor>(
                TsExtractor(
                    TsExtractor.MODE_SINGLE_PMT,
                    TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
                    SubtitleParser.Factory.UNSUPPORTED,
                    TimestampAdjuster(0),
                    DefaultTsPayloadReaderFactory(tsFlags),
                    TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
                ),
            )
        }
        val source = ProgressiveMediaSource.Factory(DataSource.Factory { SrtDataSource(rx) }, extractors)
            .setContinueLoadingCheckIntervalBytes(64 * 1024)
            .createMediaSource(MediaItem.fromUri("srt://${config.host.ifBlank { "0.0.0.0" }}:${config.port}"))
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        lastRendered = -1
        firstFrameAt = 0L
        player = p
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(playerListener)
            it.removeAnalyticsListener(analyticsListener)
            it.release()
        }
        player = null
    }

    // ---- player callbacks -----------------------------------------------------------------

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!useSoftware && config.decoderMode == DecoderMode.AUTO && isDecoderError(error)) {
                Log.i(TAG, "Hardware decoder failed (${error.errorCodeName}); switching to FFmpeg software decoder")
                useSoftware = true
                playerError.value = "Phone hardware can't decode this format. Switching to software decoder…"
                return
            }
            val detail = error.cause?.message ?: error.message ?: ""
            Log.w(TAG, "Player error ${error.errorCodeName}: $detail", error)
            playerError.value = "Player: ${error.errorCodeName.removePrefix("ERROR_CODE_")} $detail".trim()
        }

        override fun onRenderedFirstFrame() {
            firstFrameAt = SystemClock.elapsedRealtime()
            _ui.update { it.copy(phase = Phase.PLAYING, message = "") }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            _ui.update {
                val v = it.video ?: VideoInfo()
                it.copy(
                    video = v.copy(
                        width = videoSize.width,
                        height = videoSize.height,
                        pixelRatio = videoSize.pixelWidthHeightRatio,
                    ),
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO && group.length > 0) {
                    val f = group.getTrackFormat(0)
                    val supported = group.isTrackSupported(0)
                    _ui.update {
                        val a = it.audio ?: AudioInfo()
                        it.copy(
                            audio = a.copy(
                                mime = f.sampleMimeType ?: a.mime,
                                channels = if (f.channelCount > 0) f.channelCount else a.channels,
                                sampleRate = if (f.sampleRate > 0) f.sampleRate else a.sampleRate,
                                supported = supported,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun isDecoderError(e: PlaybackException) = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    )

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            _ui.update { it.copy(videoDecoder = decoderName) }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            _ui.update { it.copy(audioDecoder = decoderName) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            _ui.update {
                val v = it.video ?: VideoInfo()
                it.copy(
                    video = v.copy(
                        mime = format.sampleMimeType ?: v.mime,
                        codecs = format.codecs ?: v.codecs,
                        width = if (format.width > 0) format.width else v.width,
                        height = if (format.height > 0) format.height else v.height,
                        frameRate = if (format.frameRate > 0) format.frameRate else v.frameRate,
                    ),
                )
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            _ui.update {
                val a = it.audio ?: AudioInfo()
                it.copy(
                    audio = a.copy(
                        mime = format.sampleMimeType ?: a.mime,
                        channels = if (format.channelCount > 0) format.channelCount else a.channels,
                        sampleRate = if (format.sampleRate > 0) format.sampleRate else a.sampleRate,
                    ),
                )
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            _ui.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
        }
    }

    // ---- stats ------------------------------------------------------------------------------

    private suspend fun pollStats() {
        while (currentCoroutineContext().isActive) {
            delay(1000)
            val p = player

            var fps: Float? = null
            val counters = p?.videoDecoderCounters
            if (counters != null) {
                counters.ensureUpdated()
                val rendered = counters.renderedOutputBufferCount
                if (lastRendered >= 0) fps = (rendered - lastRendered).toFloat()
                lastRendered = rendered
            } else {
                lastRendered = -1
            }

            // Keep end-to-end delay from creeping up if the source clock runs slightly fast.
            var bufferMs = 0L
            if (p != null) {
                bufferMs = p.totalBufferedDuration
                val target = if (bufferMs > 1500) 1.04f else 1f
                if (p.playbackParameters.speed != target) p.setPlaybackSpeed(target)
            }

            val s = receiver?.stats()
            if (s != null) {
                retransTotal += s.pktRcvRetrans
                val point = StatPoint(
                    rttMs = s.msRTT.toFloat(),
                    rcvBufferMs = s.msRcvBuf.toFloat(),
                    latencyMs = s.msRcvTsbPdDelay.toFloat(),
                    kbps = (s.mbpsRecvRate * 1000.0).toFloat(),
                )
                _ui.update {
                    it.copy(
                        history = (it.history + point).takeLast(HISTORY),
                        pktReceived = s.pktRecvTotal,
                        pktLost = s.pktRcvLossTotal.toLong(),
                        pktDropped = s.pktRcvDropTotal.toLong(),
                        pktRetransmitted = retransTotal,
                        measuredFps = fps ?: it.measuredFps,
                        playerBufferMs = bufferMs,
                        message = if (s.pktRcvUndecryptTotal > 0 && it.phase == Phase.CONNECTED) {
                            "The stream is encrypted. Enter the source's passphrase in Advanced."
                        } else {
                            it.message
                        },
                    )
                }
            } else {
                _ui.update { it.copy(measuredFps = fps, playerBufferMs = bufferMs) }
            }

            val now = SystemClock.elapsedRealtime()
            if (_ui.value.phase == Phase.PLAYING && firstFrameAt > 0 &&
                now - firstFrameAt > 3000 && now - lastThumbAt > 20_000
            ) {
                saveThumbnail()
            }
        }
    }

    // ---- thumbnail for the home screen --------------------------------------------------------

    private fun saveThumbnail() {
        if (_ui.value.phase != Phase.PLAYING) return
        val tv = textureView ?: return
        if (!tv.isAvailable) return
        val bmp: Bitmap = try {
            tv.getBitmap(640, 360)
        } catch (t: Throwable) {
            null
        } ?: return
        lastThumbAt = SystemClock.elapsedRealtime()
        Thread {
            try {
                val tmp = File(appContext.filesDir, "thumb.tmp")
                FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                tmp.renameTo(thumbnailFile(appContext))
            } catch (t: Throwable) {
                Log.w(TAG, "Thumbnail failed", t)
            } finally {
                bmp.recycle()
            }
        }.start()
    }

    // ---- keep SRT on Wi-Fi even without internet -----------------------------------------------

    private fun bindToWifi() {
        if (!config.routeViaWifi) return
        val net = wifi.state.value.network ?: return
        boundToWifi = try {
            wifi.connectivityManager.bindProcessToNetwork(net)
        } catch (t: Throwable) {
            false
        }
    }

    private fun unbindWifi() {
        if (!boundToWifi) return
        try {
            wifi.connectivityManager.bindProcessToNetwork(null)
        } catch (_: Throwable) {
        }
        boundToWifi = false
    }

    companion object {
        private const val TAG = "StreamController"
        private const val HISTORY = 90

        fun thumbnailFile(context: Context) = File(context.filesDir, "thumb.jpg")
    }
}
