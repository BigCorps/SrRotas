package com.srrotas.app

/** Regras puras do slot fixo da continuidade no destino. */
object DestinationContinuityHud026Rules {
    enum class VisualState { GOOD, MEDIUM, LOW, NO_DATA }

    fun visualState(level: String?, hasProbability: Boolean): VisualState = when {
        !hasProbability && level.isNullOrBlank() -> VisualState.NO_DATA
        level == "high" -> VisualState.GOOD
        level == "medium" -> VisualState.MEDIUM
        level == "low" -> VisualState.LOW
        else -> VisualState.NO_DATA
    }

    fun displayValue(probabilityPct: Double?, levelLabel: String?): String =
        if (probabilityPct == null) "Sem dados"
        else "${probabilityPct.toInt()}% · ${levelLabel?.takeIf { it.isNotBlank() } ?: "—"}"
}
