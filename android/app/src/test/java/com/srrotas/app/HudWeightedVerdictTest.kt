package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HudWeightedVerdictTest {
    private fun offer(perMinute: Double, perKm: Double, perHour: Double) = RideOffer(
        observedAt = "2026-08-25T21:00:00Z",
        sourcePackage = "fixture",
        captureMethod = "fixture",
        rawText = "",
        fare = 20.0,
        pickupKm = 1.0,
        tripKm = 5.0,
        totalKm = 6.0,
        pickupMinutes = 5,
        tripMinutes = 20,
        totalMinutes = 25,
        perKm = perKm,
        perHour = perHour,
        perMinute = perMinute,
        estimatedCost = 5.1,
        estimatedProfit = 14.9,
        profitPerHour = 35.76,
        profitPercent = 74.5,
        passengerRating = null,
        advertisedPerKm = null,
        verdict = "ruim",
        dedupeKey = "fixture",
    )

    @Test
    fun oneRedMetricDoesNotAutomaticallyMakeRideBad() {
        val settings = DriverSettings(
            minPerMinute = 0.60,
            redPerMinuteBelow = 0.48,
            minPerKm = 1.80,
            redPerKmBelow = 1.45,
            minPerHour = 35.0,
            redPerHourBelow = 28.0,
            hudMetricOrder = "per_minute,per_km,per_hour",
            hudEnabledMetrics = "per_minute,per_km,per_hour",
        )
        val scored = HudWeightedVerdict.apply(settings, offer(0.40, 2.30, 50.0))
        assertEquals("regular", scored.verdict)
    }

    @Test
    fun hudOrderChangesPriorityWithoutSingleMetricVeto() {
        val settings = DriverSettings(
            minPerMinute = 0.60,
            redPerMinuteBelow = 0.48,
            minPerKm = 1.80,
            redPerKmBelow = 1.45,
            minPerHour = 35.0,
            redPerHourBelow = 28.0,
            hudMetricOrder = "per_km,per_hour,per_minute",
            hudEnabledMetrics = "per_minute,per_km,per_hour",
        )
        val scored = HudWeightedVerdict.apply(settings, offer(0.40, 2.30, 50.0))
        assertEquals("boa", scored.verdict)
    }
}
