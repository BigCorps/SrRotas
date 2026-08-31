package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBounds024Test {
    @Test
    fun clampsSavedPositionBackIntoPortraitViewport() {
        val p = OverlayBounds024.clamp(
            x = 980,
            y = 1700,
            viewportWidth = 1080,
            viewportHeight = 1920,
            contentWidth = 320,
            contentHeight = 500,
            margin = 12,
        )
        assertEquals(748, p.x)
        assertEquals(1408, p.y)
        assertTrue(
            OverlayBounds024.isVisible(
                p.x, p.y, 1080, 1920, 320, 500,
            ),
        )
    }

    @Test
    fun reclampsAfterRotation() {
        val portrait = OverlayBounds024.clamp(
            700, 1200, 1080, 1920, 300, 500, 12,
        )
        val landscape = OverlayBounds024.clamp(
            portrait.x, portrait.y, 1920, 1080, 300, 500, 12,
        )
        assertEquals(568, landscape.y)
        assertTrue(
            OverlayBounds024.isVisible(
                landscape.x, landscape.y, 1920, 1080, 300, 500,
            ),
        )
    }

    @Test
    fun oversizedContentNeverProducesNegativeCoordinates() {
        val p = OverlayBounds024.clamp(
            500, 500, 280, 420, 360, 600, 8,
        )
        assertEquals(8, p.x)
        assertEquals(8, p.y)
    }
}
