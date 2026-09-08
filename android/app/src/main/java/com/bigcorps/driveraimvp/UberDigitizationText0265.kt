package com.srrotas.app

/**
 * Limpeza conservadora do OCR da digitalização manual.
 * Não inventa valores: apenas normaliza separadores, remove apenas linhas duplicadas consecutivas
 * e ruído visual comum antes de entregar o texto ao parser validado.
 */
object UberDigitizationText0265 {
    private val noise = listOf(
        Regex("^(voltar|ajuda|menu|início|inicio|conta|atividade)$", RegexOption.IGNORE_CASE),
        Regex("^\\d{1,2}:\\d{2}\\s*[◇◆•·]?\\s*$"),
    )

    fun normalize(raw: String): String {
        var previousKey: String? = null
        return raw
            .replace('\u00A0', ' ')
            .replace(Regex("R\\s*\\$", RegexOption.IGNORE_CASE)) { "R$" }
            .replace(Regex("R\\$\\s+", RegexOption.IGNORE_CASE)) { "R$" }
            .replace(Regex("(\\d)\\s*,\\s*(\\d{2})(?!\\d)"), "\$1,\$2")
            .replace(Regex("(\\d)\\s*km", RegexOption.IGNORE_CASE), "\$1 km")
            .replace(Regex("(\\d)\\s*min", RegexOption.IGNORE_CASE), "\$1 min")
            .lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .filterNot { line -> noise.any { it.matches(line) } }
            .filter { line ->
                // Só elimina repetição consecutiva. No histórico, duas corridas
                // diferentes podem legitimamente ter o mesmo valor/categoria.
                val key = line.lowercase()
                val keep = key != previousKey
                previousKey = key
                keep
            }
            .joinToString("\n")
    }
}
