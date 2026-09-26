package com.srrotas.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 0.33.6 — observabilidade local do Assistente Ativo.
 *
 * Objetivo: diferenciar com segurança "não ficou elegível", "avaliou sem
 * sugestão" e "sugestão foi comprometida mas o overlay não apareceu".
 *
 * Privacidade: não persiste região, OCR, screenshot, endereço, coordenadas,
 * tarifa ou conteúdo de oferta. Apenas estados, contadores e idades de tempo.
 */
object ActiveAssistantTelemetry0336 {
    private const val PREFS = "sr_active_assistant_telemetry_0336"
    private const val ENGINE_PREFS = "sr_active_assistant_026"
    private const val ENGINE_LAST_SUGGESTION = "last_suggestion_at"
    private const val OBSERVE_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false
    private var lastOverlayVisible = false
    private var lastFetching = false
    private var lastState = ""
    private var lastEvaluationSeen = 0L
    private var lastSuggestionSeen = 0L

    fun install(application: Application) {
        app = application.applicationContext
        if (running) return
        running = true
        main.post(observer)
    }

    fun markDecorated(context: Context) = mark(context, "decorated")
    fun markIgnore(context: Context) = mark(context, "ignore_clicked")
    fun markView(context: Context) = mark(context, "view_clicked")
    fun markOutsideDismiss(context: Context) = mark(context, "outside_dismiss")
    fun markPolishError(context: Context) = mark(context, "polish_error")

    fun toJson(context: Context): JSONObject {
        observe(context.applicationContext)
        val p = prefs(context)
        val engine = engineSnapshot(context)
        return JSONObject().apply {
            put("schema", "sr-active-assistant-telemetry-v1")
            put("enabled", ActiveAssistant026.isEnabled(context))
            put("engine_running", engine.running)
            put("fetching", engine.fetching)
            put("overlay_visible", engine.overlayVisible)
            put("overlay_permission", Settings.canDrawOverlays(context))
            put("current_state", p.getString("last_state", "unknown") ?: "unknown")
            put("last_event", p.getString("last_event", "none") ?: "none")
            put("last_event_at_ms", p.getLong("last_event_at_ms", 0L))
            put("latest_anchor_age_seconds", engine.anchorAgeSeconds)
            put("observer_ticks", p.getLong("observer_ticks", 0L))
            put("evaluation_episodes", p.getLong("evaluation_episodes", 0L))
            put("fetch_episodes", p.getLong("fetch_episodes", 0L))
            put("suggestion_committed_episodes", p.getLong("suggestion_committed_episodes", 0L))
            put("overlay_seen_episodes", p.getLong("overlay_seen_episodes", 0L))
            put("overlay_closed_episodes", p.getLong("overlay_closed_episodes", 0L))
            put("decorated_episodes", p.getLong("event_decorated", 0L))
            put("ignore_clicked", p.getLong("event_ignore_clicked", 0L))
            put("view_clicked", p.getLong("event_view_clicked", 0L))
            put("outside_dismiss", p.getLong("event_outside_dismiss", 0L))
            put("polish_errors", p.getLong("event_polish_error", 0L))
            put("eligible_engine_window_episodes", p.getLong("state_eligible_engine_window", 0L))
            put("waiting_idle_episodes", p.getLong("state_waiting_idle", 0L))
            put("evaluation_cooldown_episodes", p.getLong("state_evaluation_cooldown", 0L))
            put("suggestion_cooldown_episodes", p.getLong("state_suggestion_cooldown", 0L))
            put("ride_active_episodes", p.getLong("state_ride_active", 0L))
            put("no_journey_episodes", p.getLong("state_no_journey", 0L))
            put("overlay_permission_missing_episodes", p.getLong("state_overlay_permission_missing", 0L))
            put("exports_sensitive_content", false)
            put("exports_region", false)
            put("exports_ocr", false)
            put("exports_coordinates", false)
        }
    }

    private val observer = object : Runnable {
        override fun run() {
            val context = app
            if (context != null) runCatching { observe(context) }
                .onFailure { markPolishError(context) }
            if (running) main.postDelayed(this, OBSERVE_MS)
        }
    }

