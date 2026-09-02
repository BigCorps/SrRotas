package com.srrotas.app

import kotlin.math.roundToInt

/** Regras puras de apresentação do novo sinal no HUD. */
object DestinationContinuityHudRules025 {
    fun grade(value: DestinationContinuityInsight0211): Int? = when (value.level) {
        "high" -> 2
        "medium" -> 1
        "low" -> 0
        else -> null
    }

    fun value(value: DestinationContinuityInsight0211): String =
        value.probabilityPct?.let {
            "${it.roundToInt()}% · ${DestinationContinuityPresentation0211.levelLabel(value.level)}"
        } ?: DestinationContinuityPresentation0211.levelLabel(value.level)
}
