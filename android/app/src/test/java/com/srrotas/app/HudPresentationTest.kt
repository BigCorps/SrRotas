package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HudPresentationTest {
    private fun offer() = RideOffer(
        localId = "one",
        journeyId = "journey",
        observedAt = "2026-08-19T12:00:00Z",
        sourcePackage = "com.ubercab.driver",
        captureMethod = "mediaprojection",
        rawText = "raw one",
        fare = 28.75,
        pickupKm = 1.2,
        tripKm = 7.3,
        totalKm = 8.5,
        pickupMinutes = 5,
        tripMinutes = 20,
        totalMinutes = 25,
        perKm = 3.38,
        perHour = 69.0,
        perMinute = 1.15,
        estimatedCost = 7.23,
        estimatedProfit = 21.52,
        profitPerHour = 51.65,
        profitPercent = 74.85,
        passengerRating = 4.95,
        advertisedPerKm = 3.38,
        serviceType = "uberx",
        verdict = "boa",
        confidence = .99,
        offerType = "exclusive",
        dedupeKey = "dedupe",
    )

    @Test
    fun repeatedOcrWithSameVisibleDataKeepsSameFingerprint() {
        val settings = DriverSettings()
        val first = offer()
        val reread = first.copy(
            localId = "two",
            rawText = "OCR com pequenas diferenças de texto bruto",
            confidence = .91,
            dedupeKey = "other-dedupe",
        )

        assertEquals(
            HudPresentation.visualFingerprint(first, settings),
            HudPresentation.visualFingerprint(reread, settings),
        )
    }

    @Test
    fun visibleMetricChangeProducesNewFingerprint() {
        val settings = DriverSettings()
        val first = offer()
        val changed = first.copy(perKm = 3.55)

        assertNotEquals(
            HudPresentation.visualFingerprint(first, settings),
            HudPresentation.visualFingerprint(changed, settings),
        )
    }

    @Test
    fun followHudReordersOnlyMetricsSharedWithHud() {
        val settings = DriverSettings(
            hudMetricOrder = "per_km,per_hour,per_minute,rating",
            voiceFollowHudOrder = true,
            voiceMetricOrder = "per_minute,per_km,fare,per_hour,total_km,total_minutes",
            voiceEnabledMetrics = "per_minute,per_km,fare,per_hour",
        )

        assertEquals(
            listOf("per_km", "per_hour", "fare", "per_minute"),
            HudPresentation.voiceMetricOrder(settings),
        )
    }

    @Test
    fun independentVoiceOrderIsPreservedWhenFollowHudIsOff() {
        val settings = DriverSettings(
            voiceFollowHudOrder = false,
            voiceMetricOrder = "fare,per_km,per_minute,per_hour,total_km,total_minutes",
            voiceEnabledMetrics = "fare,per_km,per_minute",
        )

        assertEquals(
            listOf("fare", "per_km", "per_minute"),
            HudPresentation.voiceMetricOrder(settings),
        )
    }
}
