package com.srrotas.app

import kotlin.math.round

/** Regras puras dos dados reais de jornada. Não altera custo/km nem veredito. */
object JourneyMetricsRules026 {
    const val KIND_FUEL = "fuel"
    const val KIND_ELECTRIC = "electric"
    const val UNIT_LITER = "liter"
    const val UNIT_KWH = "kwh"

    fun normalizedOdometer(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.0..9_999_999.9 }
            ?.let { round(it * 10.0) / 10.0 }

    fun distanceKm(startKm: Double?, endKm: Double?): Double? {
        val start = normalizedOdometer(startKm) ?: return null
        val end = normalizedOdometer(endKm) ?: return null
        if (end < start) return null
        return round((end - start) * 10.0) / 10.0
    }

    fun validEnergyEntry(
        kind: String,
        amountPaid: Double?,
        quantity: Double?,
        unit: String,
    ): Boolean {
        val normalizedKind = kind.trim().lowercase()
        val normalizedUnit = unit.trim().lowercase()
        if (normalizedKind !in setOf(KIND_FUEL, KIND_ELECTRIC)) return false
        if (normalizedKind == KIND_FUEL && normalizedUnit != UNIT_LITER) return false
        if (normalizedKind == KIND_ELECTRIC && normalizedUnit != UNIT_KWH) return false

        val amountOk = amountPaid == null || (amountPaid.isFinite() && amountPaid in 0.0..100_000.0)
        val quantityOk = quantity == null || (quantity.isFinite() && quantity in 0.0..10_000.0)
        if (!amountOk || !quantityOk) return false
        return (amountPaid ?: 0.0) > 0.0 || (quantity ?: 0.0) > 0.0
    }
}
