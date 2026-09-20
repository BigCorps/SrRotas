package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutMath027038Test {
    @Test
    fun contentNeverExceedsAvailableWidthAcrossPhoneAndTabletWidths() {
        listOf(240, 280, 320, 360, 411, 480, 600, 840, 1200).forEach { screen ->
            val margin = 16
            val result = ResponsiveLayoutMath027038.contentWidthDp(screen, maxDp = 760, horizontalMarginDp = margin)
            val available = (screen - margin * 2).coerceAtLeast(1)
            assertTrue("screen=$screen result=$result available=$available", result in 1..available)
            assertTrue("screen=$screen max exceeded", result <= 760)
        }
    }

    @Test
    fun widthBreakpointsAreStable() {
        assertTrue(ResponsiveLayoutMath027038.isVeryNarrow(320))
        assertTrue(ResponsiveLayoutMath027038.isNarrow(359))
        assertEquals(false, ResponsiveLayoutMath027038.isNarrow(360))
    }
}
