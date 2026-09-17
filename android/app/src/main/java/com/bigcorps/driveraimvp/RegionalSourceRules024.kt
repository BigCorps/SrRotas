package com.srrotas.app

/**
 * Fonte de dados do Agora.
 * RC3.5: quem participa da Base Coletiva vê uma lista unificada com a base
 * coletiva primeiro e sua base pessoal em complemento, sem dois blocos artificiais.
 */
object RegionalSourceRules024 {
    data class Selection<T>(val items: List<T>, val resolved: String)

    fun <T> select(
        requested: String,
        collectiveOptIn: Boolean,
        seed: List<T>,
        personal: List<T>,
        collective: List<T>,
    ): Selection<T> = when (requested) {
        "collective" -> if (collectiveOptIn) {
            Selection((collective + personal).distinct(), "collective_merged")
        } else {
            Selection(seed, "collective_locked_preview")
        }
        else -> if (personal.isNotEmpty()) Selection(personal, "personal") else Selection(seed, "personal_seed_fallback")
    }
}
