package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRadarRules026Test {
    @Test fun labelsAndVisibilityAreStable() {
        val item = EventRadarOpportunity026(
            id = "e1",
            type = "sports",
            name = "Jogo",
            venueName = "Arena",
            address = null,
            startsAt = "2026-09-02T21:00:00Z",
            expectedEndAt = "2026-09-03T00:00:00Z",
            egressStartAt = "2026-09-02T23:40:00Z",
            egressEndAt = "2026-09-03T01:15:00Z",
            distanceKm = 4.2,
            source = "ticketmaster",
            confidence = 0.92,
            sourceUrl = null,
        )
        assertEquals("Esporte", EventRadarRules026.typeLabel(item.type))
        assertEquals("alta", EventRadarRules026.confidenceLabel(item.confidence))
        assertTrue(EventRadarRules026.visible(item))
    }
}
