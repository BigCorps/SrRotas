package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reader 2.0 — primeiro shadow funcional da série 0.30.
 *
 * O shadow recebe a MESMA observação espacial já produzida pelo OCR do M1 no
 * caminho Uber. Não executa um segundo reconhecimento OCR, não captura uma
 * segunda imagem, não escreve na base oficial/backend e não controla HUD.
 *
 * O objetivo desta fase é medir:
 * - presença dos cinco campos patrimoniais;
 * - divergência M1 x interpretação shadow no mesmo frame;
 * - oscilação decimal x10 entre observações correlacionadas;
 * - cobertura real do handoff espacial antes de promover qualquer Reader 2.
 */
object Reader2Shadow030 {
    private const val PREFS = "sr_reader2_shadow_030"
    private const val MAX_PENDING = 96
    private const val MAX_RECENT = 64
    private const val TEMPORAL_WINDOW_MS = 7_000L

    internal data class ShadowCandidate(
        val localId: String,
        val targetKey: String,
        val capturedAtMs: Long,
        val offerTimePresent: Boolean,
        val fare: Double?,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
        val pickupMinutes: Int?,
        val tripMinutes: Int?,
        val totalMinutes: Int?,
        val pickupLabel: String?,
        val destinationLabel: String?,
        val evidenceFields: Set<String>,
        val lineCount: Int,
        val frameWidth: Int,
        val frameHeight: Int,
        val confidence: Double,
    )

    private val pendingByLocalId = linkedMapOf<String, ShadowCandidate>()
    private val recentByTarget = linkedMapOf<String, ShadowCandidate>()
    private val observedLocalIds = linkedSetOf<String>()

    /**
     * Chamado dentro do parser espacial Uber, depois do OCR M1 e antes do retorno
     * dos RideOffer. Apenas deriva uma observação shadow em memória.
     */
    @Synchronized
    internal fun captureSpatial(
        lines: List<SpatialOcrLine>,
        offers: List<RideOffer>,
        frameWidth: Int,
        frameHeight: Int,
    ) {
        if (offers.isEmpty()) return
        offers.forEach { offer ->
            Reader2ShadowRules030.fromSpatial(
                allLines = lines,
                offer = offer,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )?.let { candidate ->
                pendingByLocalId[offer.localId] = candidate
            }
        }
        trimPending()
    }

    /**
     * Chamado pela admissão 0.30. Persistimos somente contadores/flags técnicos.
     * Nenhum OCR bruto, endereço, coordenada ou screenshot é salvo aqui.
     */
    @Synchronized
    fun observe(
        context: Context,
        m1: RideOffer,
        stage: String,
        nowMs: Long,
    ) {
        if (!observedLocalIds.add(m1.localId)) return
        while (observedLocalIds.size > 256) {
            val first = observedLocalIds.firstOrNull() ?: break
            observedLocalIds.remove(first)
        }

        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        bump(prefs, "observations")
        recordM1Core(prefs, m1)

        val shadow = pendingByLocalId.remove(m1.localId)
        if (shadow == null) {
            bump(prefs, "without_spatial_handoff")
            prefs.edit()
                .putString("last_stage", stage)
                .putLong("last_at", System.currentTimeMillis())
                .putString("last_platform", m1.platform)
                .apply()
            return
        }

        bump(prefs, "spatial_handoff")
        if (Reader2ShadowRules030.coreComplete(shadow)) bump(prefs, "shadow_core_complete")
        shadow.evidenceFields.forEach { bump(prefs, "evidence_$it") }
        compareSameFrame(prefs, m1, shadow)

        val previous = recentByTarget[shadow.targetKey]
            ?.takeIf { nowMs - it.capturedAtMs <= TEMPORAL_WINDOW_MS }
        if (previous != null) {
            bump(prefs, "temporal_matches")
            val fields = Reader2ShadowRules030.decimalConflictFields(previous, shadow)
            if (fields.isNotEmpty()) {
                bump(prefs, "temporal_decimal_conflicts")
                fields.forEach { bump(prefs, "temporal_conflict_$it") }
                prefs.edit()
                    .putString("last_divergence", "temporal_decimal")
                    .putString("last_divergent_fields", fields.sorted().joinToString(","))
                    .putLong("last_at", System.currentTimeMillis())
                    .putString("last_stage", stage)
                    .putString("last_platform", m1.platform)
                    .apply()
            }
        }

        recentByTarget[shadow.targetKey] = shadow.copy(capturedAtMs = nowMs)
        purgeRecent(nowMs)
    }

