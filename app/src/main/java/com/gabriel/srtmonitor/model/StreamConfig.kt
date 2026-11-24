package com.gabriel.srtmonitor.model

import android.content.Context

enum class SrtMode { CALLER, LISTENER }

/** AUTO = phone hardware first, FFmpeg software if the hardware can't decode the stream. */
enum class DecoderMode { AUTO, HARDWARE, SOFTWARE }

/** Everything needed to receive the one stream this app is built for. */
data class StreamConfig(
    val name: String = "My Stream",
    val mode: SrtMode = SrtMode.CALLER,
    val host: String = "",
    val port: Int = 5000,
    val latencyMs: Int = 200,
    val passphrase: String = "",
    /** 0 = auto, otherwise 16 / 24 / 32 bytes (AES-128 / 192 / 256). */
    val keyLength: Int = 0,
    val streamId: String = "",
    val connectTimeoutSec: Int = 3,
    /** Keep SRT traffic on Wi-Fi even when that Wi-Fi has no internet access. */
    val routeViaWifi: Boolean = true,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
) {
    val isConfigured: Boolean get() = mode == SrtMode.LISTENER || host.isNotBlank()
    val encrypted: Boolean get() = passphrase.isNotEmpty()

    val modeLabel: String get() = if (mode == SrtMode.CALLER) "Caller" else "Listener"

    val endpointLabel: String
        get() = if (mode == SrtMode.CALLER) "$host:$port" else "Port $port"

    val encryptionLabel: String
        get() = when {
            !encrypted -> "Off"
            keyLength == 24 -> "AES-192"
            keyLength == 32 -> "AES-256"
            keyLength == 16 -> "AES-128"
            else -> "AES (auto)"
        }

    /** Returns field name -> error message. Empty map means the config is valid. */
    fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (mode == SrtMode.CALLER && host.isBlank()) errors["host"] = "Enter the address of the SRT source"
        if (port !in 1..65535) errors["port"] = "Port must be 1–65535"
        if (latencyMs !in 20..8000) errors["latency"] = "Latency must be 20–8000 ms"
        if (passphrase.isNotEmpty() && passphrase.length !in 10..79) {
            errors["passphrase"] = "Passphrase must be 10–79 characters"
        }
        if (connectTimeoutSec !in 1..30) errors["timeout"] = "Timeout must be 1–30 s"
        return errors
    }
}

object ConfigStore {
    private const val PREFS = "stream_config"

    fun load(context: Context): StreamConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = StreamConfig()
        return StreamConfig(
            name = p.getString("name", d.name) ?: d.name,
            mode = runCatching { SrtMode.valueOf(p.getString("mode", d.mode.name)!!) }.getOrDefault(d.mode),
            host = p.getString("host", d.host) ?: d.host,
            port = p.getInt("port", d.port),
            latencyMs = p.getInt("latency", d.latencyMs),
            passphrase = p.getString("passphrase", d.passphrase) ?: d.passphrase,
            keyLength = p.getInt("keyLength", d.keyLength),
            streamId = p.getString("streamId", d.streamId) ?: d.streamId,
            connectTimeoutSec = p.getInt("timeout", d.connectTimeoutSec),
            routeViaWifi = p.getBoolean("routeViaWifi", d.routeViaWifi),
            decoderMode = runCatching { DecoderMode.valueOf(p.getString("decoder", d.decoderMode.name)!!) }.getOrDefault(d.decoderMode),
        )
    }

    fun save(context: Context, c: StreamConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", c.name)
            .putString("mode", c.mode.name)
            .putString("host", c.host)
            .putInt("port", c.port)
            .putInt("latency", c.latencyMs)
            .putString("passphrase", c.passphrase)
            .putInt("keyLength", c.keyLength)
            .putString("streamId", c.streamId)
            .putInt("timeout", c.connectTimeoutSec)
            .putBoolean("routeViaWifi", c.routeViaWifi)
            .putString("decoder", c.decoderMode.name)
            .apply()
    }
}
