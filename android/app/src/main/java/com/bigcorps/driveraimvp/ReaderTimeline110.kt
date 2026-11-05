package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * vc110 — linha do tempo da captura POR SESSÃO de jornada (sem conteúdo sensível).
 *
 * Alimentada pelos pontos que o leitor oficial já percorre (`RadarHudTrace024.record`,
 * `CaptureHealthState0263`, `OfferDispatcher`, `JourneyCoordinator`), sem alterar o
 * `MediaProjectionOcrService` congelado. Mede marcos (início da jornada → captura ativa →
 * primeiro quadro → primeiro OCR → primeiro candidato Uber → primeira oferta persistida →
 * HUD), durações de OCR e contadores de descarte por motivo. Quadros descartados NÃO são
 * ofertas perdidas: para medir ofertas use `KnownSample110` com quantidade conhecida.
 */
object ReaderTimeline110 {
    private const val PREFS = "sr_reader_timeline_110"
    private const val FLUSH_MS = 5_000L
    private val MILESTONES = listOf(
        "journey_started", "projection_active", "first_frame", "first_ocr", "first_uber_candidate",
        "first_offer_dispatched", "first_offer_persisted", "first_hud_offer",
    )

    private val lock = Any()
    private var loaded = false
    private var sessionId = ""
    private val marks = linkedMapOf<String, Long>()
    private val counts = linkedMapOf<String, Int>()
    private val reasons = linkedMapOf<String, Int>()
    private var lastFrameAt = 0L
    private var ocrSamples = 0
    private var ocrTotalMs = 0L
    private var ocrMaxMs = 0L
    private var lastFlush = 0L
    @Volatile private var app: Context? = null

    fun install(context: Context) { app = context.applicationContext; synchronized(lock) { load() } }

    /** Nova sessão a cada jornada iniciada. */
    fun journeyStarted(context: Context, journeyId: String) {
        app = context.applicationContext
        synchronized(lock) {
            loaded = true
            sessionId = journeyId.take(12); marks.clear(); counts.clear(); reasons.clear()
            lastFrameAt = 0L; ocrSamples = 0; ocrTotalMs = 0L; ocrMaxMs = 0L
            mark("journey_started")
            flush(force = true)
        }
    }

    fun projectionActive() = synchronized(lock) { load(); mark("projection_active"); bump("projection_active") }

    fun projectionInactive(reason: String) = synchronized(lock) {
        load(); bump("projection_inactive"); reason("projection_inactive:" + reason.take(40)); flush(force = true)
    }

    /** Ponto único: cada evento do trace oficial (sem textos; só campos numéricos/códigos). */
    fun onTrace(stage: String, fields: Map<String, Any?>) = synchronized(lock) {
        load()
        val now = System.currentTimeMillis()
        when (stage) {
            "FRAME_CAPTURED" -> { mark("first_frame"); bump("frames_captured"); lastFrameAt = now }
            "OCR_OK" -> {
                mark("first_ocr"); bump("ocr_ok")
                if (lastFrameAt > 0) { val d = now - lastFrameAt; if (d in 0..60_000) { ocrSamples++; ocrTotalMs += d; ocrMaxMs = maxOf(ocrMaxMs, d) } }
            }
            "OCR_FAIL" -> bump("ocr_fail")
            "SCREEN_CLASSIFIED" -> {
                val candidate = fields["candidate"] == true
                val platform = fields["platform"]?.toString().orEmpty()
                if (fields["own_app"] == true) bump("frames_own_app")
                if (candidate) { bump("candidate_frames"); if (platform == "uber") mark("first_uber_candidate") }
                else reason("screen:" + bucket(fields["reason"]?.toString().orEmpty()))
                if ((fields["offers"] as? Number)?.toInt() ?: 0 > 0) bump("frames_with_offers")
            }
            "OFFER_DROP" -> reason("drop:" + fields["drop_stage"] + ":" + fields["drop_reason"].toString().take(40))
            "DISPATCH_DROP" -> reason("dispatch:" + fields["reason"].toString().take(40))
            "DISPATCH_INPUT" -> if (((fields["offers"] as? Number)?.toInt() ?: 0) > 0) { mark("first_offer_dispatched"); bump("dispatch_batches") }
            "DEDUPE_ACCEPT" -> bump("dedupe_accept")
            "DEDUPE_REJECT_EXACT", "DEDUPE_REJECT_FUZZY" -> bump("dedupe_reject")
        }
        flush()
    }

    /** Oferta gravada: cobertura de busca/destino (só presença, nunca o texto). */
    fun offerPersisted(offer: RideOffer) = synchronized(lock) {
        load(); mark("first_offer_persisted"); bump("offers_persisted")
        val c = offer.context
        if (!c?.pickupLabel.isNullOrBlank()) bump("offers_with_pickup")
        if (!c?.destinationLabel.isNullOrBlank()) bump("offers_with_destination")
        if (!c?.pickupLabel.isNullOrBlank() && !c?.destinationLabel.isNullOrBlank()) bump("offers_with_both")
        if (c?.geocodeSource == OfferContextCompletion109.SOURCE) bump("offers_context_completed_from_card")
        flush(force = true)
    }

    fun hudOffer() = synchronized(lock) { load(); mark("first_hud_offer"); bump("hud_offers") }

    fun counters(): Map<String, Int> = synchronized(lock) { load(); LinkedHashMap(counts) }

