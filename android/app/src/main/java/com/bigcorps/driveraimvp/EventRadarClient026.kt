package com.srrotas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

object EventRadarClient026 {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private const val CACHE_TTL_MS = 5 * 60_000L

    @Volatile
    private var cached: EventRadarResult026? = null
    @Volatile
    private var cachedAtMs: Long = 0L

    fun latest(): EventRadarResult026? = cached

    fun fetchNearby(
        context: Context,
        radiusKm: Int = 15,
        hours: Int = 8,
        force: Boolean = false,
        callback: (Result<EventRadarResult026>) -> Unit,
    ) {
        val app = context.applicationContext
        val now = android.os.SystemClock.elapsedRealtime()
        val snapshot = cached
        if (!force && snapshot != null && (now - cachedAtMs) in 0L until CACHE_TTL_MS) {
            main.post { callback(Result.success(snapshot)) }
            return
        }
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) { "Conecte sua conta." }
                val location = lastLocation(app)
                    ?: error("Localização ainda indisponível.")
                val endpoint = buildString {
                    append(settings.backendUrl.trimEnd('/'))
                    append("/api/v1/radar/events?lat=")
                    append(enc(location.latitude.toString()))
                    append("&lng=")
                    append(enc(location.longitude.toString()))
                    append("&radius_km=")
                    append(radiusKm.coerceIn(2, 30))
                    append("&hours=")
                    append(hours.coerceIn(1, 24))
                }
                val raw = request(endpoint, settings.deviceToken)
                parse(JSONObject(raw)).also {
                    cached = it
                    cachedAtMs = android.os.SystemClock.elapsedRealtime()
                }
            }
            main.post { callback(result) }
        }
    }

    private fun parse(json: JSONObject): EventRadarResult026 {
        val array = json.optJSONArray("opportunities")
        val items = if (array == null) {
            emptyList()
        } else {
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                EventRadarOpportunity026(
                    id = o.optString("id"),
                    type = o.optString("event_type", "event"),
                    name = o.optString("name", "Evento"),
                    venueName = o.optString("venue_name").takeIf(String::isNotBlank),
                    address = o.optString("address").takeIf(String::isNotBlank),
                    startsAt = o.optString("starts_at"),
                    expectedEndAt = o.optString("expected_end_at"),
                    egressStartAt = o.optString("egress_start_at"),
                    egressEndAt = o.optString("egress_end_at"),
                    distanceKm = o.optDouble("distance_km", Double.NaN),
                    source = o.optString("source", "unknown"),
                    confidence = o.optDouble("confidence", 0.0),
                    sourceUrl = o.optString("source_url").takeIf(String::isNotBlank),
                ).takeIf(EventRadarRules026::visible)
            }
        }
        return EventRadarResult026(
            opportunities = items,
            sourceStatus = json.optString("source_status", "unknown"),
            refreshedAt = json.optString("refreshed_at").takeIf(String::isNotBlank),
        )
    }

    private fun lastLocation(context: Context): Location? {
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(LocationManager::class.java)
        return listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun request(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching {
                JSONObject(text).optString("error").ifBlank { "HTTP $status" }
            }.getOrDefault("HTTP $status")
            error(message)
        }
        return text
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
