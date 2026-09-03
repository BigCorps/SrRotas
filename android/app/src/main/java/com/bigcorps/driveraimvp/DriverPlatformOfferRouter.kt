package com.srrotas.app

import com.google.mlkit.vision.text.Text

/** Roteador multiplataforma com isolamento espacial por card/painel. */
object DriverPlatformOfferRouter {
    data class RoutedResult(
        val platform: String?,
        val offers: List<RideOffer>,
        val candidate: Boolean,
        val ownApp: Boolean = false,
        val reason: String = "contexto desconhecido",
    )

    fun parse(
        result: Text,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): RoutedResult {
        val raw = result.text
        val lower = DriverOcrNormalizer.sanitize(raw).lowercase()
        if (lower.isBlank()) return RoutedResult(null, emptyList(), false, reason = "ocr vazio")

        // Não bloqueia o OCR inteiro só porque o Sr. Rotas está visível em
        // outra janela do tablet. Primeiro procuramos cards reais de motorista.
        if (looksLike99(lower)) {
            val offers = FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (offers.isNotEmpty()) {
                return RoutedResult("99", offers, true, reason = "candidato 99")
            }
        }

        val uberOffers = UberSpatialParser0221.parse(
            result = result,
            settings = settings,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        if (uberOffers.isNotEmpty()) {
            return RoutedResult("uber", uberOffers, true, reason = "candidato Uber isolado")
        }

        // 0.26.1: se o frame traz âncora inequívoca da Uber, mas ainda não tem
        // geometria suficiente para fechar a oferta, aguardamos outro frame.
        // Antes, esse mesmo quadro podia cair no fallback genérico e ser salvo
        // como `other`, criando duplicata e combinações cruzadas no histórico.
        val uberAnchored = OfferSpatialIsolation0221.hasUberOfferAnchor(raw)
        if (uberAnchored) {
            return RoutedResult(
                platform = "uber",
                offers = emptyList(),
                candidate = true,
                reason = "candidato Uber aguardando frame completo",
            )
        }

        val inferred = inferGenericPlatform(lower)
        if (FlexibleDriverOfferParser.looksLikeCandidate(raw)) {
            val offers = FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = inferred,
                sourcePackage = AppSignals.inferredPackage(inferred),
                captureMethod = "media-projection-ocr/$inferred",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (offers.isNotEmpty()) {
                return RoutedResult(
                    inferred,
                    offers,
                    true,
                    reason = "candidato genérico/$inferred",
                )
            }
        }

        val gate = UberScreenGate.classify(raw)
        if (gate == UberScreenGate.Kind.OWN_APP) {
            return RoutedResult(
                null,
                emptyList(),
                false,
                ownApp = true,
                reason = "interface Sr. Rotas",
            )
        }

        val candidate =
            looksLike99(lower) || FlexibleDriverOfferParser.looksLikeCandidate(raw)
        val reason = when (gate) {
            UberScreenGate.Kind.IDLE_OR_HOME -> "home/ocioso"
            UberScreenGate.Kind.FOREIGN_UI -> "outra interface"
            else -> if (
                OfferSpatialIsolation0221.navigationNoise(
                    OfferSpatialIsolation0221.lines(result),
                )
            ) {
                "tela dividida sem card isolado"
            } else {
                "contexto desconhecido"
            }
        }
        return RoutedResult(null, emptyList(), candidate, reason = reason)
    }

    private fun looksLike99(lower: String): Boolean {
        val strong = listOf(
            "perfil essencial",
            "plus nova",
            "99pop",
            "99 pop",
            "99plus",
            "99 plus",
            "99moto",
            "99 moto",
            "99táxi",
            "99taxi",
            "99electric",
            "99 entrega",
        ).any(lower::contains)
        if (strong) return true

        val action = lower.contains("escolher")
        val rideContext =
            lower.contains("corridas") ||
                lower.contains("solicitações") ||
                lower.contains("solicitacoes")
        val metrics =
            lower.contains("/km") && FlexibleDriverOfferParser.geometryCount(lower) >= 2
        return action && (rideContext || metrics)
    }

    private fun inferGenericPlatform(lower: String): String = when {
        lower.contains("indrive") || lower.contains("in drive") -> "indrive"
        lower.contains("maxim") -> "maxim"
        else -> "other"
    }
}
