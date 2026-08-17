package com.srrotas.app

import kotlin.math.roundToInt

/**
 * Deduplicação em memória durante a jornada.
 *
 * A identidade do card usa os valores mais estáveis: tarifa e geometria.
 * Avaliação, categoria, tipo e R$/km anunciado podem aparecer/desaparecer entre
 * frames do mesmo card e portanto não devem criar uma nova oferta.
 */
object OfferDeduplicator {
    private const val WINDOW_MS = 60_000L
    private const val MAX_ENTRIES = 160
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldEmit(offer: RideOffer, nowMs: Long = System.currentTimeMillis()): Boolean {
        purge(nowMs)
        val key = semanticKey(offer)
        val last = seen[key]
        if (last != null && nowMs - last < WINDOW_MS) return false
        seen[key] = nowMs
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

    private fun purge(nowMs: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value >= WINDOW_MS) iterator.remove()
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
