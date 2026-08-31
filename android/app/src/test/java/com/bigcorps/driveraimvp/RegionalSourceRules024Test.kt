package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalSourceRules024Test {
    @Test
    fun optedInCollectiveNeverFallsBackToSeed() {
        val selected = RegionalSourceRules024.select(
            requested = "collective",
            collectiveOptIn = true,
            seed = listOf("seed"),
            personal = listOf("personal"),
            collective = emptyList<String>(),
        )
        assertEquals("collective", selected.resolved)
        assertTrue(selected.items.isEmpty())
    }

    @Test
    fun lockedCollectiveMayUseSeedOnlyAsPreview() {
        val selected = RegionalSourceRules024.select(
            requested = "collective",
            collectiveOptIn = false,
            seed = listOf("preview"),
            personal = listOf("personal"),
            collective = emptyList<String>(),
        )
        assertEquals("collective_locked_preview", selected.resolved)
        assertEquals(listOf("preview"), selected.items)
    }

    @Test
    fun personalKeepsPersonalWhenAvailable() {
        val selected = RegionalSourceRules024.select(
            requested = "personal",
            collectiveOptIn = true,
            seed = listOf("seed"),
            personal = listOf("mine"),
            collective = listOf("community"),
        )
        assertEquals("personal", selected.resolved)
        assertEquals(listOf("mine"), selected.items)
    }
}
