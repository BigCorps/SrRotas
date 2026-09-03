package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetViewRules026Test {
    @Test fun validDestinationCreatesOfficialPanoUrl() {
        assertTrue(StreetViewRules026.eligible(-23.5505, -46.6333, 0.80))
        val url = StreetViewRules026.mapsUrl(-23.5505, -46.6333)
        assertTrue(url.contains("api=1"))
        assertTrue(url.contains("map_action=pano"))
        assertTrue(url.contains("viewpoint=-23.5505%2C-46.6333"))
    }

    @Test fun lowConfidenceDoesNotExposeStreetView() {
        assertFalse(StreetViewRules026.eligible(-23.5505, -46.6333, 0.30))
        assertFalse(StreetViewRules026.eligible(null, -46.6333, 0.90))
    }
}
