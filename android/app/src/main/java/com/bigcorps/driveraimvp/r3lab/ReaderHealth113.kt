package com.srrotas.app.r3lab

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject

/**
 * Issue #10 — saúde REAL do leitor da jornada, independente do M1 legado e da preferência salva.
 *
 * O R3 grava um heartbeat (tick, evento, screenshot, OCR, conexão do serviço, OCR travado, fallback)
 * em preferências privadas, que sobrevivem à morte do processo. Um monitor no processo principal
 * (não dentro do R3) avalia a máquina de estados abaixo e avisa o motorista: uma jornada aberta nunca
 * fica silenciosamente sem leitor. Só códigos e idades em segundos — sem OCR, endereço ou imagem.
 */
enum class ReaderState { NOT_IN_JOURNEY, PAUSED, ACTIVE_R3, R3_STALLED, FALLBACK_AWAITING_USER, ACTIVE_M1, NO_READER }

data class ReaderHealthInputs(
    val journeyOpen: Boolean,
    val journeyObserving: Boolean,
    val m1ProjectionActive: Boolean,
    val r3Selected: Boolean,
    val r3Effective: Boolean,
    val fallbackActive: Boolean,
    val serviceConfigured: Boolean,
    val serviceConnected: Boolean,
    val tickAgeMs: Long?,
    val ocrPoisoned: Boolean,
)

data class ReaderVerdict(val state: ReaderState, val reason: String) {
    val needsUser get() = state == ReaderState.R3_STALLED || state == ReaderState.FALLBACK_AWAITING_USER || state == ReaderState.NO_READER
}

object ReaderHealthRules113 {
    const val TICK_STALE_MS = 15_000L

    fun evaluate(i: ReaderHealthInputs): ReaderVerdict = when {
        !i.journeyOpen -> ReaderVerdict(ReaderState.NOT_IN_JOURNEY, "no_journey")
        !i.journeyObserving -> ReaderVerdict(ReaderState.PAUSED, "journey_paused")
        i.m1ProjectionActive -> ReaderVerdict(ReaderState.ACTIVE_M1, "m1_projection_active")
        i.fallbackActive -> ReaderVerdict(ReaderState.FALLBACK_AWAITING_USER, "fallback_needs_m1_authorization")
        i.r3Effective -> when {
            i.ocrPoisoned -> ReaderVerdict(ReaderState.R3_STALLED, "ocr_stuck_restart_required")
            !i.serviceConfigured -> ReaderVerdict(ReaderState.R3_STALLED, "accessibility_disabled")
            !i.serviceConnected -> ReaderVerdict(ReaderState.R3_STALLED, "accessibility_not_connected")
            i.tickAgeMs == null || i.tickAgeMs > TICK_STALE_MS -> ReaderVerdict(ReaderState.R3_STALLED, "r3_heartbeat_stale")
            else -> ReaderVerdict(ReaderState.ACTIVE_R3, "r3_alive")
        }
        i.r3Selected -> ReaderVerdict(ReaderState.NO_READER, "r3_selected_but_unavailable")
        else -> ReaderVerdict(ReaderState.NO_READER, "m1_not_capturing")
    }
}

/** Heartbeat persistido do R3 (idades por relógio de parede; sobrevive ao processo). */
object ReaderHeartbeat113 {
    private const val PREFS = "reader_health_113"
    @Volatile private var lastTickWriteAt = 0L
    @Volatile var connected = false; private set
    @Volatile var poisoned = false; private set
    @Volatile var lastTickWall = 0L; private set
    @Volatile private var lastScreenshotWall = 0L
    @Volatile private var lastOcrWall = 0L

    /** Captura/OCR do R3 (memória; vão ao disco no tick de 10 s). */
    fun screenshotOk() { lastScreenshotWall = System.currentTimeMillis() }
    fun ocrDone() { lastOcrWall = System.currentTimeMillis() }

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun processStarted(c: Context) { p(c).edit().putLong("process_started_at", System.currentTimeMillis()).apply() }
    fun event(c: Context, key: String) { p(c).edit().putLong("${key}_at", System.currentTimeMillis()).putInt("${key}_count", p(c).getInt("${key}_count", 0) + 1).apply() }

    fun serviceConnected(c: Context) { connected = true; event(c, "service_connected") }
    fun serviceDisconnected(c: Context, how: String) { connected = false; p(c).edit().putString("last_disconnect", how.take(40)).apply(); event(c, "service_disconnected") }

