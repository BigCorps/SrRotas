package com.srrotas.app

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reader 2.0 — 0.31 Parallel Candidate Builder.
 *
 * Recebe as linhas espaciais produzidas pelo MESMO OCR do M1 antes de o M1
 * formar/rejeitar RideOffer. A interpretação abaixo é independente do OfferParser,
 * UberOfferDetector e Reader2Shadow030: tarifa, geometria e contexto são derivados
 * por regras próprias para que a comparação M1 x Reader 2 tenha valor real.
 *
 * Hard gates desta fase:
 * - nenhum segundo OCR/captura;
 * - nenhuma RideOffer oficial;
 * - nenhuma persistência/backend/HUD/admissão;
 * - nenhuma promoção automática do Reader 2.
 */
internal object Reader2Parallel031 {
    @Volatile private var framesSeen = 0L
    @Volatile private var uberCandidateFrames = 0L
    @Volatile private var longCardFrames = 0L
    @Volatile private var reader2CandidateFrames = 0L
    @Volatile private var reader2Candidates = 0L
    @Volatile private var reader2CoreComplete = 0L
    @Volatile private var reader2OnlyCandidates = 0L
    @Volatile private var reader2OnlyCoreComplete = 0L
    @Volatile private var m1OffersSeen = 0L
    @Volatile private var m1OnlyOffers = 0L
    @Volatile private var matchedCandidates = 0L
    @Volatile private var framesWithDisagreement = 0L
    @Volatile private var reader2CoreAheadFrames = 0L
    @Volatile private var m1CoreAheadFrames = 0L
    @Volatile private var coreEqualFrames = 0L
    @Volatile private var splitGeometryCandidates = 0L
    @Volatile private var longCardCandidates = 0L
    @Volatile private var longCardReader2Only = 0L

    private val disagreement = linkedMapOf<String, Long>()
    @Volatile private var lastCandidateCount = 0
    @Volatile private var lastM1Count = 0
    @Volatile private var lastCoreComplete = 0
    @Volatile private var lastLongCard = false
    @Volatile private var lastTextChars = 0
    @Volatile private var lastLineCount = 0
    @Volatile private var lastDisagreementFields = ""

    @Synchronized
    fun inspectFrame(
        lines: List<SpatialOcrLine>,
        frameWidth: Int,
        frameHeight: Int,
    ): Reader2ParallelRules031.FrameObservation {
        val pure = lines.map {
            Reader2ParallelRules031.Line(
                text = it.text,
                left = it.box.left,
                top = it.box.top,
                right = it.box.right,
                bottom = it.box.bottom,
            )
        }
        val observation = Reader2ParallelRules031.inspect(pure, frameWidth, frameHeight)
        framesSeen++
        if (observation.uberCandidate) uberCandidateFrames++
        if (observation.longCard) longCardFrames++
        if (observation.candidates.isNotEmpty()) reader2CandidateFrames++
        reader2Candidates += observation.candidates.size
        val core = observation.candidates.count { it.coreComplete }
        reader2CoreComplete += core
        splitGeometryCandidates += observation.candidates.count { it.splitGeometry }
        if (observation.longCard) longCardCandidates += observation.candidates.size
        lastCandidateCount = observation.candidates.size
        lastCoreComplete = core
        lastLongCard = observation.longCard
        lastTextChars = observation.textChars
        lastLineCount = observation.lineCount
        return observation
    }

