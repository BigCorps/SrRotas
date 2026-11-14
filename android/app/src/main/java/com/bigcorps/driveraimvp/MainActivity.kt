package com.srrotas.app

/**
 * Nome público preservado para PendingIntent, notificações e integrações antigas.
 * Toda a implementação de tela vive no shell consolidado.
 */
class MainActivity : ConsolidatedMainActivity027037() {
    companion object {
        const val EXTRA_BUBBLE_ACTION = "com.srrotas.app.extra.BUBBLE_ACTION"
        const val EXTRA_RADAR_OPPORTUNITY_ID = "com.srrotas.app.extra.RADAR_OPPORTUNITY_ID"
        const val BUBBLE_ACTION_START = "start"
        /** Issue #10: recuperar o leitor da jornada ABERTA com o M1 (nunca inicia nem encerra jornada). */
        const val BUBBLE_ACTION_RECOVER_M1 = "recover_m1"
        const val BUBBLE_ACTION_HISTORY = "history"
        const val BUBBLE_ACTION_HISTORY_DIGITIZED = "history_digitized"
        const val BUBBLE_ACTION_NOW = "now"
        const val BUBBLE_ACTION_RADAR = "radar_contextual"
        const val BUBBLE_ACTION_RADAR_DEMO = "radar_contextual_demo"
    }
}
