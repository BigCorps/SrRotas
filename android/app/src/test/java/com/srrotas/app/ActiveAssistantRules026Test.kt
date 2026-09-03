package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAssistantRules026Test {
    @Test fun waitsTenMinutes() {
        val now = 1_000_000L
        assertTrue(!ActiveAssistantRules026.idleEnough(now, now - 9 * 60_000L))
        assertTrue(ActiveAssistantRules026.idleEnough(now, now - 10 * 60_000L))
    }

    @Test fun rejectsFarOrWeakRegions() {
        val result = ActiveAssistantRules026.rank(
            listOf(
                ActiveAssistantRules026.Candidate("Longe", 14.0, 30, 3.0, 60.0, "high", "personal"),
                ActiveAssistantRules026.Candidate("Fraca", 3.0, 20, 1.2, 25.0, "high", "personal"),
            ),
            targetPerKm = 1.8,
            targetPerHour = 35.0,
        )
        assertNull(result)
    }

    @Test fun combinesPersonalAndCollectiveConsensus() {
        val result = ActiveAssistantRules026.rank(
            listOf(
                ActiveAssistantRules026.Candidate("Centro", 3.2, 18, 2.25, 44.0, "high", "personal_history"),
                ActiveAssistantRules026.Candidate("Centro", 3.4, 40, 2.10, 42.0, "medium", "collective_history"),
                ActiveAssistantRules026.Candidate("Outro", 7.5, 8, 2.0, 39.0, "medium", "personal_history"),
            ),
            targetPerKm = 1.8,
            targetPerHour = 35.0,
        )
        assertNotNull(result)
        assertEquals("Centro", result!!.region)
        assertTrue("Pessoal" in result.sources)
        assertTrue("Coletiva" in result.sources)
    }

    @Test fun seedGetsLessWeight() {
        val personal = ActiveAssistantRules026.rank(
            listOf(ActiveAssistantRules026.Candidate("A", 4.0, 12, 2.6, 50.0, "medium", "personal")),
            1.8,
            35.0,
        )!!
        val seed = ActiveAssistantRules026.rank(
            listOf(ActiveAssistantRules026.Candidate("A", 4.0, 12, 2.6, 50.0, "medium", "sr_rotas_seed")),
            1.8,
            35.0,
        )!!
        assertTrue(personal.score > seed.score)
    }
}
