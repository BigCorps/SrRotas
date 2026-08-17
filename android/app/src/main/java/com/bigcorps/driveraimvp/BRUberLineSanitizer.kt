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
        line = Regex("(^|\\s)\\$\\s*(?=[0-9OSo])").replace(line) { m -> "${m.groupValues[1]}R$ " }
        // Em cartões reais apareceu 'Imin'. Corrige apenas o token de tempo.
        line = line.replace(Regex("\\b[IilL]\\s*(?=min(?:uto|utos)?\\b)", RegexOption.IGNORE_CASE), "1 ")
        return line
    }
}
