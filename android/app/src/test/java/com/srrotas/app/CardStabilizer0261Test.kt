package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CardStabilizer0261Test {
    private fun offer(
        fare: Double,
        confidence: Double,
        advertised: Double? = null,
    ) = RideOffer(
        observedAt = Instant.EPOCH.toString(),
        sourcePackage = "fixture",
        captureMethod = "fixture",
        rawText = "fixture",
        fare = fare,
        pickupKm = 1.0,
        tripKm = 9.0,
        totalKm = 10.0,
        pickupMinutes = 5,
        tripMinutes = 25,
        totalMinutes = 30,
        perKm = fare / 10.0,
        perHour = fare * 2.0,
        perMinute = fare / 30.0,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = 4.90,
        advertisedPerKm = advertised,
        serviceType = "comfort",
        verdict = "boa",
        confidence = confidence,
        offerType = "exclusive",
        dedupeKey = "fixture",
    )

    @Test
    fun periodicReliabilityFrameCanImproveSameDefaultBucket() {
        val stabilizer = CardStabilizer()
        stabilizer.submit(listOf(offer(30.0, .82)), 1_000L)

        // A leitura periódica forçada da 0.26.1 chega 1,25 s depois e ainda
        // precisa pertencer ao mesmo bucket antes da emissão estável.
        stabilizer.submit(listOf(offer(36.58, .99, 3.66)), 2_250L)

        val out = stabilizer.drainReady(2_500L)
        assertEquals(1, out.size)
        assertEquals(2, out.single().samples)
        assertEquals(36.58, out.single().offer.fare, .001)
    }
}
