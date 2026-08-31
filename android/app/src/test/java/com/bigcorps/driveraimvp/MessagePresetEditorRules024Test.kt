package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePresetEditorRules024Test {
    @Test
    fun createsExactlySixSlotsWhenRemoteIsEmpty() {
        val result = MessagePresetEditorRules024.sixSlots(
            emptyList(),
            "Minha mensagem principal",
        )
        assertEquals(6, result.size)
        assertEquals("Minha mensagem principal", result[0].text)
        assertEquals("6", result[5].shortLabel)
    }

    @Test
    fun preservesExistingSlotsAndNormalizesEditorOrder() {
        val source = listOf(
            MessageShortcut023(
                id = "x",
                order = 0,
                shortLabel = "A",
                accessibilityLabel = null,
                text = "Teste",
                colorToken = "shortcut01",
                enabled = true,
            ),
        )
        val six = MessagePresetEditorRules024.sixSlots(source, "")
        assertEquals("Teste", six[0].text)
        val normalized =
            MessagePresetEditorRules024.sanitizeEditorItems(six)
        assertEquals((0..5).toList(), normalized.map { it.order })
        assertTrue(normalized.all { it.shortLabel.isNotBlank() })
    }
}
