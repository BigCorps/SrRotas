package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OfferPreviewSafety026Test {
    @Test fun delaysOldOneHourRegressionShape() {
        assertTrue(
            OfferPreviewSafety026.requiresStabilization(
                offer(fare = 88.01, km = 22.1, minutes = 31, perHour = 170.34, perMinute = 2.84),
            ),
        )
    }

    @Test fun normalShortUrbanOfferCanPreviewImmediately() {
        assertFalse(
            OfferPreviewSafety026.requiresStabilization(
                offer(fare = 18.0, km = 4.2, minutes = 17, perHour = 63.5, perMinute = 1.06),
            ),
        )
    }

    private fun offer(
        fare: Double,
        km: Double,
        minutes: Int,
        perHour: Double,
        perMinute: Double,
    ) = RideOffer(
        observedAt = Instant.EPOCH.toString(),
        sourcePackage = "test",
        captureMethod = "test",
        rawText = "",
        fare = fare,
        pickupKm = 1.0,
        tripKm = km - 1.0,
        totalKm = km,
        pickupMinutes = 2,
        tripMinutes = minutes - 2,
        totalMinutes = minutes,
        perKm = fare / km,
        perHour = perHour,
        perMinute = perMinute,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = null,
        advertisedPerKm = null,
        serviceType = "comfort",
        verdict = "regular",
        confidence = 0.9,
        offerType = "exclusive",
        dedupeKey = "test-$minutes",
    )
}
