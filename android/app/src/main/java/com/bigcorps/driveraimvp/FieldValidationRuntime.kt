package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.max
import kotlin.math.round


data class FieldPerformanceSample(
    val running: Boolean,
    val startedAt: String,
    val elapsedMinutes: Double,
    val processCpuMinutes: Double,
    val cpuToElapsedPct: Double,
    val processContinuous: Boolean,
    val startBatteryPct: Int?,
    val currentBatteryPct: Int?,
    val batteryDropPct: Int?,
    val batteryDropPerHour: Double?,
    val startPssMb: Double,
    val currentPssMb: Double,
    val thermalStatus: String,
    val batteryTemperatureC: Double?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("running", running)
        put("started_at", startedAt)
        put("elapsed_minutes", elapsedMinutes)
        put("process_cpu_minutes", processCpuMinutes)
        put("cpu_to_elapsed_pct", cpuToElapsedPct)
        put("process_continuous", processContinuous)
        putNullable("start_battery_pct", startBatteryPct)
        putNullable("current_battery_pct", currentBatteryPct)
        putNullable("battery_drop_pct", batteryDropPct)
        putNullable("battery_drop_per_hour", batteryDropPerHour)
        put("start_pss_mb", startPssMb)
        put("current_pss_mb", currentPssMb)
        put("thermal_status", thermalStatus)
        putNullable("battery_temperature_c", batteryTemperatureC)
    }
}

object FieldValidationSession {
    private const val PREFS = "sr_rotas_field_validation_019"
    private const val RUNNING = "perf_running"
    private const val STARTED_AT = "perf_started_at"
    private const val START_ELAPSED = "perf_start_elapsed"
    private const val START_CPU = "perf_start_cpu"
    private const val START_PID = "perf_start_pid"
    private const val START_BATTERY = "perf_start_battery"
    private const val START_PSS_KB = "perf_start_pss_kb"
    private const val LAST = "perf_last_json"

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(RUNNING, false)

