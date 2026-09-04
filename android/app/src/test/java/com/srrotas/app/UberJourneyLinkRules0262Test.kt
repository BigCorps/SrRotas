package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberJourneyLinkRules0262Test {
    @Test
    fun matchesUniqueOverlappingJourney() {
        val found = UberJourneyLinkRules0262.match(
            "2026-09-02T10:21:00Z",
            "2026-09-02T19:45:00Z",
            listOf(
                UberJourneyLinkRules0262.Candidate("correct", "2026-09-02T10:20:00Z", "2026-09-02T19:46:00Z"),
                UberJourneyLinkRules0262.Candidate("other", "2026-09-02T20:00:00Z", "2026-09-02T21:00:00Z"),
            ),
        )
        assertEquals("correct", found)
    }

    @Test
    fun ambiguousJourneysAreNotLinked() {
        val found = UberJourneyLinkRules0262.match(
            "2026-09-02T10:00:00Z",
            "2026-09-02T11:00:00Z",
            listOf(
                UberJourneyLinkRules0262.Candidate("a", "2026-09-02T10:01:00Z", "2026-09-02T11:00:00Z"),
                UberJourneyLinkRules0262.Candidate("b", "2026-09-02T10:02:00Z", "2026-09-02T11:01:00Z"),
            ),
        )
        assertNull(found)
    }
}
