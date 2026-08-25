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
 * 0.21.1 — seleção exclusivamente para relatórios.
 *
 * Não altera RideOperationalStatus, JourneyOperationalState, exposição,
 * MediaProjection, OCR, CardStabilizer ou Offer Engine.
 */
object ReportSelection0211 {
    private const val PREFS = "sr_rotas_report_selection_0211"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun scope(offer: RideOffer): String =
        "selected_${offer.journeyId?.takeIf(String::isNotBlank) ?: "standalone"}"

    fun isSelected(context: Context, offer: RideOffer): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(scope(offer), "") == offer.localId

    fun toggle(
        context: Context,
        offer: RideOffer,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = scope(offer)
        val previous = prefs.getString(key, "").orEmpty()
        val selected = previous != offer.localId

        prefs.edit().apply {
            if (selected) {
                putString(key, offer.localId)
                if (previous.isNotBlank() && previous != offer.localId) remove("pending_$previous")
            } else {
                remove(key)
            }
            putBoolean("pending_${offer.localId}", selected)
        }.apply()

        // Estado local é imediato; a sincronização é best-effort e não pode
        // bloquear a leitura de novas ofertas.
        onDone?.invoke(selected)
        sync(app, offer, selected)
    }

    fun selectedLocalId(context: Context, journeyId: String?): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("selected_${journeyId?.takeIf(String::isNotBlank) ?: "standalone"}", null)
            ?.takeIf(String::isNotBlank)

    fun flush(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.all
            .filterKeys { it.startsWith("pending_") }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix("pending_")
                val selected = value as? Boolean ?: return@mapNotNull null
                id to selected
            }
        if (pending.isEmpty()) return
        val offers = LocalStore.get(app).recentOffers(300).associateBy { it.localId }
        pending.forEach { (id, selected) -> offers[id]?.let { sync(app, it, selected) } }
    }

    private fun sync(context: Context, offer: RideOffer, selected: Boolean) {
        executor.execute {
            val settings = SettingsRepository(context).load()
            if (settings.backendUrl.isBlank() || settings.deviceToken.isBlank()) return@execute
            val body = JSONObject().apply {
                put("local_offer_id", offer.localId)
                put("selected", selected)
            }
            var last: Throwable? = null
            repeat(3) { attempt ->
                val result = runCatching {
                    request(
                        "POST",
                        "${settings.backendUrl.trimEnd('/')}/api/v1/offers/report-selection",
                        body,
                        settings.deviceToken,
                    )
                }
                if (result.isSuccess) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove("pending_${offer.localId}").apply()
                    return@execute
                }
                last = result.exceptionOrNull()
                if (attempt < 2) Thread.sleep(if (attempt == 0) 1500L else 3500L)
            }
            LocalLog.append(context, "Seleção de relatório 0.21.1 pendente: ${last?.message}")
        }
    }

    private fun request(method: String, url: String, body: JSONObject, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7000
            readTimeout = 9000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status $text")
        return text
    }
}
