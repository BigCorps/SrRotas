package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudReliabilityRules0261Test {
    @Test fun forcesOcrAfterShortStaticWindow() {
        assertFalse(HudReliabilityRules0261.forceOcrDue(1_000, 2_200))
        assertTrue(HudReliabilityRules0261.forceOcrDue(1_000, 2_250))
    }

    @Test fun rejectsThreeHundredMinuteDigitDuplicationWithoutHourEvidence() {
        assertTrue(HudReliabilityRules0261.rejectUberGeometry(7.1, 300, 5.28, false))
    }

    @Test fun keepsLegitimateLongUberTripWithHourEvidence() {
        assertFalse(HudReliabilityRules0261.rejectUberGeometry(44.0, 91, 2.0, true))
    }

    @Test fun rejectsCrossCardLongDistanceWithTinyFareRate() {
        assertTrue(HudReliabilityRules0261.rejectUberGeometry(45.0, 64, 0.23, true))
        assertFalse(HudReliabilityRules0261.rejectUberGeometry(12.0, 35, 0.50, false))
    }

    @Test fun neighborFaresSplitVerticalCardsAtMidpoints() {
        assertEquals(200..400, HudReliabilityRules0261.verticalBand(300, 100, 500, 0, 900))
        assertEquals(0..400, HudReliabilityRules0261.verticalBand(300, null, 500, 0, 900))
        assertEquals(200..900, HudReliabilityRules0261.verticalBand(300, 100, null, 0, 900))
    }
}
