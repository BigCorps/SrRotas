package com.srrotas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * vc108 — complemento confirmado de campos AUSENTES a partir da releitura de um print.
 *
 * Regras:
 *  - somente endereços de busca/destino que estão vazios no registro original; valores
 *    presentes nunca são substituídos e tempos/distâncias/valores não são alterados aqui;
 *  - identidade validada pela tarifa exata do registro original e qualidade do novo contexto;
 *  - o processo `:rescan` só grava um pedido (arquivo privado) após confirmação explícita;
 *    o processo principal revalida e aplica, registrando um histórico auditável/versionado
 *    vinculado ao ID original e à impressão digital da imagem (a imagem não é guardada).
 */
object RescanSupplementV108 {
    const val ACTION_APPLY = "com.srrotas.app.APPLY_RESCAN_SUPPLEMENT_V108"
    const val EXTRA_FILE = "file"
    const val SOURCE = "gallery_rescan_v108"
    const val FIELD_PICKUP = "pickup_label"
    const val FIELD_DESTINATION = "destination_label"
    private const val DIR = "rescan-supplements"
    private const val LEDGER = "offer_supplements_v108"
    private const val LEDGER_LIMIT = 200
    private const val MIN_CONFIDENCE = 0.80

    data class Proposal(val field: String, val value: String, val label: String)

    /** Campos ausentes no original que a nova leitura preenche com evidência suficiente. */
    fun proposals(original: RideOffer?, fresh: RideOffer?): List<Proposal> {
        if (original == null || fresh == null) return emptyList()
        if (abs(original.fare - fresh.fare) > 0.011) return emptyList()
        val ctx = fresh.context ?: return emptyList()
        if (ctx.contextConfidence < MIN_CONFIDENCE) return emptyList()
        val pickup = ctx.pickupLabel?.trim()?.takeIf(OfferContextQuality0242::canGeocode)
        val destination = ctx.destinationLabel?.trim()?.takeIf(OfferContextQuality0242::canGeocode)
        if (pickup != null && destination != null && fold(pickup) == fold(destination)) return emptyList()
        val old = original.context
        val oldPickup = old?.pickupLabel?.takeIf { it.isNotBlank() }
        val oldDestination = old?.destinationLabel?.takeIf { it.isNotBlank() }
        return listOfNotNull(
            pickup?.takeIf { oldPickup == null && (oldDestination == null || fold(oldDestination) != fold(it)) }
                ?.let { Proposal(FIELD_PICKUP, it.take(180), "Origem / busca") },
            destination?.takeIf { oldDestination == null && (oldPickup == null || fold(oldPickup) != fold(it)) }
                ?.let { Proposal(FIELD_DESTINATION, it.take(180), "Destino") },
        )
    }

    /** `:rescan`: grava o pedido confirmado e entrega ao processo principal. */
    fun request(context: Context, offerId: String, proposals: List<Proposal>, imageFingerprint: String?, freshConfidence: Double): Boolean {
        if (proposals.isEmpty()) return false
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.json")
        file.writeText(JSONObject()
            .put("offer_id", offerId)
            .put("image_fingerprint", imageFingerprint ?: JSONObject.NULL)
            .put("fresh_confidence", freshConfidence)
            .put("requested_at", Instant.now().toString())
            .put("fields", JSONArray(proposals.map { JSONObject().put("field", it.field).put("value", it.value) }))
            .toString())
        context.sendBroadcast(Intent(context, RescanSupplementReceiverV108::class.java).setAction(ACTION_APPLY).putExtra(EXTRA_FILE, file.name))
        return true
    }

    data class Applied(val offerId: String, val fields: List<String>, val skipped: List<String>)

    /** Processo principal: revalida contra o registro atual e aplica somente campos ainda vazios. */
    fun apply(context: Context, fileName: String): Applied? {
        val file = File(File(context.filesDir, DIR), fileName.substringAfterLast('/'))
        if (!file.isFile) return null
        val json = runCatching { JSONObject(file.readText()) }.getOrNull()
        file.delete()
        json ?: return null
        val offerId = json.optString("offer_id").takeIf { it.isNotBlank() } ?: return null
        val store = LocalStore.get(context)
        val offer = store.offerByLocalId(offerId) ?: return null
        val current = store.contextForOffer(offerId) ?: offer.context
        val wanted = json.optJSONArray("fields") ?: return null
        var next = current ?: OfferContext(
            contextConfidence = json.optDouble("fresh_confidence", MIN_CONFIDENCE).coerceIn(0.0, 1.0),
            geocodeStatus = "unresolved",
            geocodeSource = SOURCE,
            contextVersion = "sr-context-rescan-v108",
            sourceType = "gallery_rescan",
        )
        val applied = mutableListOf<String>(); val skipped = mutableListOf<String>()
        for (i in 0 until wanted.length()) {
            val item = wanted.optJSONObject(i) ?: continue
            val field = item.optString("field"); val value = item.optString("value").trim()
            if (!OfferContextQuality0242.canGeocode(value)) { skipped += field; continue }
            next = when (field) {
                FIELD_PICKUP -> if (next.pickupLabel.isNullOrBlank() && fold(next.destinationLabel.orEmpty()) != fold(value))
                    next.copy(pickupLabel = value).also { applied += field } else next.also { skipped += field }
                FIELD_DESTINATION -> if (next.destinationLabel.isNullOrBlank() && fold(next.pickupLabel.orEmpty()) != fold(value))
                    next.copy(destinationLabel = value).also { applied += field } else next.also { skipped += field }
                else -> next.also { skipped += field }
            }
        }
        if (applied.isEmpty()) return Applied(offerId, applied, skipped)
        // Texto novo só segue para geocodificação quando a leitura é confiável (≥ 0,80).
        next = next.copy(geocodeStatus = "pending", contextConfidence = maxOf(next.contextConfidence, MIN_CONFIDENCE))
        store.saveOrUpdateContext(offerId, next, syncState = 0)
        appendLedger(context, offerId, current, applied, next, json.optString("image_fingerprint").takeIf { it.isNotBlank() && it != "null" })
        val updated = offer.copy(context = next)
        OfferContextGeocoder.enrichAsync(context, updated) { resolved ->
            store.saveOrUpdateContext(offerId, resolved, syncState = 0)
            BackendClient.sendOfferContext(context, offerId, offer.dedupeKey, resolved)
            JourneyBubbleController.refreshOffer(context)
        }
        JourneyBubbleController.refreshOffer(context)
        return Applied(offerId, applied, skipped)
    }

