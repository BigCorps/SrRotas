package com.srrotas.app

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Reader 2.0 — 0.32 short-lived accumulator + promotion-readiness shadow.
 *
 * Combina SOMENTE campos ausentes de observações compatíveis da mesma oferta
 * em uma janela curta. O estado é exclusivamente em memória e nunca publica
 * RideOffer, HUD, banco, backend ou admissão.
 *
 * A promoção continua desligada: "promotion ready" significa apenas que o
 * candidato atingiu o contrato mínimo para futura avaliação controlada.
 */
internal object Reader2Accumulator032 {
    private const val WINDOW_MS = 4_500L
    private const val MAX_ENTRIES = 12

    data class Candidate(
        val fare: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
        val pickupMinutes: Int?,
        val tripMinutes: Int?,
        val totalMinutes: Int?,
        val pickupLabel: String?,
        val destinationLabel: String?,
        val anchorY: Int,
        val confidence: Double,
        val observations: Int,
        val recoveredFields: Int,
        val conflictFree: Boolean,
        val coreComplete: Boolean,
        val promotionReady: Boolean,
    )

    data class Observation(
        val candidates: List<Candidate>,
        val sourceCandidates: Int,
        val coreComplete: Int,
        val promotionReady: Int,
    )

    private data class Entry(
        var fare: Double,
        var pickupKm: Double?,
        var tripKm: Double?,
        var totalKm: Double?,
        var pickupMinutes: Int?,
        var tripMinutes: Int?,
        var totalMinutes: Int?,
        var pickupLabel: String?,
        var destinationLabel: String?,
        var anchorY: Int,
        var confidence: Double,
        var observations: Int,
        var recoveredFields: Int,
        var conflictFree: Boolean,
        var firstSeenMs: Long,
        var lastSeenMs: Long,
        var coreCompletedByAccumulation: Boolean,
        var readyAnnounced: Boolean,
    )

    private val entries = mutableListOf<Entry>()

    @Volatile private var framesSeen = 0L
    @Volatile private var framesWithSourceCandidates = 0L
    @Volatile private var sourceCandidates = 0L
    @Volatile private var windowsStarted = 0L
    @Volatile private var windowsMerged = 0L
    @Volatile private var windowsExpired = 0L
    @Volatile private var incompatibleCandidates = 0L
    @Volatile private var recoveredFields = 0L
    @Volatile private var coreCompletedByAccumulation = 0L
    @Volatile private var promotionReadyTransitions = 0L
    @Volatile private var reader2OnlyAccumulated = 0L
    @Volatile private var reader2OnlyCoreComplete = 0L
    @Volatile private var promotionReadyReader2Only = 0L
    @Volatile private var matchedM1 = 0L
    @Volatile private var promotionReadyAgreesWithM1 = 0L
    @Volatile private var promotionReadyDisagreesWithM1 = 0L
    @Volatile private var lastSourceCandidateCount = 0
    @Volatile private var lastAccumulatedCandidateCount = 0
    @Volatile private var lastCoreComplete = 0
    @Volatile private var lastPromotionReady = 0

    @Synchronized
    fun observe(
        frame: Reader2ParallelRules031.FrameObservation,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Observation {
        framesSeen++
        purge(nowMs)

        val source = frame.candidates
        if (source.isNotEmpty()) framesWithSourceCandidates++
        sourceCandidates += source.size
        lastSourceCandidateCount = source.size

        val touched = linkedSetOf<Entry>()
        for (candidate in source) {
            val compatible = entries
                .filter { nowMs - it.lastSeenMs <= WINDOW_MS }
                .filter { compatible(it, candidate, frameHeight) }
                .maxByOrNull { compatibilityScore(it, candidate, frameHeight) }

            val entry = if (compatible == null) {
                windowsStarted++
                Entry(
                    fare = candidate.fare,
                    pickupKm = candidate.pickupKm,
                    tripKm = candidate.tripKm,
                    totalKm = candidate.totalKm,
                    pickupMinutes = candidate.pickupMinutes,
                    tripMinutes = candidate.tripMinutes,
                    totalMinutes = candidate.totalMinutes,
                    pickupLabel = candidate.pickupLabel,
                    destinationLabel = candidate.destinationLabel,
                    anchorY = candidate.anchorY,
                    confidence = candidate.confidence,
                    observations = 1,
                    recoveredFields = 0,
                    conflictFree = true,
                    firstSeenMs = nowMs,
                    lastSeenMs = nowMs,
                    coreCompletedByAccumulation = false,
                    readyAnnounced = false,
                ).also { entries += it }
            } else {
                windowsMerged++
                merge(compatible, candidate, nowMs)
                compatible
            }
            touched += entry
        }

        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
            windowsExpired++
        }

        val output = touched.map(::snapshot)
        lastAccumulatedCandidateCount = output.size
        lastCoreComplete = output.count { it.coreComplete }
        lastPromotionReady = output.count { it.promotionReady }
        return Observation(
            candidates = output,
            sourceCandidates = source.size,
            coreComplete = lastCoreComplete,
            promotionReady = lastPromotionReady,
        )
    }

