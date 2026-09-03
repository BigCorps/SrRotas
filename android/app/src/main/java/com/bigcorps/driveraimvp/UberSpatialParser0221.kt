package com.srrotas.app

import com.google.mlkit.vision.text.Text

/**
 * Uber preservado e alimentado pelo painel espacial do card.
 *
 * 0.26.1 acrescenta uma segunda tentativa conservadora usando o extrator
 * multiplataforma, mas ainda identificando a oferta como Uber. Isso evita que um
 * primeiro frame Uber parcialmente diferente seja reinterpretado como `other`.
 */
object UberSpatialParser0221 {
    fun parse(
        result: Text,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val lines = OfferSpatialIsolation0221.lines(result)
        if (lines.isEmpty()) return emptyList()
        val fares = lines.filter { UberOfferDetector.isPrimaryFareLine(it.text) }
        val strict = OfferSpatialIsolation0221.navigationNoise(lines)
        if (fares.isEmpty()) return emptyList()

        val pane = OfferSpatialIsolation0221.paneForFares(lines, fares, frameWidth, frameHeight)
        val paneFares = pane.filter { UberOfferDetector.isPrimaryFareLine(it.text) }
        val paneText = pane.joinToString("\n") { it.text }
        val radar =
            paneText.contains("radar de viagens", true) ||
                paneText.contains("selecionar", true)

        if (
            radar &&
            paneFares.size > 1 &&
            OfferSpatialIsolation0221.hasExplicitUberCardAnchor(paneText)
        ) {
            val radarOffers = BRUberRadarParser.parse(
                lines = pane,
                primaryFares = paneFares,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "media-projection-ocr/uber",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            ).map { it.copy(platform = "uber") }
            if (radarOffers.isNotEmpty()) return radarOffers
        }

        val strictOffers = fares.mapNotNull { fare ->
            val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                lines,
                fare,
                frameWidth,
                frameHeight,
            )
            val text = cluster.joinToString("\n") { it.text }
            val anchored = if (strict) {
                OfferSpatialIsolation0221.hasExplicitUberCardAnchor(text)
            } else {
                OfferSpatialIsolation0221.hasUberOfferAnchor(text)
            }
            if (!anchored) return@mapNotNull null
            if (UberScreenGate.classify(text) != UberScreenGate.Kind.OFFER_CANDIDATE) {
                return@mapNotNull null
            }
            val type = if (
                text.contains("radar de viagens", true) ||
                text.contains("selecionar", true)
            ) {
                "radar"
            } else {
                "exclusive"
            }
            OfferParser.parse(
                rawText = text,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "media-projection-ocr/uber",
                settings = settings,
                confidence = confidence(text),
                offerType = type,
            )?.copy(platform = "uber")
                ?.let { OfferContextExtractor0221.attach(it, cluster) }
        }.distinctBy(OfferDeduplicator::semanticKey)

        if (strictOffers.isNotEmpty()) return strictOffers

        // Alguns layouts recentes da Uber mudam a pontuação/posição da geometria
        // sem perder os sinais inequívocos da plataforma. Tenta o parser flexível
        // dentro do próprio caminho Uber para que o roteador não caia em `other`.
        if (!OfferSpatialIsolation0221.hasUberOfferAnchor(result.text)) return emptyList()

        return FlexibleDriverOfferParser.parseSpatial(
            result = result,
            platform = "uber",
            sourcePackage = AppSignals.UBER_PACKAGE,
            captureMethod = "media-projection-ocr/uber",
            settings = settings,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        ).map { offer ->
            val detection = UberOfferDetector.detect(offer.rawText, offer.offerType)
            offer.copy(
                platform = "uber",
                serviceType = detection?.serviceType
                    ?.takeUnless { it == "unknown" }
                    ?: offer.serviceType,
                confidence = maxOf(offer.confidence, 0.80).coerceAtMost(0.90),
                parserVersion = "sr-rotas-v0.5.4",
            )
        }.distinctBy(OfferDeduplicator::semanticKey)
    }

    private fun confidence(text: String): Double {
        var score = 0.68
        if (text.contains("aceitar", true) || text.contains("selecionar", true)) {
            score += 0.08
        }
        if (OfferSpatialIsolation0221.hasUberOfferAnchor(text)) score += 0.08
        if (
            Regex(
                "(?:R\\$|\\$)[^\\n]*/\\s*km",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(text)
        ) {
            score += 0.05
        }
        return score.coerceAtMost(0.97)
    }
}
