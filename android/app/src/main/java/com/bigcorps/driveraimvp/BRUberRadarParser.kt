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
 *
 * Internal porque recebe SpatialOcrLine, que é um tipo interno do engine OCR.
 */
internal object BRUberRadarParser {
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

            if (!shouldAttemptParse(text)) return@mapIndexedNotNull null
            val evidence = UberOfferDetector.geometryEvidence(text)

            OfferParser.parse(
                rawText = text,
                sourcePackage = sourcePackage,
                captureMethod = captureMethod,
                settings = settings,
                confidence = confidenceFor(text, evidence),
                offerType = "radar",
            )?.let { parsed ->
                // Usa o MESMO cluster já isolado pelo Radar. Não altera nenhuma
                // regra numérica; apenas associa retirada/destino ao card certo.
                OfferContextEngine.attach(parsed, cluster)
            }
        }.distinctBy { OfferDeduplicator.semanticKey(it) }
    }

    /**
     * Gate conservador para cards do Radar.
     *
     * Antes da 0.25.0 o card só avançava se o OCR preservasse simultaneamente
     * "N min (N km)" e "R$/km". Agora aceitamos também geometria solta quando
     * existe evidência suficiente, mas nunca um preço isolado ou uma duração solta.
     */
    internal fun shouldAttemptParse(text: String): Boolean {
        val evidence = UberOfferDetector.geometryEvidence(text)
        val hasAnyGeometry = evidence.distanceCount >= 1 && evidence.durationCount >= 1
        val hasCompleteLooseGeometry = evidence.distanceCount >= 2 && evidence.durationCount >= 2
        val hasSelectionAnchor = text.contains("selecionar", true)
        val hasServiceAnchor = hasServiceAnchor(text)

        return when {
            evidence.hasAdvertisedPerKm && hasAnyGeometry -> true
            evidence.pairedDurationDistanceCount >= 2 -> true
            hasCompleteLooseGeometry && (hasSelectionAnchor || hasServiceAnchor) -> true
            else -> false
        }
    }

    private fun confidenceFor(text: String, evidence: UberOfferDetector.GeometryEvidence): Double {
        var confidence = 0.68
        if (evidence.hasAdvertisedPerKm) confidence += 0.08
        if (evidence.pairedDurationDistanceCount >= 1) confidence += 0.07
        if (evidence.pairedDurationDistanceCount >= 2) confidence += 0.07
        else if (evidence.distanceCount >= 2 && evidence.durationCount >= 2) confidence += 0.05
        if (text.contains("selecionar", true)) confidence += 0.04
        if (hasServiceAnchor(text)) confidence += 0.04
        return confidence.coerceAtMost(0.94)
    }

    private fun hasServiceAnchor(text: String): Boolean = listOf(
        "uberx", "comfort", "black", "priority", "electric", "uber moto", "ubermoto",
    ).any { text.contains(it, true) }
}
