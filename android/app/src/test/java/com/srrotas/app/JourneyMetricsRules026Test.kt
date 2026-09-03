package com.srrotas.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyMetricsRules026Test {
    @Test fun distanceUsesOnlyJourneyOdometers() {
        assertEquals(123.5, JourneyMetricsRules026.distanceKm(12_345.0, 12_468.5))
        assertNull(JourneyMetricsRules026.distanceKm(500.0, 499.9))
    }

    @Test fun fuelAndElectricUnitsAreSeparated() {
        assertTrue(JourneyMetricsRules026.validEnergyEntry("fuel", 150.0, 20.0, "liter"))
        assertTrue(JourneyMetricsRules026.validEnergyEntry("electric", null, 30.0, "kwh"))
        assertFalse(JourneyMetricsRules026.validEnergyEntry("fuel", 100.0, 10.0, "kwh"))
        assertFalse(JourneyMetricsRules026.validEnergyEntry("electric", null, 0.0, "kwh"))
    }
}
