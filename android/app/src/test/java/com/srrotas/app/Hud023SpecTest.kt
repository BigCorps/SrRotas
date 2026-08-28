package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Hud023SpecTest {
    @Test
    fun threeSizesKeepExactlyTheSameMetricKeys() {
        val available = setOf("per_minute", "pickup", "per_km", "per_hour", "profit")
        val order = "per_minute,pickup,per_km,per_hour,profit"
        val enabled = "per_minute,pickup,per_km,per_hour,profit"

        val expected = Hud023Spec.visibleMetricKeys(order, enabled, available)

        listOf("compact", "normal", "large").forEach { size ->
            assertTrue(Hud023Spec.columns(size) in 1..2)
            assertEquals(expected, Hud023Spec.visibleMetricKeys(order, enabled, available))
        }
    }

    @Test
    fun compactUsesOneColumnAndOtherModesUseTwo() {
        assertEquals(1, Hud023Spec.columns("compact"))
        assertEquals(2, Hud023Spec.columns("normal"))
        assertEquals(2, Hud023Spec.columns("large"))
    }

    @Test
    fun enabledKeysMissingFromLegacyOrderAreNotLost() {
        val visible = Hud023Spec.visibleMetricKeys(
            orderCsv = "per_minute,per_km,per_hour",
            enabledCsv = "per_minute,pickup,per_km,per_hour,profit",
            availableKeys = setOf("per_minute", "pickup", "per_km", "per_hour", "profit"),
        )
        assertEquals(5, visible.size)
        assertTrue("pickup" in visible)
        assertTrue("profit" in visible)
    }

    @Test
    fun estimatedProfitIsFullWidthMetric() {
        assertTrue(Hud023Spec.fullWidthMetric("profit"))
    }
}
