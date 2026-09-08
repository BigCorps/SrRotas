package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Validation0265RulesTest {
    @Test fun assistantIntervalIsLimitedToRequestedRange() {
        assertEquals(10, ActiveAssistantPrefs0265.normalize(8))
        assertEquals(12, ActiveAssistantPrefs0265.normalize(12))
        assertEquals(15, ActiveAssistantPrefs0265.normalize(20))
    }

    @Test fun assistantTimestampPreservesConfiguredCooldownAgainstLegacyTwentyMinutes() {
        val now = 1_000_000L
        assertEquals(now - 10L * 60_000L, ActiveAssistantPrefs0265.adjustedLastSuggestion(now, 10))
        assertEquals(now - 8L * 60_000L, ActiveAssistantPrefs0265.adjustedLastSuggestion(now, 12))
        assertEquals(now - 5L * 60_000L, ActiveAssistantPrefs0265.adjustedLastSuggestion(now, 15))
    }

    @Test fun digitizationNormalizerKeepsEvidenceAndRemovesOnlyConsecutiveDuplicates() {
        val out = UberDigitizationText0265.normalize("R $ 28, 50\nR $ 28, 50\n12 min 2 km\nComfort")
        assertTrue(out.contains("R$28,50"))
        assertTrue(out.contains("12 min 2 km"))
        assertTrue(out.contains("Comfort"))
        assertEquals(1, Regex("R\\$28,50").findAll(out).count())
        assertFalse(out.contains("  "))
    }
    @Test fun digitizationNormalizerDoesNotMergeEqualValuesFromDifferentCards() {
        val out = UberDigitizationText0265.normalize("R $ 28, 50\n12 min 2 km\nR $ 28, 50\n20 min 8 km")
        assertEquals(2, Regex("R\\$28,50").findAll(out).count())
    }

}
