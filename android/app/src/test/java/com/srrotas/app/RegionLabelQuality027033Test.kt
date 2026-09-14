package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionLabelQuality027033Test {
    @Test fun rejectsUiLabels() {
        assertFalse(RegionLabelQuality027033.usable("Área"))
        assertFalse(RegionLabelQuality027033.usable("Desloque-se até"))
        assertFalse(RegionLabelQuality027033.usable("Destino"))
    }

    @Test fun acceptsRealRegion() {
        assertTrue(RegionLabelQuality027033.usable("Barra Funda"))
        assertTrue(RegionLabelQuality027033.usable("Casa Verde"))
    }
}
