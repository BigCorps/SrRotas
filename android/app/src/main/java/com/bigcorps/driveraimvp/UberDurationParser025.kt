package com.srrotas.app

/**
 * Normaliza durações exibidas pelo Uber sem perder a parcela de horas.
 *
 * Casos aceitos, entre outros:
 * - 34 min / 34 minutos
 * - 1h29 / 1h29min / 1 h 29 min
 * - 1 hora e 29 minutos
 * - 2 horas
 *
 * O parser continua conservador: o total final deve ficar entre 1 e 360 minutos.
 */
object UberDurationParser025 {
    internal val durationPattern =
        "(?:[0-9OSoIlL]{1,3}\\s*(?:horas?|hrs?|h)(?:\\s*(?:e\\s*)?[0-9OSoIlL]{1,3}\\s*(?:(?:minutos?)|min)?)?|[0-9OSoIlL]{1,3}\\s*(?:(?:minutos?)|min))"

    private val durationRegex = Regex(durationPattern, RegexOption.IGNORE_CASE)
    private val hourDurationRegex = Regex(
        "^\\s*([0-9OSoIlL]{1,3})\\s*(?:horas?|hrs?|h)(?:\\s*(?:e\\s*)?([0-9OSoIlL]{1,3})\\s*(?:(?:minutos?)|min)?)?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val minuteDurationRegex = Regex(
        "^\\s*([0-9OSoIlL]{1,3})\\s*(?:(?:minutos?)|min)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    data class DurationMatch(val minutes: Int, val range: IntRange)

    fun parseCandidate(raw: String): Int? {
        hourDurationRegex.matchEntire(raw)?.let { match ->
            val hours = numericInt(match.groupValues[1]) ?: return null
            val minutes = match.groupValues.getOrNull(2)
                ?.takeIf(String::isNotBlank)
                ?.let(::numericInt)
                ?: 0
            if (hours !in 0..6 || minutes !in 0..59) return null
            return (hours * 60 + minutes).takeIf { it in 1..360 }
        }

        minuteDurationRegex.matchEntire(raw)?.let { match ->
            return numericInt(match.groupValues[1])?.takeIf { it in 1..360 }
        }
        return null
    }

    fun findAll(raw: String): List<DurationMatch> = durationRegex.findAll(raw)
        .mapNotNull { match ->
            parseCandidate(match.value)?.let { DurationMatch(it, match.range) }
        }
        .toList()

    private fun numericInt(value: String): Int? = OfferParser.parseNumberCandidate(value)?.toInt()
}
