package com.srrotas.app

/**
 * Atalho de mensagem da 0.23.
 *
 * A UI nunca guarda frases próprias: recebe a lista sincronizada da conta,
 * ordena e apenas copia o texto. Não envia nada ao app de motorista.
 */
data class MessageShortcut023(
    val id: String,
    val order: Int,
    val shortLabel: String,
    val accessibilityLabel: String?,
    val text: String,
    val colorToken: String,
    val enabled: Boolean,
)

object MessageShortcutRules023 {
    private val allowedColors = setOf(
        "shortcut01",
        "shortcut02",
        "shortcut03",
        "shortcut04",
        "shortcut05",
        "shortcut06",
    )

    fun normalized(items: List<MessageShortcut023>): List<MessageShortcut023> =
        items.asSequence()
            .filter { it.order in 0..99 }
            .map { item ->
                item.copy(
                    id = item.id.trim().take(80).ifBlank { "slot-${item.order + 1}" },
                    shortLabel = item.shortLabel.trim().take(2).ifBlank { (item.order + 1).toString().take(2) },
                    accessibilityLabel = item.accessibilityLabel?.trim()?.take(120)?.ifBlank { null },
                    text = item.text.take(500),
                    colorToken = item.colorToken.takeIf(allowedColors::contains) ?: colorFor(item.order),
                )
            }
            .distinctBy { it.order }
            .sortedBy { it.order }
            .take(12)
            .toList()

    fun visible(items: List<MessageShortcut023>): List<MessageShortcut023> =
        normalized(items).filter { it.enabled && it.text.isNotBlank() }

    fun colorFor(order: Int): String = "shortcut0${(order % 6) + 1}"
}
