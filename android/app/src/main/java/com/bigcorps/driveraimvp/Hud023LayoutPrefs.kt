package com.srrotas.app

import android.content.Context

/**
 * Preferências locais de apresentação do HUD 0.23.
 *
 * Usa o mesmo arquivo e a mesma chave show_fare da 0.22.1 para preservar o que
 * já foi escolhido durante os testes. Distância e tempo entram desligados.
 */
object Hud023LayoutPrefs {
    private const val PREFS = "sr_rotas_0221_hud"
    private const val SHOW_FARE = "show_fare"
    private const val SHOW_DISTANCE = "show_distance_023"
    private const val SHOW_TOTAL_TIME = "show_total_time_023"

    data class State(
        val showFare: Boolean = false,
        val showDistance: Boolean = false,
        val showTotalTime: Boolean = false,
    ) {
        val hasOfferDetails: Boolean
            get() = showFare || showDistance || showTotalTime
    }

    fun load(context: Context): State {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            showFare = p.getBoolean(SHOW_FARE, false),
            showDistance = p.getBoolean(SHOW_DISTANCE, false),
            showTotalTime = p.getBoolean(SHOW_TOTAL_TIME, false),
        )
    }

    fun save(context: Context, state: State) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SHOW_FARE, state.showFare)
            .putBoolean(SHOW_DISTANCE, state.showDistance)
            .putBoolean(SHOW_TOTAL_TIME, state.showTotalTime)
            .apply()
    }
}
