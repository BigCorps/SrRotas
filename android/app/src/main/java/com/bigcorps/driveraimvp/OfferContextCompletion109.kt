package com.srrotas.app

/**
 * vc109 — completa o contexto de busca/destino de ofertas Uber que chegam SEM endereços.
 *
 * Causa: o caminho de fallback do parser Uber congelado (`UberSpatialParser0221` →
 * `FlexibleDriverOfferParser.parseText`) publica a oferta sem chamar a extração de contexto.
 * Aqui a mesma associação por papel (`OfferContextEngine.routeRoles`) roda sobre o texto do
 * próprio card (`rawText`, na ordem do card). Tempos, tarifas e fórmulas não são tocados;
 * contexto já existente nunca é substituído.
 */
object OfferContextCompletion109 {
    const val SOURCE = "card_text_roles_v109"

    fun complete(offer: RideOffer): RideOffer {
        if (offer.platform != "uber" || offer.context?.hasTextContext() == true) return offer
        val lines = offer.rawText.lines().map(String::trim).filter(String::isNotBlank)
            .mapIndexed { index, text -> ContextOcrLine(text, index * 10) }
        val found = runCatching { OfferContextEngine.extract(lines, offer.observedAt, offer.totalMinutes) }.getOrNull()
        if (found == null || !found.hasTextContext()) return offer
        val base = offer.context
        return offer.copy(context = (base ?: found).copy(
            pickupLabel = found.pickupLabel, destinationLabel = found.destinationLabel,
            estimatedArrivalAt = base?.estimatedArrivalAt ?: found.estimatedArrivalAt,
            contextConfidence = found.contextConfidence, geocodeStatus = "pending",
            geocodeSource = SOURCE, contextVersion = OfferContextExtractor0221.VERSION,
        ))
    }
}
