package com.srrotas.app

import java.util.Locale

/** Pure presentation rules shared by HUD/voice and covered by local unit tests. */
object HudPresentation {
    private val voiceKnown = listOf("per_minute", "per_km", "fare", "per_hour", "total_km", "total_minutes")

    fun visualFingerprint(offer: RideOffer, settings: DriverSettings): String {
        val size = when (settings.hudCardSize) {
            "compact", "large" -> settings.hudCardSize
            else -> "normal"
        }
        val enabled = settings.hudEnabledMetrics.csvSet()
        val limit = when (size) {
            "compact" -> 2
            "large" -> 6
            else -> 4
        }
        val visible = settings.hudMetricOrder.csvList()
            .filter { it in enabled }
            .mapNotNull { key -> metricValue(key, offer)?.let { value -> key to value } }
            .take(limit)
            .map { (key, value) -> "$key=$value" }

        val details = if (size == "compact") {
            emptyList()
        } else {
            listOf(
                "minutes=${offer.totalMinutes ?: "?"}",
                "km=${offer.totalKm?.keyNumber() ?: "?"}",
                "rating=${offer.passengerRating?.keyNumber() ?: "?"}",
            )
        }

        return buildList {
            add("size=$size")
            add("theme=${settings.hudTheme}")
            add("opacity=${settings.hudOpacity}")
            add("font=${settings.hudFontSize}")
            add("colorBlind=${settings.colorBlindMode}")
            add("position=${settings.hudPosition}")
            add("dismiss=${settings.hudDismissOnTap}")
            add("drag=${settings.hudDragEnabled}")
            add("verdict=${offer.verdict}")
            add("fare=${offer.fare.keyNumber()}")
            if (size != "compact") {
                add("service=${offer.serviceType}")
                add("offerType=${offer.offerType}")
            }
            addAll(visible)
            addAll(details)
        }.joinToString("|")
    }

    /**
     * When "seguir HUD" is active, only metrics that also exist on the HUD are
     * reordered by the HUD. Voice-only items (fare, distance, duration) keep
     * their chosen slots, so their relative placement stays predictable.
     */
    fun voiceMetricOrder(settings: DriverSettings): List<String> {
        val enabled = settings.voiceEnabledMetrics.csvSet()
        val configured = normalizedVoiceOrder(settings.voiceMetricOrder).filter { it in enabled }
        if (!settings.voiceFollowHudOrder) return configured

        val hudRank = settings.hudMetricOrder.csvList().withIndex().associate { it.value to it.index }
        val overlapPositions = configured.withIndex().filter { (_, key) -> key in hudRank }.map { it.index }
        if (overlapPositions.size < 2) return configured

        val orderedOverlap = overlapPositions
            .map { configured[it] }
            .sortedBy { hudRank[it] ?: Int.MAX_VALUE }

        var overlapCursor = 0
        return configured.mapIndexed { index, key ->
            if (index in overlapPositions) orderedOverlap[overlapCursor++] else key
        }
    }

    fun normalizedVoiceOrder(raw: String): List<String> {
        val parsed = raw.csvList().filter { it in voiceKnown }.distinct().toMutableList()
        voiceKnown.filterNot(parsed::contains).forEach(parsed::add)
        return parsed
    }

    private fun metricValue(key: String, offer: RideOffer): String? = when (key) {
        "per_minute" -> offer.perMinute?.keyNumber()
        "per_km" -> offer.perKm?.keyNumber()
        "rating" -> offer.passengerRating?.keyNumber()
        "per_hour" -> offer.perHour?.keyNumber()
        "profit_hour" -> offer.profitPerHour?.keyNumber()
        "profit_percent" -> offer.profitPercent?.keyNumber()
        "profit" -> offer.estimatedProfit?.keyNumber()
        else -> null
    }

    private fun String.csvList(): List<String> = split(',').map(String::trim).filter(String::isNotBlank)
    private fun String.csvSet(): Set<String> = csvList().toSet()
    private fun Double.keyNumber(): String = String.format(Locale.US, "%.2f", this)
}
