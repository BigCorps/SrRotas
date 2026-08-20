package com.srrotas.app

import android.content.Context

/**
 * Compatibilidade 0.20.
 *
 * O código de jornada/exposição existente continua chamando
 * JourneySyncClient.flush(...), mas toda sincronização passa agora pelo
 * SyncCoordinator único. Isso evita duas filas concorrentes e a tempestade
 * de retries 400/404 observada na 0.19.
 */
object JourneySyncClient {
    fun flush(context: Context) {
        SyncCoordinator.sync(context.applicationContext)
    }
}
