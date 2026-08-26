package com.srrotas.app

/** Classificação única usada pelo veredito geral e pelos boxes individuais. */
object HudMetricEvaluation0221 {
    fun grade(
        key: String,
        offer: RideOffer,
        settings: DriverSettings,
        maxPickupMinutes: Int,
    ): Int? = when (key) {
        "per_minute" -> gradeHigher(offer.perMinute, settings.redPerMinuteBelow, settings.minPerMinute)
        "per_km" -> gradeHigher(offer.perKm, settings.redPerKmBelow, settings.minPerKm)
        "rating" -> gradeHigher(offer.passengerRating, settings.redRatingBelow, settings.goodRatingFrom)
        "per_hour" -> gradeHigher(offer.perHour, settings.redPerHourBelow, settings.minPerHour)
        "profit_hour" -> gradeHigher(offer.profitPerHour, settings.redProfitPerHourBelow, settings.minProfitPerHour)
        "profit_percent" -> gradeHigher(offer.profitPercent, settings.redProfitPercentBelow, settings.minProfitPercent)
        "profit" -> gradeProfit(offer.estimatedProfit, settings.minProfit)
        "pickup" -> PickupPresentation0211.grade(
            offer.pickupKm,
            offer.pickupMinutes,
            settings.maxPickupKm,
            maxPickupMinutes,
        ).rank.takeIf { offer.pickupKm != null || offer.pickupMinutes != null }
        else -> null
    }

    fun weightedVerdict(
        settings: DriverSettings,
        offer: RideOffer,
        maxPickupMinutes: Int,
    ): String {
        val ordered = settings.hudMetricOrder
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        val enabled = settings.hudEnabledMetrics
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

        val active = if (enabled.isEmpty()) ordered.toSet() else enabled
        var weighted = 0.0
        var weights = 0.0

        ordered.filter(active::contains).forEach { key ->
            val grade = grade(key, offer, settings, maxPickupMinutes) ?: return@forEach
            val index = ordered.indexOf(key)
            val weight = when (index) {
                0 -> 1.70
                1 -> 1.45
                2 -> 1.25
                3 -> 1.10
                else -> 1.00
            }
            weighted += grade * weight
            weights += weight
        }

        if (weights <= 0.0) return "regular"
        val average = weighted / weights
        return when {
            average >= 1.35 -> "boa"
            average < 0.72 -> "ruim"
            else -> "regular"
        }
    }

    private fun gradeHigher(value: Double?, redBelow: Double, goodFrom: Double): Int? {
        if (value == null || (redBelow <= 0.0 && goodFrom <= 0.0)) return null
        if (redBelow > 0.0 && value < redBelow) return 0
        if (goodFrom > 0.0 && value >= goodFrom) return 2
        return 1
    }

    private fun gradeProfit(value: Double?, target: Double): Int? {
        if (value == null) return null
        if (value < 0.0) return 0
        if (target > 0.0) return if (value >= target) 2 else 1
        return if (value > 0.0) 2 else 1
    }
}
