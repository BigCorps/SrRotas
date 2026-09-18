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
        FieldValidationPolish0264.install(this)
        FieldValidationPolish0265.install(this)
        ActiveAssistantPolish0265.install(this)
        BubbleRuntimePolish0265.install(this)
        ReleasePolish0270.install(this)
        Rc35UiPolish027035.install(this)
        ReaderRecoverySupervisor027036.install(this)
        Rc36ClosingPolish027036.install(this)

        CostProfileSync.refreshOrFlush(this)
        SyncCoordinator.sync(this)
    }
}
