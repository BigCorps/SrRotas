package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SrTheme026PaletteTest {
    @Test fun lightBackgroundIsNearWhiteNotOldCream() {
        assertEquals(0xFFFAFCF8.toInt(), SrTheme024.LIGHT.background)
        assertNotEquals(0xFFF6F3EB.toInt(), SrTheme024.LIGHT.background)
    }

    @Test fun commercialAccentsRemainDistinctAndVivid() {
        val p = SrTheme024.LIGHT
        assertNotEquals(p.now, p.history)
        assertNotEquals(p.history, p.ai)
        assertEquals(0xFF087CFF.toInt(), p.now)
        assertEquals(0xFF00BDB3.toInt(), p.history)
        assertEquals(0xFF7C3AED.toInt(), p.ai)
        assertEquals(0xFFFF7600.toInt(), p.settings)
        assertEquals(0xFF00D968.toInt(), p.good)
        assertEquals(0xFFFFB800.toInt(), p.warn)
        assertEquals(0xFFFF3B30.toInt(), p.bad)
    }
}
