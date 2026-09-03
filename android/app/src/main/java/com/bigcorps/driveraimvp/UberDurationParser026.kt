package com.srrotas.app

/**
 * Duração Uber 0.26: tolerante aos ruídos reais do ML Kit, sem inferir horas.
 *
 * A regra é deliberadamente conservadora: quando o OCR contém evidência de
 * hora, mas não conseguimos reconstruir uma duração >= 60 min com segurança,
 * o chamador deve rejeitar aquele frame e esperar a próxima leitura.
 */
object UberDurationParser026 {
    private const val NUMBER = "[0-9OSoIlL]{1,3}"

    // Inclui confusões observáveis de OCR: h0ra/h0ras e hor4/hor4s.
    private const val HOUR_UNIT = "(?:horas?|hrs?|h[o0]r[a4]s?|h)"
    // min, mins, minuto(s), além de m1n/mln quando i é confundido.
    private const val MINUTE_UNIT = "(?:m[i1l]n(?:utos?|s)?)"
    private const val CONNECTOR = "(?:\\s*(?:e\\s*)?|\\s*[·•:;,.\\-–—]\\s*)"

    internal val durationPattern =
        "(?:$NUMBER\\s*$HOUR_UNIT(?:$CONNECTOR$NUMBER\\s*$MINUTE_UNIT?)?|$NUMBER\\s*$MINUTE_UNIT)"

    private val durationRegex = Regex(durationPattern, RegexOption.IGNORE_CASE)
    private val hourDurationRegex = Regex(
        "^\\s*($NUMBER)\\s*$HOUR_UNIT(?:$CONNECTOR($NUMBER)\\s*$MINUTE_UNIT?)?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val minuteDurationRegex = Regex(
        "^\\s*($NUMBER)\\s*$MINUTE_UNIT\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val hourEvidenceRegex = Regex(
        "\\b$NUMBER\\s*$HOUR_UNIT\\b",
        RegexOption.IGNORE_CASE,
    )
    private val danglingHourConnectorRegex = Regex(
        "\\b$NUMBER\\s*$HOUR_UNIT\\s*e\\b(?!\\s*$NUMBER)",
        RegexOption.IGNORE_CASE,
    )
    private val hourThenMinuteNearbyRegex = Regex(
        "\\b$NUMBER\\s*$HOUR_UNIT[\\s\\S]{0,24}?$NUMBER\\s*$MINUTE_UNIT",
        RegexOption.IGNORE_CASE,
    )

    data class DurationMatch(val minutes: Int, val range: IntRange)

    data class Audit(
        val hasHourEvidence: Boolean,
        val parsedDurations: List<Int>,
        val hasParsedHourDuration: Boolean,
        val unresolvedHourEvidence: Boolean,
    )

    fun normalizeOcrText(raw: String): String = raw
        .replace(
            Regex("\\bh[0o]r([a4])s?\\b", RegexOption.IGNORE_CASE),
        ) { match ->
            if (match.value.endsWith("s", ignoreCase = true)) "horas" else "hora"
        }
        .replace(
            Regex("\\bm[1l]n(?=\\b|utos?\\b|s\\b)", RegexOption.IGNORE_CASE),
            "min",
        )

    fun parseCandidate(raw: String): Int? {
        val value = normalizeOcrText(raw)

        hourDurationRegex.matchEntire(value)?.let { match ->
            val hours = numericInt(match.groupValues[1]) ?: return null
            val minutes = match.groupValues.getOrNull(2)
                ?.takeIf(String::isNotBlank)
                ?.let(::numericInt)
                ?: 0
            if (hours !in 0..6 || minutes !in 0..59) return null
            return (hours * 60 + minutes).takeIf { it in 1..360 }
        }

        minuteDurationRegex.matchEntire(value)?.let { match ->
            return numericInt(match.groupValues[1])?.takeIf { it in 1..360 }
        }
        return null
    }

    fun findAll(raw: String): List<DurationMatch> {
        val text = normalizeOcrText(raw)
        return durationRegex.findAll(text)
            .mapNotNull { match ->
                parseCandidate(match.value)?.let { DurationMatch(it, match.range) }
            }
            .toList()
    }

    fun audit(raw: String): Audit {
        val text = normalizeOcrText(raw)
        val parsed = findAll(text).map { it.minutes }
        val hourEvidence = hourEvidenceRegex.containsMatchIn(text)
        val parsedHour = parsed.any { it >= 60 }
        val danglingConnector = danglingHourConnectorRegex.containsMatchIn(text)
        val nearbyCompositeUnresolved = hourThenMinuteNearbyRegex.findAll(text).any { match ->
            val value = parseCandidate(match.value)
            value == null || value < 60
        }
        return Audit(
            hasHourEvidence = hourEvidence,
            parsedDurations = parsed,
            hasParsedHourDuration = parsedHour,
            unresolvedHourEvidence =
                hourEvidence && (!parsedHour || danglingConnector || nearbyCompositeUnresolved),
        )
    }

    private fun numericInt(value: String): Int? =
        OfferParser.parseNumberCandidate(value)?.toInt()
}
