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
 * 0.21.1 / 0.27 — seleção exclusivamente para relatórios.
 *
 * 0.27 corrige a limitação antiga de uma única seleção por jornada:
 * cada oferta mantém sua própria marcação independente.
 *
 * Não altera RideOperationalStatus, JourneyOperationalState, exposição,
 * MediaProjection, OCR, CardStabilizer ou Offer Engine.
 */
object ReportSelection0211 {
    private const val PREFS = "sr_rotas_report_selection_0211"
    private const val SET_PREFIX = "selected_set_"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun scopeSuffix(offer: RideOffer): String =
        offer.journeyId?.takeIf(String::isNotBlank) ?: "standalone"

    private fun legacyScope(offer: RideOffer): String =
        "selected_${scopeSuffix(offer)}"

    private fun setScope(offer: RideOffer): String =
        "$SET_PREFIX${scopeSuffix(offer)}"

    private fun loadSet(
        context: Context,
        offer: RideOffer,
    ): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = setScope(offer)
        val current = prefs.getStringSet(key, null)?.toMutableSet()
        if (current != null) return current

        // Migração transparente da versão que guardava somente um localId.
        val legacy = prefs.getString(legacyScope(offer), "").orEmpty()
        return linkedSetOf<String>().apply {
            if (legacy.isNotBlank()) add(legacy)
        }
    }

    fun isSelected(context: Context, offer: RideOffer): Boolean =
        loadSet(context, offer).contains(offer.localId)

    fun toggle(
        context: Context,
        offer: RideOffer,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = setScope(offer)
        val selectedIds = loadSet(app, offer)
        val selected =
            if (selectedIds.contains(offer.localId)) {
                selectedIds.remove(offer.localId)
                false
            } else {
                selectedIds.add(offer.localId)
                true
            }

        prefs.edit()
            .putStringSet(key, selectedIds.toSet())
            .remove(legacyScope(offer))
            .putBoolean("pending_${offer.localId}", selected)
            .apply()

        onDone?.invoke(selected)
        sync(app, offer, selected)
    }

    fun selectedLocalIds(
        context: Context,
        journeyId: String?,
    ): Set<String> {
        val suffix = journeyId?.takeIf(String::isNotBlank) ?: "standalone"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("$SET_PREFIX$suffix", null)
        if (current != null) return current.toSet()
        return prefs.getString("selected_$suffix", null)
            ?.takeIf(String::isNotBlank)
            ?.let(::setOf)
            ?: emptySet()
    }

    /** Compatibilidade com caller antigo; a API nova é selectedLocalIds(). */
    fun selectedLocalId(context: Context, journeyId: String?): String? =
        selectedLocalIds(context, journeyId).firstOrNull()

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
        pending.forEach { (id, selected) ->
            offers[id]?.let { sync(app, it, selected) }
        }
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
            LocalLog.append(context, "Seleção de relatório 0.27 pendente: ${last?.message}")
        }
    }

    private fun request(
        method: String,
        url: String,
        body: JSONObject,
        token: String,
    ): String {
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
        val text = stream?.use {
            BufferedReader(InputStreamReader(it)).readText()
        }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status $text")
        return text
    }
}
