package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A manual OCR review has its own process; avoid booting M1/sync/push there.
        val isReviewProcess = if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName().endsWith(":rescan")
        } else {
            runCatching { java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000').endsWith(":rescan") }.getOrDefault(false)
        }
        if (isReviewProcess) {
            // vc108: o MlKitInitProvider só roda no processo principal; sem isto todo OCR da
            // releitura falhava ("MlKitContext has not been initialized") e a tela ficava no status.
            runCatching { com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(this) }
            return
        }
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
        // Rehydrate the legacy idle Assistant after Android recreates our process.
        // The helper is a no-op when there is no active journey or it was disabled.
        ActiveAssistant026.ensureRunning(this)
        ReaderRecoverySupervisor027036.install(this)
        AccessResolver10B.sync(this)
        CostProfileSync.refreshOrFlush(this)
        JourneyMetricsClient026.syncPending(this)

        // Defaults false: em instalação nova isso não faz requests nem muda UI.
        RadarContextualIntegrationV1.syncRuntime(this)
    }
}
