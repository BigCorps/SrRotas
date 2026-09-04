package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Resultado real (COMPLETED) por jornada, sem alterar Analytics de ofertas. */
object JourneyRealizedClient0262 {
    data class Snapshot(
        val completedTrips: Int,
        val realizedRevenue: Double,
        val fareMatchedTrips: Int,
        val revenueComplete: Boolean,
        val sessionEarnings: Double? = null,
        val sessionCompletedTrips: Int? = null,
        val sessionOfferedTrips: Int? = null,
        val sessionConfidence: Double? = null,
        val sessionStartedAt: String? = null,
        val sessionEndedAt: String? = null,
    )

    private const val PREFS = "sr_journey_realized_0262"
    private val executor = Executors.newSingleThreadExecutor()

    fun snapshot(context: Context, journeyId: String): Snapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(journeyId, null) ?: return null
        return runCatching { parse(JSONObject(raw)) }.getOrNull()
    }

    fun refreshDays(context: Context, days: Int, onResult: (Result<Int>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.backendUrl.isNotBlank() && settings.deviceToken.isNotBlank()) {
                    "Aparelho sem sessão."
                }
                val text = request(
                    "${settings.backendUrl.trimEnd('/')}/api/v1/journey-realized?days=${days.coerceIn(1, 90)}",
                    settings.deviceToken,
                )
                val items = JSONObject(text).optJSONArray("items")
                val editor = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                var imported = 0
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val row = items.optJSONObject(i) ?: continue
                        val journeyId = row.optString("journey_id")
                        if (journeyId.isBlank()) continue
                        editor.putString(journeyId, row.toString())
                        imported++
                    }
                }
                editor.apply()
                imported
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    private fun parse(row: JSONObject) = Snapshot(
        completedTrips = row.optInt("completed_trips", 0),
        realizedRevenue = row.optDouble("realized_revenue", 0.0).takeIf { it.isFinite() } ?: 0.0,
        fareMatchedTrips = row.optInt("fare_matched_trips", 0),
        revenueComplete = row.optBoolean("revenue_complete", false),
        sessionEarnings = row.numberOrNull("session_earnings"),
        sessionCompletedTrips = row.intOrNull("session_completed_trips"),
        sessionOfferedTrips = row.intOrNull("session_offered_trips"),
        sessionConfidence = row.numberOrNull("session_confidence"),
        sessionStartedAt = row.stringOrNull("session_started_at"),
        sessionEndedAt = row.stringOrNull("session_ended_at"),
    )

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun request(url: String, bearer: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
            error(if (message.isBlank()) "HTTP $status" else message)
        }
        return text
    }
}
