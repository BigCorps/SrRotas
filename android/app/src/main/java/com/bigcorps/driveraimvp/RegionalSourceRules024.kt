package com.srrotas.app

/**
 * Fonte de dados do Agora.
 *
 * A Base Coletiva, quando selecionada e autorizada, é a fonte efetiva.
 * Não fazemos fallback silencioso para a seed quando a base coletiva está vazia.
 */
object RegionalSourceRules024 {
    data class Selection<T>(
        val items: List<T>,
        val resolved: String,
    )

    fun <T> select(
        requested: String,
        collectiveOptIn: Boolean,
        seed: List<T>,
        personal: List<T>,
        collective: List<T>,
    ): Selection<T> = when (requested) {
        "collective" ->
            if (collectiveOptIn) {
                Selection(collective, "collective")
            } else {
                Selection(seed, "collective_locked_preview")
            }

        else ->
            if (personal.isNotEmpty()) {
                Selection(personal, "personal")
            } else {
                Selection(seed, "personal_seed_fallback")
            }
    }
}
