package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferContextEngineTest {
    @Test
    fun explicitLabelsWinAndEtaIsLocal() {
        val context = OfferContextEngine.extract(
            lines = listOf(
                ContextOcrLine("R$ 32,40", 100),
                ContextOcrLine("Retirada", 200),
                ContextOcrLine("Av. Paulista, 1000", 220),
                ContextOcrLine("8 min (2,0 km)", 250),
                ContextOcrLine("Destino", 300),
                ContextOcrLine("Rua Vergueiro, 1500", 320),
                ContextOcrLine("22 min (8,5 km)", 350),
            ),
            observedAt = "2026-08-19T12:00:00Z",
            totalMinutes = 30,
        )

        assertNotNull(context)
        assertEquals("Av. Paulista, 1000", context?.pickupLabel)
        assertEquals("Rua Vergueiro, 1500", context?.destinationLabel)
        assertEquals("2026-08-19T12:30:00Z", context?.estimatedArrivalAt)
        assertTrue((context?.contextConfidence ?: 0.0) >= 0.9)
    }

    @Test
    fun geometryAnchorsAssociateTwoPlacesWithoutTouchingFinancialParser() {
        val context = OfferContextEngine.extract(
            lines = listOf(
                ContextOcrLine("R$ 28,50", 100),
                ContextOcrLine("5 min (1,2 km)", 180),
                ContextOcrLine("Rua das Flores, 25", 210),
                ContextOcrLine("20 min (7,3 km)", 280),
                ContextOcrLine("Vila Mariana, São Paulo", 315),
                ContextOcrLine("R$ 3,35/km", 370),
            ),
            observedAt = "2026-08-19T12:00:00Z",
            totalMinutes = 25,
        )

        assertEquals("Rua das Flores, 25", context?.pickupLabel)
        assertEquals("Vila Mariana, São Paulo", context?.destinationLabel)
        assertTrue((context?.contextConfidence ?: 0.0) >= 0.8)
    }

    @Test
    fun utilityLinesAreNotInventedAsDestination() {
        val context = OfferContextEngine.extract(
            lines = listOf(
                ContextOcrLine("R$ 18,90", 100),
                ContextOcrLine("UberX", 130),
                ContextOcrLine("4 min (1,0 km)", 180),
                ContextOcrLine("R$ 2,10/km", 220),
                ContextOcrLine("ACEITAR", 300),
            ),
            observedAt = "2026-08-19T12:00:00Z",
            totalMinutes = 12,
        )

        assertNull(context?.pickupLabel)
        assertNull(context?.destinationLabel)
        assertEquals("2026-08-19T12:12:00Z", context?.estimatedArrivalAt)
    }

    @Test
    fun geoCellIsStableAndVersioned() {
        assertEquals("g2:-2356:-4664", OfferContextEngine.geoCell(-23.5505, -46.6333))
    }
}
