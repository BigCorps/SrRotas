package com.srrotas.app

import android.content.Context

/**
 * Preferências isoladas do sinal de continuidade no destino.
 * O sinal nunca entra no veredito financeiro da corrida.
 */
object DestinationContinuityHud025 {
    private const val PREFS = "sr_rotas_025_hud"
    private const val KEY_ENABLED = "destination_continuity_hud_enabled"
    private const val KEY_POSITION = "destination_continuity_hud_position"

    const val POSITION_TOP = "top"
    const val POSITION_BOTTOM = "bottom"

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 0.26 passa a usar topo como padrão para tornar o sinal visível sem
     * depender de existir insight. Quem já preferir abaixo pode trocar no card
     * de Configurações.
     */
    fun position(context: Context): String =
        normalizePosition(prefs(context).getString(KEY_POSITION, POSITION_TOP))

    fun setPosition(context: Context, position: String) {
        prefs(context).edit().putString(KEY_POSITION, normalizePosition(position)).apply()
    }

    fun positionLabel(context: Context): String = when (position(context)) {
        POSITION_BOTTOM -> "ABAIXO"
        else -> "TOPO"
    }

    fun fingerprint(context: Context, localOfferId: String): String =
        if (!enabled(context)) {
            "off"
        } else {
            "${position(context)}:${DestinationContinuityClient0211.fingerprint(localOfferId)}"
        }

    internal fun normalizePosition(value: String?): String = when (value) {
        POSITION_BOTTOM -> POSITION_BOTTOM
        else -> POSITION_TOP
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
