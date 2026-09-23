package com.srrotas.app

import android.content.Context
import org.json.JSONObject

/**
 * 0.31.1 — estado auditável da interrupção/retomada da MediaProjection.
 *
 * O Android exige novo consentimento para uma nova sessão de captura. Por isso
 * este módulo NÃO tenta reutilizar token nem religar a projeção silenciosamente.
 * Ele preserva a jornada, registra a interrupção e permite que UI/notificação
 * ofereçam uma retomada explícita na mesma jornada.
 *
 * Não armazena OCR, screenshot, endereço, coordenada ou conteúdo da tela.
 */
object CaptureResilience0311 {
    private const val PREFS = "sr_capture_resilience_0311"

    private const val KEY_PENDING = "pending_recovery"
    private const val KEY_JOURNEY = "pending_journey_id"
    private const val KEY_INTERRUPTED_AT = "interrupted_at_ms"
    private const val KEY_LAST_REASON = "last_reason"
    private const val KEY_LAST_REQUEST_SOURCE = "last_request_source"
    private const val KEY_LAST_RESUMED_AT = "last_resumed_at_ms"
    private const val KEY_LAST_FAILED_AT = "last_failed_at_ms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Sincroniza o estado lógico da captura com jornada + flag oficial.
     * Pode ser chamado periodicamente e em broadcasts; é idempotente.
     */
    fun sync(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val repo = SettingsRepository(app)
        val journeyId = repo.currentJourneyId().takeIf(String::isNotBlank) ?: return
        val journey = LocalStore.get(app).journey(journeyId) ?: return
        val journeyOpen = journey.endedAt == null

        if (!journeyOpen || !ReaderLab027036.m1Enabled(app)) return

        if (repo.isProjectionActive()) {
            markResumedIfPending(app, journeyId, nowMs)
        } else {
            markInterruptedIfNeeded(
                app,
                journeyId,
                CaptureHealthState0263.lastReason(app).ifBlank { "projection_inactive" },
                nowMs,
            )
        }
    }

    fun needsRecovery(context: Context): Boolean {
        val app = context.applicationContext
        val repo = SettingsRepository(app)
        val journeyId = repo.currentJourneyId().takeIf(String::isNotBlank) ?: return false
        val journey = LocalStore.get(app).journey(journeyId) ?: return false
        return CaptureResilienceRules0311.shouldShowRecovery(
            journeyOpen = journey.endedAt == null,
            m1Enabled = ReaderLab027036.m1Enabled(app),
            projectionActive = repo.isProjectionActive(),
        )
    }

    fun markResumeRequested(
        context: Context,
        source: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val app = context.applicationContext
        sync(app, nowMs)
        if (!needsRecovery(app)) return
        val p = prefs(app)
        bump(p, "resume_requested")
        p.edit()
            .putString(KEY_LAST_REQUEST_SOURCE, source.take(80))
            .apply()
    }

    fun markResumeAuthorized(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return
        bump(p, "resume_authorized")
    }

    fun markResumeCancelled(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return
        bump(p, "resume_cancelled")
    }

    fun markResumeFailed(
        context: Context,
        reason: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return
        bump(p, "resume_failed")
        p.edit()
            .putLong(KEY_LAST_FAILED_AT, nowMs)
            .putString(KEY_LAST_REASON, reason.take(80))
            .apply()
    }

    fun toJson(context: Context, nowMs: Long = System.currentTimeMillis()): JSONObject {
        sync(context, nowMs)
        val p = prefs(context)
        val pending = p.getBoolean(KEY_PENDING, false)
        val interruptedAt = p.getLong(KEY_INTERRUPTED_AT, 0L)
        return JSONObject().apply {
            put("schema", "sr-capture-resilience-0311-v1")
            put("pending_recovery", pending)
            put("current_interruption_ms", if (pending) CaptureResilienceRules0311.durationMs(interruptedAt, nowMs) else 0L)
            put("interruptions", p.getInt("interruptions", 0))
            put("projection_stopped_by_system", p.getInt("reason_projection_stopped_by_system", 0))
            put("service_destroyed", p.getInt("reason_service_destroyed", 0))
            put("projection_inactive_other", p.getInt("reason_other", 0))
            put("resume_requested", p.getInt("resume_requested", 0))
            put("resume_authorized", p.getInt("resume_authorized", 0))
            put("resume_success", p.getInt("resume_success", 0))
            put("resume_cancelled", p.getInt("resume_cancelled", 0))
            put("resume_failed", p.getInt("resume_failed", 0))
            put("total_interruption_ms", p.getLong("total_interruption_ms", 0L))
            put("last_reason", p.getString(KEY_LAST_REASON, "") ?: "")
            put("last_request_source", p.getString(KEY_LAST_REQUEST_SOURCE, "") ?: "")
            put("last_resumed_at_ms", p.getLong(KEY_LAST_RESUMED_AT, 0L))
            put("last_failed_at_ms", p.getLong(KEY_LAST_FAILED_AT, 0L))
            put("same_journey_resume", true)
            put("silent_token_reuse", false)
            put("requires_user_consent_for_new_projection", true)
            put(
                "privacy",
                "Somente estado/counters de captura; sem OCR, screenshot, endereço, coordenada ou conteúdo de tela.",
            )
        }
    }

    private fun markInterruptedIfNeeded(
        context: Context,
        journeyId: String,
        reason: String,
        nowMs: Long,
    ) {
        val p = prefs(context)
        val samePending =
            p.getBoolean(KEY_PENDING, false) &&
                p.getString(KEY_JOURNEY, "").orEmpty() == journeyId

        if (samePending) {
            if (reason.isNotBlank() && reason != "projection_inactive") {
                p.edit().putString(KEY_LAST_REASON, reason.take(80)).apply()
            }
            return
        }

        bump(p, "interruptions")
        bump(p, "reason_${CaptureResilienceRules0311.reasonBucket(reason)}")
        p.edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_JOURNEY, journeyId)
            .putLong(KEY_INTERRUPTED_AT, nowMs)
            .putString(KEY_LAST_REASON, reason.take(80))
            .apply()
    }

    private fun markResumedIfPending(context: Context, journeyId: String, nowMs: Long) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return
        if (p.getString(KEY_JOURNEY, "").orEmpty() != journeyId) return

        val started = p.getLong(KEY_INTERRUPTED_AT, 0L)
        val downtime = CaptureResilienceRules0311.durationMs(started, nowMs)
        bump(p, "resume_success")
        p.edit()
            .putBoolean(KEY_PENDING, false)
            .putLong(KEY_LAST_RESUMED_AT, nowMs)
            .putLong("total_interruption_ms", p.getLong("total_interruption_ms", 0L) + downtime)
            .apply()
    }

    private fun bump(prefs: android.content.SharedPreferences, key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}

internal object CaptureResilienceRules0311 {
    fun shouldShowRecovery(
        journeyOpen: Boolean,
        m1Enabled: Boolean,
        projectionActive: Boolean,
    ): Boolean = journeyOpen && m1Enabled && !projectionActive

    fun reasonBucket(reason: String): String = when (reason) {
        "projection_stopped_by_system" -> "projection_stopped_by_system"
        "service_destroyed" -> "service_destroyed"
        else -> "other"
    }

    fun durationMs(startMs: Long, endMs: Long): Long =
        if (startMs <= 0L || endMs < startMs) 0L else endMs - startMs
}
