package com.srrotas.app

/**
 * Contexto espacial por card.
 *
 * vc109: busca e destino são associados pelo PAPEL de cada linha de geometria
 * (`OfferContextEngine.routeRoles`: total × busca × viagem por evidência), não pela posição
 * da âncora. O caminho espacial oficial, o histórico e a releitura passam a usar a mesma
 * regra; sem papel comprovado o endereço fica indisponível em vez de trocado.
 */
internal object OfferContextExtractor0221 {
    const val VERSION = "sr-context-v1.09"

    fun attach(offer: RideOffer, spatial: List<SpatialOcrLine>): RideOffer {
        val context = extract(spatial, offer.observedAt, offer.totalMinutes)
        return offer.copy(context = context)
    }

    fun extract(
        spatial: List<SpatialOcrLine>,
        observedAt: String,
        totalMinutes: Int?,
    ): OfferContext? {
        val lines = spatial.map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) }
        val context = OfferContextEngine.extract(lines, observedAt, totalMinutes) ?: return null
        return if (context.hasTextContext()) context.copy(geocodeSource = "spatial_ocr_0262", contextVersion = VERSION) else context
    }
}
