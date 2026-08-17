package com.srrotas.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Associação espacial específica do Radar BR.
 *
 * Em vez de usar um raio grande em torno de cada R$, divide verticalmente a tela
 * pelos pontos médios entre preços principais. Isso reduz a chance de pegar tempo
 * ou distância do card vizinho.
 */
object BRUberRadarParser {
    fun parse(
        lines: List<SpatialOcrLine>,
        primaryFares: List<SpatialOcrLine>,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        if (primaryFares.isEmpty()) return emptyList()
        val fares = primaryFares.sortedBy { it.box.centerY() }
        val maxVerticalRadius = (frameHeight * 0.24).toInt().coerceAtLeast(260)
        val maxHorizontal = (frameWidth * 0.55).toInt().coerceAtLeast(300)

        return fares.mapIndexedNotNull { index, fareLine ->
            val cy = fareLine.box.centerY()
            val cx = fareLine.box.centerX()
            val previousCy = fares.getOrNull(index - 1)?.box?.centerY()
            val nextCy = fares.getOrNull(index + 1)?.box?.centerY()

            val naturalTop = cy - maxVerticalRadius
            val naturalBottom = cy + maxVerticalRadius
            val splitTop = previousCy?.let { (it + cy) / 2 } ?: naturalTop
            val splitBottom = nextCy?.let { (cy + it) / 2 } ?: naturalBottom
            val top = max(naturalTop, splitTop)
            val bottom = min(naturalBottom, splitBottom)

            val cluster = lines.filter { line ->
                val lineCy = line.box.centerY()
                val horizontalDistance = abs(line.box.centerX() - cx)
                val verticallyInside = lineCy in top..bottom
                val horizontallyRelevant = horizontalDistance <= maxHorizontal || line.box.width().toDouble() >= frameWidth * 0.70
                verticallyInside && horizontallyRelevant &&
                    (!UberOfferDetector.isPrimaryFareLine(line.text) || line === fareLine)
            }.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })

            if (cluster.isEmpty()) return@mapIndexedNotNull null
            val text = buildString {
                append("Radar de Viagens\n")
                cluster.forEach { append(it.text).append('\n') }
            }.trim()

            val geometryPairs = Regex(
                "[0-9OSo]{1,3}\\s*(?:min|minuto|minutos)\\s*\\(\\s*[0-9OSo.,]+\\s*km\\s*\\)",
                RegexOption.IGNORE_CASE,
            ).findAll(text).count()
            val advertised = Regex("(?:R\\$|\\$)\\s*[0-9OSo.,]+\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(text)
            if (geometryPairs == 0 || !advertised) return@mapIndexedNotNull null

            var confidence = 0.72
            if (geometryPairs >= 2) confidence += 0.12
            if (text.contains("selecionar", true)) confidence += 0.05
            if (text.contains("uberx", true) || text.contains("comfort", true) || text.contains("black", true) || text.contains("priority", true)) confidence += 0.05

            OfferParser.parse(
                rawText = text,
                sourcePackage = sourcePackage,
                captureMethod = captureMethod,
                settings = settings,
                confidence = confidence.coerceAtMost(0.94),
                offerType = "radar",
            )
        }.distinctBy { OfferDeduplicator.semanticKey(it) }
    }
}