    private fun appendLedger(context: Context, offerId: String, before: OfferContext?, fields: List<String>, after: OfferContext, fingerprint: String?) {
        val prefs = context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE)
        val rows = runCatching { JSONArray(prefs.getString("rows", "[]")) }.getOrDefault(JSONArray())
        val revision = (0 until rows.length()).count { rows.optJSONObject(it)?.optString("offer_id") == offerId } + 1
        fields.forEach { field ->
            rows.put(JSONObject()
                .put("offer_id", offerId).put("field", field).put("revision", revision)
                .put("before", (if (field == FIELD_PICKUP) before?.pickupLabel else before?.destinationLabel) ?: JSONObject.NULL)
                .put("after", if (field == FIELD_PICKUP) after.pickupLabel else after.destinationLabel)
                .put("source", SOURCE).put("image_fingerprint", fingerprint ?: JSONObject.NULL)
                .put("applied_at", Instant.now().toString()))
        }
        val trimmed = JSONArray((maxOf(0, rows.length() - LEDGER_LIMIT) until rows.length()).map { rows.get(it) })
        prefs.edit().putString("rows", trimmed.toString()).apply()
    }

    /** Histórico auditável dos complementos de uma oferta (mais recente por último). */
    fun history(context: Context, offerId: String): List<JSONObject> {
        val rows = runCatching { JSONArray(context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE).getString("rows", "[]")) }
            .getOrDefault(JSONArray())
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.filter { it.optString("offer_id") == offerId }
    }

    /** Impressão digital estável e não reversível da imagem decodificada (amostra de pixels). */
    fun fingerprint(image: android.graphics.Bitmap): String? = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val stepX = maxOf(1, image.width / 32); val stepY = maxOf(1, image.height / 48)
        digest.update("${image.width}x${image.height}".toByteArray())
        var y = 0
        while (y < image.height) { var x = 0; while (x < image.width) { val p = image.getPixel(x, y)
            digest.update(byteArrayOf((p shr 16).toByte(), (p shr 8).toByte(), p.toByte())); x += stepX }; y += stepY }
        digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun fold(value: String) = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}

/** Recebe no processo principal o pedido confirmado vindo do processo `:rescan`. */
class RescanSupplementReceiverV108 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RescanSupplementV108.ACTION_APPLY) return
        val name = intent.getStringExtra(RescanSupplementV108.EXTRA_FILE) ?: return
        val pending = goAsync()
        Thread {
            try { runCatching { RescanSupplementV108.apply(context.applicationContext, name) } } finally { pending.finish() }
        }.start()
    }
}

/** O processo `:rescan` vive mais que a jornada: recarrega preferências escritas no processo principal. */
object RescanProcessStateV108 {
    @Suppress("DEPRECATION")
    fun reload(context: Context) {
        listOf("driver_ai_settings", "field_rescan_v1", "sr_reader_lab_027036").forEach {
            runCatching { context.applicationContext.getSharedPreferences(it, Context.MODE_MULTI_PROCESS) }
        }
    }
}

/** Mensagens finais observáveis da releitura (sem texto bruto do erro). */
object ScreenshotRescanFailureV108 {
    fun message(error: Throwable): String = when {
        error.message == "multiple_cards" -> "Releitura concluída com mais de um card na imagem; escolha um print com uma única oferta. Nenhum dado oficial mudou."
        error is IllegalStateException && error.message?.contains("MlKit", true) == true ->
            "Leitor de texto indisponível neste aparelho/processo. Feche esta tela e tente novamente. Nenhum dado oficial mudou."
        error is OutOfMemoryError -> "Imagem grande demais para a releitura. Nenhum dado oficial mudou."
        else -> "A releitura não pôde ser concluída (${error.javaClass.simpleName.take(40)}). Nenhum dado oficial mudou. Tente novamente em condição segura."
    }
}
