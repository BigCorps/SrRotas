package com.srrotas.app

import kotlin.math.ln

/** Regras determinísticas do Assistente Ativo 0.26.0. */
object ActiveAssistantRules026 {
    const val IDLE_THRESHOLD_MS = 10L * 60L * 1000L
    const val EVALUATION_COOLDOWN_MS = 5L * 60L * 1000L
    const val SUGGESTION_COOLDOWN_MS = 20L * 60L * 1000L
    const val CARD_TIMEOUT_MS = 25L * 1000L
    const val MAX_DISTANCE_KM = 12.0
    const val MIN_DISTANCE_KM = 0.8

    data class Candidate(
        val region: String,
        val distanceKm: Double?,
        val samples: Int,
        val perKm: Double?,
        val perHour: Double?,
        val confidence: String,
        val source: String,
    )

    data class Ranked(
        val region: String,
        val distanceKm: Double,
        val samples: Int,
        val perKm: Double?,
        val perHour: Double?,
        val confidence: String,
        val sources: Set<String>,
        val score: Double,
    )

    fun idleEnough(nowMs: Long, anchorMs: Long): Boolean =
        anchorMs > 0L && nowMs - anchorMs >= IDLE_THRESHOLD_MS

    fun evaluationAllowed(nowMs: Long, lastEvaluationMs: Long): Boolean =
        lastEvaluationMs <= 0L || nowMs - lastEvaluationMs >= EVALUATION_COOLDOWN_MS

    fun suggestionAllowed(nowMs: Long, lastSuggestionMs: Long): Boolean =
        lastSuggestionMs <= 0L || nowMs - lastSuggestionMs >= SUGGESTION_COOLDOWN_MS

    fun rank(
        candidates: List<Candidate>,
        targetPerKm: Double,
        targetPerHour: Double,
    ): Ranked? {
        val kmTarget = targetPerKm.takeIf { it > 0.0 } ?: 1.0
        val hourTarget = targetPerHour.takeIf { it > 0.0 } ?: 30.0

        val grouped = candidates
            .filter { it.region.isNotBlank() }
            .filter { it.distanceKm != null && it.distanceKm.isFinite() }
            .filter { it.distanceKm!! in MIN_DISTANCE_KM..MAX_DISTANCE_KM }
            .groupBy { normalizeRegion(it.region) }

        return grouped.values.mapNotNull { group ->
            val evaluated = group.mapNotNull candidateLoop@ { candidate ->
                val distance = candidate.distanceKm ?: return@candidateLoop null
                val ratios = buildList {
                    candidate.perKm?.takeIf { it > 0.0 }?.let { add(it / kmTarget) }
                    candidate.perHour?.takeIf { it > 0.0 }?.let { add(it / hourTarget) }
                }
                if (ratios.isEmpty()) return@candidateLoop null
                val performance = ratios.average().coerceIn(0.0, 2.0)
                if (ratios.maxOrNull()!! < 1.05) return@candidateLoop null

                val confidence = confidenceWeight(candidate.confidence)
                val samples = sampleWeight(candidate.samples)
                val proximity = (1.0 - distance / MAX_DISTANCE_KM).coerceIn(0.0, 1.0)
                val source = sourceWeight(candidate.source)
                val score = (
                    performance * 0.65 +
                        confidence * 0.15 +
                        samples * 0.10 +
                        proximity * 0.10
                    ) * source
                Scored(candidate, score)
            }
            if (evaluated.isEmpty()) return@mapNotNull null
            val best = evaluated.maxByOrNull { it.score } ?: return@mapNotNull null
            val sources = group.map { sourceLabel(it.source) }.toSet()
            val consensusBonus = if ("Pessoal" in sources && "Coletiva" in sources) 0.05 else 0.0
            val finalScore = best.score + consensusBonus
            if (finalScore < 0.95) return@mapNotNull null
            Ranked(
                region = best.value.region,
                distanceKm = best.value.distanceKm!!,
                samples = group.maxOf { it.samples },
                perKm = group.mapNotNull { it.perKm }.maxOrNull(),
                perHour = group.mapNotNull { it.perHour }.maxOrNull(),
                confidence = group.maxByOrNull { confidenceWeight(it.confidence) }?.confidence ?: "low",
                sources = sources,
                score = finalScore,
            )
        }.maxByOrNull { it.score }
    }

    fun sourceLabel(raw: String): String {
        val value = raw.lowercase()
        return when {
            "collect" in value -> "Coletiva"
            "personal" in value || "pessoal" in value -> "Pessoal"
            else -> "Base Sr. Rotas"
        }
    }

    private fun normalizeRegion(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun confidenceWeight(value: String): Double = when (value.trim().lowercase()) {
        "high", "alta", "alto" -> 1.0
        "medium", "media", "média", "medio", "médio" -> 0.82
        else -> 0.58
    }

    private fun sampleWeight(samples: Int): Double {
        if (samples <= 0) return 0.0
        return (ln(1.0 + samples.coerceAtMost(50)) / ln(21.0)).coerceIn(0.0, 1.0)
    }

    private fun sourceWeight(raw: String): Double =
        if (sourceLabel(raw) == "Base Sr. Rotas") 0.86 else 1.0

    private data class Scored(val value: Candidate, val score: Double)
}
