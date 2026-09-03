package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationContinuityHud026RulesTest {
    @Test fun slotNeverDisappearsWhenNoData() {
        assertEquals(DestinationContinuityHud026Rules.VisualState.NO_DATA, DestinationContinuityHud026Rules.visualState(null, false))
        assertEquals("Sem dados", DestinationContinuityHud026Rules.displayValue(null, null))
    }

    @Test fun colorsFollowProbabilityLevel() {
        assertEquals(DestinationContinuityHud026Rules.VisualState.GOOD, DestinationContinuityHud026Rules.visualState("high", true))
        assertEquals(DestinationContinuityHud026Rules.VisualState.MEDIUM, DestinationContinuityHud026Rules.visualState("medium", true))
        assertEquals(DestinationContinuityHud026Rules.VisualState.LOW, DestinationContinuityHud026Rules.visualState("low", true))
    }
}
