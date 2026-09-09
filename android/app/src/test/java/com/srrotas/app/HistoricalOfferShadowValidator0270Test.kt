package com.srrotas.app

import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalOfferShadowValidator0270Test {
    private fun offer(
        fare: Double = 23.01,
        pickupKm: Double? = 0.8,
        tripKm: Double? = 5.3,
        totalKm: Double? = 6.1,
        pickupMinutes: Int? = 4,
        tripMinutes: Int? = 18,
        totalMinutes: Int? = 22,
        perKm: Double? =
            totalKm
                ?.takeIf { it > 0.0 }
                ?.let { fare / it },
    ) = RideOffer(
        observedAt = "2026-09-09T00:00:00Z",
        sourcePackage = "com.ubercab.driver",
        captureMethod = "test",
        rawText = "",
        fare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        totalKm = totalKm,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        totalMinutes = totalMinutes,
        perKm = perKm,
        perHour = null,
        perMinute = null,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = null,
        advertisedPerKm = null,
        verdict = "regular",
        dedupeKey = "shadow-test",
    )

    @Test
    fun ordinaryHistoricalShapeDoesNotRaiseSignal() {
        val result =
            HistoricalOfferShadowValidator0270
                .evaluate(offer())

        assertTrue(result.allSignals.isEmpty())
    }

    @Test
    fun historicalTailIsObservedButNotRejected() {
        val result =
            HistoricalOfferShadowValidator0270
                .evaluate(
                    offer(fare = 120.0),
                )

        assertTrue(
            "fare_above_historical_p99" in
                result.tailSignals,
        )
    }

    @Test
    fun inconsistentTotalsAreObserved() {
        val result =
            HistoricalOfferShadowValidator0270
                .evaluate(
                    offer(totalMinutes = 60),
                )

        assertTrue(
            "total_minutes_component_mismatch" in
                result.consistencySignals,
        )
    }
}
