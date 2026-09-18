package com.srrotas.app

import kotlin.math.abs
import kotlin.math.roundToInt

/** Regras puras do comparador de leitores RC3.6. */
object ReaderLabRules027036 {
    data class Candidate(
        val platform: String,
        val fare: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
        val pickupMinutes: Int?,
        val totalMinutes: Int?,
        val pickupLabel: String?,
        val destinationLabel: String?,
        val observedAtMs: Long,
    )

    data class CoreCompleteness(
        val timestamp: Boolean,
        val pickup: Boolean,
        val pickupTime: Boolean,
        val destination: Boolean,
        val totalTime: Boolean,
    ) {
        val score: Int get() = listOf(timestamp, pickup, pickupTime, destination, totalTime).count { it }
        val complete: Boolean get() = score == 5
    }

    fun completeness(candidate: Candidate): CoreCompleteness = CoreCompleteness(
        timestamp = candidate.observedAtMs > 0L,
        pickup = !candidate.pickupLabel.isNullOrBlank(),
        pickupTime = candidate.pickupMinutes != null,
        destination = !candidate.destinationLabel.isNullOrBlank(),
        totalTime = candidate.totalMinutes != null,
    )

    fun sameOffer(a: Candidate, b: Candidate, maxDeltaMs: Long = 18_000L): Boolean {
        if (!a.platform.equals(b.platform, ignoreCase = true)) return false
        if (abs(a.observedAtMs - b.observedAtMs) > maxDeltaMs) return false
        if (abs(a.fare - b.fare) > 0.05) return false

        fun near(x: Double?, y: Double?, tolerance: Double): Boolean =
            x == null || y == null || abs(x - y) <= tolerance
        fun nearInt(x: Int?, y: Int?, tolerance: Int): Boolean =
            x == null || y == null || abs(x - y) <= tolerance

        if (!near(a.pickupKm, b.pickupKm, 0.45)) return false
        if (!near(a.tripKm, b.tripKm, 0.55)) return false
        if (!near(a.totalKm, b.totalKm, 0.65)) return false
        if (!nearInt(a.pickupMinutes, b.pickupMinutes, 2)) return false
        if (!nearInt(a.totalMinutes, b.totalMinutes, 3)) return false

        val ad = normalized(a.destinationLabel)
        val bd = normalized(b.destinationLabel)
        if (ad.isNotBlank() && bd.isNotBlank() && ad != bd && !ad.contains(bd) && !bd.contains(ad)) return false
        return true
    }

    fun fingerprint(candidate: Candidate): String = listOf(
        candidate.platform.lowercase(),
        (candidate.fare * 100.0).roundToInt(),
        candidate.pickupKm?.let { (it * 10.0).roundToInt() } ?: "_",
        candidate.tripKm?.let { (it * 10.0).roundToInt() } ?: "_",
        candidate.totalKm?.let { (it * 10.0).roundToInt() } ?: "_",
        candidate.pickupMinutes ?: "_",
        candidate.totalMinutes ?: "_",
    ).joinToString("|")

    private fun normalized(value: String?): String = value.orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9áàâãéêíóôõúç ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