    private fun observe(context: Context) {
        val p = prefs(context)
        increment(context, "observer_ticks")

        val engine = engineSnapshot(context)
        val state = deriveState(context, engine)
        if (state != lastState) {
            lastState = state
            p.edit()
                .putString("last_state", state)
                .putLong("last_state_at_ms", System.currentTimeMillis())
                .apply()
            increment(context, "state_$state")
            mark(context, "state:$state", incrementEventCounter = false)
        }

        if (engine.evaluationMs > 0L && engine.evaluationMs != lastEvaluationSeen) {
            lastEvaluationSeen = engine.evaluationMs
            increment(context, "evaluation_episodes")
            mark(context, "evaluation_started", incrementEventCounter = false)
        }

        if (engine.suggestionMs > 0L && engine.suggestionMs != lastSuggestionSeen) {
            lastSuggestionSeen = engine.suggestionMs
            increment(context, "suggestion_committed_episodes")
            mark(context, "suggestion_committed", incrementEventCounter = false)
        }

        if (engine.fetching && !lastFetching) {
            increment(context, "fetch_episodes")
            mark(context, "fetch_started", incrementEventCounter = false)
        }
        lastFetching = engine.fetching

        if (engine.overlayVisible && !lastOverlayVisible) {
            increment(context, "overlay_seen_episodes")
            mark(context, "overlay_seen", incrementEventCounter = false)
        } else if (!engine.overlayVisible && lastOverlayVisible) {
            increment(context, "overlay_closed_episodes")
            mark(context, "overlay_closed", incrementEventCounter = false)
        }
        lastOverlayVisible = engine.overlayVisible
    }

    private data class EngineSnapshot(
        val running: Boolean,
        val fetching: Boolean,
        val overlayVisible: Boolean,
        val evaluationMs: Long,
        val suggestionMs: Long,
        val anchorMs: Long,
        val anchorAgeSeconds: Long,
    )

    private fun engineSnapshot(context: Context): EngineSnapshot {
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        val store = LocalStore.get(context)
        val latest = if (journeyId.isBlank()) null else store.recentOffers(100).firstOrNull { it.journeyId == journeyId }
        val anchorMs = if (journeyId.isBlank()) {
            0L
        } else {
            latest?.observedAt?.let(::epochMs)
                ?: store.journey(journeyId)?.startedAt?.let(::epochMs)
                ?: 0L
        }
        val now = System.currentTimeMillis()
        val suggestionMs = context.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE)
            .getLong(ENGINE_LAST_SUGGESTION, 0L)
        return EngineSnapshot(
            running = privateField<Boolean>(ActiveAssistant026, "running") ?: false,
            fetching = privateField<AtomicBoolean>(ActiveAssistant026, "fetching")?.get() ?: false,
            overlayVisible = privateField<View>(ActiveAssistant026, "overlay") != null,
            evaluationMs = privateField<Long>(ActiveAssistant026, "lastEvaluationMs") ?: 0L,
            suggestionMs = suggestionMs,
            anchorMs = anchorMs,
            anchorAgeSeconds = if (anchorMs > 0L) ((now - anchorMs).coerceAtLeast(0L) / 1_000L) else -1L,
        )
    }

    private fun deriveState(context: Context, engine: EngineSnapshot): String {
        if (!ActiveAssistant026.isEnabled(context)) return "disabled"
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        if (journeyId.isBlank()) return "no_journey"

        val snapshot = JourneyCoordinator.snapshot(context)
        if (snapshot.journeyState != JourneyOperationalState.ACTIVE) return "journey_not_active"
        if (snapshot.currentRide != null) return "ride_active"
        if (!Settings.canDrawOverlays(context)) return "overlay_permission_missing"
        if (engine.overlayVisible) return "overlay_visible"
        if (engine.fetching) return "fetching"

        val now = System.currentTimeMillis()
        if (!ActiveAssistantRules026.idleEnough(now, engine.anchorMs)) return "waiting_idle"
        if (!ActiveAssistantRules026.evaluationAllowed(now, engine.evaluationMs)) return "evaluation_cooldown"
        if (!ActiveAssistantRules026.suggestionAllowed(now, engine.suggestionMs)) return "suggestion_cooldown"
        return "eligible_engine_window"
    }

    private fun mark(
        context: Context,
        event: String,
        incrementEventCounter: Boolean = true,
    ) {
        val p = prefs(context)
        if (incrementEventCounter) increment(context, "event_${key(event)}")
        p.edit()
            .putString("last_event", event.take(80))
            .putLong("last_event_at_ms", System.currentTimeMillis())
            .apply()
    }

    private fun increment(context: Context, name: String) {
        val p = prefs(context)
        p.edit().putLong(name, p.getLong(name, 0L) + 1L).apply()
    }

    private fun key(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').take(60)

    private fun epochMs(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
            field.get(target) as? T
        }.getOrNull()
}