    @Synchronized
    fun observeM1(observation: Observation, offers: List<RideOffer>) {
        if (observation.candidates.isEmpty() && offers.isEmpty()) return
        val m1 = offers.filter { it.platform.equals("uber", true) }.toMutableList()

        if (m1.isEmpty()) {
            reader2OnlyAccumulated += observation.candidates.size
            reader2OnlyCoreComplete += observation.candidates.count { it.coreComplete }
            promotionReadyReader2Only += observation.candidates.count { it.promotionReady }
            return
        }

        observation.candidates.forEach { r2 ->
            val best = m1.maxByOrNull { matchScore(r2, it) } ?: return@forEach
            val score = matchScore(r2, best)
            if (score < 4.0) return@forEach
            m1.remove(best)
            matchedM1++
            if (r2.promotionReady) {
                if (agreesOnCore(r2, best)) promotionReadyAgreesWithM1++
                else promotionReadyDisagreesWithM1++
            }
        }
    }

    @Synchronized
    fun resetRuntime() {
        entries.clear()
        framesSeen = 0
        framesWithSourceCandidates = 0
        sourceCandidates = 0
        windowsStarted = 0
        windowsMerged = 0
        windowsExpired = 0
        incompatibleCandidates = 0
        recoveredFields = 0
        coreCompletedByAccumulation = 0
        promotionReadyTransitions = 0
        reader2OnlyAccumulated = 0
        reader2OnlyCoreComplete = 0
        promotionReadyReader2Only = 0
        matchedM1 = 0
        promotionReadyAgreesWithM1 = 0
        promotionReadyDisagreesWithM1 = 0
        lastSourceCandidateCount = 0
        lastAccumulatedCandidateCount = 0
        lastCoreComplete = 0
        lastPromotionReady = 0
    }

