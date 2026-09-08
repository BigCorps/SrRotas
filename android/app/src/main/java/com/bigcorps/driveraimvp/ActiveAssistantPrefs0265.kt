package com.srrotas.app

import android.content.Context

object ActiveAssistantPrefs0265 {
    private const val PREFS = "sr_active_assistant_0265"
    private const val KEY_INTERVAL = "ignored_interval_minutes"

    fun intervalMinutes(context: Context): Int =
        normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_INTERVAL, 12))

    fun setIntervalMinutes(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL, normalize(value)).apply()
    }

    fun normalize(value: Int): Int = when {
        value <= 10 -> 10
        value >= 15 -> 15
        else -> 12
    }

    /**
     * ActiveAssistant026 ainda usa janela histórica de 20 min. Ajustamos o
     * timestamp gravado para que a regra existente expire no intervalo escolhido
     * sem tocar no motor de ranking já validado.
     */
    fun adjustedLastSuggestion(nowMs: Long, minutes: Int): Long {
        val desired = normalize(minutes)
        val historical = 20
        return nowMs - (historical - desired).coerceAtLeast(0) * 60_000L
    }
}
