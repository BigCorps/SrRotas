package com.srrotas.app

import android.app.Application

/**
 * Runtime consolidado pré-1.0.
 *
 * Regra: nenhuma camada visual de versão anterior é instalada aqui. A interface
 * atual vive nos componentes definitivos usados pelo shell 0.27.0 RC3.7.
 */
class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyCoordinator.hydrateRuntime(this)
        ReaderLab027036.migrateForConsolidation(this)

        // Infraestruturas funcionais sem mutação periódica das telas.
        OwnUiCaptureGuard0212.install(this)
        ConfigurationBackOverlay0212.install(this)
        VersionBadgeUpdater.install(this)
        BetaTelemetry.install(this)
        PushManager.initialize(this)
        OfferNotificationPreferenceWatcher0262.install(this)
        ActiveAssistantPolish0265.install(this)
        ReaderRecoverySupervisor027036.install(this)

        CostProfileSync.refreshOrFlush(this)
        SyncCoordinator.sync(this)
    }
}
