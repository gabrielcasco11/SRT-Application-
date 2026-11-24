package com.gabriel.srtmonitor

import android.app.Application
import com.gabriel.srtmonitor.net.WifiMonitor
import io.github.thibaultbee.srtdroid.core.Srt

class SrtApp : Application() {
    lateinit var wifi: WifiMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        Srt.startUp()
        runCatching { Srt.setLogLevel(7) } // LOG_DEBUG: handshake details go to logcat
        wifi = WifiMonitor(this)
    }
}
