package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HudBorderRules025Test {
    private fun offer(
        perMinute: Double,
        perKm: Double,
        perHour: Double,
        verdict: String,
    ) = RideOffer(
        observedAt = Instant.EPOCH.toString(),
        sourcePackage = "test",
        captureMethod = "test",
        rawText = "",
        fare = 30.0,
        pickupKm = 1.0,
        tripKm = 9.0,
        totalKm = 10.0,
        pickupMinutes = 4,
        tripMinutes = 20,
        totalMinutes = 24,
        perKm = perKm,
        perHour = perHour,
        perMinute = perMinute,
        estimatedCost = null,
        estimatedProfit = null,
        profitPerHour = null,
        profitPercent = null,
        passengerRating = null,
        advertisedPerKm = null,
        serviceType = "UberX",
        verdict = verdict,
        confidence = 1.0,
        offerType = "exclusive",
        dedupeKey = "border-test-$perMinute-$perKm-$perHour",
    )

    private val settings = DriverSettings(
        redPerMinuteBelow = 0.40,
        minPerMinute = 0.50,
        redPerKmBelow = 1.20,
        minPerKm = 1.50,
        redPerHourBelow = 24.0,
        minPerHour = 30.0,
        hudMetricOrder = "per_minute,per_km,per_hour",
        hudEnabledMetrics = "per_minute,per_km,per_hour",
    )

    @Test
    fun border_uses_weighted_average_not_stale_offer_verdict() {
        val value = offer(0.80, 2.10, 48.0, verdict = "ruim")
        assertEquals("boa", HudBorderRules025.weightedVerdict(settings, value, 8))
    }

    @Test
    fun border_is_bad_when_weighted_metrics_are_bad() {
        val value = offer(0.20, 0.80, 18.0, verdict = "boa")
        assertEquals("ruim", HudBorderRules025.weightedVerdict(settings, value, 8))
    }

    @Test
    fun border_width_is_more_visible_without_changing_hud_size() {
        assertEquals(2, HudBorderRules025.strokeDp("compact"))
        assertEquals(3, HudBorderRules025.strokeDp("normal"))
        assertEquals(3, HudBorderRules025.strokeDp("large"))
    }
}
