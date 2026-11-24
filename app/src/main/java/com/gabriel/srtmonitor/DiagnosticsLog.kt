package com.gabriel.srtmonitor

import android.os.Build

/** Reads this app's own log lines (libsrt + our classes) so they can be viewed/shared on the phone. */
object DiagnosticsLog {
    fun read(maxLines: Int = 500): String = try {
        val cmd = arrayOf(
            "logcat", "-d", "-v", "time", "-t", maxLines.toString(),
            "libsrt:V", "SrtReceiver:V", "StreamController:V", "*:S",
        )
        val proc = Runtime.getRuntime().exec(cmd)
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        val header = "SRT Monitor ${BuildConfig.VERSION_NAME} · ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n\n"
        header + text.ifBlank { "(no log lines yet — try connecting first)" }
    } catch (t: Throwable) {
        "Could not read log: ${t.message}"
    }
}
