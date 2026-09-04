package com.srrotas.app

import org.json.JSONArray
import org.json.JSONObject

data class UberSessionSummary026(
    val sourceKey: String,
    val capturedAt: String,
    val startedAt: String?,
    val endedAt: String?,
    val earnings: Double?,
    val completedTrips: Int?,
    val offeredTrips: Int?,
    val confidence: Double,
    val journeyId: String? = null,
    val observation: String? = null,
)

data class UberCompletedRide026(
    val sourceKey: String,
    val capturedAt: String,
    val occurredAt: String?,
    val fare: Double,
    val serviceType: String,
    val pickupLabel: String?,
    val destinationLabel: String?,
    val confidence: Double,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val surgeAmount: Double? = null,
    val extraAmount: Double? = null,
    val rideStatus: String = STATUS_COMPLETED,
) {
    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CANCELLED = "cancelled"
    }
}

sealed class UberDigitizationResult026 {
    data class Session(val value: UberSessionSummary026) : UberDigitizationResult026()
    data class Rides(val values: List<UberCompletedRide026>) : UberDigitizationResult026()
}

object UberDigitizationJson026 {
    fun session(value: UberSessionSummary026): JSONObject = JSONObject().apply {
        put("action", "session_summary")
        put("source_key", value.sourceKey)
        put("captured_at", value.capturedAt)
        putNullable("started_at", value.startedAt)
        putNullable("ended_at", value.endedAt)
        putNullable("earnings", value.earnings)
        putNullable("completed_trips", value.completedTrips)
        putNullable("offered_trips", value.offeredTrips)
        put("confidence", value.confidence)
        putNullable("journey_id", value.journeyId)
        putNullable("observation", value.observation)
    }

    fun rides(values: List<UberCompletedRide026>): JSONObject = JSONObject().apply {
        put("action", "completed_rides")
        put("rides", JSONArray().apply {
            values.forEach { value ->
                put(JSONObject().apply {
                    put("source_key", value.sourceKey)
                    put("captured_at", value.capturedAt)
                    putNullable("occurred_at", value.occurredAt)
                    put("fare", value.fare)
                    put("service_type", value.serviceType)
                    putNullable("pickup_label", value.pickupLabel)
                    putNullable("destination_label", value.destinationLabel)
                    put("confidence", value.confidence)
                    putNullable("duration_seconds", value.durationSeconds)
                    putNullable("distance_km", value.distanceKm)
                    putNullable("surge_amount", value.surgeAmount)
                    putNullable("extra_amount", value.extraAmount)
                    put("ride_status", value.rideStatus)
                })
            }
        })
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
