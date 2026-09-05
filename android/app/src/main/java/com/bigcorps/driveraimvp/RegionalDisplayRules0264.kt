package com.srrotas.app

/**
 * Regras puras de apresentação da Base Coletiva 0.26.4.
 *
 * Mantém prioridade de qualidade e alterna Base Pessoal/Coletiva quando os
 * próximos candidatos têm qualidade comparável. Uma fonte não é forçada se
 * sua próxima região estiver materialmente abaixo da melhor opção disponível.
 */
object RegionalDisplayRules0264 {
    data class Sourced<T>(
        val source: String,
        val value: T,
        val score: Double,
    )

    fun <T> mergeBestAlternating(
        personal: List<T>,
        collective: List<T>,
        score: (T) -> Double,
        key: (T) -> String,
        alternateRatio: Double = 0.82,
        limit: Int = 12,
    ): List<Sourced<T>> {
        if (limit <= 0) return emptyList()

        // A mesma região/perfil pode existir nas duas bases. Mantemos somente
        // a leitura com melhor condição para evitar dois cards redundantes.
        val unique = (personal.map { Sourced("personal", it, score(it)) } +
            collective.map { Sourced("collective", it, score(it)) })
            .groupBy { key(it.value).trim().lowercase() }
            .values
            .mapNotNull { candidates -> candidates.maxByOrNull { it.score } }

        val p = unique.filter { it.source == "personal" }.sortedByDescending { it.score }.toMutableList()
        val c = unique.filter { it.source == "collective" }.sortedByDescending { it.score }.toMutableList()
        val out = mutableListOf<Sourced<T>>()
        var lastSource: String? = null

        while (out.size < limit && (p.isNotEmpty() || c.isNotEmpty())) {
            val pNext = p.firstOrNull()
            val cNext = c.firstOrNull()
            val best = listOfNotNull(pNext, cNext).maxByOrNull { it.score } ?: break
            val opposite = when (lastSource) {
                "personal" -> cNext
                "collective" -> pNext
                else -> null
            }

            val chosen = if (
                opposite != null &&
                comparable(opposite.score, best.score, alternateRatio)
            ) {
                opposite
            } else {
                best
            }

            if (chosen.source == "personal") p.removeAt(0) else c.removeAt(0)
            out += chosen
            lastSource = chosen.source
        }
        return out
    }

    private fun comparable(candidate: Double, best: Double, ratio: Double): Boolean {
        if (best <= 0.0) return true
        return candidate >= best * ratio.coerceIn(0.0, 1.0)
    }
}
