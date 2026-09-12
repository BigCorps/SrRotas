package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Rastro local do caminho Radar -> HUD.
 *
 * Não envia telemetria para o backend e não grava endereços/OCR bruto.
 * Guarda apenas estágio, contagens, motivos e fingerprints não reversíveis.
 *
 * 0.27.0-RC2-DIAG1 amplia apenas a retenção deste rastro anonimizado e expõe
 * um snapshot agregado para diagnosticar ofertas que deveriam gerar HUD.
 */
object RadarHudTrace024 {
    private const val FILE_NAME = "radar_hud_024.ndjson"
    private const val MAX_BYTES = 1_500 * 1024L
    private const val KEEP_LINES = 2_400
    private const val DIAGNOSTIC_RECENT_EVENTS = 140
    private const val FAILURE_BEFORE_MS = 20_000L
    private const val FAILURE_AFTER_MS = 12_000L
    private const val MAX_FAILURE_EVENTS = 320

    enum class Stage {
        FRAME_CAPTURED,
        OCR_OK,
        SPATIAL_DIAGNOSTIC,
        SCREEN_CLASSIFIED,
        PARSED,
        PARSE_REJECTED,
        DISPATCH_INPUT,
        DEDUPE_ACCEPT,
        DEDUPE_REJECT_EXACT,
        DEDUPE_REJECT_FUZZY,
        OCR_FAIL,
        MANUAL_FAILURE,
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var activeScope: String = ""

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val journeyPrefix = runCatching {
            SettingsRepository(app).currentJourneyId().take(8)
        }.getOrDefault("")
        if (journeyPrefix.isBlank()) return

        val scope = "${BuildConfig.VERSION_CODE}|$journeyPrefix"
        if (activeScope == scope) return
        synchronized(this) {
            if (activeScope == scope) return@synchronized
            val prefs = app.getSharedPreferences("sr_radar_trace_scope_0270", Context.MODE_PRIVATE)
            val stored = prefs.getString("scope", "").orEmpty()
            if (stored != scope) {
                runCatching { File(app.filesDir, FILE_NAME).delete() }
                prefs.edit().putString("scope", scope).apply()
            }
            activeScope = scope
        }
    }

    fun record(
        stage: Stage,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val context = appContext ?: return
        val json = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("stage", stage.name)

        fields.forEach { (key, value) ->
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is Number, is Boolean, is String -> json.put(key, value)
                else -> json.put(key, value.toString())
            }
        }

