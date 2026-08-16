package com.srrotas.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object BackendClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val flushRunning = AtomicBoolean(false)

    fun pair(context: Context, backendUrl: String, pairingCode: String, onResult: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(backendUrl.isNotBlank()) { "Informe a URL do backend." }
                require(pairingCode.isNotBlank()) { "Informe o código de pareamento." }
                val response = request("POST", "${backendUrl.trim().trimEnd('/')}/api/v1/pair", JSONObject().apply {
                    put("code", pairingCode.trim()); put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                })
                val token = JSONObject(response).optString("device_token")
                require(token.isNotBlank()) { "Backend não retornou device_token." }
                SettingsRepository(context).saveDeviceToken(token)
                token
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun ask(context: Context, question: String, onResult: (Result<String>) -> Unit) {
        val settings = SettingsRepository(context).load()
        executor.execute {
            val result = runCatching {
                require(settings.backendUrl.isNotBlank()) { "Configure a URL do backend." }
                require(settings.deviceToken.isNotBlank()) { "Pareie o aparelho primeiro." }
                require(question.trim().length >= 3) { "Digite uma pergunta." }
                val response = request("POST", "${settings.backendUrl.trimEnd('/')}/api/v1/ask", JSONObject().apply { put("question", question.trim()) }, settings.deviceToken)
                JSONObject(response).optString("answer").ifBlank { "O backend não retornou uma resposta." }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun sendOffer(context: Context, offer: RideOffer) { val app=context.applicationContext; executor.execute { sendOfferNow(app, offer) } }

    fun flushPendingOffers(context: Context) {
        val app = context.applicationContext
        if (!flushRunning.compareAndSet(false, true)) return
        executor.execute {
            try {
                val settings = SettingsRepository(app).load()
                if (settings.backendUrl.isBlank() || settings.deviceToken.isBlank()) return@execute
                val store = LocalStore.get(app)
                store.pendingJourneyStarts(20).forEach { syncJourneyStartNow(app, it) }
                store.pendingOffers(50).forEach { sendOfferNow(app, it) }
                store.pendingJourneyEnds(20).forEach { syncJourneyEndNow(app, it) }
            } finally { flushRunning.set(false) }
        }
    }

    fun startJourney(context: Context, journey: JourneyRecord) {
        val app=context.applicationContext
        executor.execute { syncJourneyStartNow(app, journey) }
    }

    fun endJourney(context: Context, summary: JourneySummary) {
        val app=context.applicationContext
        executor.execute {
            syncJourneyStartNow(app, summary.journey)
            syncJourneyEndNow(app, summary.journey)
        }
    }

    private fun syncJourneyStartNow(context: Context, journey: JourneyRecord): Boolean {
        val s=SettingsRepository(context).load(); if(s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/journeys", JSONObject().apply {
                put("action","start"); put("journey_id",journey.id); put("platform",journey.platform); put("started_at",journey.startedAt)
            }, s.deviceToken)
            LocalStore.get(context).markJourneyStartSynced(journey.id); true
        }.onFailure { LocalLog.append(context,"Falha ao sincronizar início da jornada: ${it.message}") }.getOrDefault(false)
    }

    private fun syncJourneyEndNow(context: Context, journey: JourneyRecord): Boolean {
        if(journey.endedAt.isNullOrBlank()) return false
        val s=SettingsRepository(context).load(); if(s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/journeys", JSONObject().apply {
                put("action","end"); put("journey_id",journey.id); put("ended_at",journey.endedAt); put("end_reason",journey.endReason ?: "user_or_system")
            }, s.deviceToken)
            LocalStore.get(context).markJourneyEndSynced(journey.id); true
        }.onFailure { LocalLog.append(context,"Falha ao sincronizar fim da jornada: ${it.message}") }.getOrDefault(false)
    }

    fun syncPreferences(context: Context) {
        val app=context.applicationContext
        executor.execute {
            val s=SettingsRepository(app).load(); if(s.backendUrl.isBlank() || s.deviceToken.isBlank()) return@execute
            runCatching {
                request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/preferences", JSONObject().apply {
                    put("min_per_km",s.minPerKm); put("red_per_km_below",s.redPerKmBelow); put("min_per_hour",s.minPerHour); put("red_per_hour_below",s.redPerHourBelow); put("good_rating_from",s.goodRatingFrom); put("red_rating_below",s.redRatingBelow); put("min_per_minute",s.minPerMinute); put("red_per_minute_below",s.redPerMinuteBelow); put("min_fare",s.minFare); put("max_pickup_km",s.maxPickupKm); put("min_profit",s.minProfit); put("min_profit_per_hour",s.minProfitPerHour); put("red_profit_per_hour_below",s.redProfitPerHourBelow); put("min_profit_percent",s.minProfitPercent); put("red_profit_percent_below",s.redProfitPercentBelow); put("cost_per_km",s.costPerKm); put("timezone","America/Sao_Paulo")
                }, s.deviceToken)
            }.onFailure { LocalLog.append(app,"Falha ao sincronizar estratégia: ${it.message}") }
        }
    }

    private fun sendOfferNow(context: Context, offer: RideOffer): Boolean {
        val s=SettingsRepository(context).load(); if(s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/offers", offer.toJson(), s.deviceToken)
            LocalStore.get(context).markOfferSynced(offer.localId); true
        }.onFailure { LocalLog.append(context,"Falha ao enviar oferta: ${it.message}") }.getOrDefault(false)
    }

    private fun request(method: String, url: String, body: JSONObject, bearer: String? = null): String {
        val connection=(URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod=method; connectTimeout=8000; readTimeout=12000; doOutput=true
            setRequestProperty("Content-Type","application/json; charset=utf-8"); setRequestProperty("Accept","application/json"); setRequestProperty("X-SrRotas-App-Version",BuildConfig.VERSION_NAME)
            if(!bearer.isNullOrBlank()) setRequestProperty("Authorization","Bearer $bearer")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status=connection.responseCode
        val stream=if(status in 200..299) connection.inputStream else connection.errorStream
        val text=stream?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        connection.disconnect(); if(status !in 200..299) error("HTTP $status: $text"); return text
    }
}
