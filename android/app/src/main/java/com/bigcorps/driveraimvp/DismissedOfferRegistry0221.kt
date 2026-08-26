package com.srrotas.app

import kotlin.math.abs

/**
 * Evita que uma oferta dispensada manualmente ressuscite enquanto o mesmo card
 * continua na tela. Uma oferta suficientemente diferente libera imediatamente
 * o HUD para a próxima corrida.
 */
object DismissedOfferRegistry0221 {
    private const val MAX_AGE_MS = 120_000L

    private data class Snapshot(
        val at: Long,
        val platform: String,
        val serviceType: String,
        val fare: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
        val pickupMinutes: Int?,
        val tripMinutes: Int?,
        val totalMinutes: Int?,
    )

    @Volatile private var dismissed: Snapshot? = null

    @Synchronized
    fun dismiss(offer: RideOffer, nowMs: Long = System.currentTimeMillis()) {
        dismissed = snapshot(offer, nowMs)
    }

    @Synchronized
    fun shouldSuppress(offer: RideOffer, nowMs: Long = System.currentTimeMillis()): Boolean {
        val previous = dismissed ?: return false
        if (nowMs - previous.at !in 0..MAX_AGE_MS) {
            dismissed = null
            return false
        }
        if (sameOffer(previous, offer)) return true

        // Uma leitura realmente diferente representa a próxima oferta.
        dismissed = null
        return false
    }

    @Synchronized
    fun reset() {
        dismissed = null
    }

    private fun sameOffer(previous: Snapshot, current: RideOffer): Boolean {
        if (!previous.platform.equals(current.platform, ignoreCase = true)) return false
        if (abs(previous.fare - current.fare) > 0.05) return false

        val previousService = previous.serviceType.takeUnless { it == "unknown" }
        val currentService = current.serviceType.takeUnless { it == "unknown" }
        if (previousService != null && currentService != null && !previousService.equals(currentService, ignoreCase = true)) {
            return false
        }

        if (!near(previous.tripKm, current.tripKm, 0.35)) return false
        if (!near(previous.totalKm, current.totalKm, 0.50)) return false
        if (!near(previous.pickupKm, current.pickupKm, 0.35)) return false
        if (!near(previous.totalMinutes, current.totalMinutes, 5)) return false
        if (!near(previous.tripMinutes, current.tripMinutes, 4)) return false
        if (!near(previous.pickupMinutes, current.pickupMinutes, 3)) return false
        return true
    }

    private fun near(a: Double?, b: Double?, tolerance: Double): Boolean {
        if (a == null || b == null) return true
        return abs(a - b) <= tolerance
    }

    private fun near(a: Int?, b: Int?, tolerance: Int): Boolean {
        if (a == null || b == null) return true
        return kotlin.math.abs(a - b) <= tolerance
    }

    private fun snapshot(offer: RideOffer, nowMs: Long) = Snapshot(
        at = nowMs,
        platform = offer.platform,
        serviceType = offer.serviceType,
        fare = offer.fare,
        pickupKm = offer.pickupKm,
        tripKm = offer.tripKm,
        totalKm = offer.totalKm,
        pickupMinutes = offer.pickupMinutes,
        tripMinutes = offer.tripMinutes,
        totalMinutes = offer.totalMinutes,
    )
}
