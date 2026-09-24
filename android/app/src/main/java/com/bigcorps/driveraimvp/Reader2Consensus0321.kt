package com.srrotas.app

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Reader 2.0 — 0.32.1 temporal consensus shadow.
 *
 * Objetivo: confirmar, em frames realmente separados, candidatos core-completos
 * que o M1 não publicou. Nenhuma oferta é persistida ou exibida nesta etapa.
 *
 * Regras duras:
 * - mesmo OCR compartilhado;
 * - somente candidatos já produzidos pelo Reader 2;
 * - nenhuma correção/invenção de valor;
 * - repetição muito próxima é tratada como duplicata de frame;
 * - conflito em campo core invalida a janela de consenso;
 * - readiness continua telemetria, sem efeito oficial.
 */
internal object Reader2Consensus0321 {
    private const val WINDOW_MS = 8_000L
    private const val MIN_DISTINCT_MS = 450L
    private const val MAX_ENTRIES = 16

    private data class Entry(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val pickupMinutes: Int,
        val tripMinutes: Int,
        val totalMinutes: Int,
        val pickupLabel: String,
        val destinationLabel: String,
        var anchorY: Int,
        var confidence: Double,
        var confirmations: Int,
        var firstSeenMs: Long,
        var lastSeenMs: Long,
        var lastAcceptedMs: Long,
        var conflictFree: Boolean,
        var readyAnnounced: Boolean,
    )

    private val entries = mutableListOf<Entry>()

    @Volatile private var framesSeen = 0L
    @Volatile private var coreCandidatesSeen = 0L
    @Volatile private var reader2OnlyCoreSeen = 0L
    @Volatile private var matchedM1CoreSeen = 0L
    @Volatile private var consensusWindowsStarted = 0L
    @Volatile private var confirmationsAccepted = 0L
    @Volatile private var duplicateFrameSuppressed = 0L
    @Volatile private var conflictsDetected = 0L
    @Volatile private var windowsExpired = 0L
    @Volatile private var consensusReadyTransitions = 0L
    @Volatile private var consensusReadyReader2Only = 0L
    @Volatile private var matchedM1Agrees = 0L
    @Volatile private var matchedM1Disagrees = 0L
    @Volatile private var lastReader2OnlyCore = 0
    @Volatile private var lastConsensusReady = 0

    @Synchronized
    fun observe(
        observation: Reader2Accumulator032.Observation,
        offers: List<RideOffer>,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        framesSeen++
        purge(nowMs)

        val m1 = offers.filter { it.platform.equals("uber", true) }.toMutableList()
        var reader2OnlyThisFrame = 0
        var readyThisFrame = 0

        observation.candidates.filter { it.coreComplete }.forEach { candidate ->
            coreCandidatesSeen++
            val best = m1.maxByOrNull { matchScore(candidate, it) }
            val matched = best?.takeIf { matchScore(candidate, it) >= 6.0 }
            if (matched != null) {
                m1.remove(matched)
                matchedM1CoreSeen++
                if (agreesOnCore(candidate, matched)) matchedM1Agrees++ else matchedM1Disagrees++
                return@forEach
            }

            reader2OnlyCoreSeen++
            reader2OnlyThisFrame++
            val probe = toProbe(candidate) ?: return@forEach

            val sameTarget = entries
                .filter { nowMs - it.lastSeenMs <= WINDOW_MS }
                .filter { probableSameTarget(it, probe, frameHeight) }
                .maxByOrNull { targetScore(it, probe, frameHeight) }

            if (sameTarget == null) {
                entries += Entry(
                    fare = probe.fare,
                    pickupKm = probe.pickupKm,
                    tripKm = probe.tripKm,
                    pickupMinutes = probe.pickupMinutes,
                    tripMinutes = probe.tripMinutes,
                    totalMinutes = probe.totalMinutes,
                    pickupLabel = probe.pickupLabel,
                    destinationLabel = probe.destinationLabel,
                    anchorY = probe.anchorY,
                    confidence = probe.confidence,
                    confirmations = 1,
                    firstSeenMs = nowMs,
                    lastSeenMs = nowMs,
                    lastAcceptedMs = nowMs,
                    conflictFree = true,
                    readyAnnounced = false,
                )
                consensusWindowsStarted++
                return@forEach
            }

            sameTarget.lastSeenMs = nowMs
            sameTarget.anchorY = ((sameTarget.anchorY * sameTarget.confirmations) + probe.anchorY) / (sameTarget.confirmations + 1)
            sameTarget.confidence = max(sameTarget.confidence, probe.confidence)

            if (!stableSameCore(sameTarget, probe)) {
                sameTarget.conflictFree = false
                conflictsDetected++
                return@forEach
            }

            if (nowMs - sameTarget.lastAcceptedMs < MIN_DISTINCT_MS) {
                duplicateFrameSuppressed++
                return@forEach
            }

            sameTarget.confirmations++
            sameTarget.lastAcceptedMs = nowMs
            confirmationsAccepted++

            if (ready(sameTarget) && !sameTarget.readyAnnounced) {
                sameTarget.readyAnnounced = true
                consensusReadyTransitions++
                consensusReadyReader2Only++
            }
            if (ready(sameTarget)) readyThisFrame++
        }

        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
            windowsExpired++
        }

