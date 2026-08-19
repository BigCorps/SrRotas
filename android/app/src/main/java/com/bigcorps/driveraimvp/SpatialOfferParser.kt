package com.srrotas.app

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

internal data class SpatialOcrLine(val text: String, val box: Rect)

object SpatialOfferParser {
    fun parse(
        result: Text,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = line.text.trim()
            if (text.isBlank()) null else SpatialOcrLine(text, box)
        }
        if (lines.isEmpty()) return emptyList()

        val globalText = lines.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })
            .joinToString("\n") { it.text }
        if (UberScreenGate.classify(globalText) != UberScreenGate.Kind.OFFER_CANDIDATE) return emptyList()

        val primaryFares = lines.filter { UberOfferDetector.isPrimaryFareLine(it.text) }
        if (primaryFares.isEmpty()) return emptyList()

        val globalIsRadar = globalText.contains("radar de viagens", true) || globalText.contains("selecionar", true)
        if (globalIsRadar && primaryFares.size > 1) {
            return BRUberRadarParser.parse(
                lines = lines,
                primaryFares = primaryFares,
                sourcePackage = sourcePackage,
                captureMethod = captureMethod,
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
        }

        val type = if (globalIsRadar) "radar" else "exclusive"
        return OfferParser.parse(
            rawText = globalText,
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            settings = settings,
            confidence = estimateConfidence(globalText, clustered = false),
            offerType = type,
        )?.let { parsed ->
            // Context Engine é aditivo: não participa da validação financeira.
            listOf(OfferContextEngine.attach(parsed, lines))
        } ?: emptyList()
    }

    private fun estimateConfidence(text: String, clustered: Boolean): Double {
        var score = if (clustered) 0.66 else 0.62
        if (text.contains("aceitar", true) || text.contains("selecionar", true)) score += 0.08
        if (text.contains("uberx", true) || text.contains("priority", true) || text.contains("comfort", true) || text.contains("black", true)) score += 0.06
        if (Regex("[0-9]+\\s*(?:min|minutos?)\\s*\\([^)]*km", RegexOption.IGNORE_CASE).findAll(text).count() >= 2) score += 0.10
        if (Regex("(?:R\\$|\\$)[^\\n]*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(text)) score += 0.06
        return score.coerceAtMost(0.98)
    }
}
