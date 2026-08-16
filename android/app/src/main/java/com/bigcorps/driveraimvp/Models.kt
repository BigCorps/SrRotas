package com.bigcorps.driveraimvp

import org.json.JSONObject
import java.util.UUID

data class DriverSettings(
    val backendUrl: String = "",
    val deviceToken: String = "",
    val minPerKm: Double = 1.80,
    val minPerHour: Double = 35.0,
    val costPerKm: Double = 0.85,
    val ocrEnabled: Boolean = true,
    val consentAccepted: Boolean = false,
)

data class RideOffer(
    val localId: String = UUID.randomUUID().toString(),
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
    val parserVersion: String = "uber-br-v0.1",
    val dedupeKey: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("local_id", localId)
        put("platform", platform)
        put("observed_at", observedAt)
        put("source_package", sourcePackage)
        put("capture_method", captureMethod)
        put("raw_text", rawText)
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
        put("parser_version", parserVersion)
        put("dedupe_key", dedupeKey)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