    @Synchronized
    fun observeM1(
        frame: Reader2ParallelRules031.FrameObservation,
        offers: List<RideOffer>,
    ) {
        if (!frame.uberCandidate && frame.candidates.isEmpty() && offers.isEmpty()) return
        val m1 = offers.filter { it.platform.equals("uber", true) }
        m1OffersSeen += m1.size
        lastM1Count = m1.size

        val m1Core = m1.count(::m1CoreComplete)
        val r2Core = frame.candidates.count { it.coreComplete }
        when {
            r2Core > m1Core -> reader2CoreAheadFrames++
            m1Core > r2Core -> m1CoreAheadFrames++
            else -> coreEqualFrames++
        }

        if (m1.isEmpty() && frame.candidates.isNotEmpty()) {
            reader2OnlyCandidates += frame.candidates.size
            reader2OnlyCoreComplete += r2Core
            if (frame.longCard) longCardReader2Only += frame.candidates.size
            lastDisagreementFields = "reader2_only"
            return
        }
        if (frame.candidates.isEmpty() && m1.isNotEmpty()) {
            m1OnlyOffers += m1.size
            lastDisagreementFields = "m1_only"
            return
        }

        val remaining = m1.toMutableList()
        var frameDisagreed = false
        val fields = linkedSetOf<String>()
        frame.candidates.sortedBy { it.anchorY }.forEach { r2 ->
            val best = remaining.maxByOrNull { matchScore(r2, it) } ?: return@forEach
            remaining.remove(best)
            matchedCandidates++
            val diffs = compareFields(r2, best)
            if (diffs.isNotEmpty()) {
                frameDisagreed = true
                fields += diffs
                diffs.forEach { field -> disagreement[field] = (disagreement[field] ?: 0L) + 1L }
            }
        }
        if (remaining.isNotEmpty()) m1OnlyOffers += remaining.size
        if (frameDisagreed) framesWithDisagreement++
        lastDisagreementFields = fields.sorted().joinToString(",")
    }

    @Synchronized
    fun resetRuntime() {
        framesSeen = 0
        uberCandidateFrames = 0
        longCardFrames = 0
        reader2CandidateFrames = 0
        reader2Candidates = 0
        reader2CoreComplete = 0
        reader2OnlyCandidates = 0
        reader2OnlyCoreComplete = 0
        m1OffersSeen = 0
        m1OnlyOffers = 0
        matchedCandidates = 0
        framesWithDisagreement = 0
        reader2CoreAheadFrames = 0
        m1CoreAheadFrames = 0
        coreEqualFrames = 0
        splitGeometryCandidates = 0
        longCardCandidates = 0
        longCardReader2Only = 0
        disagreement.clear()
        lastCandidateCount = 0
        lastM1Count = 0
        lastCoreComplete = 0
        lastLongCard = false
        lastTextChars = 0
        lastLineCount = 0
        lastDisagreementFields = ""
    }