    /** Tick do R3: memória a cada tick; disco no máximo a cada 10 s. */
    fun tick(c: Context, state: String, block: String?) {
        val now = System.currentTimeMillis()
        lastTickWall = now
        if (now - lastTickWriteAt < 10_000L) return
        lastTickWriteAt = now
        p(c).edit().putLong("tick_at", now).putString("r3_state", state.take(30)).putString("r3_block", (block ?: "").take(40))
            .apply { if (lastScreenshotWall > 0) putLong("screenshot_ok_at", lastScreenshotWall) }
            .apply { if (lastOcrWall > 0) putLong("ocr_done_at", lastOcrWall) }.apply()
    }

    fun setPoisoned(c: Context, value: Boolean) { if (poisoned != value) { poisoned = value; p(c).edit().putBoolean("poisoned", value).apply(); if (value) event(c, "ocr_stuck") } }

    fun notice(c: Context, kind: String, posted: Boolean, blockedReason: String?) {
        p(c).edit().putString("last_notice_kind", kind).putBoolean("last_notice_posted", posted)
            .putString("last_notice_blocked", blockedReason ?: "").putLong("last_notice_at", System.currentTimeMillis()).apply()
    }

    fun notificationsAllowed(c: Context): Boolean = runCatching {
        val nm = c.getSystemService(android.app.NotificationManager::class.java) ?: return@runCatching false
        (android.os.Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
            nm.areNotificationsEnabled()
    }.getOrDefault(false)

    fun lastEventAt(c: Context, key: String) = p(c).getLong("${key}_at", 0L)

    fun bubble(c: Context, what: String) { p(c).edit().putString("bubble_last", what.take(40)).apply(); event(c, "bubble_$what") }

    /** Idade do último tick: memória (mesmo processo) ou disco (processo recriado). */
    fun tickAgeMs(c: Context, now: Long = System.currentTimeMillis()): Long? {
        val at = maxOf(lastTickWall, p(c).getLong("tick_at", 0L))
        return if (at <= 0L) null else (now - at).coerceAtLeast(0L)
    }

    /** Simula processo recriado: zera só a memória (o disco permanece). */
    internal fun resetMemoryForTest() { connected = false; poisoned = false; lastTickWall = 0L; lastTickWriteAt = 0L; lastScreenshotWall = 0L; lastOcrWall = 0L }

    /**
     * Classe da falha para o diagnóstico (issue #10, etapa 1.6): distingue serviço parado, OCR travado,
     * ausência de captura, permissão e janela flutuante. Pura (testável).
     */
    fun failureClass(v: ReaderVerdict?, overlayAllowed: Boolean, notificationsAllowed: Boolean, bubbleExpected: Boolean,
                     bubbleAttached: Boolean, screenshotAgeMs: Long?, r3Active: Boolean): String = when {
        v == null -> "unknown"
        v.reason == "ocr_stuck_restart_required" -> "ocr_stuck"
        v.reason == "accessibility_disabled" -> "permission_accessibility"
        v.reason == "accessibility_not_connected" || v.reason == "r3_heartbeat_stale" -> "service_stopped"
        !notificationsAllowed && v.needsUser -> "permission_notifications" // o aviso de "sem leitor" não chega
        v.state == ReaderState.FALLBACK_AWAITING_USER || v.state == ReaderState.NO_READER -> "no_reader"
        !overlayAllowed && bubbleExpected -> "permission_overlay"
        bubbleExpected && !bubbleAttached -> "overlay_missing"
        r3Active && (screenshotAgeMs == null || screenshotAgeMs > 60_000L) -> "no_capture_recent"
        else -> "ok"
    }

    /** OCR travado é estado do PROCESSO (a Task morre com ele): só vale o valor em memória. */
    fun persistedPoisoned(@Suppress("UNUSED_PARAMETER") c: Context) = poisoned

    fun toJson(c: Context, verdict: ReaderVerdict?, now: Long = System.currentTimeMillis()): JSONObject {
        val prefs = p(c)
        fun age(key: String) = prefs.getLong(key, 0L).takeIf { it > 0 }?.let { (now - it) / 1000 }
        return JSONObject().put("schema", "sr-reader-health-113")
            .put("state", verdict?.state?.name).put("reason", verdict?.reason)
            .put("selected_mode", R3Mode.selected(c)).put("r3_requested", R3Mode.primaryRequested(c))
            .put("r3_eligible", R3Mode.primaryEligible(c)).put("r3_effective", R3Mode.effectivePrimary(c))
            .put("service_configured", R3Mode.accessibilityEnabled(c)).put("service_connected_now", connected)
            .put("supervisor_loaded", R3Mode.supervisorLoaded)
            .put("tick_age_s", tickAgeMs(c, now)?.div(1000)).put("r3_state", prefs.getString("r3_state", null)).put("r3_block", prefs.getString("r3_block", null))
            .put("ocr_poisoned", persistedPoisoned(c))
            .put("process_age_s", age("process_started_at"))
            .put("service_connected_age_s", age("service_connected_at")).put("service_connected_count", prefs.getInt("service_connected_count", 0))
            .put("service_disconnected_age_s", age("service_disconnected_at")).put("service_disconnected_count", prefs.getInt("service_disconnected_count", 0))
            .put("last_disconnect", prefs.getString("last_disconnect", null))
            .put("ocr_stuck_age_s", age("ocr_stuck_at"))
            .put("fallback_reason", R3Mode.fallback(c)?.reason).put("fallback_age_s", R3Mode.fallback(c)?.atMs?.takeIf { it > 0 }?.let { (now - it) / 1000 })
            .put("fallback_this_journey", R3Mode.fallbackActive(c))
            .put("last_notice_kind", prefs.getString("last_notice_kind", null)).put("last_notice_posted", if (prefs.contains("last_notice_posted")) prefs.getBoolean("last_notice_posted", false) else JSONObject.NULL)
            .put("last_notice_blocked", prefs.getString("last_notice_blocked", null)).put("last_notice_age_s", age("last_notice_at"))
            .put("bubble_last", prefs.getString("bubble_last", null)).put("bubble_reattached_count", prefs.getInt("bubble_reattached_count", 0))
            .put("bubble_add_failed_count", prefs.getInt("bubble_add_failed_count", 0))
            .put("r3_live_state", R3LabTelemetry.state).put("r3_live_block", R3LabTelemetry.lastBlock)
            .put("r3_targets_platforms", R3LabTelemetry.lastTargets.split(',').map { it.trim().trimEnd('*') }.filter { it.isNotBlank() }.distinct().joinToString(","))
            .put("r3_counters", JSONObject(R3LabTelemetry.snapshot().entries.take(80).associate { it.key to it.value }))
            .put("hud_offer_visible", com.srrotas.app.OverlayController.anyVisible())
            .put("hud_last_error", com.srrotas.app.OverlayController.lastError)
            .put("journey_bubble_attached", com.srrotas.app.JourneyBubbleController.isShowingAttached())
            .put("m1_projection_flag", runCatching { com.srrotas.app.SettingsRepository(c).isProjectionActive() }.getOrNull())
            .put("m1_projection_active", runCatching { ReaderHealthMonitor113.m1Alive(c) }.getOrNull())
            .put("r3_last_screenshot_age_s", maxOf(lastScreenshotWall, prefs.getLong("screenshot_ok_at", 0L)).takeIf { it > 0 }?.let { (now - it) / 1000 } ?: JSONObject.NULL)
            .put("r3_last_ocr_age_s", maxOf(lastOcrWall, prefs.getLong("ocr_done_at", 0L)).takeIf { it > 0 }?.let { (now - it) / 1000 } ?: JSONObject.NULL)
            .put("m1_capture_heartbeat_ok", runCatching { com.srrotas.app.CaptureHealthState0263.isHealthy(c, com.srrotas.app.SettingsRepository(c).currentJourneyId(), com.srrotas.app.SettingsRepository(c).isProjectionActive()) }.getOrNull() ?: JSONObject.NULL)
            .put("m1_last_reason", runCatching { com.srrotas.app.CaptureHealthState0263.lastReason(c).take(40) }.getOrNull())
            .put("overlay_permission", runCatching { android.provider.Settings.canDrawOverlays(c) }.getOrNull())
            .put("notifications_permission", notificationsAllowed(c))
            .put("restart_requested_age_s", age("restart_requested_at") ?: JSONObject.NULL)
            .put("failure_class", runCatching {
                val bubbleExpected = verdict != null && verdict.state != ReaderState.NOT_IN_JOURNEY && com.srrotas.app.JourneyUiPreferences(c).enabled()
                val shot = maxOf(lastScreenshotWall, prefs.getLong("screenshot_ok_at", 0L)).takeIf { it > 0 }?.let { now - it }
                failureClass(verdict, android.provider.Settings.canDrawOverlays(c), notificationsAllowed(c), bubbleExpected,
                    com.srrotas.app.JourneyBubbleController.isShowingAttached(), shot,
                    verdict?.state == ReaderState.ACTIVE_R3 && R3LabTelemetry.lastTargets.isNotBlank()) // só com Uber/99 visível
            }.getOrDefault("unknown"))
            .put("privacy", "Somente estados, códigos e idades em segundos.")
    }
}
