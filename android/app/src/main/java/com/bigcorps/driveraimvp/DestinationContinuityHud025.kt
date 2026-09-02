package com.srrotas.app

import android.content.Context

/** Preferência isolada: o sinal de continuidade não entra no veredito financeiro. */
object DestinationContinuityHud025 {
    private const val PREFS = "sr_rotas_025_hud"
    private const val KEY_ENABLED = "destination_continuity_hud_enabled"

    fun enabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun fingerprint(context: Context, localOfferId: String): String =
        if (!enabled(context)) "off"
        else DestinationContinuityClient0211.fingerprint(localOfferId)
}
