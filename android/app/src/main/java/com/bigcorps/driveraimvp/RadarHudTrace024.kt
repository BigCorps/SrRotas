package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Rastro local do caminho Radar -> HUD.
 *
 * Não envia telemetria para o backend e não grava endereços/OCR bruto.
 * Guarda apenas estágio, contagens, motivos e fingerprints não reversíveis.
 */
object RadarHudTrace024 {
    private const val FILE_NAME = "radar_hud_024.ndjson"
    private const val MAX_BYTES = 320 * 1024L
    private const val KEEP_LINES = 450

    enum class Stage {
        FRAME_CAPTURED,
        OCR_OK,
        SPATIAL_DIAGNOSTIC,
        SCREEN_CLASSIFIED,
        PARSED,
        PARSE_REJECTED,
        DISPATCH_INPUT,
        DEDUPE_ACCEPT,
        DEDUPE_REJECT_EXACT,
        DEDUPE_REJECT_FUZZY,
        OCR_FAIL,
    }

    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun record(
        stage: Stage,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val context = appContext ?: return
        val json = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("stage", stage.name)

        fields.forEach { (key, value) ->
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is Number, is Boolean, is String -> json.put(key, value)
                else -> json.put(key, value.toString())
            }
        }

        synchronized(this) {
            val file = File(context.filesDir, FILE_NAME)
            trimIfNeeded(file)
            runCatching {
                file.appendText(json.toString() + "\n")
            }
        }
    }

    fun recordOcr(
        chars: Int,
        blocks: Int,
        rawText: String,
    ) = record(
        Stage.OCR_OK,
        mapOf(
            "chars" to chars,
            "blocks" to blocks,
            "text_fp" to fingerprint(rawText),
        ),
    )

    fun recordRoute(
        platform: String?,
        candidate: Boolean,
        ownApp: Boolean,
        reason: String,
        offers: Int,
    ) = record(
        Stage.SCREEN_CLASSIFIED,
        mapOf(
            "platform" to platform.orEmpty(),
            "candidate" to candidate,
            "own_app" to ownApp,
            "reason" to reason.take(120),
            "offers" to offers,
        ),
    )

    fun recordOffer(
        stage: Stage,
        offer: RideOffer,
        reason: String? = null,
    ) = record(
        stage,
        buildMap {
            put("offer_fp", offerFingerprint(offer))
            put("platform", offer.platform.take(20))
            put("type", offer.offerType.take(32))
            put("service", offer.serviceType.take(32))
            put("fare_cents", (offer.fare * 100.0).toInt())
            put("pickup_dm", offer.pickupKm?.let { (it * 10.0).toInt() })
            put("trip_dm", offer.tripKm?.let { (it * 10.0).toInt() })
            put("total_dm", offer.totalKm?.let { (it * 10.0).toInt() })
            put("confidence", offer.confidence)
            reason?.let { put("reason", it.take(120)) }
        },
    )

    fun readRecent(limit: Int = 120): List<String> {
        val context = appContext ?: return emptyList()
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().takeLast(limit.coerceIn(1, 500))
        }.getOrDefault(emptyList())
    }

    fun clear() {
        val context = appContext ?: return
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
        }
    }

    private fun offerFingerprint(offer: RideOffer): String =
        fingerprint(
            listOf(
                offer.platform.lowercase(Locale.ROOT),
                offer.offerType,
                offer.serviceType,
                "%.2f".format(Locale.US, offer.fare),
                offer.pickupKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                offer.tripKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                offer.totalKm?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
            ).joinToString("|"),
        )

    private fun fingerprint(value: String): String {
        if (value.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val tail = runCatching {
            file.readLines().takeLast(KEEP_LINES)
        }.getOrDefault(emptyList())
        runCatching {
            file.writeText(
                if (tail.isEmpty()) "" else tail.joinToString("\n", postfix = "\n"),
            )
        }
    }
}