        synchronized(this) {
            val file = File(context.filesDir, FILE_NAME)
            trimIfNeeded(file)
            runCatching {
                file.appendText(json.toString() + "\n")
            }
        }
    }

    fun recordOcr(
        chars: Int,
        blocks: Int,
        rawText: String,
    ) = record(
        Stage.OCR_OK,
        mapOf(
            "chars" to chars,
            "blocks" to blocks,
            "text_fp" to fingerprint(rawText),
        ),
    )

    fun recordRoute(
        platform: String?,
        candidate: Boolean,
        ownApp: Boolean,
        reason: String,
        offers: Int,
    ) = record(
        Stage.SCREEN_CLASSIFIED,
        mapOf(
            "platform" to platform.orEmpty(),
            "candidate" to candidate,
            "own_app" to ownApp,
            "reason" to reason.take(120),
            "offers" to offers,
        ),
    )

    fun markManualFailure(context: Context, source: String) {
        install(context)
        record(
            Stage.MANUAL_FAILURE,
            mapOf("source" to source.take(120)),
        )
    }

    fun recordOffer(
        stage: Stage,
        offer: RideOffer,
        reason: String? = null,
    ) = record(
        stage,
        buildMap {
            put("offer_fp", offerFingerprint(offer))
            put("platform", offer.platform.take(20))
            put("type", offer.offerType.take(32))
            put("service", offer.serviceType.take(32))
            put("fare_cents", (offer.fare * 100.0).toInt())
            put("pickup_dm", offer.pickupKm?.let { (it * 10.0).toInt() })
            put("trip_dm", offer.tripKm?.let { (it * 10.0).toInt() })
            put("total_dm", offer.totalKm?.let { (it * 10.0).toInt() })
            put("confidence", offer.confidence)
            reason?.let { put("reason", it.take(120)) }
        },
    )

    fun readRecent(limit: Int = 120): List<String> {
        val context = appContext ?: return emptyList()
        return readRecent(context, limit)
    }

    /**
     * Diagnóstico independente da instância do Service. Isso permite compartilhar
     * o rastro mesmo se a captura tiver sido encerrada depois da falha.
     */
    fun diagnosticSnapshot(
        context: Context,
        reliability: JSONObject? = null,
    ): JSONObject {
        val events = readObjects(context, KEEP_LINES)
        val recent = events.takeLast(DIAGNOSTIC_RECENT_EVENTS)
        val traceFailure = lastTraceFailure(events)
        val reliabilityFailure = lastReliabilityFailure(reliability)
        val failureMark = listOfNotNull(traceFailure, reliabilityFailure)
            .maxByOrNull { it.at }
        val failureAt = failureMark?.at
        val failureWindow = if (failureAt == null) {
            emptyList()
        } else {
            events.asSequence()
                .filter { event ->
                    val at = event.optLong("at", -1L)
                    at >= failureAt - FAILURE_BEFORE_MS &&
                        at <= failureAt + FAILURE_AFTER_MS
                }
                .toList()
                .takeLast(MAX_FAILURE_EVENTS)
        }

        val stageCounts = linkedMapOf<String, Int>()
        val screenReasons = linkedMapOf<String, Int>()
        val parseRejectReasons = linkedMapOf<String, Int>()
        var spatialSamples = 0
        var fareLinesZero = 0
        var farePresentClusterZero = 0
        var uberAnchorGeometryLt2 = 0
        var uberAnchorSamples = 0
        var ninetyNineAnchorGeometryLt2 = 0
        var ninetyNineAnchorSamples = 0
        var navigationNoiseSamples = 0

        events.forEach { event ->
            val stage = event.optString("stage")
            if (stage.isNotBlank()) {
                stageCounts[stage] = (stageCounts[stage] ?: 0) + 1
            }
            when (stage) {
                Stage.SCREEN_CLASSIFIED.name -> {
                    val reason = event.optString("reason").take(120)
                    if (reason.isNotBlank()) {
                        screenReasons[reason] = (screenReasons[reason] ?: 0) + 1
                    }
                }
                Stage.PARSE_REJECTED.name -> {
                    val reason = event.optString("reason").take(120)
                    if (reason.isNotBlank()) {
                        parseRejectReasons[reason] = (parseRejectReasons[reason] ?: 0) + 1
                    }
                }
                Stage.SPATIAL_DIAGNOSTIC.name -> {
                    spatialSamples++
                    val fareLines = event.optInt("fare_lines", 0)
                    val clusters = event.optInt("clusters", 0)
                    val geometryPairs = event.optInt("geometry_pairs", 0)
                    val uberAnchor = event.optBoolean("uber_anchor", false)
                    val ninetyNineAnchor = event.optBoolean("99_anchor", false)
                    if (fareLines == 0) fareLinesZero++
                    if (fareLines > 0 && clusters == 0) farePresentClusterZero++
                    if (uberAnchor) {
                        uberAnchorSamples++
                        if (geometryPairs < 2) uberAnchorGeometryLt2++
                    }
                    if (ninetyNineAnchor) {
                        ninetyNineAnchorSamples++
                        if (geometryPairs < 2) ninetyNineAnchorGeometryLt2++
                    }
                    if (event.optBoolean("navigation_noise", false)) {
                        navigationNoiseSamples++
                    }
                }
            }
        }

        return JSONObject().apply {
            put("schema", "sr-radar-hud-trace-024-rc32")
            put("retained_events", events.size)
            put("buffer_max_bytes", MAX_BYTES)
            put("buffer_keep_lines", KEEP_LINES)
            put("trace_scope", activeScope)
            put("last_manual_failure_at", failureAt ?: JSONObject.NULL)
            put("last_manual_failure_source", failureMark?.source ?: JSONObject.NULL)
            put("stage_counts", countsJson(stageCounts))
            put("screen_reason_counts", countsJson(screenReasons))
            put("parse_reject_reason_counts", countsJson(parseRejectReasons))
            put(
                "spatial_summary",
                JSONObject().apply {
                    put("samples", spatialSamples)
                    put("fare_lines_zero", fareLinesZero)
                    put("fare_present_cluster_zero", farePresentClusterZero)
                    put("uber_anchor_samples", uberAnchorSamples)
                    put("uber_anchor_geometry_pairs_lt_2", uberAnchorGeometryLt2)
                    put("99_anchor_samples", ninetyNineAnchorSamples)
                    put("99_anchor_geometry_pairs_lt_2", ninetyNineAnchorGeometryLt2)
                    put("navigation_noise_samples", navigationNoiseSamples)
                },
            )
            put("failure_window", JSONArray().apply { failureWindow.forEach { put(it) } })
            put("recent_events", JSONArray().apply { recent.forEach { put(it) } })
            put(
                "privacy",
                "Trace técnico anonimizado: sem OCR bruto, screenshot, endereço ou coordenada; fingerprints não reversíveis.",
            )
        }
    }

    fun clear() {
        val context = appContext ?: return
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
        }
    }

    private fun readRecent(context: Context, limit: Int): List<String> {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().takeLast(limit.coerceIn(1, KEEP_LINES))
        }.getOrDefault(emptyList())
    }

    private fun readObjects(context: Context, limit: Int): List<JSONObject> =
        readRecent(context, limit).mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }

    private fun countsJson(counts: Map<String, Int>): JSONObject =
        JSONObject().apply {
            counts.forEach { (key, value) -> put(key, value) }
        }

    private data class FailureMark(
        val at: Long,
        val source: String,
    )

    private fun lastTraceFailure(events: List<JSONObject>): FailureMark? =
        events.asReversed().firstNotNullOfOrNull { event ->
            if (event.optString("stage") != Stage.MANUAL_FAILURE.name) {
                null
            } else {
                val at = event.optLong("at", 0L)
                if (at <= 0L) null
                else FailureMark(at, event.optString("source").take(120))
            }
        }

    private fun lastReliabilityFailure(reliability: JSONObject?): FailureMark? {
        val events = reliability?.optJSONArray("recent_events") ?: return null
        var fallback: FailureMark? = null
        for (index in events.length() - 1 downTo 0) {
            val event = events.optJSONObject(index) ?: continue
            if (event.optString("type") != "manual_failure_mark") continue
            val at = event.optLong("at", 0L)
            if (at <= 0L) continue
            val source = event.optString("detail").take(120)
            val mark = FailureMark(at, source)
            if (fallback == null) fallback = mark
            // O botão "Reiniciar leitura" também chama markFailure antes da
            // recuperação. Para diagnóstico, priorizamos o clique explícito em
            // "Registrar falha" (source=notification).
            if (!source.startsWith("before_recovery:")) return mark
        }
        return fallback
    }

    private fun offerFingerprint(offer: RideOffer): String =
        fingerprint(
            listOf(
                offer.platform.lowercase(Locale.ROOT),
                offer.offerType,
                offer.serviceType,
                "%.2f".format(Locale.US, offer.fare),
                offer.pickupKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                offer.tripKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                offer.totalKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
            ).joinToString("|"),
        )

    private fun fingerprint(value: String): String {
        if (value.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val tail = runCatching {
            file.readLines().takeLast(KEEP_LINES)
        }.getOrDefault(emptyList())
        runCatching {
            file.writeText(
                if (tail.isEmpty()) "" else tail.joinToString("\n", postfix = "\n"),
            )
        }
    }
}
