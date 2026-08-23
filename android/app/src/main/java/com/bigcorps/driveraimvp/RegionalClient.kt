package com.srrotas.app

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

object RegionalClient {
    data class Tip(
        val region: String,
        val profile: String,
        val samples: Int,
        val medianPerKm: Double?,
        val p25PerKm: Double?,
        val p75PerKm: Double?,
        val medianPerHour: Double?,
        val p25PerHour: Double?,
        val p75PerHour: Double?,
        val pickupKm: Double?,
        val pickupMinutes: Double?,
        val wording: String,
        val confidence: String,
        val source: String,
        val distanceKm: Double? = null,
    )
    data class Result(val tips: List<Tip>, val collectiveOptIn: Boolean, val preferred: String, val note: String)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun fetch(context: Context, mode: String, source: String, region: String = "", profile: String = "", callback: (kotlin.Result<Result>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val found = runCatching {
                val s = SettingsRepository(app).load()
                require(s.deviceToken.isNotBlank()) { "Conecte sua conta para consultar a inteligência." }
                val params = mutableListOf("mode=${enc(mode)}", "source=${enc(source)}")
                if (region.isNotBlank()) params += "region=${enc(region)}"
                if (profile.isNotBlank()) params += "profile=${enc(profile)}"
                val raw = request("${s.backendUrl.trimEnd('/')}/api/v1/intelligence/now?${params.joinToString("&")}", s.deviceToken)
                val json = JSONObject(raw)
                val personal = parse(json.optJSONArray("personal"))
                val collective = parse(json.optJSONArray("collective"))
                val seed = parse(json.optJSONArray("seed"))
                val selected = when {
                    source == "collective" && collective.isNotEmpty() -> collective
                    source == "personal" && personal.isNotEmpty() -> personal
                    else -> seed
                }
                Result(
                    tips = addLocalDistances(app, selected),
                    collectiveOptIn = json.optBoolean("collective_opt_in", false),
                    preferred = json.optString("preferred", "sr_rotas_seed"),
                    note = json.optString("note", "Tendência histórica; não garante corrida."),
                )
            }
            main.post { callback(found) }
        }
    }

    private fun parse(array: JSONArray?): List<Tip> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Tip(
                region = o.optString("region_label", "Região"), profile = o.optString("service_profile", "unknown"),
                samples = o.optInt("sample_count", 0), medianPerKm = optDouble(o,"median_per_km") ?: optDouble(o,"average_per_km"),
                p25PerKm = optDouble(o,"p25_per_km"), p75PerKm = optDouble(o,"p75_per_km"),
                medianPerHour = optDouble(o,"median_per_hour") ?: optDouble(o,"average_per_hour"), p25PerHour = optDouble(o,"p25_per_hour"), p75PerHour = optDouble(o,"p75_per_hour"),
                pickupKm = optDouble(o,"average_pickup_km"), pickupMinutes = optDouble(o,"average_pickup_minutes"),
                wording = o.optString("wording", "Histórico disponível"), confidence = o.optString("confidence", "low"), source = o.optString("source", "sr_rotas_seed"),
            )
        }
    }

    private fun addLocalDistances(context: Context, tips: List<Tip>): List<Tip> {
        val current = lastLocation(context) ?: return tips
        if (!Geocoder.isPresent()) return tips
        val geocoder = Geocoder(context, Locale("pt", "BR"))
        val enriched = tips.take(18).map { tip ->
            val point = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName("${tip.region}, São Paulo, SP, Brasil", 1)?.firstOrNull()
            }.getOrNull()
            if (point?.hasLatitude() == true && point.hasLongitude()) {
                val meters = FloatArray(1)
                Location.distanceBetween(current.latitude, current.longitude, point.latitude, point.longitude, meters)
                tip.copy(distanceKm = meters[0] / 1000.0)
            } else tip
        }
        return enriched.sortedWith(compareBy<Tip> { it.distanceKm ?: Double.MAX_VALUE }.thenByDescending { it.samples })
    }

    private fun lastLocation(context: Context): Location? {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
    }

    private fun request(url: String, token: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod="GET";connectTimeout=8000;readTimeout=15000
            setRequestProperty("Accept","application/json");setRequestProperty("Authorization","Bearer $token")
            setRequestProperty("X-SrRotas-App-Version",BuildConfig.VERSION_NAME)
        }
        val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream
        val text=stream?.use{BufferedReader(InputStreamReader(it)).readText()}.orEmpty();c.disconnect();if(status !in 200..299)error("HTTP $status");return text
    }
    private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
    private fun optDouble(o:JSONObject,key:String):Double?=if(o.isNull(key)||!o.has(key))null else o.optDouble(key).takeIf{!it.isNaN()}
}
