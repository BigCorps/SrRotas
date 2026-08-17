package com.srrotas.app

import kotlin.math.abs

/**
 * Consolida leituras sucessivas do mesmo card durante uma janela curta.
 *
 * O HUD continua imediato. Esta classe decide apenas qual leitura vira
 * histórico/backend, evitando persistir estágios intermediários do OCR.
 */
class CardStabilizer(
    private val windowMs: Long = 750L,
) {
    data class StableResult(
        val offer: RideOffer,
        val samples: Int,
        val replacements: Int,
    )

    private data class Bucket(
        val startedAtMs: Long,
        var lastBatchId: Long,
        var best: RideOffer,
        var samples: Int = 1,
        var replacements: Int = 0,
    )

    private val buckets = mutableListOf<Bucket>()
    private var nextBatchId = 1L

    @Synchronized
    fun submit(offers: List<RideOffer>, nowMs: Long): List<StableResult> {
        val ready = drainReady(nowMs).toMutableList()
        if (offers.isEmpty()) return ready

        val batchId = nextBatchId++
        offers.forEach { offer ->
            val match = findMatch(offer, batchId, nowMs)
            if (match == null) {
                buckets += Bucket(nowMs, batchId, offer)
            } else {
                match.lastBatchId = batchId
                match.samples++
                if (isBetter(offer, match.best)) {
                    match.best = offer
                    match.replacements++
                }
            }
        }
        return ready
    }

    @Synchronized
    fun drainReady(nowMs: Long): List<StableResult> {
        if (buckets.isEmpty()) return emptyList()
        val ready = mutableListOf<StableResult>()
        val iterator = buckets.iterator()
        while (iterator.hasNext()) {
            val bucket = iterator.next()
            if (nowMs - bucket.startedAtMs >= windowMs) {
                ready += bucket.toResult()
                iterator.remove()
            }
        }
        return ready
    }

    @Synchronized
    fun flushAll(): List<StableResult> {
        val all = buckets.map { it.toResult() }
        buckets.clear()
        return all
    }

    @Synchronized
    fun nextDelayMs(nowMs: Long): Long? {
        val next = buckets.minOfOrNull { it.startedAtMs + windowMs } ?: return null
        return (next - nowMs).coerceAtLeast(1L)
    }

    @Synchronized
    fun pendingCount(): Int = buckets.size

    private fun findMatch(offer: RideOffer, batchId: Long, nowMs: Long): Bucket? {
        val candidates = buckets.asSequence()
            .filter { it.lastBatchId != batchId }
            .filter { nowMs - it.startedAtMs < windowMs }
            .filter { sameCardGeometry(it.best, offer) }
            .toList()

        if (candidates.isEmpty()) return null

        // Quando dois cards do Radar têm geometria parecida, associa a nova
        // leitura ao bucket cuja tarifa atual está mais próxima. Tarifa é só
        // desempate de associação, nunca requisito para ser o mesmo card.
        return candidates.minByOrNull { candidate ->
            val base = maxOf(1.0, candidate.best.fare, offer.fare)
            abs(candidate.best.fare - offer.fare) / base
        }
    }

    internal fun sameCardGeometry(a: RideOffer, b: RideOffer): Boolean {
        if (a.serviceType != "unknown" && b.serviceType != "unknown" && a.serviceType != b.serviceType) {
            return false
        }

        val aTrip = a.tripKm
        val bTrip = b.tripKm
        if (aTrip != null && bTrip != null && abs(aTrip - bTrip) <= 0.25) {
            val aPickup = a.pickupKm
            val bPickup = b.pickupKm

            if (aPickup != null && bPickup != null) {
                return abs(aPickup - bPickup) <= 0.25 && closeNullable(a.totalKm, b.totalKm, 0.35)
            }

            // Um frame parcial pode perder apenas o pickup.
            val knownPickup = aPickup ?: bPickup
            val aTotal = a.totalKm
            val bTotal = b.totalKm
            if (knownPickup != null && aTotal != null && bTotal != null) {
                return abs(aTotal - bTotal) <= knownPickup + 0.30
            }
            return true
        }

        return closeNullable(a.totalKm, b.totalKm, 0.20) &&
            (a.pickupKm == null || b.pickupKm == null)
    }

    internal fun qualityScore(o: RideOffer): Double {
        var score = o.confidence * 10.0
        if (o.pickupKm != null && o.tripKm != null) score += 7.0
        if (o.pickupMinutes != null && o.tripMinutes != null) score += 5.0
        if (o.advertisedPerKm != null) score += 4.0
        if (o.serviceType != "unknown") score += 2.0
        if (o.passengerRating != null) score += 1.0

        val advertised = o.advertisedPerKm
        val calculated = o.perKm
        if (advertised != null && advertised > 0.0 && calculated != null) {
            val delta = abs(advertised - calculated) / advertised
            when {
                delta <= 0.03 -> score += 4.0
                delta <= 0.08 -> score += 2.0
                delta > 0.15 -> score -= 5.0
            }
        }

        if (o.pickupKm == null && o.advertisedPerKm == null && o.confidence < 0.75) {
            score -= 5.0
        }
        return score
    }

    internal fun isWeakPartial(o: RideOffer): Boolean =
        o.pickupKm == null && o.advertisedPerKm == null && o.confidence < 0.75

    private fun isBetter(candidate: RideOffer, current: RideOffer): Boolean {
        val candidateScore = qualityScore(candidate)
        val currentScore = qualityScore(current)
        if (candidateScore != currentScore) return candidateScore > currentScore
        return true // empate: leitura mais recente
    }

    private fun closeNullable(a: Double?, b: Double?, tolerance: Double): Boolean =
        a != null && b != null && abs(a - b) <= tolerance

    private fun Bucket.toResult() = StableResult(best, samples, replacements)
}
