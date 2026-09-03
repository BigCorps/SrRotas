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
)

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
                })
            }
        })
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