    fun start(context: Context): FieldPerformanceSample {
        if (isRunning(context)) {
            return current(context) ?: error("field_session_missing")
        }

        val now = Instant.now().toString()
        prefs(context).edit()
            .putBoolean(RUNNING, true)
            .putString(STARTED_AT, now)
            .putLong(START_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(START_CPU, android.os.Process.getElapsedCpuTime())
            .putInt(START_PID, android.os.Process.myPid())
            .putInt(START_BATTERY, batteryPct(context) ?: -1)
            .putInt(START_PSS_KB, pssKb())
            .apply()

        return current(context) ?: error("field_session_start_failed")
    }

    fun current(context: Context): FieldPerformanceSample? {
        val p = prefs(context)
        if (!p.getBoolean(RUNNING, false)) return null

        return calculate(
            context = context,
            running = true,
            startedAt = p.getString(STARTED_AT, "") ?: "",
            startElapsed = p.getLong(START_ELAPSED, 0L),
            startCpu = p.getLong(START_CPU, 0L),
            startPid = p.getInt(START_PID, -1),
            startBattery = p.getInt(START_BATTERY, -1).takeIf { it in 0..100 },
            startPssKb = p.getInt(START_PSS_KB, 0),
        )
    }

    fun finish(context: Context): FieldPerformanceSample? {
        val p = prefs(context)
        if (!p.getBoolean(RUNNING, false)) return last(context)

        val sample = calculate(
            context = context,
            running = false,
            startedAt = p.getString(STARTED_AT, "") ?: "",
            startElapsed = p.getLong(START_ELAPSED, 0L),
            startCpu = p.getLong(START_CPU, 0L),
            startPid = p.getInt(START_PID, -1),
            startBattery = p.getInt(START_BATTERY, -1).takeIf { it in 0..100 },
            startPssKb = p.getInt(START_PSS_KB, 0),
        )

        if (sample != null) {
            p.edit()
                .putBoolean(RUNNING, false)
                .putString(LAST, sample.toJson().toString())
                .apply()
        }
        return sample
    }

    fun last(context: Context): FieldPerformanceSample? {
        val raw = prefs(context).getString(LAST, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            FieldPerformanceSample(
                running = o.optBoolean("running", false),
                startedAt = o.optString("started_at"),
                elapsedMinutes = o.optDouble("elapsed_minutes", 0.0),
                processCpuMinutes = o.optDouble("process_cpu_minutes", 0.0),
                cpuToElapsedPct = o.optDouble("cpu_to_elapsed_pct", 0.0),
                processContinuous = o.optBoolean("process_continuous", true),
                startBatteryPct = o.intOrNull("start_battery_pct"),
                currentBatteryPct = o.intOrNull("current_battery_pct"),
                batteryDropPct = o.intOrNull("battery_drop_pct"),
                batteryDropPerHour = o.doubleOrNull("battery_drop_per_hour"),
                startPssMb = o.optDouble("start_pss_mb", 0.0),
                currentPssMb = o.optDouble("current_pss_mb", 0.0),
                thermalStatus = o.optString("thermal_status", "unknown"),
                batteryTemperatureC = o.doubleOrNull("battery_temperature_c"),
            )
        }.getOrNull()
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .remove(RUNNING)
            .remove(STARTED_AT)
            .remove(START_ELAPSED)
            .remove(START_CPU)
            .remove(START_PID)
            .remove(START_BATTERY)
            .remove(START_PSS_KB)
            .remove(LAST)
            .apply()
    }

    private fun calculate(
        context: Context,
        running: Boolean,
        startedAt: String,
        startElapsed: Long,
        startCpu: Long,
        startPid: Int,
        startBattery: Int?,
        startPssKb: Int,
    ): FieldPerformanceSample? {
        if (startElapsed <= 0L || startCpu < 0L) return null

        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedMs = max(1L, nowElapsed - startElapsed)
        val processContinuous = startPid > 0 && startPid == android.os.Process.myPid()
        val cpuMs = if (processContinuous) {
            max(0L, android.os.Process.getElapsedCpuTime() - startCpu)
        } else {
            0L
        }
        val currentBattery = batteryPct(context)
        val drop = if (startBattery != null && currentBattery != null) {
            (startBattery - currentBattery).coerceAtLeast(0)
        } else {
            null
        }

        val elapsedHours = elapsedMs.toDouble() / 3_600_000.0
        val dropPerHour = if (drop != null && elapsedHours >= (5.0 / 60.0)) {
            r2(drop / elapsedHours)
        } else {
            null
        }

        return FieldPerformanceSample(
            running = running,
            startedAt = startedAt,
            elapsedMinutes = r2(elapsedMs.toDouble() / 60_000.0),
            processCpuMinutes = r2(cpuMs.toDouble() / 60_000.0),
            cpuToElapsedPct = r2(cpuMs.toDouble() / elapsedMs.toDouble() * 100.0),
            processContinuous = processContinuous,
            startBatteryPct = startBattery,
            currentBatteryPct = currentBattery,
            batteryDropPct = drop,
            batteryDropPerHour = dropPerHour,
            startPssMb = r2(startPssKb.toDouble() / 1024.0),
            currentPssMb = r2(pssKb().toDouble() / 1024.0),
            thermalStatus = thermalStatus(context),
            batteryTemperatureC = batteryTemperatureC(context),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pssKb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.coerceAtLeast(0)
    }

    private fun batteryPct(context: Context): Int? {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
    }

    private fun batteryTemperatureC(context: Context): Double? {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null
        val raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (raw == Int.MIN_VALUE) null else r2(raw / 10.0)
    }

    private fun thermalStatus(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unavailable"
        val status = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
            ?: return "unknown"
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
    }

    private fun r2(value: Double) = round(value * 100.0) / 100.0

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
}

object FieldValidationManualStore {
    private const val PREFS = "sr_rotas_field_manual_019"

    fun isChecked(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("check_$id", false)

    fun setChecked(context: Context, id: String, checked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("check_$id", checked)
            .apply()
    }

    fun completed(context: Context): Int =
        FieldValidationManualChecklist.items.count { isChecked(context, it.id) }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

object FieldValidationCollector {
    fun collect(context: Context): FieldValidationFacts {
        val app = context.applicationContext
        val store = LocalStore.get(app)
        val db = store.readableDatabase

        fun count(sql: String, args: Array<String>? = null): Int =
            runCatching {
                db.rawQuery(sql, args).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)

        val importSummary = runCatching {
            HistoricalImportStore.get(app).summary()
        }.getOrNull()

        val regional = runCatching {
            LocalRegionalIntelligence.build(app, 30)
        }.getOrNull()

        val minSamples = RegionalProbability.MIN_PROBABILITY_SAMPLES
        val guardrailViolations = regional?.topRegions?.sumOf { row ->
            listOf(row.p5, row.p10, row.p15).count { h ->
                h.eligibleIntervals < minSamples && h.probabilityPct != null
            }
        } ?: 0

        val settings = SettingsRepository(app).load()

        return FieldValidationFacts(
            offers = count("select count(*) from local_offers"),
            exclusiveOffers = count("select count(*) from local_offers where offer_type='exclusive'"),
            radarOffers = count("select count(*) from local_offers where offer_type='radar'"),
            offersWithContext = count(
                "select count(*) from local_offer_context where pickup_label is not null or destination_label is not null",
            ),
            offersWithDestinationCell = count(
                "select count(*) from local_offer_context where destination_cell is not null",
            ),
            resolvedContexts = count(
                "select count(*) from local_offer_context where geocode_status in ('resolved','partial')",
            ),
            closedExposures = count(
                "select count(*) from local_zone_exposure where ended_at is not null",
            ),
            journeyEvents = count("select count(*) from local_journey_events"),
            rideOutcomes = count("select count(*) from local_ride_outcomes"),
            completedRides = count(
                "select count(*) from local_ride_outcomes where status='COMPLETED'",
            ),
            importedOffers = importSummary?.importedOffers ?: 0,
            duplicateImports = importSummary?.duplicateOffers ?: 0,
            failedImports = importSummary?.failedFiles ?: 0,
            costConfigured = runCatching {
                CostProfileStore.get(app).load() != null
            }.getOrDefault(false),
            offersWithCostSnapshot = count(
                "select count(*) from local_offers where cost_per_km_used is not null",
            ),
            probabilityReadyCells = regional?.dataQuality?.probabilityReadyCells ?: 0,
            probabilityGuardrailViolations = guardrailViolations,
            pendingOffers = store.pendingOfferCount(),
            pendingContexts = count(
                "select count(*) from local_offer_context where sync_state=0",
            ),
            pendingJourneyEvents = count(
                "select count(*) from local_journey_events where sync_state=0",
            ),
            pendingRideOutcomes = count(
                "select count(*) from local_ride_outcomes where sync_state=0",
            ),
            pendingExposures = count(
                "select count(*) from local_zone_exposure where sync_state=0 and ended_at is not null",
            ),
            online = ConnectivityState.isOnline(app),
            paired = settings.deviceToken.isNotBlank(),
            overlayAllowed = Settings.canDrawOverlays(app),
            coarseLocationAllowed = app.checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
            pendingCrash = BetaTelemetry.hasPendingCrash(app),
        )
    }
}

object FieldValidationReporter {
    fun build(context: Context): String {
        val facts = FieldValidationCollector.collect(context)
        val checks = FieldValidationAssessment.evaluate(facts)
        val performance = FieldValidationSession.current(context)
            ?: FieldValidationSession.last(context)
        val manualCompleted = FieldValidationManualStore.completed(context)

        return JSONObject().apply {
            put("schema", "sr-rotas-field-validation-v0.19")
            put("generated_at", Instant.now().toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE)
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER.take(80))
                put("model", Build.MODEL.take(100))
                put("android_sdk", Build.VERSION.SDK_INT)
                put("android_release", Build.VERSION.RELEASE ?: "")
            })
            put("facts", facts.toJson())
            put("checks", JSONArray().apply {
                checks.forEach { check ->
                    put(JSONObject().apply {
                        put("id", check.id)
                        put("title", check.title)
                        put("status", check.status.name.lowercase(Locale.ROOT))
                        put("detail", check.detail)
                    })
                }
            })
            put("manual", JSONObject().apply {
                put("completed", manualCompleted)
                put("total", FieldValidationManualChecklist.items.size)
                put("items", JSONArray().apply {
                    FieldValidationManualChecklist.items.forEach { item ->
                        put(JSONObject().apply {
                            put("id", item.id)
                            put("checked", FieldValidationManualStore.isChecked(context, item.id))
                        })
                    }
                })
            })
            put("performance", performance?.toJson() ?: JSONObject.NULL)
            put(
                "privacy_note",
                "Relatório 0.19 agregado. Não inclui token, e-mail, screenshot, OCR bruto, endereço textual nem coordenadas exatas.",
            )
        }.toString(2)
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Validação de campo Sr. Rotas ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, build(context))
        }
        val chooser = Intent.createChooser(send, "Compartilhar validação 0.19")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun FieldValidationFacts.toJson(): JSONObject = JSONObject().apply {
        put("offers", offers)
        put("exclusive_offers", exclusiveOffers)
        put("radar_offers", radarOffers)
        put("offers_with_context", offersWithContext)
        put("offers_with_destination_cell", offersWithDestinationCell)
        put("resolved_contexts", resolvedContexts)
        put("closed_exposures", closedExposures)
        put("journey_events", journeyEvents)
        put("ride_outcomes", rideOutcomes)
        put("completed_rides", completedRides)
        put("imported_offers", importedOffers)
        put("duplicate_imports", duplicateImports)
        put("failed_imports", failedImports)
        put("cost_configured", costConfigured)
        put("offers_with_cost_snapshot", offersWithCostSnapshot)
        put("probability_ready_cells", probabilityReadyCells)
        put("probability_guardrail_violations", probabilityGuardrailViolations)
        put("pending_total", pendingTotal)
        put("pending_offers", pendingOffers)
        put("pending_contexts", pendingContexts)
        put("pending_journey_events", pendingJourneyEvents)
        put("pending_ride_outcomes", pendingRideOutcomes)
        put("pending_exposures", pendingExposures)
        put("online", online)
        put("paired", paired)
        put("overlay_allowed", overlayAllowed)
        put("coarse_location_allowed", coarseLocationAllowed)
        put("pending_crash", pendingCrash)
    }
}
