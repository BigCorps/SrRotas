package com.srrotas.app

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/**
 * Parser espacial do Sr. Rotas.
 * Em vez de juntar toda a tela, usa as posições das linhas reconhecidas para
 * separar uma oferta exclusiva de múltiplos cards do Radar.
 */
object SpatialOfferParser {
    private data class OcrLine(val text: String, val box: Rect)

    fun parse(
        result: Text,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val lines = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                if (text.isBlank()) null else OcrLine(text, box)
            }

        if (lines.isEmpty()) return emptyList()
        val fares = lines.filter { it.text.contains("R$", ignoreCase = true) }
        if (fares.isEmpty()) {
            return OfferParser.parse(
                rawText = result.text,
                sourcePackage = sourcePackage,
                captureMethod = captureMethod,
                settings = settings,
                confidence = 0.58,
                offerType = "exclusive",
            )?.let(::listOf) ?: emptyList()
        }

        if (fares.size == 1) {
            val text = lines.sortedBy { it.box.top }.joinToString("\n") { it.text }
            val confidence = estimateConfidence(text, spatiallyClustered = false)
            return OfferParser.parse(
                text, sourcePackage, captureMethod, settings, confidence, "exclusive"
            )?.let(::listOf) ?: emptyList()
        }

        // Radar: cada linha é atribuída ao preço horizontalmente mais próximo,
        // desde que esteja numa faixa vertical plausível do card.
        val maxVertical = (frameHeight * 0.33).toInt().coerceAtLeast(280)
        val maxHorizontal = (frameWidth * 0.42).toInt().coerceAtLeast(260)

        return fares.mapNotNull { fareLine ->
            val fareCx = fareLine.box.centerX()
            val fareCy = fareLine.box.centerY()
            val cluster = lines.filter { line ->
                val dx = abs(line.box.centerX() - fareCx)
                val dy = abs(line.box.centerY() - fareCy)
                dx <= maxHorizontal && dy <= maxVertical &&
                    // Se outra linha de preço estiver presente, só mantém o preço-âncora.
                    (!line.text.contains("R$", ignoreCase = true) || line === fareLine)
            }.sortedBy { it.box.top }

            val text = cluster.joinToString("\n") { it.text }
            val confidence = estimateConfidence(text, spatiallyClustered = true)
            OfferParser.parse(
                text, sourcePackage, captureMethod, settings, confidence, "radar"
            )
        }.distinctBy { it.dedupeKey }
    }

    private fun estimateConfidence(text: String, spatiallyClustered: Boolean): Double {
        var score = if (spatiallyClustered) 0.64 else 0.60
        val kmCount = Regex("[0-9OSo]+(?:[.,][0-9OSo]+)?\\s*km\\b", RegexOption.IGNORE_CASE).findAll(text).count()
        val minCount = Regex("[0-9OSo]+\\s*(?:min|minuto|minutos)\\b", RegexOption.IGNORE_CASE).findAll(text).count()
        if (text.contains("R$")) score += 0.12
        if (kmCount >= 1) score += 0.08
        if (kmCount >= 2) score += 0.05
        if (minCount >= 1) score += 0.06
        if (minCount >= 2) score += 0.03
        return score.coerceAtMost(0.98)
    }
}
