package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalDisplayRules0264Test {
    private data class Item(val id: String, val score: Double)

    @Test fun alternatesComparableSources() {
        val mixed = RegionalDisplayRules0264.mergeBestAlternating(
            personal = listOf(Item("p1", 10.0), Item("p2", 9.0)),
            collective = listOf(Item("c1", 9.5), Item("c2", 8.8)),
            score = { it.score },
            key = { it.id },
        )
        assertEquals(listOf("personal", "collective", "personal", "collective"), mixed.map { it.source })
    }

    @Test fun materiallyBetterRegionKeepsPriority() {
        val mixed = RegionalDisplayRules0264.mergeBestAlternating(
            personal = listOf(Item("p1", 10.0), Item("p2", 9.5)),
            collective = listOf(Item("c1", 3.0)),
            score = { it.score },
            key = { it.id },
        )
        assertEquals(listOf("p1", "p2", "c1"), mixed.map { it.value.id })
    }

    @Test fun duplicateRegionUsesBetterSource() {
        val mixed = RegionalDisplayRules0264.mergeBestAlternating(
            personal = listOf(Item("same", 7.0)),
            collective = listOf(Item("same", 9.0)),
            score = { it.score },
            key = { it.id },
        )
        assertEquals(1, mixed.size)
        assertEquals("collective", mixed.single().source)
    }
}
