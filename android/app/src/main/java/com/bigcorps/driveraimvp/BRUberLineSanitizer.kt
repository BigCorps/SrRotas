package com.srrotas.app

/** Correções OCR pequenas e contextuais antes da extração numérica. */
object BRUberLineSanitizer {
    fun sanitize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .lines()
        .joinToString("\n") { sanitizeLine(it) }
        .trim()

    private fun sanitizeLine(raw: String): String {
        var line = raw.replace(Regex("[ \\t]+"), " ").trim()
        // O ML Kit às vezes perde o R de R$; só corrige quando '$' precede número.
        line = Regex("(^|\\s)\\$\\s*(?=[0-9OSo])").replace(line) { m -> "${m.groupValues[1]}R$ " }
        // Em cartões reais apareceu 'Imin'. Corrige apenas o token de tempo.
        line = line.replace(Regex("\\b[IilL]\\s*(?=min(?:uto|utos)?\\b)", RegexOption.IGNORE_CASE), "1 ")
        return line
    }
}
