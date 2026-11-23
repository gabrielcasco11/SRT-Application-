package com.gabriel.srtmonitor.player

enum class Phase { IDLE, CONNECTING, LISTENING, CONNECTED, PLAYING, RECONNECTING, ERROR }

/** One sample per second for the charts. */
data class StatPoint(
    val rttMs: Float,
    val rcvBufferMs: Float,
    val latencyMs: Float,
    val kbps: Float,
)

data class VideoInfo(
    val mime: String? = null,
    val codecs: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = -1f,
    val pixelRatio: Float = 1f,
)

data class AudioInfo(
    val mime: String? = null,
    val channels: Int = -1,
    val sampleRate: Int = -1,
    val supported: Boolean = true,
)

data class LiveUi(
    val phase: Phase = Phase.IDLE,
    val message: String = "",
    val peer: String? = null,
    val connectedAtMs: Long? = null,
    val video: VideoInfo? = null,
    val audio: AudioInfo? = null,
    val videoDecoder: String? = null,
    val audioDecoder: String? = null,
    val droppedFrames: Long = 0,
    val measuredFps: Float? = null,
    val playerBufferMs: Long = 0,
    val history: List<StatPoint> = emptyList(),
    val pktReceived: Long = 0,
    val pktLost: Long = 0,
    val pktDropped: Long = 0,
    val pktRetransmitted: Long = 0,
) {
    val latest: StatPoint? get() = history.lastOrNull()
    val videoAspect: Float
        get() {
            val v = video ?: return 16f / 9f
            if (v.width <= 0 || v.height <= 0) return 16f / 9f
            return v.width * v.pixelRatio / v.height
        }
}

object Labels {
    fun videoCodec(mime: String?): String = when (mime) {
        "video/hevc" -> "H.265 / HEVC"
        "video/avc" -> "H.264 / AVC"
        null -> "—"
        else -> mime.removePrefix("video/").uppercase()
    }

    fun shortVideoCodec(mime: String?): String = when (mime) {
        "video/hevc" -> "HEVC"
        "video/avc" -> "H.264"
        null -> "—"
        else -> mime.removePrefix("video/").uppercase()
    }

    /** Human profile name from an RFC 6381 codecs string, e.g. hvc1.2.4.L123 -> Main 10. */
    fun profile(codecs: String?): String {
        if (codecs.isNullOrBlank()) return "—"
        val parts = codecs.split('.')
        val tag = parts.firstOrNull()?.lowercase() ?: return codecs
        return when (tag) {
            "hvc1", "hev1" -> when (parts.getOrNull(1)?.trimStart('A', 'B', 'C')) {
                "1" -> "Main"
                "2" -> "Main 10"
                "3" -> "Main Still"
                "4" -> "Range Ext. (4:2:2 / 4:4:4)"
                else -> codecs
            }
            "avc1", "avc3" -> when (parts.getOrNull(1)?.take(2)?.uppercase()) {
                "42" -> "Baseline"
                "4D" -> "Main"
                "58" -> "Extended"
                "64" -> "High"
                "6E" -> "High 10"
                "7A" -> "High 4:2:2"
                "F4" -> "High 4:4:4"
                else -> codecs
            }
            else -> codecs
        }
    }

    fun audioCodec(mime: String?): String = when (mime) {
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg-L2" -> "MPEG-1 Layer II"
        "audio/mpeg" -> "MPEG Audio"
        "audio/ac3" -> "Dolby AC-3"
        "audio/eac3" -> "Dolby E-AC-3"
        "audio/opus" -> "Opus"
        null -> "—"
        else -> mime.removePrefix("audio/").uppercase()
    }

    fun isHardwareDecoder(name: String?): Boolean? {
        if (name == null) return null
        val n = name.lowercase()
        return !(n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.contains(".sw."))
    }

    fun bitrate(kbps: Float?): String = when {
        kbps == null -> "—"
        kbps >= 1000f -> String.format("%.1f Mb/s", kbps / 1000f)
        else -> String.format("%.0f kb/s", kbps)
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }
}
