package com.srrotas.app

/**
 * Nome público preservado para PendingIntent, notificações e integrações antigas.
 * Toda a implementação de tela vive no shell consolidado.
 */
class MainActivity : ConsolidatedMainActivity027037() {
    companion object {
        const val EXTRA_BUBBLE_ACTION = "com.srrotas.app.extra.BUBBLE_ACTION"
        const val BUBBLE_ACTION_START = "start"
        const val BUBBLE_ACTION_HISTORY = "history"
        const val BUBBLE_ACTION_NOW = "now"
    }
}
