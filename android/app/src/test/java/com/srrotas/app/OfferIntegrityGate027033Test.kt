package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferIntegrityGate027033Test {
    private fun offer(
        pickupKm: Double? = 2.0,
        tripKm: Double? = 8.0,
        totalKm: Double? = 10.0,
        pickupMinutes: Int? = 5,
        tripMinutes: Int? = 20,
        totalMinutes: Int? = 25,
    ) = RideOffer(
        observedAt = "2026-09-13T18:00:00Z",
        sourcePackage = "test",
        captureMethod = "test",
        rawText = "",
        fare = 20.0,
        pickupKm = pickupKm,
        tripKm = tripKm,
        totalKm = totalKm,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        totalMinutes = totalMinutes,
        perKm = 2.0,
        perHour = 48.0,
        perMinute = 0.8,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = null,
        advertisedPerKm = null,
        verdict = "GOOD",
        dedupeKey = "test",
    )

    @Test fun completeBothLegsIsReady() {
        assertTrue(OfferIntegrityGate027033.assess(offer()).ready)
    }

    @Test fun tripOnlyNeverBecomesReady() {
        assertFalse(
            OfferIntegrityGate027033.assess(
                offer(pickupKm = null, pickupMinutes = null, totalKm = 8.0, totalMinutes = 20),
            ).ready,
        )
    }

    @Test fun inconsistentTotalIsRejected() {
        assertFalse(OfferIntegrityGate027033.assess(offer(totalKm = 8.0)).ready)
    }
}
