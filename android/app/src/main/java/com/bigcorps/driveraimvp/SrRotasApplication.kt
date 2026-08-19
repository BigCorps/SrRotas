package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyCoordinator.hydrateRuntime(this)
        VersionBadgeUpdater.install(this)
        BetaTelemetry.install(this)
        PushManager.initialize(this)
    }
}