    @Synchronized
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "sr-reader2-parallel-031-v1")
        put("mode", "parallel_shadow")
        put("input_stage", "pre_m1_offer_from_shared_spatial_ocr")
        put("independent_candidate_builder", true)
        put("m1_required_for_reader2", false)
        put("can_observe_m1_rejected_frames", true)
        put("second_ocr", false)
        put("official_persistence", false)
        put("backend_effect", false)
        put("hud_effect", false)
        put("admission_influence", false)
        put("frames_seen", framesSeen)
        put("uber_candidate_frames", uberCandidateFrames)
        put("long_card_frames", longCardFrames)
        put("reader2_candidate_frames", reader2CandidateFrames)
        put("reader2_candidates", reader2Candidates)
        put("reader2_core_complete", reader2CoreComplete)
        put("reader2_only_candidates", reader2OnlyCandidates)
        put("reader2_only_core_complete", reader2OnlyCoreComplete)
        put("m1_offers_seen", m1OffersSeen)
        put("m1_only_offers", m1OnlyOffers)
        put("matched_candidates", matchedCandidates)
        put("frames_with_disagreement", framesWithDisagreement)
        put("reader2_core_ahead_frames", reader2CoreAheadFrames)
        put("m1_core_ahead_frames", m1CoreAheadFrames)
        put("core_equal_frames", coreEqualFrames)
        put("split_geometry_candidates", splitGeometryCandidates)
        put("long_card_candidates", longCardCandidates)
        put("long_card_reader2_only", longCardReader2Only)
        put("disagree_fare", disagreement["fare"] ?: 0L)
        put("disagree_pickup_km", disagreement["pickup_km"] ?: 0L)
        put("disagree_trip_km", disagreement["trip_km"] ?: 0L)
        put("disagree_pickup_minutes", disagreement["pickup_minutes"] ?: 0L)
        put("disagree_trip_minutes", disagreement["trip_minutes"] ?: 0L)
        put("disagree_total_minutes", disagreement["total_minutes"] ?: 0L)
        put("disagree_pickup_label", disagreement["pickup_label"] ?: 0L)
        put("disagree_destination_label", disagreement["destination_label"] ?: 0L)
        put("last_candidate_count", lastCandidateCount)
        put("last_m1_count", lastM1Count)
        put("last_core_complete", lastCoreComplete)
        put("last_long_card", lastLongCard)
        put("last_text_chars", lastTextChars)
        put("last_line_count", lastLineCount)
        put("last_disagreement_fields", lastDisagreementFields)
        put("core_contract", "offerTime,pickupLabel,pickupKm,pickupMinutes,tripKm,tripMinutes,destinationLabel,totalMinutes")
        put(
            "privacy",
            "Somente contadores/flags e nomes de campos divergentes; nenhum OCR bruto, endereço, coordenada ou screenshot é persistido.",
        )
    }

    private fun m1CoreComplete(offer: RideOffer): Boolean =
        offer.observedAt.isNotBlank() &&
            !offer.context?.pickupLabel.isNullOrBlank() &&
            offer.pickupKm != null &&
            offer.pickupMinutes != null &&
            offer.tripKm != null &&
            offer.tripMinutes != null &&
            !offer.context?.destinationLabel.isNullOrBlank() &&
            offer.totalMinutes != null

    private fun matchScore(r2: Reader2ParallelRules031.Candidate, m1: RideOffer): Double {
        var score = 0.0
        if (close(r2.fare, m1.fare, 0.08, 0.02)) score += 4.0
        if (close(r2.pickupKm, m1.pickupKm, 0.30, 0.10)) score += 2.0
        if (close(r2.tripKm, m1.tripKm, 0.45, 0.10)) score += 2.0
        if (close(r2.pickupMinutes?.toDouble(), m1.pickupMinutes?.toDouble(), 1.0, 0.0)) score += 1.5
        if (close(r2.tripMinutes?.toDouble(), m1.tripMinutes?.toDouble(), 2.0, 0.0)) score += 1.5
        if (sameLabel(r2.pickupLabel, m1.context?.pickupLabel)) score += 1.0
        if (sameLabel(r2.destinationLabel, m1.context?.destinationLabel)) score += 1.0
        return score
    }

    private fun compareFields(r2: Reader2ParallelRules031.Candidate, m1: RideOffer): Set<String> = buildSet {
        if (!close(r2.fare, m1.fare, 0.08, 0.02)) add("fare")
        if (!closeNullable(r2.pickupKm, m1.pickupKm, 0.30, 0.10)) add("pickup_km")
        if (!closeNullable(r2.tripKm, m1.tripKm, 0.45, 0.10)) add("trip_km")
        if (!closeNullable(r2.pickupMinutes?.toDouble(), m1.pickupMinutes?.toDouble(), 1.0, 0.0)) add("pickup_minutes")
        if (!closeNullable(r2.tripMinutes?.toDouble(), m1.tripMinutes?.toDouble(), 2.0, 0.0)) add("trip_minutes")
        if (!closeNullable(r2.totalMinutes?.toDouble(), m1.totalMinutes?.toDouble(), 2.0, 0.0)) add("total_minutes")
        if (!sameLabelNullable(r2.pickupLabel, m1.context?.pickupLabel)) add("pickup_label")
        if (!sameLabelNullable(r2.destinationLabel, m1.context?.destinationLabel)) add("destination_label")
    }

    private fun close(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null || b == null) return false
        val tolerance = max(absolute, max(abs(a), abs(b)) * relative)
        return abs(a - b) <= tolerance
    }

    private fun closeNullable(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null && b == null) return true
        return close(a, b, absolute, relative)
    }

    private fun sameLabel(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && normalizeLabel(a) == normalizeLabel(b)

    private fun sameLabelNullable(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return true
        return sameLabel(a, b)
    }

    private fun normalizeLabel(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
}

/** Regras puras e testáveis do candidate builder independente do Reader 2. */
internal object Reader2ParallelRules031 {
    data class Line(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val width: Int get() = (right - left).coerceAtLeast(1)
        val height: Int get() = (bottom - top).coerceAtLeast(1)
    }

    data class GeometryPair(
        val minutes: Int,
        val km: Double,
        val y: Int,
        val split: Boolean,
    )

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
        val coreComplete: Boolean,
        val splitGeometry: Boolean,
        val lineCount: Int,
        val textChars: Int,
    )

    data class FrameObservation(
        val uberCandidate: Boolean,
        val candidates: List<Candidate>,
        val longCard: Boolean,
        val lineCount: Int,
        val textChars: Int,
    )

    private data class NumberPoint(val value: Double, val lineIndex: Int, val y: Int)
    private data class DurationPoint(val minutes: Int, val lineIndex: Int, val y: Int)

    private val moneyRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val distanceRegex = Regex(
        "\\b([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val hourRegex = Regex(
        "\\b([0-9OSoIlL]{1,2})\\s*(?:h|hora|horas)\\s*(?:([0-9OSoIlL]{1,2})\\s*(?:min|minuto|minutos))?",
        RegexOption.IGNORE_CASE,
    )
    private val minuteRegex = Regex(
        "\\b([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val rangeMinutesRegex = Regex(
        "\\b[0-9OSoIlL]{1,2}\\s*[-–—]\\s*[0-9OSoIlL]{1,2}\\s*(?:min|minuto|minutos)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val promotionWords = listOf(
        "turbo mais", "turbo+", "turbo +", "promoção", "promocao", "bônus", "bonus",
        "incentivo", "ganho extra", "valor extra",
    )
    private val secondaryWords = listOf(
        "pedágio", "pedagio", "gorjeta", "taxa", "reembolso", "incluído", "incluido", "ganhos",
    )
    private val uberAnchors = listOf(
        "aceitar", "selecionar", "exclusivo", "radar de viagens", "uberx", "comfort",
        "priority", "uber moto", "ubermoto", "black", "electric",
    )
    private val utilityWords = listOf(
        "aceitar", "selecionar", "exclusivo", "radar de viagens", "verificado", "turbo mais",
        "promoção", "promocao", "bônus", "bonus", "aprox", "r$", "/km", "uberx", "comfort",
        "priority", "black", "electric", "moto", "avaliação", "avaliacao", "ganhos", "taxa",
    )
    private val addressWord = Regex(
        "^(?:rua|r\\.|avenida|av\\.|alameda|estrada|rodovia|travessa|praça|praca|largo|marginal|via)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun inspect(input: List<Line>, frameWidth: Int, frameHeight: Int): FrameObservation {
        val lines = input
            .map { it.copy(text = clean(it.text)) }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
        val textChars = lines.sumOf { it.text.length }
        val longCard = lines.size >= 18 || textChars >= 700
        if (lines.isEmpty()) return FrameObservation(false, emptyList(), longCard, 0, 0)

        val global = lines.joinToString("\n") { it.text }.lowercase()
        val uberCandidate = uberAnchors.any(global::contains)
        if (!uberCandidate) return FrameObservation(false, emptyList(), longCard, lines.size, textChars)

        val fares = primaryFareLines(lines)
        if (fares.isEmpty()) return FrameObservation(true, emptyList(), longCard, lines.size, textChars)

        val candidates = fares.mapNotNull { fareLine ->
            val cluster = cluster(lines, fares, fareLine, frameWidth, frameHeight)
            buildCandidate(cluster, fareLine)
        }
        return FrameObservation(
            uberCandidate = true,
            candidates = candidates.distinctBy { candidateIdentity(it) },
            longCard = longCard,
            lineCount = lines.size,
            textChars = textChars,
        )
    }

    private fun buildCandidate(cluster: List<Line>, fareLine: Line): Candidate? {
        if (cluster.isEmpty()) return null
        val fare = moneyValue(fareLine.text) ?: chooseFare(cluster) ?: return null
        val pairs = geometryPairs(cluster)
        val pickup = pairs.getOrNull(0)
        val trip = pairs.getOrNull(1)
        val pickupKm = pickup?.km
        val tripKm = trip?.km
        val pickupMinutes = pickup?.minutes
        val tripMinutes = trip?.minutes
        val totalKm = if (pickupKm != null && tripKm != null) pickupKm + tripKm else tripKm
        val totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else tripMinutes
        val labels = labels(cluster, pickup, trip)
        val coreComplete =
            pickupKm != null && pickupMinutes != null &&
                tripKm != null && tripMinutes != null &&
                !labels.first.isNullOrBlank() && !labels.second.isNullOrBlank() &&
                totalMinutes != null

        var confidence = 0.46
        confidence += 0.12
        if (pickup != null) confidence += 0.10
        if (trip != null) confidence += 0.16
        if (!labels.first.isNullOrBlank()) confidence += 0.07
        if (!labels.second.isNullOrBlank()) confidence += 0.07
        if (coreComplete) confidence += 0.06
        if (pairs.any { it.split }) confidence += 0.02

        return Candidate(
            fare = round2(fare),
            pickupKm = pickupKm?.let(::round2),
            tripKm = tripKm?.let(::round2),
            totalKm = totalKm?.let(::round2),
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            pickupLabel = labels.first,
            destinationLabel = labels.second,
            anchorY = fareLine.centerY,
            confidence = confidence.coerceIn(0.0, 0.97),
            coreComplete = coreComplete,
            splitGeometry = pairs.take(2).any { it.split },
            lineCount = cluster.size,
            textChars = cluster.sumOf { it.text.length },
        )
    }

    private fun primaryFareLines(lines: List<Line>): List<Line> {
        val money = lines.filter { moneyValue(it.text) != null && !explicitSecondary(lines, it) }
        if (money.isEmpty()) return emptyList()
        val accepted = mutableListOf<Line>()
        for (candidate in money) {
            val previous = accepted.lastOrNull { sameColumn(it, candidate) }
            if (previous == null) {
                accepted += candidate
                continue
            }
            val between = lines.filter {
                it.centerY > previous.centerY && it.centerY < candidate.centerY && sameColumn(previous, it)
            }
            if (between.any(::routeBoundary)) accepted += candidate
        }
        return accepted
    }

    private fun explicitSecondary(lines: List<Line>, target: Line): Boolean {
        val line = target.text.lowercase()
        if (line.contains("/km") || line.contains("aprox")) return true
        if (Regex("\\+\\s*(?:R\\$|\\$)", RegexOption.IGNORE_CASE).containsMatchIn(line)) return true
        val previous = lines
            .filter { it !== target && it.centerY < target.centerY && sameColumn(it, target) }
            .sortedByDescending { it.centerY }
            .take(2)
        val context = (previous.map { it.text } + target.text).joinToString(" ").lowercase()
        return promotionWords.any(context::contains) || secondaryWords.any(context::contains)
    }

    private fun chooseFare(lines: List<Line>): Double? =
        lines.asSequence()
            .filter { moneyValue(it.text) != null && !explicitSecondary(lines, it) }
            .mapNotNull { moneyValue(it.text) }
            .firstOrNull()

    private fun cluster(
        lines: List<Line>,
        fares: List<Line>,
        fare: Line,
        frameWidth: Int,
        frameHeight: Int,
    ): List<Line> {
        val sameColumnFares = fares.filter { sameColumn(it, fare) }.sortedBy { it.centerY }
        val index = sameColumnFares.indexOfFirst { sameLine(it, fare) }
        val previous = sameColumnFares.getOrNull(index - 1)
        val next = sameColumnFares.getOrNull(index + 1)
        val typicalHeight = lines.map { it.height }.sorted().let { it.getOrNull(it.size / 2) ?: 28 }
        val naturalTop = if (frameHeight > 0) {
            (fare.centerY - max(frameHeight * 18 / 100, typicalHeight * 7)).coerceAtLeast(0)
        } else fare.centerY - typicalHeight * 7
        val naturalBottom = if (frameHeight > 0) {
            (fare.centerY + max(frameHeight * 72 / 100, typicalHeight * 30)).coerceAtMost(frameHeight)
        } else fare.centerY + typicalHeight * 30
        val top = previous?.let { prev ->
            lines.filter { it.centerY > prev.centerY && it.centerY < fare.centerY && actionBoundary(it) }
                .maxByOrNull { it.centerY }
                ?.bottom
                ?.plus(1)
                ?: ((prev.centerY + fare.centerY) / 2)
        } ?: naturalTop
        val bottom = next?.let { nextFare ->
            lines.filter { it.centerY > fare.centerY && it.centerY < nextFare.centerY && actionBoundary(it) }
                .maxByOrNull { it.centerY }
                ?.bottom
                ?: (nextFare.top - 1)
        } ?: naturalBottom
        val strict = lines.any { value ->
            val l = value.text.lowercase()
            l.contains("waze") || l.contains("google maps") || l == "maps"
        }
        val radius = if (frameWidth > 0) {
            val ratio = if (strict) 0.34 else 0.56
            (frameWidth * ratio).toInt().coerceAtLeast(180)
        } else 320
        return lines.filter { line ->
            line.centerY in top..bottom &&
                (abs(line.centerX - fare.centerX) <= radius || horizontalOverlap(fare, line))
        }.sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
    }

    private fun geometryPairs(lines: List<Line>): List<GeometryPair> {
        val durations = mutableListOf<DurationPoint>()
        val distances = mutableListOf<NumberPoint>()
        val direct = mutableListOf<GeometryPair>()

        lines.forEachIndexed { index, line ->
            if (rangeMinutesRegex.containsMatchIn(line.text)) return@forEachIndexed
            val ds = durations(line.text).map { DurationPoint(it, index, line.centerY) }
            val ks = distances(line.text).map { NumberPoint(it, index, line.centerY) }
            if (ds.isNotEmpty() && ks.isNotEmpty() && !isMoneyLine(line.text)) {
                val count = min(ds.size, ks.size)
                for (i in 0 until count) {
                    direct += GeometryPair(ds[i].minutes, ks[i].value, line.centerY, false)
                }
            } else {
                durations += ds
                if (!isMoneyLine(line.text)) distances += ks
            }
        }

        val result = direct.sortedBy { it.y }.toMutableList()
        if (result.size >= 2) return result.take(3)

        val usedDistance = BooleanArray(distances.size)
        val medianHeight = lines.map { it.height }.sorted().let { it.getOrNull(it.size / 2) ?: 28 }
        durations.sortedBy { it.y }.forEach { duration ->
            val best = distances.indices
                .asSequence()
                .filter { !usedDistance[it] }
                .map { i ->
                    val distance = distances[i]
                    Triple(i, distance, abs(distance.lineIndex - duration.lineIndex))
                }
                .filter { (_, distance, lineDelta) ->
                    lineDelta <= 4 && abs(distance.y - duration.y) <= max(160, medianHeight * 6)
                }
                .minWithOrNull(compareBy<Triple<Int, NumberPoint, Int>> { it.third }.thenBy { abs(it.second.y - duration.y) })
                ?: return@forEach
            usedDistance[best.first] = true
            result += GeometryPair(
                minutes = duration.minutes,
                km = best.second.value,
                y = (duration.y + best.second.y) / 2,
                split = duration.lineIndex != best.second.lineIndex,
            )
        }
        return result.distinctBy { "${it.minutes}|${round2(it.km)}|${it.y / 24}" }
            .sortedBy { it.y }
            .take(3)
    }

    private fun labels(
        lines: List<Line>,
        pickup: GeometryPair?,
        trip: GeometryPair?,
    ): Pair<String?, String?> {
        if (pickup == null) return null to null
        val places = lines.filter { looksLikePlace(it.text) }
        val pickupUpper = trip?.y ?: Int.MAX_VALUE
        val pickupLine = places.firstOrNull { it.centerY > pickup.y && it.centerY < pickupUpper }
        val destinationLine = if (trip != null) places.firstOrNull { it.centerY > trip.y } else null
        val pickupText = pickupLine?.let { composePlace(lines, it) }
        val destinationText = destinationLine?.let { composePlace(lines, it) }
            ?.takeUnless { pickupText != null && normalizeLabel(it) == normalizeLabel(pickupText) }
        return pickupText to destinationText
    }

    private fun composePlace(lines: List<Line>, start: Line): String {
        val ordered = lines.sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
        val index = ordered.indexOfFirst { sameLine(it, start) }
        if (index < 0) return clean(start.text).take(180)
        var value = clean(start.text)
        val next = ordered.getOrNull(index + 1)
        if (next != null && looksLikePlace(next.text)) {
            val gap = next.top - start.bottom
            val maxGap = max(start.height, next.height) * 2 + 32
            if (gap in -8..maxGap && !addressWord.containsMatchIn(next.text)) {
                value = clean("$value ${next.text}")
            }
        }
        return value.take(180)
    }

    private fun durations(raw: String): List<Int> {
        val text = raw
        val values = mutableListOf<Pair<IntRange, Int>>()
        hourRegex.findAll(text).forEach { match ->
            val hours = parseInteger(match.groupValues[1]) ?: return@forEach
            val extra = match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.let(::parseInteger) ?: 0
            val total = hours * 60 + extra
            if (total in 1..600) values += match.range to total
        }
        minuteRegex.findAll(text).forEach { match ->
            if (values.any { (range, _) -> match.range.first >= range.first && match.range.last <= range.last }) return@forEach
            val value = parseInteger(match.groupValues[1]) ?: return@forEach
            if (value in 1..600) values += match.range to value
        }
        return values.sortedBy { it.first.first }.map { it.second }
    }

    private fun distances(raw: String): List<Double> {
        if (raw.contains("R$", true) || raw.contains('$') || raw.contains("/km", true)) return emptyList()
        return distanceRegex.findAll(raw).mapNotNull { match ->
            val value = parseNumber(match.groupValues[1]) ?: return@mapNotNull null
            val km = if (match.groupValues[2].equals("m", true)) value / 1000.0 else value
            km.takeIf { it in 0.05..500.0 }
        }.toList()
    }

    private fun moneyValue(raw: String): Double? {
        val match = moneyRegex.find(raw) ?: return null
        return parseNumber(match.groupValues[1])?.takeIf { it in 2.0..1000.0 }
    }


    private fun actionBoundary(line: Line): Boolean {
        val text = line.text.lowercase()
        return listOf("aceitar", "selecionar").any(text::contains)
    }

    private fun routeBoundary(line: Line): Boolean {
        val text = line.text.lowercase()
        return durations(text).isNotEmpty() || distances(text).isNotEmpty() ||
            listOf("aceitar", "selecionar", "exclusivo", "radar de viagens").any(text::contains)
    }

    private fun looksLikePlace(raw: String): Boolean {
        val value = clean(raw)
        if (value.length !in 4..180) return false
        val lower = value.lowercase()
        if (utilityWords.any(lower::contains)) return false
        if (durations(value).isNotEmpty() || distances(value).isNotEmpty() || moneyValue(value) != null) return false
        if (promotionWords.any(lower::contains) || secondaryWords.any(lower::contains)) return false
        if (!value.any(Char::isLetter) || value.count(Char::isLetter) < 4) return false
        if (value.endsWith("?") && !addressWord.containsMatchIn(value)) return false
        if (addressWord.containsMatchIn(value)) return true
        if (Regex("\\b\\d{1,5}\\b").containsMatchIn(value) && value.any(Char::isLetter)) return true
        if (value.contains(',') && value.split(',').any { it.trim().length >= 3 }) return true
        val words = value.split(Regex("\\s+")).filter { it.length >= 2 }
        return words.size >= 2 && value.length >= 8
    }

    private fun candidateIdentity(candidate: Candidate): String = listOf(
        round2(candidate.fare),
        candidate.pickupMinutes,
        candidate.tripMinutes,
        candidate.pickupKm?.let(::round2),
        candidate.tripKm?.let(::round2),
        normalizeLabel(candidate.pickupLabel.orEmpty()),
        normalizeLabel(candidate.destinationLabel.orEmpty()),
    ).joinToString("|")

    private fun sameColumn(a: Line, b: Line): Boolean {
        if (horizontalOverlap(a, b)) return true
        val width = max(a.width, b.width)
        return abs(a.centerX - b.centerX) <= width * 1.35
    }

    private fun horizontalOverlap(a: Line, b: Line): Boolean =
        min(a.right, b.right) - max(a.left, b.left) > 0

    private fun sameLine(a: Line, b: Line): Boolean =
        a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom && a.text == b.text

    private fun isMoneyLine(text: String): Boolean =
        text.contains("R$", true) || text.contains('$') || text.contains("/km", true)

    private fun parseInteger(raw: String): Int? =
        numericNormalize(raw).filter(Char::isDigit).toIntOrNull()

    private fun parseNumber(raw: String): Double? {
        val token = numericNormalize(raw).trim()
        if (!token.any(Char::isDigit)) return null
        val cleaned = if (token.contains(',') && token.contains('.')) {
            token.replace(".", "").replace(',', '.')
        } else token.replace(',', '.')
        return cleaned.toDoubleOrNull()
    }

    private fun numericNormalize(raw: String): String =
        raw.replace('O', '0').replace('o', '0')
            .replace('S', '5').replace('s', '5')
            .replace('I', '1').replace('l', '1').replace('L', '1')

    private fun clean(value: String): String =
        value.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun normalizeLabel(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