        lastReader2OnlyCore = reader2OnlyThisFrame
        lastConsensusReady = readyThisFrame
    }

    @Synchronized
    fun resetRuntime() {
        entries.clear()
        framesSeen = 0
        coreCandidatesSeen = 0
        reader2OnlyCoreSeen = 0
        matchedM1CoreSeen = 0
        consensusWindowsStarted = 0
        confirmationsAccepted = 0
        duplicateFrameSuppressed = 0
        conflictsDetected = 0
        windowsExpired = 0
        consensusReadyTransitions = 0
        consensusReadyReader2Only = 0
        matchedM1Agrees = 0
        matchedM1Disagrees = 0
        lastReader2OnlyCore = 0
        lastConsensusReady = 0
    }

    @Synchronized
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "sr-reader2-consensus-0321-v1")
        put("mode", "consensus_shadow")
        put("window_ms", WINDOW_MS)
        put("min_distinct_ms", MIN_DISTINCT_MS)
        put("shared_ocr", true)
        put("second_ocr", false)
        put("official_persistence", false)
        put("backend_effect", false)
        put("hud_effect", false)
        put("admission_influence", false)
        put("controlled_hybrid_effect", false)
        put("frames_seen", framesSeen)
        put("core_candidates_seen", coreCandidatesSeen)
        put("reader2_only_core_seen", reader2OnlyCoreSeen)
        put("matched_m1_core_seen", matchedM1CoreSeen)
        put("consensus_windows_started", consensusWindowsStarted)
        put("confirmations_accepted", confirmationsAccepted)
        put("duplicate_frame_suppressed", duplicateFrameSuppressed)
        put("conflicts_detected", conflictsDetected)
        put("windows_expired", windowsExpired)
        put("consensus_ready_transitions", consensusReadyTransitions)
        put("consensus_ready_reader2_only", consensusReadyReader2Only)
        put("matched_m1_agrees", matchedM1Agrees)
        put("matched_m1_disagrees", matchedM1Disagrees)
        put("active_windows", entries.size)
        put("last_reader2_only_core", lastReader2OnlyCore)
        put("last_consensus_ready", lastConsensusReady)
        put(
            "consensus_contract",
            "reader2-only core complete repeated in distinct frames >=450ms apart within 8s, stable core, conflict-free; telemetry only",
        )
        put(
            "privacy",
            "Estado vive somente em memória; diagnóstico persiste apenas contadores e flags, sem OCR bruto, endereço, coordenada ou screenshot.",
        )
    }

    private data class Probe(
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val pickupMinutes: Int,
        val tripMinutes: Int,
        val totalMinutes: Int,
        val pickupLabel: String,
        val destinationLabel: String,
        val anchorY: Int,
        val confidence: Double,
    )

    private fun toProbe(candidate: Reader2Accumulator032.Candidate): Probe? {
        val pickupKm = candidate.pickupKm ?: return null
        val tripKm = candidate.tripKm ?: return null
        val pickupMinutes = candidate.pickupMinutes ?: return null
        val tripMinutes = candidate.tripMinutes ?: return null
        val totalMinutes = candidate.totalMinutes ?: return null
        val pickupLabel = candidate.pickupLabel?.takeIf(String::isNotBlank) ?: return null
        val destinationLabel = candidate.destinationLabel?.takeIf(String::isNotBlank) ?: return null
        return Probe(
            fare = candidate.fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            pickupLabel = pickupLabel,
            destinationLabel = destinationLabel,
            anchorY = candidate.anchorY,
            confidence = candidate.confidence,
        )
    }

    private fun probableSameTarget(entry: Entry, probe: Probe, frameHeight: Int): Boolean {
        if (!close(entry.fare, probe.fare, 0.12, 0.025)) return false
        val yTolerance = max(120, (frameHeight * 0.16).toInt())
        if (abs(entry.anchorY - probe.anchorY) > yTolerance) return false

        val pickupCompatible = sameLabel(entry.pickupLabel, probe.pickupLabel) ||
            close(entry.pickupKm, probe.pickupKm, 0.50, 0.15)
        val destinationCompatible = sameLabel(entry.destinationLabel, probe.destinationLabel) ||
            close(entry.tripKm, probe.tripKm, 0.75, 0.15)
        return pickupCompatible && destinationCompatible
    }

    private fun stableSameCore(entry: Entry, probe: Probe): Boolean =
        close(entry.fare, probe.fare, 0.10, 0.02) &&
            close(entry.pickupKm, probe.pickupKm, 0.35, 0.12) &&
            close(entry.tripKm, probe.tripKm, 0.55, 0.12) &&
            abs(entry.pickupMinutes - probe.pickupMinutes) <= 2 &&
            abs(entry.tripMinutes - probe.tripMinutes) <= 3 &&
            abs(entry.totalMinutes - probe.totalMinutes) <= 3 &&
            sameLabel(entry.pickupLabel, probe.pickupLabel) &&
            sameLabel(entry.destinationLabel, probe.destinationLabel)

    private fun ready(entry: Entry): Boolean =
        entry.confirmations >= 2 &&
            entry.conflictFree &&
            entry.lastAcceptedMs - entry.firstSeenMs >= MIN_DISTINCT_MS &&
            entry.confidence >= 0.70

    private fun targetScore(entry: Entry, probe: Probe, frameHeight: Int): Double {
        var score = 4.0
        if (sameLabel(entry.pickupLabel, probe.pickupLabel)) score += 2.0
        if (sameLabel(entry.destinationLabel, probe.destinationLabel)) score += 2.0
        if (close(entry.pickupKm, probe.pickupKm, 0.35, 0.12)) score += 1.0
        if (close(entry.tripKm, probe.tripKm, 0.55, 0.12)) score += 1.0
        score -= abs(entry.anchorY - probe.anchorY) / max(1, frameHeight).toDouble()
        return score
    }

    private fun matchScore(r2: Reader2Accumulator032.Candidate, m1: RideOffer): Double {
        var score = 0.0
        if (close(r2.fare, m1.fare, 0.10, 0.02)) score += 4.0
        if (close(r2.pickupKm, m1.pickupKm, 0.35, 0.12)) score += 2.0
        if (close(r2.tripKm, m1.tripKm, 0.55, 0.12)) score += 2.0
        if (close(r2.pickupMinutes?.toDouble(), m1.pickupMinutes?.toDouble(), 2.0, 0.0)) score += 1.0
        if (close(r2.tripMinutes?.toDouble(), m1.tripMinutes?.toDouble(), 3.0, 0.0)) score += 1.0
        if (sameLabelNullable(r2.pickupLabel, m1.context?.pickupLabel)) score += 1.0
        if (sameLabelNullable(r2.destinationLabel, m1.context?.destinationLabel)) score += 1.0
        return score
    }

    private fun agreesOnCore(r2: Reader2Accumulator032.Candidate, m1: RideOffer): Boolean =
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

    private fun closeNullable(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null && b == null) return true
        return close(a, b, absolute, relative)
    }

    private fun sameLabelNullable(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return true
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return sameLabel(a, b)
    }

    private fun sameLabel(a: String, b: String): Boolean = normalizeLabel(a) == normalizeLabel(b)

    private fun normalizeLabel(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)

    private fun purge(nowMs: Long) {
        val before = entries.size
        entries.removeAll { nowMs - it.lastSeenMs > WINDOW_MS }
        windowsExpired += (before - entries.size).coerceAtLeast(0)
    }
}
