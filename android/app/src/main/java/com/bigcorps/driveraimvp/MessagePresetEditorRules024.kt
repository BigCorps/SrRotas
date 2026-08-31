package com.srrotas.app

/** Regras puras do editor de mensagens rápidas 0.24.1. */
object MessagePresetEditorRules024 {
    const val MIN_SLOTS = 6
    const val MAX_SLOTS = 12

    private val suggested = listOf(
        "Olá! Já estou a caminho do local de embarque.",
        "Cheguei ao local de embarque.",
        "Estou próximo. Pode se dirigir ao ponto de embarque?",
        "O trânsito está um pouco lento, mas sigo a caminho.",
        "Não consigo parar exatamente aí. Pode vir até um ponto seguro próximo?",
        "Obrigado! Boa viagem.",
    )

    fun editorSlots(
        current: List<MessageShortcut023>,
        fallbackFirst: String,
        requestedCount: Int? = null,
    ): List<MessageShortcut023> {
        val normalized = MessageShortcutRules023.normalized(current)
            .associateBy { it.order }
        val highestExisting =
            normalized.keys.maxOrNull()?.plus(1) ?: 0
        val count = maxOf(
            MIN_SLOTS,
            highestExisting,
            requestedCount ?: 0,
        ).coerceAtMost(MAX_SLOTS)

        return (0 until count).map { order ->
            normalized[order] ?: newSlot(
                order = order,
                fallbackFirst = fallbackFirst,
            )
        }
    }

    /** Compatibilidade com código/testes antigos. */
    fun sixSlots(
        current: List<MessageShortcut023>,
        fallbackFirst: String,
    ): List<MessageShortcut023> =
        editorSlots(
            current = current,
            fallbackFirst = fallbackFirst,
            requestedCount = MIN_SLOTS,
        ).take(MIN_SLOTS)

    fun addSlot(
        current: List<MessageShortcut023>,
        fallbackFirst: String = "",
    ): List<MessageShortcut023> {
        val normalized = editorSlots(
            current,
            fallbackFirst,
        )
        if (normalized.size >= MAX_SLOTS) return normalized
        return editorSlots(
            current = normalized,
            fallbackFirst = fallbackFirst,
            requestedCount = normalized.size + 1,
        )
    }

    fun sanitizeEditorItems(
        items: List<MessageShortcut023>,
    ): List<MessageShortcut023> =
        MessageShortcutRules023.normalized(items)
            .filter { it.order in 0 until MAX_SLOTS }
            .mapIndexed { index, item ->
                item.copy(
                    order = index,
                    shortLabel = (index + 1).toString(),
                    colorToken = MessageShortcutRules023.colorFor(index),
                )
            }
            .take(MAX_SLOTS)

    private fun newSlot(
        order: Int,
        fallbackFirst: String,
    ) = MessageShortcut023(
        id = "local-slot-${order + 1}",
        order = order,
        shortLabel = (order + 1).toString(),
        accessibilityLabel = "Mensagem rápida ${order + 1}",
        text = when {
            order == 0 && fallbackFirst.isNotBlank() ->
                fallbackFirst.trim().take(500)
            order in suggested.indices ->
                suggested[order]
            else -> ""
        },
        colorToken = MessageShortcutRules023.colorFor(order),
        enabled = true,
    )
}
