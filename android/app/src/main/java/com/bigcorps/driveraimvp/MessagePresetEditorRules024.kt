package com.srrotas.app

/** Regras puras do editor local de mensagens rápidas. */
object MessagePresetEditorRules024 {
    private val suggested = listOf(
        "Olá! Já estou a caminho do local de embarque.",
        "Cheguei ao local de embarque.",
        "Estou próximo. Pode se dirigir ao ponto de embarque?",
        "O trânsito está um pouco lento, mas sigo a caminho.",
        "Não consigo parar exatamente aí. Pode vir até um ponto seguro próximo?",
        "Obrigado! Boa viagem.",
    )

    fun sixSlots(
        current: List<MessageShortcut023>,
        fallbackFirst: String,
    ): List<MessageShortcut023> {
        val normalized = MessageShortcutRules023.normalized(current)
            .associateBy { it.order }

        return (0 until 6).map { order ->
            normalized[order] ?: MessageShortcut023(
                id = "local-slot-${order + 1}",
                order = order,
                shortLabel = (order + 1).toString(),
                accessibilityLabel = "Mensagem rápida ${order + 1}",
                text = if (order == 0 && fallbackFirst.isNotBlank()) {
                    fallbackFirst.trim().take(500)
                } else {
                    suggested[order]
                },
                colorToken = MessageShortcutRules023.colorFor(order),
                enabled = true,
            )
        }
    }

    fun sanitizeEditorItems(
        items: List<MessageShortcut023>,
    ): List<MessageShortcut023> =
        MessageShortcutRules023.normalized(items)
            .filter { it.order in 0..5 }
            .mapIndexed { index, item ->
                item.copy(
                    order = index,
                    shortLabel = (index + 1).toString(),
                    colorToken = MessageShortcutRules023.colorFor(index),
                )
            }
}
