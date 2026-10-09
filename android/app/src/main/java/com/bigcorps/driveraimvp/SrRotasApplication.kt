package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenshotRescanGateV1.seed(this)
        JourneyCoordinator.hydrateRuntime(this)
        ExposureQueueRepair0338.run(this, force=true)
        ReaderLab027036.migrateForConsolidation(this)
        OwnUiCaptureGuard0212.install(this)
        ConfigurationBackOverlay0212.install(this)
        VersionBadgeUpdater.install(this)
        BetaTelemetry.install(this)
        PushManager.initialize(this)
        OfferNotificationPreferenceWatcher0262.install(this)
        ActiveAssistantPolish0265.install(this)
        ReaderRecoverySupervisor027036.install(this)
        AccessResolver10B.sync(this)
        CostProfileSync.refreshOrFlush(this)
        JourneyMetricsClient026.syncPending(this)

        // Defaults false: em instalação nova isso não faz requests nem muda UI.
        RadarContextualIntegrationV1.syncRuntime(this)
    }
}
