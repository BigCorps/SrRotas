package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudConfigRules024Test {
    @Test
    fun parsesBrazilianDecimalWithoutUnitTyping() {
        assertEquals(1.8, HudConfigRules024.parseDecimal("1,80")!!, 0.0001)
        assertEquals(35.0, HudConfigRules024.parseDecimal("35,00 %")!!, 0.0001)
    }

    @Test
    fun blocksTargetBelowMinimum() {
        val result = HudConfigRules024.validateBenefitPair(
            "R$/km",
            minimum = 2.0,
            target = 1.5,
        )
        assertFalse(result.valid)
    }

    @Test
    fun acceptsEqualOrHigherTarget() {
        assertTrue(
            HudConfigRules024.validateBenefitPair(
                "R$/km",
                minimum = 1.5,
                target = 1.5,
            ).valid,
        )
        assertTrue(
            HudConfigRules024.validateBenefitPair(
                "R$/km",
                minimum = 1.5,
                target = 2.0,
            ).valid,
        )
    }

    @Test
    fun appendsPickupToMetricOrderWhenEnabled() {
        val order = HudConfigRules024.ensureMetricOrder(
            "per_minute,per_km,rating,per_hour",
            setOf("per_minute", "pickup"),
        )
        assertEquals(
            "per_minute,per_km,rating,per_hour,pickup",
            order,
        )
    }

    @Test
    fun requiresAtLeastOneActiveMetric() {
        assertFalse(HudConfigRules024.validateEnabled(emptySet()).valid)
        assertTrue(HudConfigRules024.validateEnabled(setOf("per_km")).valid)
    }

    @Test
    fun pickupBoundaryPreservesValidatedSeventyFivePercentRule() {
        assertEquals(
            3.75,
            HudConfigRules024.pickupGoodBoundary(5.0),
            0.0001,
        )
    }
}
