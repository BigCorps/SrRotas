package com.srrotas.app

/**
 * Validações conservadoras. Quando os números parecem pertencer a cards
 * diferentes, a leitura é descartada em vez de virar uma oferta falsa.
 */
object OfferValidator {
    fun isPlausible(
        offerType: String,
        fare: Double,
        totalKm: Double,
        totalMinutes: Int,
        perKm: Double,
        perHour: Double,
        perMinute: Double,
    ): Boolean {
        if (fare !in 2.0..1000.0) return false
        if (totalKm !in 0.2..500.0) return false
        if (totalMinutes !in 2..360) return false
        if (perKm <= 0.0 || perKm > 50.0) return false
        if (perHour <= 0.0 || perHour > 360.0) return false
        if (perMinute <= 0.0 || perMinute > 6.0) return false

        val impliedAverageKmh = totalKm * 60.0 / totalMinutes
        if (impliedAverageKmh > 140.0) return false

        // Radar é a tela em que mais vimos associação cruzada entre cards.
        // Os casos reais de R$61,31 e R$73,87 ficaram com tempo curto demais
        // para a distância lida. Nesta combinação preferimos "incerto".
        if (offerType == "radar" && impliedAverageKmh > 55.0 && perMinute > 3.0) return false

        return true
    }
}
