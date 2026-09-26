package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sincronização isolada de odômetro/energia.
 *
 * 0.33.3:
 * - nunca tenta enviar métricas antes do SyncCoordinator garantir a jornada;
 * - chamadas concorrentes são coalescidas;
 * - pendências antigas são recuperáveis em startup/onResume/edição;
 * - telemetria não exporta valores de km, combustível ou dinheiro.
 */
object JourneyMetricsClient026 {
    private const val PREFS = "sr_journey_metrics_sync_0333"
    private const val KEY_RUNS = "runs"
    private const val KEY_METRIC_ATTEMPTS = "metric_attempts"
    private const val KEY_METRIC_SUCCEEDED = "metric_succeeded"
    private const val KEY_METRIC_FAILED = "metric_failed"
    private const val KEY_ENERGY_ATTEMPTS = "energy_attempts"
    private const val KEY_ENERGY_SUCCEEDED = "energy_succeeded"
    private const val KEY_ENERGY_FAILED = "energy_failed"
    private const val KEY_LAST_RUN_AT = "last_run_at_ms"
    private const val KEY_LAST_CORE_SKIPPED = "last_core_skipped"
    private const val KEY_LAST_CORE_REASON = "last_core_reason"

    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Entrada pública canônica.
     *
     * Primeiro passa pelo SyncCoordinator para garantir/reparar a jornada no
     * backend. Somente depois começa o POST de odômetro/energia.
     */
    fun syncPending(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        SyncCoordinator.sync(app) { coreResult ->
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_LAST_CORE_SKIPPED, coreResult.skipped)
                .putString(KEY_LAST_CORE_REASON, coreResult.reason ?: "")
                .apply()

            if (coreResult.skipped) {
                onDone?.invoke()
                return@sync
            }
            flushAfterCoreSync(app, onDone)
        }
    }

    /**
     * Só deve ser usado quando o chamador já recebeu o callback de um
     * SyncCoordinator.sync bem-sucedido. Mantido público para integração
     * futura do botão "Sincronizar" sem duplicar o sync core.
     */
    fun flushAfterCoreSync(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        onDone?.let(callbacks::add)

        if (!running.compareAndSet(false, true)) return

        executor.execute {
            val store = JourneyMetricsStore026.get(app)
            val metrics = store.pendingMetrics(100)
            val energy = store.pendingEnergy(200)

            var metricSucceeded = 0
            var metricFailed = 0
            var energySucceeded = 0
            var energyFailed = 0

            metrics.forEach {
                if (syncMetricNow(app, it)) metricSucceeded++ else metricFailed++
            }
            energy.forEach {
                if (syncEnergyNow(app, it)) energySucceeded++ else energyFailed++
            }

            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_RUNS, prefs.getInt(KEY_RUNS, 0) + 1)
                .putInt(KEY_METRIC_ATTEMPTS, prefs.getInt(KEY_METRIC_ATTEMPTS, 0) + metrics.size)
                .putInt(KEY_METRIC_SUCCEEDED, prefs.getInt(KEY_METRIC_SUCCEEDED, 0) + metricSucceeded)
                .putInt(KEY_METRIC_FAILED, prefs.getInt(KEY_METRIC_FAILED, 0) + metricFailed)
                .putInt(KEY_ENERGY_ATTEMPTS, prefs.getInt(KEY_ENERGY_ATTEMPTS, 0) + energy.size)
                .putInt(KEY_ENERGY_SUCCEEDED, prefs.getInt(KEY_ENERGY_SUCCEEDED, 0) + energySucceeded)
                .putInt(KEY_ENERGY_FAILED, prefs.getInt(KEY_ENERGY_FAILED, 0) + energyFailed)
                .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
                .apply()

            LocalLog.append(
                app,
                "JourneyMetrics 0.33.3: km ${metrics.size} " +
                    "(ok=$metricSucceeded falha=$metricFailed), " +
                    "energia ${energy.size} (ok=$energySucceeded falha=$energyFailed)",
            )

            val deliver = callbacks.toList()
            callbacks.clear()
            running.set(false)
            main.post {
                deliver.forEach { callback -> runCatching(callback) }
            }
        }
    }

    /**
     * Diagnóstico somente de estado/contadores.
     * Não inclui odômetros, valores pagos, litros/kWh ou fuel_type.
     */
    fun toJson(context: Context): JSONObject {
        val app = context.applicationContext
        val store = JourneyMetricsStore026.get(app)
        val pendingMetrics = store.pendingMetrics(100).size
        val pendingEnergy = store.pendingEnergy(200).size
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return JSONObject().apply {
            put("schema", "sr-journey-metrics-sync-0333-v1")
            put("running", running.get())
            put("pending_metrics", pendingMetrics)
            put("pending_metrics_capped", pendingMetrics >= 100)
            put("pending_energy", pendingEnergy)
            put("pending_energy_capped", pendingEnergy >= 200)
            put("runs", prefs.getInt(KEY_RUNS, 0))
            put("metric_attempts", prefs.getInt(KEY_METRIC_ATTEMPTS, 0))
            put("metric_succeeded", prefs.getInt(KEY_METRIC_SUCCEEDED, 0))
            put("metric_failed", prefs.getInt(KEY_METRIC_FAILED, 0))
            put("energy_attempts", prefs.getInt(KEY_ENERGY_ATTEMPTS, 0))
            put("energy_succeeded", prefs.getInt(KEY_ENERGY_SUCCEEDED, 0))
            put("energy_failed", prefs.getInt(KEY_ENERGY_FAILED, 0))
            put("last_run_at_ms", prefs.getLong(KEY_LAST_RUN_AT, 0L))
            put("last_core_skipped", prefs.getBoolean(KEY_LAST_CORE_SKIPPED, false))
            put("last_core_reason", prefs.getString(KEY_LAST_CORE_REASON, "") ?: "")
            put("core_sync_before_metrics", true)
            put("exports_sensitive_metric_values", false)
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
            main.post { onResult(result) }
        }
    }

    private fun syncMetricNow(
        context: Context,
        metric: JourneyMetricsStore026.Metric,
    ): Boolean {
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
        }.onFailure {
            LocalLog.append(context, "Falha ao sincronizar km da jornada: ${it.message}")
        }.getOrDefault(false)
    }

    private fun syncEnergyNow(
        context: Context,
        entry: JourneyMetricsStore026.EnergyEntry,
    ): Boolean {
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
        }.onFailure {
            LocalLog.append(context, "Falha ao sincronizar gasto da jornada: ${it.message}")
        }.getOrDefault(false)
    }

    private fun request(
        method: String,
        url: String,
        body: JSONObject?,
        bearer: String,
    ): String {
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
        if (body != null) {
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
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

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}
