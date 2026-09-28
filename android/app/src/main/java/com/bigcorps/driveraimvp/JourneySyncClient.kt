package com.srrotas.app

import android.content.Context

/**
 * Compatibilidade 0.20 + saneamento 0.33.8.
 *
 * Toda sincronização continua passando pelo SyncCoordinator único.
 * Antes do flush, o reparador 0.33.8 retira da fila de rede somente
 * exposures cujo payload viola de forma determinística o contrato atual.
 * Os registros ficam preservados localmente em sync_state=2.
 */
object JourneySyncClient {
    fun flush(context: Context) {
        val app = context.applicationContext
        ExposureQueueRepair0338.run(app)
        SyncCoordinator.sync(app)
    }
}
