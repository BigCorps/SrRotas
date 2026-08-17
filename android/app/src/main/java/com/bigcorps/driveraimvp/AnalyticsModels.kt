package com.srrotas.app

import org.json.JSONArray
import org.json.JSONObject

data class AnalyticsSummary(
    val offerCount: Int = 0,
    val totalOfferedFare: Double = 0.0,
    val averageFare: Double? = null,
    val averagePerKm: Double? = null,
    val averagePerHour: Double? = null,
    val averagePerMinute: Double? = null,
    val estimatedTotalProfit: Double = 0.0,
    val averageEstimatedProfit: Double? = null,
    val goodCount: Int = 0,
    val regularCount: Int = 0,
    val badCount: Int = 0,
)

data class AnalyticsPoint(
    val key: String,
    val label: String,
    val offerCount: Int,
    val averagePerKm: Double?,
    val averagePerHour: Double?,
    val averagePerMinute: Double?,
    val averageProfit: Double?,
)

data class ServiceAnalytics(
    val serviceType: String,
    val offerCount: Int,
    val averagePerKm: Double?,
    val averagePerHour: Double?,
    val averageProfit: Double?,
)

data class JourneyAnalytics(
    val id: String,
    val startedAt: String,
    val endedAt: String?,
    val durationMinutes: Int?,
    val offerCount: Int,
    val goodCount: Int,
    val regularCount: Int,
    val badCount: Int,
    val averagePerKm: Double?,
    val averagePerHour: Double?,
    val estimatedProfitObserved: Double?,
)

data class TopOfferAnalytics(
    val observedAt: String,
    val fare: Double,
    val serviceType: String,
    val offerType: String,
    val verdict: String,
    val perKm: Double?,
    val perHour: Double?,
    val perMinute: Double?,
    val estimatedProfit: Double?,
    val passengerRating: Double?,
)

data class AnalyticsComparison(
    val previous: AnalyticsSummary,
    val offerCountPct: Double?,
    val averagePerKmPct: Double?,
    val averagePerHourPct: Double?,
    val averagePerMinutePct: Double?,
    val averageProfitPct: Double?,
)

data class HistoryAnalytics(
    val source: String,
    val from: String,
    val to: String,
    val summary: AnalyticsSummary,
    val comparison: AnalyticsComparison?,
    val daily: List<AnalyticsPoint>,
    val hours: List<AnalyticsPoint>,
    val services: List<ServiceAnalytics>,
    val journeys: List<JourneyAnalytics>,
    val topOffers: List<TopOfferAnalytics>,
    val truncated: Boolean,
    val note: String,
) {
    companion object {
        fun fromJson(json: JSONObject, source: String = "cloud"): HistoryAnalytics {
            val range = json.optJSONObject("range") ?: JSONObject()
            val current = json.optJSONObject("summary") ?: JSONObject()
            val comparisonJson = json.optJSONObject("comparison")
            val comparison = comparisonJson?.let {
                val previous = summary(it.optJSONObject("previous") ?: JSONObject())
                val delta = it.optJSONObject("delta") ?: JSONObject()
                AnalyticsComparison(
                    previous = previous,
                    offerCountPct = delta.numberOrNull("offer_count_pct"),
                    averagePerKmPct = delta.numberOrNull("average_per_km_pct"),
                    averagePerHourPct = delta.numberOrNull("average_per_hour_pct"),
                    averagePerMinutePct = delta.numberOrNull("average_per_minute_pct"),
                    averageProfitPct = delta.numberOrNull("average_estimated_profit_pct"),
                )
            }
            return HistoryAnalytics(
                source = source,
                from = range.optString("from"),
                to = range.optString("to"),
                summary = summary(current),
                comparison = comparison,
                daily = points(json.optJSONArray("daily")),
                hours = points(json.optJSONArray("hours")),
                services = services(json.optJSONArray("services")),
                journeys = journeys(json.optJSONArray("journeys")),
                topOffers = topOffers(json.optJSONArray("top_offers")),
                truncated = json.optBoolean("truncated", false),
                note = json.optString("note", "Métricas calculadas sobre ofertas observadas."),
            )
        }

        private fun summary(o: JSONObject) = AnalyticsSummary(
            offerCount = o.optInt("offer_count"),
            totalOfferedFare = o.optDouble("total_offered_fare", 0.0),
            averageFare = o.numberOrNull("average_fare"),
            averagePerKm = o.numberOrNull("average_per_km"),
            averagePerHour = o.numberOrNull("average_per_hour"),
            averagePerMinute = o.numberOrNull("average_per_minute"),
            estimatedTotalProfit = o.optDouble("estimated_total_profit", 0.0),
            averageEstimatedProfit = o.numberOrNull("average_estimated_profit"),
            goodCount = o.optJSONObject("verdicts")?.optInt("boa") ?: 0,
            regularCount = o.optJSONObject("verdicts")?.optInt("regular") ?: 0,
            badCount = o.optJSONObject("verdicts")?.optInt("ruim") ?: 0,
        )

        private fun points(a: JSONArray?): List<AnalyticsPoint> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.map {
                AnalyticsPoint(
                    key = it.optString("key", it.optString("date", it.optString("hour"))),
                    label = it.optString("label", it.optString("date", it.optString("hour"))),
                    offerCount = it.optInt("offer_count"),
                    averagePerKm = it.numberOrNull("average_per_km"),
                    averagePerHour = it.numberOrNull("average_per_hour"),
                    averagePerMinute = it.numberOrNull("average_per_minute"),
                    averageProfit = it.numberOrNull("average_estimated_profit"),
                )
            }
        }

        private fun services(a: JSONArray?): List<ServiceAnalytics> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                ServiceAnalytics(
                    serviceType = it.optString("service_type", "unknown"),
                    offerCount = it.optInt("offer_count"),
                    averagePerKm = it.numberOrNull("average_per_km"),
                    averagePerHour = it.numberOrNull("average_per_hour"),
                    averageProfit = it.numberOrNull("average_estimated_profit"),
                )
            }
        }

        private fun journeys(a: JSONArray?): List<JourneyAnalytics> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                JourneyAnalytics(
                    id = it.optString("id"),
                    startedAt = it.optString("started_at"),
                    endedAt = it.stringOrNull("ended_at"),
                    durationMinutes = it.intOrNull("duration_minutes"),
                    offerCount = it.optInt("offer_count"),
                    goodCount = it.optInt("good_count"),
                    regularCount = it.optInt("regular_count"),
                    badCount = it.optInt("bad_count"),
                    averagePerKm = it.numberOrNull("average_per_km"),
                    averagePerHour = it.numberOrNull("average_per_hour"),
                    estimatedProfitObserved = it.numberOrNull("estimated_profit_observed"),
                )
            }
        }

        private fun topOffers(a: JSONArray?): List<TopOfferAnalytics> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                TopOfferAnalytics(
                    observedAt = it.optString("observed_at"),
                    fare = it.optDouble("fare"),
                    serviceType = it.optString("service_type", "unknown"),
                    offerType = it.optString("offer_type", "exclusive"),
                    verdict = it.optString("verdict", "regular"),
                    perKm = it.numberOrNull("per_km"),
                    perHour = it.numberOrNull("per_hour"),
                    perMinute = it.numberOrNull("per_minute"),
                    estimatedProfit = it.numberOrNull("estimated_profit"),
                    passengerRating = it.numberOrNull("passenger_rating"),
                )
            }
        }

        private fun JSONObject.numberOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

        private fun JSONObject.intOrNull(key: String): Int? =
            if (!has(key) || isNull(key)) null else optInt(key)
    }
}
