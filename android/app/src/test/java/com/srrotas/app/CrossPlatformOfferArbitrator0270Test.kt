package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossPlatformOfferArbitrator0270Test {
    @Test
    fun sameFinancialCardReadByBothParsersKeeps99When99IdentityIsStrong() {
        val raw = """
            Solicitações
            Plus Nova
            R$7,83
            9 min 2,1 km
            9 min 2,2 km
            Escolher
        """.trimIndent()
        val result = CrossPlatformOfferArbitrator0270.resolve(
            ninetyNine = listOf(offer("99", raw, 0.90)),
            uber = listOf(offer("uber", raw, 0.82)),
        )

        assertEquals(1, result.ninetyNine.size)
        assertEquals(0, result.uber.size)
        assertEquals(1, result.resolvedCollisions)
    }

    @Test
    fun twoStrongDifferentClustersArePreservedEvenWhenNumbersCoincide() {
        val result = CrossPlatformOfferArbitrator0270.resolve(
            ninetyNine = listOf(
                offer(
                    "99",
                    "Solicitações\n99Plus\nR$7,83\n9 min 2,1 km\n9 min 2,2 km\nEscolher",
                    0.90,
                ),
            ),
            uber = listOf(
                offer(
                    "uber",
                    "UberX\nExclusivo\nR$7,83\n9 min 2,1 km\n9 min 2,2 km\nAceitar",
                    0.90,
                ),
            ),
        )

        assertEquals(1, result.ninetyNine.size)
        assertEquals(1, result.uber.size)
        assertEquals(0, result.resolvedCollisions)
    }

    @Test
    fun differentOffersAreNeverCollapsed() {
        val result = CrossPlatformOfferArbitrator0270.resolve(
            ninetyNine = listOf(offer("99", "99Plus\nEscolher", 0.90)),
            uber = listOf(offer("uber", "UberX\nAceitar", 0.90, fare = 12.50)),
        )

        assertEquals(1, result.ninetyNine.size)
        assertEquals(1, result.uber.size)
        assertEquals(0, result.resolvedCollisions)
    }

    private fun offer(
        platform: String,
        raw: String,
        confidence: Double,
        fare: Double = 7.83,
    ) = RideOffer(
        platform = platform,
        observedAt = "2026-09-12T17:25:21Z",
        sourcePackage = "fixture",
        captureMethod = "fixture",
        rawText = raw,
        fare = fare,
        pickupKm = 2.1,
        tripKm = 2.2,
        totalKm = 4.3,
        pickupMinutes = 9,
        tripMinutes = 9,
        totalMinutes = 18,
        perKm = fare / 4.3,
        perHour = fare / (18.0 / 60.0),
        perMinute = fare / 18.0,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = null,
        advertisedPerKm = null,
        serviceType = if (platform == "99") "99plus" else "uberx",
        verdict = "regular",
        confidence = confidence,
        dedupeKey = "$platform-$fare",
    )
}
