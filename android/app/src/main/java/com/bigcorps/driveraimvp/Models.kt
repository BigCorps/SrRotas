package com.srrotas.app

import org.json.JSONObject
import java.util.UUID

data class DriverSettings(
    val backendUrl: String = SettingsRepository.DEFAULT_BACKEND_URL,
    val deviceToken: String = "",
    val driverDisplayName: String = "Motorista",
    val accountEmail: String = "",
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Int = 0,
    val minPerKm: Double = 1.80,
    val redPerKmBelow: Double = 1.45,
    val minPerHour: Double = 35.0,
    val redPerHourBelow: Double = 28.0,
    val goodRatingFrom: Double = 4.85,
    val redRatingBelow: Double = 4.70,
    val minPerMinute: Double = 0.60,
    val redPerMinuteBelow: Double = 0.48,
    val minFare: Double = 0.0,
    val maxPickupKm: Double = 5.0,
    val minProfit: Double = 0.0,
    val minProfitPerHour: Double = 0.0,
    val redProfitPerHourBelow: Double = 0.0,
    val minProfitPercent: Double = 0.0,
    val redProfitPercentBelow: Double = 0.0,
    val costPerKm: Double = 0.85,
    val ocrEnabled: Boolean = true,
    val consentAccepted: Boolean = false,
    val hudMetricOrder: String = "per_minute,per_km,rating,per_hour,profit_hour,profit_percent,profit",
    val hudEnabledMetrics: String = "per_minute,per_km,rating,per_hour",
    val hudPosition: String = "left",
    val hudTheme: String = "auto",
    val hudCardSize: String = "normal",
    val hudDismissOnTap: Boolean = true,
    val hudDragEnabled: Boolean = true,
    val colorBlindMode: Boolean = false,
    val hudOpacity: Int = 90,
    val hudFontSize: Int = 16,
    val textNotificationEnabled: Boolean = false,
    val voiceNotificationEnabled: Boolean = false,
    val voiceFollowHudOrder: Boolean = true,
    val voiceMetricOrder: String = "per_minute,per_km,fare,per_hour,total_km,total_minutes,destination",
    val voiceEnabledMetrics: String = "per_km,per_hour",
    val privateScreenshotEnabled: Boolean = false,
    val defaultPassengerMessage: String = "Olá! Já estou a caminho do local de embarque.",
)

data class JourneyRecord(
    val id: String = UUID.randomUUID().toString(),
    val platform: String = "uber",
    val startedAt: String,
    val endedAt: String? = null,
    val endReason: String? = null,
)

data class JourneySummary(
    val journey: JourneyRecord,
    val offerCount: Int,
    val goodCount: Int,
    val regularCount: Int,
    val badCount: Int,
    val averagePerKm: Double?,
    val averagePerHour: Double?,
    val estimatedProfitObserved: Double?,
)


data class OfferContext(
    val pickupLabel: String? = null,
    val destinationLabel: String? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val pickupCell: String? = null,
    val destinationCell: String? = null,
    val estimatedArrivalAt: String? = null,
    val contextConfidence: Double = 0.0,
    val geocodeStatus: String = "unresolved",
    val geocodeSource: String? = null,
    val contextVersion: String = "sr-context-v0.14.0",
    val sourceType: String = "live_ocr",
    val timeSource: String = "system_observed_at",
) {
    fun hasTextContext(): Boolean = !pickupLabel.isNullOrBlank() || !destinationLabel.isNullOrBlank()
    fun hasAnyCoordinate(): Boolean = pickupLat != null || destinationLat != null
}

data class RideOffer(
    val localId: String = UUID.randomUUID().toString(),
    val journeyId: String? = null,
    val platform: String = "uber",
    val observedAt: String,
    val sourcePackage: String,
    val captureMethod: String,
    val rawText: String,
    val fare: Double,
    val pickupKm: Double?,
    val tripKm: Double?,
    val totalKm: Double?,
    val pickupMinutes: Int?,
    val tripMinutes: Int?,
    val totalMinutes: Int?,
    val perKm: Double?,
    val perHour: Double?,
    val perMinute: Double?,
    val estimatedCost: Double?,
    val estimatedProfit: Double?,
    val profitPerHour: Double?,
    val profitPercent: Double?,
    val passengerRating: Double?,
    val advertisedPerKm: Double?,
    val serviceType: String = "unknown",
    val verdict: String,
    val confidence: Double = 0.65,
    val offerType: String = "exclusive",
    val context: OfferContext? = null,
    // Offer Engine v1 congelado; contexto espacial evolui separadamente.
    val parserVersion: String = "sr-rotas-v0.5.4",
    val dedupeKey: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("local_id", localId)
        if (journeyId.isNullOrBlank()) put("journey_id", JSONObject.NULL) else put("journey_id", journeyId)
        put("platform", platform)
        put("observed_at", observedAt)
        put("source_package", sourcePackage)
        put("capture_method", captureMethod)
        put("raw_text", "")
        put("share_raw_text", false)
        put("fare", fare)
        putNullable("pickup_km", pickupKm)
        putNullable("trip_km", tripKm)
        putNullable("total_km", totalKm)
        putNullable("pickup_minutes", pickupMinutes)
        putNullable("trip_minutes", tripMinutes)
        putNullable("total_minutes", totalMinutes)
        putNullable("per_km", perKm)
        putNullable("per_hour", perHour)
        putNullable("per_minute", perMinute)
        putNullable("estimated_cost", estimatedCost)
        putNullable("estimated_profit", estimatedProfit)
        putNullable("profit_per_hour", profitPerHour)
        putNullable("profit_percent", profitPercent)
        putNullable("passenger_rating", passengerRating)
        putNullable("advertised_per_km", advertisedPerKm)
        put("service_type", serviceType)
        put("verdict", verdict)
        put("confidence", confidence)
        put("offer_type", offerType)
        context?.let { ctx ->
            putNullable("pickup_label", ctx.pickupLabel)
            putNullable("destination_label", ctx.destinationLabel)
            putNullable("pickup_lat", ctx.pickupLat)
            putNullable("pickup_lng", ctx.pickupLng)
            putNullable("destination_lat", ctx.destinationLat)
            putNullable("destination_lng", ctx.destinationLng)
            putNullable("pickup_cell", ctx.pickupCell)
            putNullable("destination_cell", ctx.destinationCell)
            putNullable("estimated_arrival_at", ctx.estimatedArrivalAt)
            put("context_confidence", ctx.contextConfidence.coerceIn(0.0, 1.0))
            put("geocode_status", ctx.geocodeStatus)
            putNullable("geocode_source", ctx.geocodeSource)
            put("context_version", ctx.contextVersion)
            put("context_source_type", ctx.sourceType)
            put("context_time_source", ctx.timeSource)
        }
        put("parser_version", parserVersion)
        put("dedupe_key", dedupeKey)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
