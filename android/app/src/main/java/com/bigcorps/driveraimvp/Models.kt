package com.srrotas.app

import org.json.JSONObject
import java.util.UUID

data class DriverSettings(
    val backendUrl: String = SettingsRepository.DEFAULT_BACKEND_URL,
    val deviceToken: String = "",
    val minPerKm: Double = 1.80,
    val minPerHour: Double = 35.0,
    val minFare: Double = 0.0,
    val maxPickupKm: Double = 5.0,
    val minProfit: Double = 0.0,
    val costPerKm: Double = 0.85,
    val ocrEnabled: Boolean = true,
    val consentAccepted: Boolean = false,
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
    val estimatedCost: Double?,
    val estimatedProfit: Double?,
    val verdict: String,
    val confidence: Double = 0.65,
    val offerType: String = "exclusive",
    val parserVersion: String = "sr-rotas-v0.3",
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
        putNullable("estimated_cost", estimatedCost)
        putNullable("estimated_profit", estimatedProfit)
        put("verdict", verdict)
        put("confidence", confidence)
        put("offer_type", offerType)
        put("parser_version", parserVersion)
        put("dedupe_key", dedupeKey)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
