package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationContinuityHudPosition026Test {
    @Test fun invalidPositionFallsBackToTop() {
        assertEquals(DestinationContinuityHud025.POSITION_TOP, DestinationContinuityHud025.normalizePosition(null))
        assertEquals(DestinationContinuityHud025.POSITION_TOP, DestinationContinuityHud025.normalizePosition("middle"))
        assertEquals(DestinationContinuityHud025.POSITION_BOTTOM, DestinationContinuityHud025.normalizePosition("bottom"))
    }
}
