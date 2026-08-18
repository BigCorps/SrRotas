package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushManager.initialize(this)
    }
}