    fun toJson(): JSONObject = synchronized(lock) {
        load()
        val start = marks["journey_started"]
        JSONObject().apply {
            put("schema", "sr-reader-timeline-110")
            put("session", sessionId)
            put("milestones_ms_after_journey_start", JSONObject().apply {
                MILESTONES.forEach { key -> put(key, marks[key]?.let { at -> if (start != null) at - start else JSONObject.NULL } ?: JSONObject.NULL) }
            })
            put("counters", JSONObject(counts as Map<*, *>))
            put("ocr_ms_avg", if (ocrSamples > 0) ocrTotalMs / ocrSamples else JSONObject.NULL)
            put("ocr_ms_max", if (ocrSamples > 0) ocrMaxMs else JSONObject.NULL)
            put("discard_reasons", JSONObject(reasons.entries.sortedByDescending { it.value }.take(25).associate { it.key to it.value }))
            put("note", "Quadros descartados não equivalem a ofertas perdidas; use known_sample_110 com quantidade conhecida.")
        }
    }

    private fun bucket(reason: String): String {
        val r = reason.lowercase()
        return when {
            r.isBlank() -> "unknown"
            r.contains("home") || r.contains("ocioso") -> "home_idle"
            r.contains("outra interface") -> "other_ui"
            r.contains("dividida") -> "split_screen"
            r.contains("aguardando frame") -> "waiting_full_frame"
            r.contains("99") -> "platform_99"
            r.contains("própria") || r.contains("propria") || r.contains("own") -> "own_app"
            else -> r.replace(Regex("[^a-z0-9_ ]"), "").trim().take(32).ifBlank { "other" }
        }
    }

    private fun mark(key: String) { if (!marks.containsKey(key)) marks[key] = System.currentTimeMillis() }
    private fun bump(key: String) { counts[key] = (counts[key] ?: 0) + 1 }
    private fun reason(key: String) { if (reasons.containsKey(key) || reasons.size < 60) reasons[key] = (reasons[key] ?: 0) + 1 }

    private fun load() {
        if (loaded) return
        loaded = true
        val p = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val json = runCatching { JSONObject(p.getString("state", "{}")) }.getOrNull() ?: return
        sessionId = json.optString("session")
        json.optJSONObject("marks")?.let { m -> m.keys().forEach { marks[it] = m.getLong(it) } }
        json.optJSONObject("counts")?.let { m -> m.keys().forEach { counts[it] = m.getInt(it) } }
        json.optJSONObject("reasons")?.let { m -> m.keys().forEach { reasons[it] = m.getInt(it) } }
        ocrSamples = json.optInt("ocr_samples"); ocrTotalMs = json.optLong("ocr_total_ms"); ocrMaxMs = json.optLong("ocr_max_ms")
    }

    private fun flush(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFlush < FLUSH_MS) return
        lastFlush = now
        val p = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        p.edit().putString("state", JSONObject().put("session", sessionId)
            .put("marks", JSONObject(marks as Map<*, *>)).put("counts", JSONObject(counts as Map<*, *>))
            .put("reasons", JSONObject(reasons as Map<*, *>)).put("ocr_samples", ocrSamples)
            .put("ocr_total_ms", ocrTotalMs).put("ocr_max_ms", ocrMaxMs).toString()).apply()
    }
}

/**
 * vc110 — teste com quantidade CONHECIDA de ofertas exibidas (homologação no SM-X626B).
 * O motorista inicia, opera normalmente e, ao encerrar, informa quantas ofertas a Uber exibiu.
 * O resultado compara com o que o Reader gravou no intervalo (sem conteúdo sensível).
 */
object KnownSample110 {
    private const val PREFS = "sr_known_sample_110"

    data class Result(val startedAt: Long, val endedAt: Long, val shown: Int, val persisted: Int, val withPickup: Int,
        val withDestination: Int, val withBoth: Int, val candidateFrames: Int, val ocr: Int) {
        val detectionRate get() = if (shown > 0) persisted.coerceAtMost(shown).toDouble() / shown else null
        fun toJson(): JSONObject = JSONObject().put("started_at", startedAt).put("ended_at", endedAt).put("shown", shown)
            .put("persisted", persisted).put("detection_rate", detectionRate ?: JSONObject.NULL)
            .put("with_pickup", withPickup).put("with_destination", withDestination).put("with_both", withBoth)
            .put("candidate_frames", candidateFrames).put("ocr", ocr)
    }

    fun running(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("started_at", 0L) > 0L

    fun start(context: Context) {
        val c = ReaderTimeline110.counters()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("started_at", System.currentTimeMillis())
            .putString("baseline", JSONObject(c as Map<*, *>).toString()).apply()
    }

    fun stop(context: Context, shown: Int): Result? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startedAt = p.getLong("started_at", 0L).takeIf { it > 0 } ?: return null
        val base = runCatching { JSONObject(p.getString("baseline", "{}")) }.getOrDefault(JSONObject())
        val now = ReaderTimeline110.counters()
        fun d(k: String) = ((now[k] ?: 0) - base.optInt(k, 0)).coerceAtLeast(0)
        val result = Result(startedAt, System.currentTimeMillis(), shown.coerceAtLeast(0), d("offers_persisted"), d("offers_with_pickup"),
            d("offers_with_destination"), d("offers_with_both"), d("candidate_frames"), d("ocr_ok"))
        val rows = runCatching { JSONArray(p.getString("results", "[]")) }.getOrDefault(JSONArray()).put(result.toJson())
        val trimmed = JSONArray((maxOf(0, rows.length() - 10) until rows.length()).map { rows.get(it) })
        p.edit().remove("started_at").remove("baseline").putString("results", trimmed.toString()).apply()
        return result
    }

    fun toJson(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().put("running", running(context))
            .put("results", runCatching { JSONArray(p.getString("results", "[]")) }.getOrDefault(JSONArray()))
    }
}
