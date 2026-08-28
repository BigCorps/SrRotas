package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageShortcutRules023Test {
    @Test
    fun visibleOrdersAndFiltersWithoutChangingText() {
        val items = listOf(
            MessageShortcut023("b", 2, "3", null, "  Terceira mensagem  ", "shortcut03", true),
            MessageShortcut023("a", 0, "1", "Primeira", "Primeira mensagem", "shortcut01", true),
            MessageShortcut023("x", 1, "2", null, "Segunda mensagem", "shortcut02", false),
        )
        val visible = MessageShortcutRules023.visible(items)
        assertEquals(listOf(0, 2), visible.map { it.order })
        assertEquals("Primeira mensagem", visible[0].text)
        assertEquals("Terceira mensagem", visible[1].text)
    }

    @Test
    fun invalidColorFallsBackToSlotColor() {
        val item = MessageShortcut023("a", 4, "5", null, "Mensagem", "anything", true)
        val normalized = MessageShortcutRules023.normalized(listOf(item)).single()
        assertEquals("shortcut05", normalized.colorToken)
        assertTrue(normalized.enabled)
        assertFalse(normalized.text.isBlank())
    }
}
