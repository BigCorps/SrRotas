package com.srrotas.app

/**
 * Regras puras da UI Freeze 0.23 para o HUD.
 *
 * Nenhuma regra financeira mora aqui: este objeto define apenas apresentação.
 */
object Hud023Spec {
    const val SIZE_COMPACT = "compact"
    const val SIZE_NORMAL = "normal"
    const val SIZE_LARGE = "large"

    fun normalizeSize(value: String?): String = when (value?.lowercase()) {
        SIZE_COMPACT -> SIZE_COMPACT
        SIZE_LARGE -> SIZE_LARGE
        else -> SIZE_NORMAL
    }

    fun columns(size: String?): Int =
        if (normalizeSize(size) == SIZE_COMPACT) 1 else 2

    /**
     * Os três tamanhos mostram exatamente as mesmas métricas habilitadas.
     * O tamanho muda somente a composição visual.
     */
    fun visibleMetricKeys(
        orderCsv: String,
        enabledCsv: String,
        availableKeys: Set<String>,
    ): List<String> {
        val ordered = orderCsv
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        val enabled = enabledCsv
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

        val active = if (enabled.isEmpty()) ordered.toSet() else enabled
        val result = ordered
            .filter { it in active && it in availableKeys }
            .toMutableList()

        // Preserva métricas habilitadas por versões futuras mesmo se ainda não
        // estiverem presentes na ordem antiga salva no aparelho.
        active
            .filter { it in availableKeys && it !in result }
            .sorted()
            .forEach(result::add)

        return result
    }

    fun fullWidthMetric(key: String): Boolean = key == "profit"
}
