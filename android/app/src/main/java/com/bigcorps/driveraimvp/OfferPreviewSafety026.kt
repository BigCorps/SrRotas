package com.srrotas.app

/**
 * Decide quando um card precisa passar pela janela curta de estabilização
 * antes de aparecer no HUD.
 *
 * Não reprova a oferta: apenas evita que uma leitura OCR muito otimista seja
 * exibida instantaneamente enquanto ainda há frames melhores chegando.
 */
object OfferPreviewSafety026 {
    fun requiresStabilization(offer: RideOffer): Boolean {
        val minutes = offer.totalMinutes ?: return false
        val km = offer.totalKm ?: return false
        if (minutes <= 0 || km <= 0.0) return false

        val longDistanceTooShort = km >= 15.0 && minutes <= 35
        val rateLooksOcrSensitive =
            km >= 8.0 &&
                ((offer.perHour ?: 0.0) >= 160.0 || (offer.perMinute ?: 0.0) >= 2.60)

        return longDistanceTooShort || rateLooksOcrSensitive
    }
}
