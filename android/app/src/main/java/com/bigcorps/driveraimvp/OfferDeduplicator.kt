package com.srrotas.app

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deduplicação em memória durante a jornada.
 *
 * Identidade normal: tarifa + geometria.
 * 0.5.3 também trata o flicker completo -> parcial por uma janela curta,
 * sem transformar o dedupe em uma janela larga que esconda novas ofertas.
 */
object OfferDeduplicator {
    private const val WINDOW_MS = 60_000L
    private const val FUZZY_WINDOW_MS = 2_500L
    private const val MAX_ENTRIES = 180

    private data class SeenOffer(
        val at: Long,
        val fare: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
    )

    private val seen = LinkedHashMap<String, SeenOffer>()

    @Synchronized
    fun shouldEmit(offer: RideOffer, nowMs: Long = System.currentTimeMillis()): Boolean {
        purge(nowMs)
        val key = semanticKey(offer)

        val exact = seen[key]
        if (exact != null && nowMs - exact.at < WINDOW_MS) return false

        val fuzzy = seen.values.any { previous -> isShortFlicker(previous, offer, nowMs) }
        if (fuzzy) {
            // Aprende também a forma parcial para não voltar a registrá-la após 2,5 s.
            seen[key] = SeenOffer(nowMs, offer.fare, offer.pickupKm, offer.tripKm, offer.totalKm)
            trim()
            return false
        }

        seen[key] = SeenOffer(nowMs, offer.fare, offer.pickupKm, offer.tripKm, offer.totalKm)
        trim()
        return true
    }

    @Synchronized
    fun reset() = seen.clear()

    internal fun semanticKey(offer: RideOffer): String = listOf(
        cents(offer.fare),
        tenth(offer.pickupKm),
        tenth(offer.tripKm),
        tenth(offer.totalKm),
    ).joinToString("|")

    @Synchronized
    internal fun size(): Int = seen.size

    private fun isShortFlicker(previous: SeenOffer, current: RideOffer, nowMs: Long): Boolean {
        if (nowMs - previous.at !in 0 until FUZZY_WINDOW_MS) return false
        if (cents(previous.fare) != cents(current.fare)) return false

        val prevTrip = previous.tripKm
        val curTrip = current.tripKm
        if (prevTrip != null && curTrip != null && abs(prevTrip - curTrip) <= 0.20) {
            val prevTotal = previous.totalKm
            val curTotal = current.totalKm
            if (prevTotal == null || curTotal == null) return true
            if (abs(prevTotal - curTotal) <= 0.35) return true

            // Um frame pode perder apenas o pickup: total passa de pickup+trip para trip.
            val knownPickup = previous.pickupKm ?: current.pickupKm
            if ((previous.pickupKm == null || current.pickupKm == null) && knownPickup != null) {
                if (abs(prevTotal - curTotal) <= knownPickup + 0.25) return true
            }
        }

        val prevTotal = previous.totalKm
        val curTotal = current.totalKm
        return prevTotal != null && curTotal != null &&
            abs(prevTotal - curTotal) <= 0.20 &&
            (previous.pickupKm == null || current.pickupKm == null)
    }

    private fun purge(nowMs: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value.at >= WINDOW_MS) iterator.remove()
        }
    }

    private fun trim() {
        while (seen.size > MAX_ENTRIES) {
            val first = seen.entries.firstOrNull()?.key ?: return
            seen.remove(first)
        }
    }

    private fun cents(v: Double?) = v?.let { (it * 100.0).roundToInt().toString() } ?: "_"
    private fun tenth(v: Double?) = v?.let { (it * 10.0).roundToInt().toString() } ?: "_"
}
