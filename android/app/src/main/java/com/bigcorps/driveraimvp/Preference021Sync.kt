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

/**
 * Ponte leve de preferências 0.21.
 * O legado continua em SettingsRepository e o backend vira o ponto comum Android/Web.
 */
object Preference021Sync {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun sync(context: Context) {
        val app = context.applicationContext
        executor.execute {
            val s = SettingsRepository(app).load()
            val x = Strategy021Store.load(app)
            if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return@execute
            runCatching {
                val body = JSONObject().apply {
                    put("min_per_km", s.minPerKm)
                    put("red_per_km_below", s.redPerKmBelow)
                    put("min_per_hour", s.minPerHour)
                    put("red_per_hour_below", s.redPerHourBelow)
                    put("min_per_minute", s.minPerMinute)
                    put("red_per_minute_below", s.redPerMinuteBelow)
                    put("good_rating_from", s.goodRatingFrom)
                    put("red_rating_below", s.redRatingBelow)
                    put("min_fare", s.minFare)
                    put("max_pickup_km", s.maxPickupKm)
                    put("max_pickup_minutes", x.maxPickupMinutes)
                    put("min_profit", s.minProfit)
                    put("min_profit_per_hour", s.minProfitPerHour)
                    put("red_profit_per_hour_below", s.redProfitPerHourBelow)
                    put("min_profit_percent", s.minProfitPercent)
                    put("red_profit_percent_below", s.redProfitPercentBelow)
                    put("cost_per_km", s.costPerKm)
                    put("timezone", "America/Sao_Paulo")
                    put("collective_stats_opt_in", s.collectiveStatsOptIn)
                    put("strategy_preset", x.strategyPreset)
                    put("app_theme", x.appTheme)
                    put("hud_theme_mode", x.hudThemeMode)
                }
                request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/preferences", body, s.deviceToken)
            }.onFailure {
                LocalLog.append(app, "Preferências 0.21: ${it.message}")
            }
        }
    }

    fun refresh(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            val current = SettingsRepository(app).load()
            if (current.backendUrl.isBlank() || current.deviceToken.isBlank()) {
                main.post { onDone?.invoke() }
                return@execute
            }

            runCatching {
                val raw = request(
                    "GET",
                    "${current.backendUrl.trimEnd('/')}/api/v1/preferences",
                    null,
                    current.deviceToken,
                )
                val p = JSONObject(raw).optJSONObject("preferences") ?: return@runCatching
                fun d(key: String, fallback: Double) =
                    if (p.has(key) && !p.isNull(key)) p.optDouble(key, fallback) else fallback

                val next = current.copy(
                    minPerKm = d("min_per_km", current.minPerKm),
                    redPerKmBelow = d("red_per_km_below", current.redPerKmBelow),
                    minPerHour = d("min_per_hour", current.minPerHour),
                    redPerHourBelow = d("red_per_hour_below", current.redPerHourBelow),
                    minPerMinute = d("min_per_minute", current.minPerMinute),
                    redPerMinuteBelow = d("red_per_minute_below", current.redPerMinuteBelow),
                    goodRatingFrom = d("good_rating_from", current.goodRatingFrom),
                    redRatingBelow = d("red_rating_below", current.redRatingBelow),
                    minFare = d("min_fare", current.minFare),
                    maxPickupKm = d("max_pickup_km", current.maxPickupKm),
                    minProfit = d("min_profit", current.minProfit),
                    minProfitPerHour = d("min_profit_per_hour", current.minProfitPerHour),
                    redProfitPerHourBelow = d("red_profit_per_hour_below", current.redProfitPerHourBelow),
                    minProfitPercent = d("min_profit_percent", current.minProfitPercent),
                    redProfitPercentBelow = d("red_profit_percent_below", current.redProfitPercentBelow),
                    collectiveStatsOptIn = p.optBoolean(
                        "collective_stats_opt_in",
                        current.collectiveStatsOptIn,
                    ),
                )
                SettingsRepository(app).save(next)
                Strategy021Store.savePreset(app, p.optString("strategy_preset", Strategy021Store.load(app).strategyPreset))
                Strategy021Store.saveMaxPickupMinutes(
                    app,
                    p.optInt("max_pickup_minutes", Strategy021Store.load(app).maxPickupMinutes),
                )
                Strategy021Store.saveAppTheme(app, p.optString("app_theme", Strategy021Store.load(app).appTheme))
                Strategy021Store.saveHudThemeMode(app, p.optString("hud_theme_mode", Strategy021Store.load(app).hudThemeMode))

                val mode = Strategy021Store.load(app).hudThemeMode
                val resolvedHud = when (mode) {
                    "light" -> "light"
                    "dark" -> "dark"
                    else -> Strategy021Store.load(app).appTheme
                }
                SettingsRepository(app).save(SettingsRepository(app).load().copy(hudTheme = resolvedHud))
            }.onFailure {
                LocalLog.append(app, "Falha ao atualizar preferências 0.21: ${it.message}")
            }

            main.post { onDone?.invoke() }
        }
    }

    private fun request(method: String, url: String, body: JSONObject?, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 12000
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        if (body != null) {
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status $text")
        return text
    }
}
