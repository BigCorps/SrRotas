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

        // 0.33.3: este fluxo chama o SyncCoordinator oficial primeiro e,
        // somente no callback dele, recupera odômetro/energia pendentes.
        // Assim pendências de versões anteriores também se autocorrigem
        // depois de abrir o app com rede e sessão válidas.
        JourneyMetricsClient026.syncPending(this)
    }
}
