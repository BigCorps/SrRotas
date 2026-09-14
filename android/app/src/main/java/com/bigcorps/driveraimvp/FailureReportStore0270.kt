package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** RC3.3 — reportes manuais + automáticos, sem limite silencioso de 20 no export. */
object FailureReportStore0270 {
    private const val FILE_NAME = "failure_reports_0270.json"
    private const val MAX_MANUAL_REPORTS = 80
    private const val MAX_AUTOMATIC_REPORTS = 120
    private const val MAX_REPORTS = MAX_MANUAL_REPORTS + MAX_AUTOMATIC_REPORTS
    private const val BEFORE_EVENTS = 160
    private const val AFTER_EVENTS = 240
    private const val AFTER_WINDOW_MS = 12_000L

    data class MarkResult(
        val reportId: String,
        val numberInJourney: Int,
    )

    @Synchronized
    fun mark(context: Context, source: String): MarkResult {
        val app = context.applicationContext
        RadarHudTrace024.install(app)
        val journeyId = SettingsRepository(app).currentJourneyId().trim()
        val at = System.currentTimeMillis()
        val before = parseTrace(RadarHudTrace024.readRecent(BEFORE_EVENTS), maxAt = at)
        val reportId = UUID.randomUUID().toString()
        val root = load(app)
        val reports = root.optJSONArray("reports") ?: JSONArray()
        val kind = if (source.startsWith("auto:")) "automatic" else "manual"
        val report = JSONObject().apply {
            put("report_id", reportId)
            put("at", at)
            put("source", source.take(120))
            put("kind", kind)
            put("journey_id", journeyId)
            put("journey_prefix", journeyId.take(8))
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE)
            put("before", before)
            put("after", JSONArray())
            put("finalized", false)
        }
        reports.put(report)
        val trimmed = trim(reports)
        save(app, JSONObject().apply {
            put("schema", "sr-failure-reports-v2")
            put("reports", trimmed)
        })

        val number = (0 until trimmed.length())
            .mapNotNull { trimmed.optJSONObject(it) }
            .count {
                val storedKind = it.optString(
                    "kind",
                    if (it.optString("source").startsWith("auto:")) "automatic" else "manual",
                )
                it.optString("journey_id") == journeyId && storedKind == kind
            }

        Handler(Looper.getMainLooper()).postDelayed(
            { finalizeReport(app, reportId) },
            AFTER_WINDOW_MS,
        )
        return MarkResult(reportId, number)
    }

    @Synchronized
    fun snapshot(context: Context): JSONObject {
        val app = context.applicationContext
        RadarHudTrace024.install(app)
        completeOpenReports(app)
        val reports = load(app).optJSONArray("reports") ?: JSONArray()
        val currentJourney = SettingsRepository(app).currentJourneyId().trim()
        val chosenJourney = if (currentJourney.isNotBlank()) {
            currentJourney
        } else {
            (reports.length() - 1 downTo 0)
                .asSequence()
                .mapNotNull { reports.optJSONObject(it)?.optString("journey_id") }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

        val selected = (0 until reports.length())
            .mapNotNull { reports.optJSONObject(it) }
            .filter { chosenJourney.isBlank() || it.optString("journey_id") == chosenJourney }
        val manual = selected.count { it.optString("kind", if (it.optString("source").startsWith("auto:")) "automatic" else "manual") == "manual" }
        val automatic = selected.size - manual

        return JSONObject().apply {
            put("schema", "sr-failure-reports-v2")
            if (chosenJourney.isBlank()) put("journey_id", JSONObject.NULL) else put("journey_id", chosenJourney)
            put("report_count", selected.size)
            put("total_report_count", selected.size)
            put("exported_report_count", selected.size)
            put("manual_report_count", manual)
            put("automatic_report_count", automatic)
            put("retention_limit", MAX_REPORTS)
            put("reports", JSONArray().apply { selected.forEach { put(it) } })
            put("privacy", "Janelas técnicas anonimizadas; sem OCR bruto, screenshot, endereço ou coordenada.")
        }
    }

    @Synchronized
    private fun finalizeReport(context: Context, reportId: String) {
        val root = load(context)
        val reports = root.optJSONArray("reports") ?: return
        var changed = false
        for (index in 0 until reports.length()) {
            val report = reports.optJSONObject(index) ?: continue
            if (report.optString("report_id") != reportId) continue
            val at = report.optLong("at", 0L)
            if (at <= 0L) return
            report.put(
                "after",
                parseTrace(RadarHudTrace024.readRecent(AFTER_EVENTS), minAt = at, maxAt = at + AFTER_WINDOW_MS),
            )
            report.put("finalized", true)
            report.put("finalized_at", System.currentTimeMillis())
            changed = true
            break
        }
        if (changed) save(context, root)
    }

    @Synchronized
    private fun completeOpenReports(context: Context) {
        val root = load(context)
        val reports = root.optJSONArray("reports") ?: return
        val now = System.currentTimeMillis()
        var changed = false
        for (index in 0 until reports.length()) {
            val report = reports.optJSONObject(index) ?: continue
            if (report.optBoolean("finalized", false)) continue
            val at = report.optLong("at", 0L)
            if (at <= 0L) continue
            val end = minOf(now, at + AFTER_WINDOW_MS)
            report.put("after", parseTrace(RadarHudTrace024.readRecent(AFTER_EVENTS), minAt = at, maxAt = end))
            if (now >= at + AFTER_WINDOW_MS) {
                report.put("finalized", true)
                report.put("finalized_at", now)
            }
            changed = true
        }
        if (changed) save(context, root)
    }

    private fun parseTrace(lines: List<String>, minAt: Long? = null, maxAt: Long? = null): JSONArray = JSONArray().apply {
        lines.forEach { raw ->
            val event = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val at = event.optLong("at", -1L)
            if (minAt != null && at < minAt) return@forEach
            if (maxAt != null && at > maxAt) return@forEach
            put(event)
        }
    }

    private fun trim(source: JSONArray): JSONArray {
        val all = (0 until source.length()).mapNotNull { source.optJSONObject(it) }
        val manual = all.filter {
            it.optString("kind", if (it.optString("source").startsWith("auto:")) "automatic" else "manual") == "manual"
        }.takeLast(MAX_MANUAL_REPORTS)
        val automatic = all.filter {
            it.optString("kind", if (it.optString("source").startsWith("auto:")) "automatic" else "manual") == "automatic"
        }.takeLast(MAX_AUTOMATIC_REPORTS)
        return JSONArray().apply {
            (manual + automatic).sortedBy { it.optLong("at", 0L) }.forEach { put(it) }
        }
    }

    private fun load(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return JSONObject().apply {
            put("schema", "sr-failure-reports-v2")
            put("reports", JSONArray())
        }
        return runCatching { JSONObject(file.readText()) }.getOrElse {
            JSONObject().apply {
                put("schema", "sr-failure-reports-v2")
                put("reports", JSONArray())
            }
        }
    }

    private fun save(context: Context, root: JSONObject) {
        runCatching { File(context.filesDir, FILE_NAME).writeText(root.toString()) }
    }
}
