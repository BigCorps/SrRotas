package com.srrotas.app

/** Correções OCR pequenas e contextuais antes da extração numérica. */
object BRUberLineSanitizer {
    private val compactMetricPatterns = listOf(
        Regex("(?:R\\$\\s*)?[0-9]+[.,][0-9]+\\s*/\\s*km\\b", RegexOption.IGNORE_CASE),
        Regex("[0-9]+[.,][0-9]+\\s*/\\s*(?:hr|h)\\b", RegexOption.IGNORE_CASE),
        Regex("[0-9]+[.,][0-9]+\\s*/\\s*min\\b", RegexOption.IGNORE_CASE),
    )
    private val externalDurationDistance = Regex(
        "\\b[0-9]{1,2}\\s*h\\s*[0-9]{1,2}\\s*m\\s*[-–—]\\s*[0-9]{1,4}(?:[.,][0-9]+)?\\s*km\\b",
        RegexOption.IGNORE_CASE,
    )

    // Só corrige I/l/L quando o token é claramente numérico e vem antes de min/km.
    private val numericGeometryToken = Regex(
        "\\b([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)(?=\\s*(?:min|minuto|minutos|km)\\b)",
        RegexOption.IGNORE_CASE,
    )

    fun sanitize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .lines()
        .map(::sanitizeLine)
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()

    internal fun looksLikeExternalMetricOverlay(raw: String): Boolean {
        val line = raw.replace('\u00A0', ' ').trim()
        if (line.isBlank()) return false
        val metricHits = compactMetricPatterns.count { it.containsMatchIn(line) }
        return metricHits >= 2 || externalDurationDistance.containsMatchIn(line)
    }

    private fun sanitizeLine(raw: String): String {
        var line = raw.replace(Regex("[ \\t]+"), " ").trim()
        if (looksLikeExternalMetricOverlay(line)) return ""

        // O ML Kit às vezes perde o R de R$; só corrige quando '$' precede número.
        line = Regex("(^|\\s)\\$\\s*(?=[0-9OSoIlL])").replace(line) { m -> "${m.groupValues[1]}R$ " }

        // Casos reais: I min -> 1 min, 1l minutos -> 11 minutos, ll minutos -> 11 minutos.
        line = numericGeometryToken.replace(line) { m -> normalizeNumericToken(m.groupValues[1]) }
        return line
    }

    internal fun normalizeNumericToken(value: String): String = value
        .replace('O', '0').replace('o', '0')
        .replace('S', '5').replace('s', '5')
        .replace('I', '1').replace('i', '1')
        .replace('L', '1').replace('l', '1')
}
