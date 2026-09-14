package com.srrotas.app

import java.text.Normalizer

/** Bloqueia textos de interface que não representam uma região real. */
object RegionLabelQuality027033 {
    private val exact = setOf(
        "area", "regiao", "destino", "origem", "retirada", "embarque",
        "buscar", "aceitar", "escolher", "entrada principal", "viagem longa",
        "como foi a viagem", "area semi", "semi coberta", "uberx", "comfort",
        "black", "99pop", "99plus", "99moto",
    )

    fun usable(value: String?): Boolean {
        val key = normalize(value)
        if (key.length < 3 || key in exact) return false
        if (key.startsWith("desloque se")) return false
        if (key.matches(Regex("^(area|regiao|destino|origem|retirada|embarque|buscar|aceitar|escolher)(\\s.*)?$"))) return false
        return true
    }

    fun sanitize(value: String?): String? = value?.trim()?.takeIf(::usable)

    private fun normalize(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
