package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissedOfferRegistry0221Test {
    private fun offer(fare: Double, tripKm: Double, totalKm: Double) = RideOffer(
        observedAt = "2026-08-26T12:00:00Z",
        platform = "99",
        sourcePackage = "fixture",
        captureMethod = "fixture",
        rawText = "",
        fare = fare,
        pickupKm = totalKm - tripKm,
        tripKm = tripKm,
        totalKm = totalKm,
        pickupMinutes = 5,
        tripMinutes = 20,
        totalMinutes = 25,
        perKm = fare / totalKm,
        perHour = fare / (25.0 / 60.0),
        perMinute = fare / 25.0,
        estimatedCost = 1.0,
        estimatedProfit = fare - 1.0,
        profitPerHour = 1.0,
        profitPercent = 50.0,
        passengerRating = null,
        advertisedPerKm = null,
        serviceType = "99plus",
        verdict = "boa",
        dedupeKey = "$fare-$tripKm",
    )

    @Test
    fun dismissedOfferStaysSuppressedButNextDifferentOfferIsAllowed() {
        DismissedOfferRegistry0221.reset()
        val first = offer(25.0, 2.0, 2.6)
        DismissedOfferRegistry0221.dismiss(first, nowMs = 1_000L)
        assertTrue(DismissedOfferRegistry0221.shouldSuppress(offer(25.0, 2.1, 2.7), nowMs = 2_000L))
        assertFalse(DismissedOfferRegistry0221.shouldSuppress(offer(31.0, 4.0, 4.8), nowMs = 3_000L))
    }
}
