package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalSourceRules024Test {
    @Test
    fun optedInCollectiveMergesPersonalWithoutSeedFallback() {
        val selected = RegionalSourceRules024.select(
            requested = "collective",
            collectiveOptIn = true,
            seed = listOf("seed"),
            personal = listOf("personal"),
            collective = emptyList<String>(),
        )
        assertEquals("collective_merged", selected.resolved)
        assertEquals(listOf("personal"), selected.items)
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

    @Test
    fun optedInCollectiveKeepsCollectiveFirstAndRemovesDuplicates() {
        val selected = RegionalSourceRules024.select(
            requested = "collective",
            collectiveOptIn = true,
            seed = listOf("seed"),
            personal = listOf("shared", "personal"),
            collective = listOf("collective", "shared"),
        )
        assertEquals("collective_merged", selected.resolved)
        assertEquals(listOf("collective", "shared", "personal"), selected.items)
    }
}
