package com.srrotas.app

import android.content.Context

/**
 * Estado mínimo, local e não sensível da saúde da captura.
 *
 * Serve apenas para distinguir "jornada ativa + captura saudável" de uma
 * jornada que continua aberta mas perdeu a MediaProjection. Não armazena OCR,
 * tela, oferta ou localização.
 */
object CaptureHealthState0263 {
    private const val PREFS = "sr_capture_health_0263"
    private const val KEY_JOURNEY = "journey_id"
    private const val KEY_HEARTBEAT = "heartbeat_ms"
    private const val KEY_ACTIVE = "active"
    private const val KEY_REASON = "last_reason"
    const val FRESH_FOR_MS = 18_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markActive(context: Context, journeyId: String?) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_JOURNEY, journeyId.orEmpty())
            .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
            .putString(KEY_REASON, "")
            .apply()
    }

    fun heartbeat(context: Context, journeyId: String?) {
        val p = prefs(context)
        val stored = p.getString(KEY_JOURNEY, "").orEmpty()
        if (stored.isNotBlank() && !journeyId.isNullOrBlank() && stored != journeyId) return
        p.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_JOURNEY, journeyId.orEmpty())
            .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
            .apply()
    }

    fun markInactive(context: Context, reason: String) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_HEARTBEAT, 0L)
            .putString(KEY_REASON, reason.take(80))
            .apply()
    }

    fun isHealthy(
        context: Context,
        journeyId: String?,
        projectionFlag: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!projectionFlag || journeyId.isNullOrBlank()) return false
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        val storedJourney = p.getString(KEY_JOURNEY, "").orEmpty()
        if (storedJourney.isNotBlank() && storedJourney != journeyId) return false
        val heartbeat = p.getLong(KEY_HEARTBEAT, 0L)
        return heartbeat > 0L && nowMs >= heartbeat && nowMs - heartbeat <= FRESH_FOR_MS
    }

    fun lastReason(context: Context): String =
        prefs(context).getString(KEY_REASON, "").orEmpty()
}
