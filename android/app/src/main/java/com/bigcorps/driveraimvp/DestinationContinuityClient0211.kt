package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 0.21.1 — consulta assíncrona da continuidade no destino.
 *
 * Não participa do OCR/parser/verdict/dedupe. É disparada somente depois de a
 * oferta válida ter sido persistida e o Context Engine resolver o destino.
 */
object DestinationContinuityClient0211 {
    private data class Cached(
        val value: DestinationContinuityInsight0211,
        val savedAt: Long,
    )

    private const val MAX_CACHE = 80
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val REGIONAL_CACHE_TTL_MS = 10 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, Cached>()
    private val regionalCache = ConcurrentHashMap<String, Cached>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun get(localOfferId: String): DestinationContinuityInsight0211? {
        val found = cache[localOfferId] ?: return null
        if (System.currentTimeMillis() - found.savedAt > CACHE_TTL_MS) {
            cache.remove(localOfferId)
            return null
        }
        return found.value
    }

    fun fingerprint(localOfferId: String): String {
        val value = get(localOfferId) ?: return "pending"
        return listOf(
            value.kind,
            value.level,
            value.probabilityPct?.toString() ?: "-",
            value.samples,
            value.source,
        ).joinToString(":")
    }

    fun request(
        context: Context,
        offer: RideOffer,
        callback: (Result<DestinationContinuityInsight0211>) -> Unit = {},
    ) {
        val current = get(offer.localId)
        if (current != null) {
            main.post { callback(Result.success(current)) }
            return
        }
        val ctx = offer.context
        val eta =
            ctx?.estimatedArrivalAt
                ?: OfferContextEngine.estimatedArrivalAt(
                    offer.observedAt,
                    offer.totalMinutes,
                )
                ?: run {
                    main.post { callback(Result.failure(IllegalStateException("ETA indisponível"))) }
                    return
                }
        val cell = ctx?.destinationCell.orEmpty()
        val destination =
            OfferContextGeocoder.regionLabelForCell(ctx?.destinationCell)
                ?: ctx?.destinationLabel.orEmpty()
        if (cell.isBlank() && destination.isBlank()) {
            main.post { callback(Result.failure(IllegalStateException("Destino indisponível"))) }
            return
        }

        val regionalKey =
            (cell.ifBlank { destination.lowercase() }) + "|" + eta.take(13)
        regionalCache[regionalKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.savedAt <= REGIONAL_CACHE_TTL_MS) {
                cache[offer.localId] = Cached(cached.value, System.currentTimeMillis())
                main.post { callback(Result.success(cached.value)) }
                return
            } else {
                regionalCache.remove(regionalKey)
            }
        }

        if (!inFlight.add(offer.localId)) return

        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) {
                    "Conta desconectada"
                }

                val params = mutableListOf("eta=${enc(eta)}")
                if (cell.isNotBlank()) params += "cell=${enc(cell)}"
                if (destination.isNotBlank()) params += "destination=${enc(destination)}"

                val raw = requestUrl(
                    "${settings.backendUrl.trimEnd('/')}/api/v1/intelligence/destination?${params.joinToString("&")}",
                    settings.deviceToken,
                )
                val root = JSONObject(raw)
                val display = root.optJSONObject("display") ?: error("Resposta sem display")
                DestinationContinuityInsight0211(
                    kind = display.optString("kind", "insufficient"),
                    level = display.optString("level", "insufficient"),
                    probabilityPct = optDouble(display, "probability_pct"),
                    samples = display.optInt("samples", 0).coerceAtLeast(0),
                    confidence = display.optString("confidence", "insufficient"),
                    source = display.optString("source", "none"),
                    regionLabel = display.optString("region_label", "").takeIf(String::isNotBlank),
                    wording = display.optString(
                        "wording",
                        "Tendência histórica; não garante nova corrida.",
                    ),
                )
            }

            result.onSuccess { value ->
                val now = System.currentTimeMillis()
                cache[offer.localId] = Cached(value, now)
                regionalCache[regionalKey] = Cached(value, now)
                prune()
            }
            inFlight.remove(offer.localId)
            main.post { callback(result) }
        }
    }

    private fun prune() {
        if (cache.size <= MAX_CACHE) return
        cache.entries
            .sortedBy { it.value.savedAt }
            .take(cache.size - MAX_CACHE)
            .forEach { cache.remove(it.key) }
        regionalCache.entries
            .filter { System.currentTimeMillis() - it.value.savedAt > REGIONAL_CACHE_TTL_MS }
            .forEach { regionalCache.remove(it.key) }
    }

    private fun requestUrl(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status")
        return text
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun optDouble(o: JSONObject, key: String): Double? =
        if (!o.has(key) || o.isNull(key)) null
        else o.optDouble(key).takeIf { !it.isNaN() }
}
