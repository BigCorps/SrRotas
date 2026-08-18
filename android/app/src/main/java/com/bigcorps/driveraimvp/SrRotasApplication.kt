package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BetaTelemetry.install(this)
        PushManager.initialize(this)
    }
}
