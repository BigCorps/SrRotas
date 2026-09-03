package com.srrotas.app

import kotlin.math.roundToInt

data class DestinationContinuityInsight0211(
    val kind: String,
    val level: String,
    val probabilityPct: Double?,
    val samples: Int,
    val confidence: String,
    val source: String,
    val regionLabel: String?,
    val wording: String,
)

object DestinationContinuityPresentation0211 {
    fun levelLabel(level: String): String =
        when (level) {
            "high" -> "Alta"
            "medium" -> "Média"
            "low" -> "Baixa"
            else -> "Dados insuf."
        }

    fun hudLabel(value: DestinationContinuityInsight0211): String =
        value.probabilityPct?.let {
            "Prob. novas corridas: ${it.roundToInt()}% · ${levelLabel(value.level)}"
        } ?: "Prob. novas corridas: ${levelLabel(value.level)}"

    fun cardTitle(value: DestinationContinuityInsight0211): String =
        value.probabilityPct?.let {
            "Probabilidade de novas corridas: ${it.roundToInt()}% · ${levelLabel(value.level)}"
        } ?: "Probabilidade de novas corridas: ${levelLabel(value.level)}"

    fun detail(value: DestinationContinuityInsight0211): String =
        when (value.kind) {
            "probability" ->
                "P10 · ${value.samples} intervalos elegíveis · ${sourceLabel(value.source)}"
            "historical_indicator" ->
                "Base Sr. Rotas · ${value.samples} amostras históricas · ${confidenceLabel(value.confidence)}"
            else -> value.wording
        }

    fun sourceLabel(source: String): String =
        when (source) {
            "personal_exposure" -> "sua exposição observada"
            "collective_exposure" -> "base coletiva"
            "sr_rotas_seed" -> "Base Sr. Rotas"
            else -> "histórico disponível"
        }

    fun confidenceLabel(value: String): String =
        when (value) {
            "high" -> "confiança alta"
            "medium" -> "confiança média"
            "low" -> "confiança inicial"
            else -> "dados insuficientes"
        }
}
