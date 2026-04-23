package com.keyflow.relay

import android.app.Application

class RelayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // If user had toggled service ON before last app-process death, restart it.
        val settings = Settings(this)
        if (settings.serviceEnabled) {
            ForwardingService.start(this)
        }
    }
}
