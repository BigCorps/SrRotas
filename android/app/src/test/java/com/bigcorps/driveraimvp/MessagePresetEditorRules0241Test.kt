package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePresetEditorRules0241Test {
    @Test
    fun keepsSixSlotsAsMinimum() {
        assertEquals(
            6,
            MessagePresetEditorRules024.editorSlots(
                emptyList(),
                "",
            ).size,
        )
    }

    @Test
    fun canGrowUntilTwelveAndRepeatsColors() {
        var items =
            MessagePresetEditorRules024.editorSlots(
                emptyList(),
                "",
            )
        repeat(10) {
            items =
                MessagePresetEditorRules024.addSlot(items)
        }
        assertEquals(12, items.size)
        assertEquals(
            MessageShortcutRules023.colorFor(0),
            items[6].colorToken,
        )
    }

    @Test
    fun sanitizationNeverExceedsTwelve() {
        val source = (0 until 20).map { index ->
            MessageShortcut023(
                id = "x-$index",
                order = index,
                shortLabel = index.toString(),
                accessibilityLabel = null,
                text = "m$index",
                colorToken =
                    MessageShortcutRules023.colorFor(index),
                enabled = true,
            )
        }
        val clean =
            MessagePresetEditorRules024.sanitizeEditorItems(
                source,
            )
        assertEquals(12, clean.size)
        assertTrue(clean.all { it.order in 0..11 })
    }
}