    @Synchronized
    fun resetRuntime() {
        pendingByLocalId.clear()
        recentByTarget.clear()
        observedLocalIds.clear()
    }

    fun toJson(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("schema", "sr-reader2-shadow-030-v1")
            put("enabled", true)
            put("mode", "shadow")
            put("source", "shared_m1_spatial_ocr")
            put("second_ocr", false)
            put("official_persistence", false)
            put("backend_effect", false)
            put("hud_effect", false)
            put("admission_influence", false)
            put("observations", prefs.getInt("observations", 0))
            put("spatial_handoff", prefs.getInt("spatial_handoff", 0))
            put("without_spatial_handoff", prefs.getInt("without_spatial_handoff", 0))
            put("m1_core_complete", prefs.getInt("m1_core_complete", 0))
            put("shadow_core_complete", prefs.getInt("shadow_core_complete", 0))
            put("same_frame_matches", prefs.getInt("same_frame_matches", 0))
            put("same_frame_disagreements", prefs.getInt("same_frame_disagreements", 0))
            put("same_frame_decimal_disagreements", prefs.getInt("same_frame_decimal_disagreements", 0))
            put("temporal_matches", prefs.getInt("temporal_matches", 0))
            put("temporal_decimal_conflicts", prefs.getInt("temporal_decimal_conflicts", 0))
            put("core_offer_time", prefs.getInt("core_offer_time", 0))
            put("core_pickup_label", prefs.getInt("core_pickup_label", 0))
            put("core_pickup_minutes", prefs.getInt("core_pickup_minutes", 0))
            put("core_destination_label", prefs.getInt("core_destination_label", 0))
            put("core_total_minutes", prefs.getInt("core_total_minutes", 0))
            put("evidence_offer_time", prefs.getInt("evidence_offer_time", 0))
            put("evidence_fare", prefs.getInt("evidence_fare", 0))
            put("evidence_pickup_km", prefs.getInt("evidence_pickup_km", 0))
            put("evidence_trip_km", prefs.getInt("evidence_trip_km", 0))
            put("evidence_pickup_minutes", prefs.getInt("evidence_pickup_minutes", 0))
            put("evidence_trip_minutes", prefs.getInt("evidence_trip_minutes", 0))
            put("evidence_pickup_label", prefs.getInt("evidence_pickup_label", 0))
            put("evidence_destination_label", prefs.getInt("evidence_destination_label", 0))
            put("temporal_conflict_fare", prefs.getInt("temporal_conflict_fare", 0))
            put("temporal_conflict_pickup_km", prefs.getInt("temporal_conflict_pickup_km", 0))
            put("temporal_conflict_trip_km", prefs.getInt("temporal_conflict_trip_km", 0))
            put("temporal_conflict_total_km", prefs.getInt("temporal_conflict_total_km", 0))
            put("temporal_conflict_pickup_minutes", prefs.getInt("temporal_conflict_pickup_minutes", 0))
            put("temporal_conflict_trip_minutes", prefs.getInt("temporal_conflict_trip_minutes", 0))
            put("temporal_conflict_total_minutes", prefs.getInt("temporal_conflict_total_minutes", 0))
            put("last_divergence", prefs.getString("last_divergence", "") ?: "")
            put("last_divergent_fields", prefs.getString("last_divergent_fields", "") ?: "")
            put("last_stage", prefs.getString("last_stage", "") ?: "")
            put("last_at", prefs.getLong("last_at", 0L))
            put("last_platform", prefs.getString("last_platform", "") ?: "")
            put(
                "core_contract",
                "offerTime,pickupLabel,pickupMinutes,destinationLabel,totalMinutes",
            )
            put(
                "privacy",
                "Persistência contém somente contadores, nomes de campos divergentes e flags técnicas; sem OCR bruto/endereço/coordenada/screenshot.",
            )
        }
    }

    private fun recordM1Core(
        prefs: android.content.SharedPreferences,
        offer: RideOffer,
    ) {
        if (offer.observedAt.isNotBlank()) bump(prefs, "core_offer_time")
        if (!offer.context?.pickupLabel.isNullOrBlank()) bump(prefs, "core_pickup_label")
        if (offer.pickupMinutes != null) bump(prefs, "core_pickup_minutes")
        if (!offer.context?.destinationLabel.isNullOrBlank()) bump(prefs, "core_destination_label")
        if (offer.totalMinutes != null) bump(prefs, "core_total_minutes")
        if (
            offer.observedAt.isNotBlank() &&
            !offer.context?.pickupLabel.isNullOrBlank() &&
            offer.pickupMinutes != null &&
            !offer.context?.destinationLabel.isNullOrBlank() &&
            offer.totalMinutes != null
        ) {
            bump(prefs, "m1_core_complete")
        }
    }

    private fun compareSameFrame(
        prefs: android.content.SharedPreferences,
        m1: RideOffer,
        shadow: ShadowCandidate,
    ) {
        val comparisons = linkedMapOf<String, Pair<Double?, Double?>>()
        comparisons["fare"] = m1.fare to shadow.fare
        comparisons["pickup_km"] = m1.pickupKm to shadow.pickupKm
        comparisons["trip_km"] = m1.tripKm to shadow.tripKm
        comparisons["total_km"] = m1.totalKm to shadow.totalKm
        comparisons["pickup_minutes"] = m1.pickupMinutes?.toDouble() to shadow.pickupMinutes?.toDouble()
        comparisons["trip_minutes"] = m1.tripMinutes?.toDouble() to shadow.tripMinutes?.toDouble()
        comparisons["total_minutes"] = m1.totalMinutes?.toDouble() to shadow.totalMinutes?.toDouble()

        var anyCompared = false
        var anyDisagreement = false
        val decimalFields = mutableSetOf<String>()
        comparisons.forEach { (field, pair) ->
            val a = pair.first
            val b = pair.second
            if (a == null || b == null) return@forEach
            anyCompared = true
            if (!Reader2ShadowRules030.closeField(field, a, b)) {
                anyDisagreement = true
                bump(prefs, "same_frame_disagree_$field")
                if (Reader2ShadowRules030.factorTenish(a, b)) decimalFields += field
            }
        }

        val pickupCompared = !m1.context?.pickupLabel.isNullOrBlank() && !shadow.pickupLabel.isNullOrBlank()
        val destinationCompared = !m1.context?.destinationLabel.isNullOrBlank() && !shadow.destinationLabel.isNullOrBlank()
        if (pickupCompared) {
            anyCompared = true
            if (!Reader2ShadowRules030.sameLabel(m1.context?.pickupLabel, shadow.pickupLabel)) {
                anyDisagreement = true
                bump(prefs, "same_frame_disagree_pickup_label")
            }
        }
        if (destinationCompared) {
            anyCompared = true
            if (!Reader2ShadowRules030.sameLabel(m1.context?.destinationLabel, shadow.destinationLabel)) {
                anyDisagreement = true
                bump(prefs, "same_frame_disagree_destination_label")
            }
        }

        if (anyCompared && !anyDisagreement) bump(prefs, "same_frame_matches")
        if (anyDisagreement) bump(prefs, "same_frame_disagreements")
        if (decimalFields.isNotEmpty()) {
            bump(prefs, "same_frame_decimal_disagreements")
            prefs.edit()
                .putString("last_divergence", "same_frame_decimal")
                .putString("last_divergent_fields", decimalFields.sorted().joinToString(","))
                .apply()
        }
    }

    private fun bump(
        prefs: android.content.SharedPreferences,
        key: String,
    ) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun trimPending() {
        while (pendingByLocalId.size > MAX_PENDING) {
            val first = pendingByLocalId.entries.firstOrNull()?.key ?: break
            pendingByLocalId.remove(first)
        }
    }

    private fun purgeRecent(nowMs: Long) {
        recentByTarget.entries.removeAll { nowMs - it.value.capturedAtMs > TEMPORAL_WINDOW_MS }
        while (recentByTarget.size > MAX_RECENT) {
            val first = recentByTarget.entries.firstOrNull()?.key ?: break
            recentByTarget.remove(first)
        }
    }

    internal object Reader2ShadowRules030 {
        fun fromSpatial(
            allLines: List<SpatialOcrLine>,
            offer: RideOffer,
            frameWidth: Int,
            frameHeight: Int,
        ): ShadowCandidate? {
            if (!offer.platform.equals("uber", true)) return null
            val relevant = relevantLines(allLines, offer.rawText)
            if (relevant.isEmpty()) return null

            val detected = UberOfferDetector.detect(offer.rawText, offer.offerType) ?: return null
            var pickupKm: Double? = null
            var tripKm: Double? = null
            var pickupMinutes: Int? = null
            var tripMinutes: Int? = null

            if (detected.pairs.size >= 2) {
                pickupMinutes = detected.pairs[0].minutes
                pickupKm = detected.pairs[0].km
                tripMinutes = detected.pairs[1].minutes
                tripKm = detected.pairs[1].km
            } else if (detected.pairs.size == 1) {
                tripMinutes = detected.pairs[0].minutes
                tripKm = detected.pairs[0].km
            } else {
                val fallback = UberOfferDetector.fallbackDistancesAndMinutes(offer.rawText)
                val distances = fallback.first
                val minutes = fallback.second
                if (distances.size >= 2) {
                    pickupKm = distances[0]
                    tripKm = distances[1]
                } else if (distances.size == 1) {
                    tripKm = distances[0]
                }
                if (minutes.size >= 2) {
                    pickupMinutes = minutes[0]
                    tripMinutes = minutes[1]
                } else if (minutes.size == 1) {
                    tripMinutes = minutes[0]
                }
            }

            val totalKm = if (pickupKm != null && tripKm != null) pickupKm + tripKm else tripKm
            val totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else tripMinutes
            val context = OfferContextExtractor0221.extract(relevant, offer.observedAt, totalMinutes)
            val evidence = linkedSetOf<String>()
            if (offer.observedAt.isNotBlank()) evidence += "offer_time"
            if (detected.fare > 0.0) evidence += "fare"
            if (pickupKm != null) evidence += "pickup_km"
            if (tripKm != null) evidence += "trip_km"
            if (pickupMinutes != null) evidence += "pickup_minutes"
            if (tripMinutes != null) evidence += "trip_minutes"
            if (!context?.pickupLabel.isNullOrBlank()) evidence += "pickup_label"
            if (!context?.destinationLabel.isNullOrBlank()) evidence += "destination_label"

            val targetKey = targetKey(
                journeyId = offer.journeyId,
                platform = offer.platform,
                offerType = detected.offerType,
                pickupLabel = context?.pickupLabel,
                destinationLabel = context?.destinationLabel,
                rawText = offer.rawText,
            )

            return ShadowCandidate(
                localId = offer.localId,
                targetKey = targetKey,
                capturedAtMs = System.currentTimeMillis(),
                offerTimePresent = offer.observedAt.isNotBlank(),
                fare = detected.fare,
                pickupKm = pickupKm,
                tripKm = tripKm,
                totalKm = totalKm,
                pickupMinutes = pickupMinutes,
                tripMinutes = tripMinutes,
                totalMinutes = totalMinutes,
                pickupLabel = context?.pickupLabel,
                destinationLabel = context?.destinationLabel,
                evidenceFields = evidence,
                lineCount = relevant.size,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                confidence = ((detected.confidence + (context?.contextConfidence ?: 0.0)) / 2.0).coerceIn(0.0, 1.0),
            )
        }

        fun coreComplete(candidate: ShadowCandidate): Boolean =
            candidate.offerTimePresent &&
            !candidate.pickupLabel.isNullOrBlank() &&
                candidate.pickupMinutes != null &&
                !candidate.destinationLabel.isNullOrBlank() &&
                candidate.totalMinutes != null

        fun decimalConflictFields(a: ShadowCandidate, b: ShadowCandidate): Set<String> = buildSet {
            if (factorTenish(a.fare, b.fare)) add("fare")
            if (factorTenish(a.pickupKm, b.pickupKm)) add("pickup_km")
            if (factorTenish(a.tripKm, b.tripKm)) add("trip_km")
            if (factorTenish(a.totalKm, b.totalKm)) add("total_km")
            if (factorTenish(a.pickupMinutes?.toDouble(), b.pickupMinutes?.toDouble())) add("pickup_minutes")
            if (factorTenish(a.tripMinutes?.toDouble(), b.tripMinutes?.toDouble())) add("trip_minutes")
            if (factorTenish(a.totalMinutes?.toDouble(), b.totalMinutes?.toDouble())) add("total_minutes")
        }

        fun factorTenish(a: Double?, b: Double?): Boolean {
            if (a == null || b == null || a <= 0.0 || b <= 0.0) return false
            val ratio = max(a, b) / min(a, b)
            return ratio in 8.0..12.5
        }

        fun closeField(field: String, a: Double, b: Double): Boolean {
            val absolute = when (field) {
                "fare" -> 0.05
                "pickup_km" -> 0.20
                "trip_km" -> 0.35
                "total_km" -> 0.45
                "pickup_minutes" -> 1.0
                "trip_minutes" -> 2.0
                "total_minutes" -> 2.0
                else -> 0.05
            }
            val relative = when (field) {
                "fare" -> 0.01
                "pickup_km", "trip_km", "total_km" -> 0.08
                else -> 0.0
            }
            val tolerance = max(absolute, max(abs(a), abs(b)) * relative)
            return abs(a - b) <= tolerance
        }

        fun sameLabel(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return false
            return normalizeIdentity(a) == normalizeIdentity(b)
        }

        private fun relevantLines(allLines: List<SpatialOcrLine>, rawText: String): List<SpatialOcrLine> {
            val normalizedRaw = rawText.lines()
                .map(::normalizeLine)
                .filter(String::isNotBlank)
                .toSet()
            if (normalizedRaw.isEmpty()) return emptyList()
            return allLines.filter { normalizeLine(it.text) in normalizedRaw }
                .ifEmpty {
                    // Fallback conservador: se o cluster foi reconstruído com pequenas
                    // diferenças de whitespace, mantém somente linhas cujo texto aparece
                    // no card bruto. Nunca amplia para um segundo OCR.
                    val oneLine = normalizeLine(rawText)
                    allLines.filter { line ->
                        val value = normalizeLine(line.text)
                        value.length >= 3 && oneLine.contains(value)
                    }
                }
        }

        private fun targetKey(
            journeyId: String?,
            platform: String,
            offerType: String,
            pickupLabel: String?,
            destinationLabel: String?,
            rawText: String,
        ): String {
            val pickup = normalizeIdentity(pickupLabel.orEmpty())
            val destination = normalizeIdentity(destinationLabel.orEmpty())
            val identity = if (pickup.isNotBlank() && destination.isNotBlank()) {
                "$pickup|$destination"
            } else {
                rawText.lowercase()
                    .replace(Regex("[0-9]+(?:[.,][0-9]+)?"), "#")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(500)
            }
            return listOf(
                journeyId.orEmpty(),
                platform.lowercase(),
                offerType.lowercase(),
                identity.hashCode().toString(),
            ).joinToString("|")
        }

        private fun normalizeLine(value: String): String =
            value.lowercase()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

        private fun normalizeIdentity(value: String): String =
            value.lowercase()
                .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(180)
    }
}
