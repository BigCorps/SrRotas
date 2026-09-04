package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyCoordinator.hydrateRuntime(this)
        OwnUiCaptureGuard0212.install(this)
        ConfigurationBackOverlay0212.install(this)
        VersionBadgeUpdater.install(this)
        BetaTelemetry.install(this)
        PushManager.initialize(this)
        OfferNotificationPreferenceWatcher0262.install(this)
        NowPanelPolish0262.install(this)
        FieldValidationPolish0263.install(this)

        CostProfileSync.refreshOrFlush(this)

        // Tentativa coalescida no startup; filas continuam locais se estiver offline.
        SyncCoordinator.sync(this)
    }
}
