package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OfferIntegrityGuard028Test {
    private fun offer(
        fare: Double = 4.30,
        pickupKm: Double? = 1.0,
        tripKm: Double? = 2.2,
        pickupMinutes: Int? = 4,
        tripMinutes: Int? = 9,
        totalKmOverride: Double? = null,
        perKmOverride: Double? = null,
    ): RideOffer {
        val totalKm = totalKmOverride ?: listOfNotNull(pickupKm, tripKm).sum().takeIf { it > 0.0 }
        val totalMinutes = listOfNotNull(pickupMinutes, tripMinutes).sum().takeIf { it > 0 }
        return RideOffer(
            observedAt = Instant.EPOCH.toString(),
            sourcePackage = "fixture",
            captureMethod = "fixture",
            rawText = "fixture",
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            totalKm = totalKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            perKm = perKmOverride ?: totalKm?.takeIf { it > 0.0 }?.let { fare / it },
            perHour = totalMinutes?.takeIf { it > 0 }?.let { fare / (it / 60.0) },
            perMinute = totalMinutes?.takeIf { it > 0 }?.let { fare / it },
            estimatedCost = null,
            estimatedProfit = null,
            profitPerHour = null,
            profitPercent = null,
            passengerRating = 4.77,
            advertisedPerKm = null,
            serviceType = "unknown",
            verdict = "ruim",
            confidence = 0.80,
            offerType = "exclusive",
            dedupeKey = "fixture",
        )
    }

    @Test fun rejectsFieldRegressionElevenKmPickupInFourMinutes() {
        val bad = offer(pickupKm = 11.0, tripKm = 2.2, pickupMinutes = 4, tripMinutes = 9)
        val inspection = OfferIntegrityGuard028.inspect(bad)
        assertFalse(inspection.accepted)
        assertTrue(inspection.reason == "pickup_speed_outlier")
    }

    @Test fun keepsSameOfferWithCorrectDecimalPickup() {
        val good = offer(pickupKm = 1.0, tripKm = 2.2, pickupMinutes = 4, tripMinutes = 9)
        assertTrue(OfferIntegrityGuard028.isPlausible(good))
    }

    @Test fun keepsLongHighwayTrip() {
        val highway = offer(
            fare = 206.82,
            pickupKm = null,
            tripKm = 114.0,
            pickupMinutes = null,
            tripMinutes = 96,
        )
        assertTrue(OfferIntegrityGuard028.isPlausible(highway))
    }

    @Test fun rejectsArithmeticTotalMismatch() {
        val bad = offer(
            pickupKm = 1.0,
            tripKm = 2.2,
            pickupMinutes = 4,
            tripMinutes = 9,
            totalKmOverride = 13.2,
            perKmOverride = 4.30 / 13.2,
        )
        val inspection = OfferIntegrityGuard028.inspect(bad)
        assertFalse(inspection.accepted)
        assertTrue(inspection.reason == "total_km_mismatch")
    }
}
