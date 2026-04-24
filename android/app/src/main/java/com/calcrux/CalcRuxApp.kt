package com.calcrux

import android.app.Application
import com.calcrux.data.RustBridge
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CalcRuxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-load the Rust JNI library on a background thread so the first
        // swipe to the Convert tab is instant (library is already in memory).
        Thread { runCatching { RustBridge.version() } }
            .apply { isDaemon = true; start() }
    }
}
