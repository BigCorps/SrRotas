package com.srrotas.app.r3lab

import android.content.Context
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Telemetria LOCAL do laboratório: só contadores, códigos e latências. Nunca texto de OCR,
 * endereço, coordenada ou imagem. Quadros ≠ OCR ≠ candidatos ≠ ofertas distintas.
 */
object R3LabTelemetry {
    private const val PREFS = "r3_lab_telemetry"
    private val counters = ConcurrentHashMap<String, Long>()
    private val latencies = ConcurrentHashMap<String, ArrayDeque<Long>>()
    @Volatile var state: String = "DISABLED"; private set
    @Volatile var lastBlock: String? = null; private set
    @Volatile var lastTargets: String = ""; private set
    @Volatile private var sessionStartElapsed = 0L
    @Volatile private var sessionStartCpuMs = 0L
    @Volatile private var sessionStartBattery = -1
    @Volatile private var loaded = false

    fun count(key: String, n: Long = 1) { counters.merge(key.take(80), n, Long::plus) }
    fun latency(key: String, ms: Long) {
        val q = latencies.getOrPut(key) { ArrayDeque() }
        synchronized(q) { q.addLast(ms); while (q.size > 300) q.removeFirst() }
    }
    fun state(value: String, block: String? = null) { state = value; lastBlock = block }
    fun targets(value: String) { lastTargets = value.take(120) }

    fun startSession(context: Context) {
        sessionStartElapsed = SystemClock.elapsedRealtime()
        sessionStartCpuMs = Process.getElapsedCpuTime()
        sessionStartBattery = battery(context)
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val j = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("counters", "{}") ?: "{}")
            j.keys().forEach { k -> counters.merge(k, j.getLong(k), Long::plus) }
        }
    }

    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("counters", JSONObject(counters.toMap()).toString()).apply()
    }

    fun reset(context: Context) {
        counters.clear(); latencies.clear()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        startSession(context)
    }

    fun snapshot(): Map<String, Long> = counters.toSortedMap()

    fun percentile(key: String, p: Double): Long? {
        val q = latencies[key] ?: return null
        val values = synchronized(q) { q.toList() }.sorted()
        if (values.isEmpty()) return null
        return values[((values.size - 1) * p).toInt()]
    }

    private fun battery(context: Context): Int = runCatching {
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

    /** Exportação redigida: sem OCR, endereços, imagens ou IDs completos. */
    fun export(context: Context, buildLabel: String): JSONObject {
        val db = R3LabDb.get(context)
        val labels = db.labels(); val cov = db.coverage()
        val sessionMin = if (sessionStartElapsed > 0) (SystemClock.elapsedRealtime() - sessionStartElapsed) / 60_000.0 else 0.0
        return JSONObject().put("schema", "sr-r3-lab-diagnostic-1").put("build", buildLabel).put("api", android.os.Build.VERSION.SDK_INT)
            .put("device", android.os.Build.MODEL).put("mode", R3LabSettings.mode(context)).put("state", state).put("last_block", lastBlock)
            .put("counters", JSONObject(snapshot()))
            .put("latency_ms", JSONObject().apply {
                listOf("screenshot", "ocr", "frame_to_observation").forEach { k ->
                    put(k, JSONObject().put("p50", percentile(k, 0.5)).put("p95", percentile(k, 0.95)))
                }
            })
            .put("ground_truth", JSONObject().put("tp", labels.tp).put("fp", labels.fp).put("fn", labels.fn).put("unlabeled", labels.unlabeled)
                .put("precision", if (labels.tp + labels.fp > 0) labels.tp.toDouble() / (labels.tp + labels.fp) else JSONObject.NULL)
                .put("recall", if (labels.tp + labels.fn > 0) labels.tp.toDouble() / (labels.tp + labels.fn) else JSONObject.NULL))
            .put("evidence", JSONObject().put("accepted_offers", cov.acceptedOffers).put("accepted_with_ready_primary", cov.withReadyPrimary)
                .put("coverage", if (cov.acceptedOffers > 0) cov.withReadyPrimary.toDouble() / cov.acceptedOffers else JSONObject.NULL)
                .put("frames_ready", cov.framesReady).put("frames_failed", cov.framesFailed).put("frames_purged", cov.framesPurged))
            .put("consumption", JSONObject().put("session_minutes", sessionMin)
                .put("cpu_ms", Process.getElapsedCpuTime() - sessionStartCpuMs)
                .put("battery_start_pct", sessionStartBattery).put("battery_now_pct", battery(context)))
            .put("matrix_cell", if (R3LabSettings.mode(context) == R3LabSettings.MODE_D) "D (captura R3 + perfil R3)" else "C (captura R3 + parser oficial)")
            .put("privacy", "Sem texto de OCR, endereço, coordenada, imagem ou identificador completo.")
            .put("note", "Quadros, OCR e candidatos não são ofertas. Precisão/recall dependem dos rótulos manuais (TP/FP/FN).")
    }

    fun recentReasons(prefix: String, limit: Int = 8): List<Pair<String, Long>> =
        snapshot().filterKeys { it.startsWith(prefix) }.toList().sortedByDescending { it.second }.take(limit)

    @Suppress("unused") private fun json(values: List<Long>) = JSONArray(values)
}
