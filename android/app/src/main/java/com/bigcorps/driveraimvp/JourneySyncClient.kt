package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object JourneySyncClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val flushing = AtomicBoolean(false)

    fun flush(context: Context) {
        val app = context.applicationContext
        if (!flushing.compareAndSet(false, true)) return
        executor.execute {
            try {
                val settings = SettingsRepository(app).load()
                if (settings.deviceToken.isBlank() || settings.backendUrl.isBlank()) return@execute
                val store = LocalStore.get(app)
                store.pendingJourneyEvents(80).forEach { event ->
                    if (post(app, JSONObject().apply {
                            put("action", "state_event")
                            put("client_event_id", event.id)
                            put("journey_id", event.journeyId)
                            put("event_type", event.eventType)
                            put("state", event.state.name)
                            put("occurred_at", event.occurredAt)
                        })) store.markJourneyEventSynced(event.id)
                }
                store.pendingRideOutcomes(100).forEach { outcome ->
                    if (post(app, JSONObject().apply {
                            put("action", "ride_outcome")
                            put("local_offer_id", outcome.localOfferId)
                            put("journey_id", outcome.journeyId)
                            put("status", outcome.status.name)
                            putNullable("started_at", outcome.startedAt)
                            putNullable("completed_at", outcome.completedAt)
                            putNullable("cancelled_at", outcome.cancelledAt)
                            putNullable("corrected_at", outcome.correctedAt)
                            put("source", outcome.source)
                            put("revision", outcome.revision)
                        })) store.markRideOutcomeSynced(outcome.localOfferId, outcome.revision)
                }
                store.pendingExposures(100).forEach { exposure ->
                    if (post(app, JSONObject().apply {
                            put("action", "exposure")
                            put("client_exposure_id", exposure.id)
                            put("journey_id", exposure.journeyId)
                            put("cell", exposure.cell)
                            put("started_at", exposure.startedAt)
                            put("ended_at", exposure.endedAt)
                            put("duration_seconds", exposure.durationSeconds ?: 0L)
                            put("close_reason", exposure.closeReason ?: "unknown")
                            putNullable("next_offer_local_id", exposure.nextOfferLocalId)
                            putNullable("location_accuracy_m", exposure.locationAccuracyM)
                        })) store.markExposureSynced(exposure.id)
                }
            } finally {
                flushing.set(false)
            }
        }
    }

    private fun post(context: Context, body: JSONObject): Boolean {
        val settings = SettingsRepository(context).load()
        return runCatching {
            val conn = (URL("${settings.backendUrl.trimEnd('/')}/api/v1/journeys").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
                setRequestProperty("Authorization", "Bearer ${settings.deviceToken}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            stream?.use { BufferedReader(InputStreamReader(it)).readText() }
            conn.disconnect()
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            true
        }.onFailure { LocalLog.append(context, "Sync 0.15 falhou (${body.optString("action")}): ${it.message}") }.getOrDefault(false)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
