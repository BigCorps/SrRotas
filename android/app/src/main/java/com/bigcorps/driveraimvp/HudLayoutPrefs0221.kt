package com.srrotas.app

import android.content.Context

/** Preferências locais de apresentação introduzidas na 0.22.1. */
object HudLayoutPrefs0221 {
    private const val PREFS = "sr_rotas_0221_hud"
    private const val INDICATORS_ONLY_COMPACT = "indicators_only_compact"
    private const val SHOW_FARE = "show_fare"

    data class State(
        val indicatorsOnlyCompact: Boolean = false,
        val showFare: Boolean = false,
    )

    fun load(context: Context): State {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            indicatorsOnlyCompact = p.getBoolean(INDICATORS_ONLY_COMPACT, false),
            showFare = p.getBoolean(SHOW_FARE, false),
        )
    }

    fun save(context: Context, state: State) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INDICATORS_ONLY_COMPACT, state.indicatorsOnlyCompact)
            .putBoolean(SHOW_FARE, state.showFare)
            .apply()
    }
}
