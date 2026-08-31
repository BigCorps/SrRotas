package com.srrotas.app

import java.util.Locale

/**
 * Regras puras da tela Configuração do HUD 0.24.
 *
 * Não altera cálculo financeiro. Apenas traduz/valida o formulário antes de
 * persistir nos campos já usados pelo motor.
 */
object HudConfigRules024 {
    data class PairValue(
        val minimum: Double,
        val target: Double,
    )

    data class Validation(
        val valid: Boolean,
        val message: String? = null,
    )

    data class Preset(
        val key: String,
        val perKm: PairValue,
        val perMinute: PairValue,
        val perHour: PairValue,
    )

    val POPULAR = Preset(
        key = "popular",
        perKm = PairValue(1.20, 1.50),
        perMinute = PairValue(0.40, 0.50),
        perHour = PairValue(24.0, 30.0),
    )

    val COMFORT = Preset(
        key = "comfort",
        perKm = PairValue(1.50, 1.80),
        perMinute = PairValue(0.50, 0.65),
        perHour = PairValue(30.0, 39.0),
    )

    val PREMIUM = Preset(
        key = "premium",
        perKm = PairValue(1.80, 2.20),
        perMinute = PairValue(0.65, 0.85),
        perHour = PairValue(39.0, 51.0),
    )

    fun preset(key: String): Preset? = when (key) {
        "popular" -> POPULAR
        "comfort" -> COMFORT
        "premium" -> PREMIUM
        else -> null
    }

    fun parseDecimal(value: String): Double? =
        value.trim()
            .replace("R$", "", ignoreCase = true)
            .replace("%", "")
            .replace("★", "")
            .replace(',', '.')
            .trim()
            .toDoubleOrNull()

    fun format(value: Double, decimals: Int = 2): String =
        String.format(
            Locale("pt", "BR"),
            "%.${decimals.coerceIn(0, 4)}f",
            value,
        )

    /**
     * Benefício: maior é melhor.
     * "Mínimo" = limite aceitável (vermelho abaixo).
     * "Máximo/meta" = início da faixa boa (verde).
     */
    fun validateBenefitPair(
        name: String,
        minimum: Double?,
        target: Double?,
        maxAllowed: Double = 100000.0,
    ): Validation {
        if (minimum == null || target == null) {
            return Validation(false, "$name: preencha Mínimo e Máximo.")
        }
        if (minimum < 0.0 || target < 0.0) {
            return Validation(false, "$name: os valores não podem ser negativos.")
        }
        if (minimum > maxAllowed || target > maxAllowed) {
            return Validation(false, "$name: valor acima do limite permitido.")
        }
        if (target < minimum) {
            return Validation(
                false,
                "$name: Máximo/meta não pode ser menor que o Mínimo.",
            )
        }
        return Validation(true)
    }

    fun validatePickupLimit(
        name: String,
        value: Double?,
        maxAllowed: Double,
    ): Validation {
        if (value == null) return Validation(false, "$name: informe o limite.")
        if (value < 0.0) return Validation(false, "$name: o limite não pode ser negativo.")
        if (value > maxAllowed) {
            return Validation(false, "$name: limite acima do permitido.")
        }
        return Validation(true)
    }

    fun validateEnabled(enabled: Set<String>): Validation =
        if (enabled.isEmpty()) {
            Validation(
                false,
                "Mantenha ao menos uma métrica ativa para classificar as ofertas.",
            )
        } else {
            Validation(true)
        }

    /**
     * O motor ponderado percorre hudMetricOrder. Portanto, uma métrica nova
     * habilitada (como pickup) precisa existir também na ordem.
     */
    fun ensureMetricOrder(
        existingCsv: String,
        enabled: Set<String>,
    ): String {
        val known = listOf(
            "per_minute",
            "per_km",
            "rating",
            "per_hour",
            "profit_hour",
            "profit_percent",
            "profit",
            "pickup",
        )
        val result = existingCsv
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toMutableList()

        enabled.forEach { key ->
            if (key in known && key !in result) result += key
        }
        return result.joinToString(",")
    }

    /** O comportamento atual da Busca usa 75% do limite como início da faixa média. */
    fun pickupGoodBoundary(maximum: Double): Double =
        (maximum.coerceAtLeast(0.0) * 0.75)
}
