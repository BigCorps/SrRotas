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

/** Sincronização isolada da 0.26 para não tocar no BackendClient validado. */
object JourneyMetricsClient026 {
    private val executor = Executors.newSingleThreadExecutor()

    fun syncPending(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            val store = JourneyMetricsStore026.get(app)
            store.pendingMetrics().forEach { syncMetricNow(app, it) }
            store.pendingEnergy().forEach { syncEnergyNow(app, it) }
            Handler(Looper.getMainLooper()).post { onDone?.invoke() }
        }
    }

    fun refreshDays(context: Context, days: Int, onResult: (Result<Int>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val s = SettingsRepository(app).load()
                require(s.backendUrl.isNotBlank() && s.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                val response = request(
                    "GET",
                    "${s.backendUrl.trimEnd('/')}/api/v1/journey-metrics?days=${days.coerceIn(1, 90)}",
                    null,
                    s.deviceToken,
                )
                val json = JSONObject(response)
                val store = JourneyMetricsStore026.get(app)
                var imported = 0
                val metrics = json.optJSONArray("metrics")
                if (metrics != null) {
                    for (i in 0 until metrics.length()) {
                        val row = metrics.optJSONObject(i) ?: continue
                        val id = row.optString("journey_id")
                        if (id.isBlank()) continue
                        store.importMetric(
                            JourneyMetricsStore026.Metric(
                                journeyId = id,
                                odometerStartKm = row.numberOrNull("odometer_start_km"),
                                odometerEndKm = row.numberOrNull("odometer_end_km"),
                                updatedAt = row.optString("updated_at"),
                                syncState = 1,
                            ),
                        )
                        imported++
                    }
                }
                val entries = json.optJSONArray("energy_entries")
                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val row = entries.optJSONObject(i) ?: continue
                        val id = row.optString("client_entry_id")
                        val journeyId = row.optString("journey_id")
                        if (id.isBlank() || journeyId.isBlank()) continue
                        store.importEnergy(
                            JourneyMetricsStore026.EnergyEntry(
                                id = id,
                                journeyId = journeyId,
                                kind = row.optString("energy_type"),
                                amountPaid = row.numberOrNull("amount_paid"),
                                quantity = row.numberOrNull("quantity"),
                                unit = row.optString("unit"),
                                fuelType = row.stringOrNull("fuel_type"),
                                recordedAt = row.optString("recorded_at"),
                                syncState = 1,
                            ),
                        )
                        imported++
                    }
                }
                imported
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    private fun syncMetricNow(context: Context, metric: JourneyMetricsStore026.Metric): Boolean {
        val s = SettingsRepository(context).load()
        if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request(
                "POST",
                "${s.backendUrl.trimEnd('/')}/api/v1/journey-metrics",
                JSONObject().apply {
                    put("action", "metrics")
                    put("journey_id", metric.journeyId)
                    putNullable("odometer_start_km", metric.odometerStartKm)
                    putNullable("odometer_end_km", metric.odometerEndKm)
                    put("updated_at", metric.updatedAt)
                },
                s.deviceToken,
            )
            JourneyMetricsStore026.get(context).markMetricSynced(metric.journeyId)
            true
        }.onFailure { LocalLog.append(context, "Falha ao sincronizar km da jornada: ${it.message}") }
            .getOrDefault(false)
    }

    private fun syncEnergyNow(context: Context, entry: JourneyMetricsStore026.EnergyEntry): Boolean {
        val s = SettingsRepository(context).load()
        if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request(
                "POST",
                "${s.backendUrl.trimEnd('/')}/api/v1/journey-metrics",
                JSONObject().apply {
                    put("action", "energy")
                    put("journey_id", entry.journeyId)
                    put("client_entry_id", entry.id)
                    put("energy_type", entry.kind)
                    putNullable("amount_paid", entry.amountPaid)
                    putNullable("quantity", entry.quantity)
                    put("unit", entry.unit)
                    putNullable("fuel_type", entry.fuelType)
                    put("recorded_at", entry.recordedAt)
                },
                s.deviceToken,
            )
            JourneyMetricsStore026.get(context).markEnergySynced(entry.id)
            true
        }.onFailure { LocalLog.append(context, "Falha ao sincronizar gasto da jornada: ${it.message}") }
            .getOrDefault(false)
    }

    private fun request(method: String, url: String, body: JSONObject?, bearer: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
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

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}
