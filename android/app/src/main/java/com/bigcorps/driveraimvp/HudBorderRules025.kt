package com.srrotas.app

/**
 * A borda 0.25 representa a média ponderada das métricas ativas no HUD.
 * Ela não substitui o veredito textual da oferta nem remove travas absolutas.
 */
object HudBorderRules025 {
    fun weightedVerdict(
        settings: DriverSettings,
        offer: RideOffer,
        maxPickupMinutes: Int,
    ): String = HudMetricEvaluation0221.weightedVerdict(
        settings = settings,
        offer = offer,
        maxPickupMinutes = maxPickupMinutes,
    )

    fun grade(verdict: String): Int = when (verdict) {
        "boa" -> 2
        "ruim" -> 0
        else -> 1
    }

    fun strokeDp(size: String?): Int = when (Hud023Spec.normalizeSize(size)) {
        Hud023Spec.SIZE_COMPACT -> 2
        else -> 3
    }
}
