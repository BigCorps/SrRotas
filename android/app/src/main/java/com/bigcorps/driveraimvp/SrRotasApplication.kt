package com.srrotas.app

import android.app.Application

class SrRotasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyCoordinator.hydrateRuntime(this)
        VersionBadgeUpdater.install(this)
        BetaTelemetry.install(this)
        PushManager.initialize(this)

        CostProfileSync.refreshOrFlush(this)

        // 0.20: tentativa única e coalescida no startup. Se estiver offline,
        // nada é apagado; a fila será tentada novamente no próximo onResume
        // ou em "Sincronizar agora".
        SyncCoordinator.sync(this)
    }
}
