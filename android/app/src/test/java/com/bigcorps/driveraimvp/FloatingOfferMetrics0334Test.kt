package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FloatingOfferMetrics0334Test {
    private fun offer() = RideOffer(
        observedAt = "2026-09-25T12:00:00Z",
        sourcePackage = "com.ubercab.driver",
        captureMethod = "media-projection",
        rawText = "",
        fare = 40.0,
        pickupKm = 2.0,
        tripKm = 8.0,
        totalKm = 10.0,
        pickupMinutes = 5,
        tripMinutes = 20,
        totalMinutes = 25,
        perKm = 4.0,
        perHour = 96.0,
        perMinute = 1.6,
        estimatedCost = 8.5,
        estimatedProfit = 31.5,
        profitPerHour = 75.6,
        profitPercent = 78.75,
        passengerRating = 4.95,
        advertisedPerKm = null,
        verdict = "boa",
        dedupeKey = "floating-0334-test",
    )

    @Test fun coreDecisionMetricsAreRestoredInStableOrder() {
        val specs = FloatingOfferMetrics0334.specs(
            offer(),
            DriverSettings(),
            maxPickupMinutes = 10,
        )
        assertEquals(
            listOf("per_km", "per_minute", "per_hour", "total_km", "total_minutes", "profit"),
            specs.take(6).map { it.key },
        )
    }

    @Test fun financialGradesComeFromCanonicalHudEvaluator() {
        val settings = DriverSettings(
            minPerKm = 3.0,
            redPerKmBelow = 2.0,
            minPerMinute = 1.5,
            redPerMinuteBelow = 1.0,
            minPerHour = 80.0,
            redPerHourBelow = 60.0,
            minProfit = 25.0,
        )
        val o = offer()
        val specs = FloatingOfferMetrics0334.specs(o, settings, 10).associateBy { it.key }
        listOf("per_km", "per_minute", "per_hour", "profit").forEach { key ->
            assertEquals(
                HudMetricEvaluation0221.grade(key, o, settings, 10),
                specs[key]?.grade,
            )
        }
        assertEquals(null, specs["total_km"]?.grade)
        assertEquals(null, specs["total_minutes"]?.grade)
    }

    @Test fun controllerPlacesMetricsInFirstExpandedLevel() {
        val root = locateSources()
        val controller = File(root, "JourneyBubbleController.kt").readText()
        val helper = File(root, "FloatingOfferMetrics0334.kt").readText()
        val helperIndex = controller.indexOf("FloatingOfferMetrics0334.build")
        val routeIndex = controller.indexOf("val pickupIntent", helperIndex)
        val deepIndex = controller.indexOf("val deepOpen", helperIndex)
        assertTrue(helperIndex >= 0)
        assertTrue(routeIndex > helperIndex)
        assertTrue(deepIndex > helperIndex)
        assertTrue(helper.contains("HudMetricEvaluation0221.grade"))
        assertFalse(helper.contains("redPerKmBelow"))
        assertFalse(helper.contains("redPerMinuteBelow"))
        assertFalse(helper.contains("redPerHourBelow"))
    }

    private fun locateSources(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }.toList()
        return candidates.firstOrNull { File(it, "JourneyBubbleController.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas")
    }
}