    @Synchronized
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "sr-reader2-accumulator-032-v1")
        put("mode", "accumulator_shadow")
        put("window_ms", WINDOW_MS)
        put("shared_ocr", true)
        put("second_ocr", false)
        put("official_persistence", false)
        put("backend_effect", false)
        put("hud_effect", false)
        put("admission_influence", false)
        put("promotion_effect", false)
        put("frames_seen", framesSeen)
        put("frames_with_source_candidates", framesWithSourceCandidates)
        put("source_candidates", sourceCandidates)
        put("windows_started", windowsStarted)
        put("windows_merged", windowsMerged)
        put("windows_expired", windowsExpired)
        put("incompatible_candidates", incompatibleCandidates)
        put("fields_recovered", recoveredFields)
        put("core_completed_by_accumulation", coreCompletedByAccumulation)
        put("promotion_ready_transitions", promotionReadyTransitions)
        put("reader2_only_accumulated", reader2OnlyAccumulated)
        put("reader2_only_core_complete", reader2OnlyCoreComplete)
        put("promotion_ready_reader2_only", promotionReadyReader2Only)
        put("matched_m1", matchedM1)
        put("promotion_ready_agrees_with_m1", promotionReadyAgreesWithM1)
        put("promotion_ready_disagrees_with_m1", promotionReadyDisagreesWithM1)
        put("active_windows", entries.size)
        put("last_source_candidate_count", lastSourceCandidateCount)
        put("last_accumulated_candidate_count", lastAccumulatedCandidateCount)
        put("last_core_complete", lastCoreComplete)
        put("last_promotion_ready", lastPromotionReady)
        put(
            "promotion_ready_contract",
            "coreComplete && observations>=2 && conflictFree && confidence>=0.70; readiness is telemetry only",
        )
        put(
            "privacy",
            "Acumulador vive somente em memória; diagnóstico persiste apenas contadores/flags, nunca OCR bruto, endereços, coordenadas ou screenshot.",
        )
    }

    private fun merge(
        entry: Entry,
        incoming: Reader2ParallelRules031.Candidate,
        nowMs: Long,
    ) {
        val wasCore = coreComplete(entry)
        var recovered = 0

        fun <T> fill(current: T?, next: T?): T? {
            if (current == null && next != null) {
                recovered++
                return next
            }
            return current
        }

        entry.pickupKm = fill(entry.pickupKm, incoming.pickupKm)
        entry.tripKm = fill(entry.tripKm, incoming.tripKm)
        entry.totalKm = fill(entry.totalKm, incoming.totalKm)
        entry.pickupMinutes = fill(entry.pickupMinutes, incoming.pickupMinutes)
        entry.tripMinutes = fill(entry.tripMinutes, incoming.tripMinutes)
        entry.totalMinutes = fill(entry.totalMinutes, incoming.totalMinutes)
        entry.pickupLabel = fill(entry.pickupLabel, incoming.pickupLabel)
        entry.destinationLabel = fill(entry.destinationLabel, incoming.destinationLabel)

        // totalKm/totalMinutes são derivados. Quando os dois trechos passam a
        // existir, a soma substitui o total parcial (que em um frame incompleto
        // pode representar apenas a corrida). Nenhum campo observado é corrigido.
        if (entry.pickupKm != null && entry.tripKm != null) {
            entry.totalKm = round2(entry.pickupKm!! + entry.tripKm!!)
        }
        if (entry.pickupMinutes != null && entry.tripMinutes != null) {
            entry.totalMinutes = entry.pickupMinutes!! + entry.tripMinutes!!
        }

        entry.anchorY = ((entry.anchorY * entry.observations) + incoming.anchorY) / (entry.observations + 1)
        entry.confidence = max(entry.confidence, incoming.confidence)
        entry.observations++
        entry.lastSeenMs = nowMs
        entry.recoveredFields += recovered
        recoveredFields += recovered

        val nowCore = coreComplete(entry)
        if (!wasCore && nowCore && entry.observations >= 2) {
            entry.coreCompletedByAccumulation = true
            coreCompletedByAccumulation++
        }

        if (promotionReady(entry) && !entry.readyAnnounced) {
            entry.readyAnnounced = true
            promotionReadyTransitions++
        }
    }

    private fun snapshot(entry: Entry): Candidate = Candidate(
        fare = entry.fare,
        pickupKm = entry.pickupKm,
        tripKm = entry.tripKm,
        totalKm = entry.totalKm,
        pickupMinutes = entry.pickupMinutes,
        tripMinutes = entry.tripMinutes,
        totalMinutes = entry.totalMinutes,
        pickupLabel = entry.pickupLabel,
        destinationLabel = entry.destinationLabel,
        anchorY = entry.anchorY,
        confidence = entry.confidence,
        observations = entry.observations,
        recoveredFields = entry.recoveredFields,
        conflictFree = entry.conflictFree,
        coreComplete = coreComplete(entry),
        promotionReady = promotionReady(entry),
    )

    private fun coreComplete(entry: Entry): Boolean =
        entry.pickupKm != null && entry.pickupMinutes != null &&
            entry.tripKm != null && entry.tripMinutes != null &&
            !entry.pickupLabel.isNullOrBlank() && !entry.destinationLabel.isNullOrBlank() &&
            entry.totalMinutes != null

    private fun promotionReady(entry: Entry): Boolean =
        coreComplete(entry) &&
            entry.observations >= 2 &&
            entry.conflictFree &&
            entry.confidence >= 0.70

    private fun compatible(
        entry: Entry,
        incoming: Reader2ParallelRules031.Candidate,
        frameHeight: Int,
    ): Boolean {
        if (!close(entry.fare, incoming.fare, 0.10, 0.02)) return false
        val yTolerance = max(110, (frameHeight * 0.14).toInt())
        if (abs(entry.anchorY - incoming.anchorY) > yTolerance) return false

        val conflict =
            !compatibleNullable(entry.pickupKm, incoming.pickupKm, 0.35, 0.12) ||
                !compatibleNullable(entry.tripKm, incoming.tripKm, 0.55, 0.12) ||
                !compatibleNullable(entry.pickupMinutes?.toDouble(), incoming.pickupMinutes?.toDouble(), 2.0, 0.0) ||
                !compatibleNullable(entry.tripMinutes?.toDouble(), incoming.tripMinutes?.toDouble(), 3.0, 0.0) ||
                !compatibleLabel(entry.pickupLabel, incoming.pickupLabel) ||
                !compatibleLabel(entry.destinationLabel, incoming.destinationLabel)
        if (conflict) {
            entry.conflictFree = false
            incompatibleCandidates++
            return false
        }
        return true
    }

    private fun compatibilityScore(
        entry: Entry,
        incoming: Reader2ParallelRules031.Candidate,
        frameHeight: Int,
    ): Double {
        var score = 5.0
        val yDenominator = max(1, frameHeight).toDouble()
        score -= abs(entry.anchorY - incoming.anchorY) / yDenominator
        if (closeNullable(entry.pickupKm, incoming.pickupKm, 0.35, 0.12)) score += 1.0
        if (closeNullable(entry.tripKm, incoming.tripKm, 0.55, 0.12)) score += 1.0
        if (compatibleLabel(entry.pickupLabel, incoming.pickupLabel)) score += 0.5
        if (compatibleLabel(entry.destinationLabel, incoming.destinationLabel)) score += 0.5
        return score
    }

    private fun purge(nowMs: Long) {
        val before = entries.size
        entries.removeAll { nowMs - it.lastSeenMs > WINDOW_MS }
        windowsExpired += (before - entries.size).coerceAtLeast(0)
    }

    private fun matchScore(r2: Candidate, m1: RideOffer): Double {
        var score = 0.0
        if (close(r2.fare, m1.fare, 0.10, 0.02)) score += 4.0
        if (close(r2.pickupKm, m1.pickupKm, 0.35, 0.12)) score += 2.0
        if (close(r2.tripKm, m1.tripKm, 0.55, 0.12)) score += 2.0
        if (close(r2.pickupMinutes?.toDouble(), m1.pickupMinutes?.toDouble(), 2.0, 0.0)) score += 1.0
        if (close(r2.tripMinutes?.toDouble(), m1.tripMinutes?.toDouble(), 3.0, 0.0)) score += 1.0
        return score
    }

    private fun agreesOnCore(r2: Candidate, m1: RideOffer): Boolean =
        close(r2.fare, m1.fare, 0.10, 0.02) &&
            closeNullable(r2.pickupKm, m1.pickupKm, 0.35, 0.12) &&
            closeNullable(r2.tripKm, m1.tripKm, 0.55, 0.12) &&
            closeNullable(r2.pickupMinutes?.toDouble(), m1.pickupMinutes?.toDouble(), 2.0, 0.0) &&
            closeNullable(r2.tripMinutes?.toDouble(), m1.tripMinutes?.toDouble(), 3.0, 0.0) &&
            closeNullable(r2.totalMinutes?.toDouble(), m1.totalMinutes?.toDouble(), 3.0, 0.0) &&
            sameLabelNullable(r2.pickupLabel, m1.context?.pickupLabel) &&
            sameLabelNullable(r2.destinationLabel, m1.context?.destinationLabel)

    private fun close(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null || b == null) return false
        val tolerance = max(absolute, max(abs(a), abs(b)) * relative)
        return abs(a - b) <= tolerance
    }

    private fun compatibleNullable(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean =
        a == null || b == null || close(a, b, absolute, relative)

    private fun closeNullable(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null && b == null) return true
        return close(a, b, absolute, relative)
    }

    private fun compatibleLabel(a: String?, b: String?): Boolean =
        a.isNullOrBlank() || b.isNullOrBlank() || normalizeLabel(a) == normalizeLabel(b)

    private fun sameLabelNullable(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return true
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return normalizeLabel(a) == normalizeLabel(b)
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun normalizeLabel(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
}
