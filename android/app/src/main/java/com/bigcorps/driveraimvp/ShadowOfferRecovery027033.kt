package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject

/**
 * Compatibilidade RC3.3 preservada em 0.29, mas sem segundo OCR.
 *
 * O diagnóstico 0.28 registrou 1.699 passes extras de ML Kit para apenas
 * 11 ofertas recuperadas. A partir da 0.29 o M1 mantém um único OCR pesado.
 * Este objeto continua recebendo os pedidos antigos apenas para medir quanto
 * trabalho deixou de ser executado; nenhuma imagem é copiada ou persistida.
 */
object ShadowOfferRecovery027033 {
    private val lock = Any()

    private var submitted = 0L
    private var suppressed = 0L
    private var lastReason = ""

    @Suppress("UNUSED_PARAMETER")
    fun submit(
        context: Context,
        source: Bitmap,
        settings: DriverSettings,
        journeyId: String?,
        reason: String,
        callback: (List<RideOffer>) -> Unit,
    ) {
        synchronized(lock) {
            submitted += 1L
            suppressed += 1L
            lastReason = reason.take(100)
        }
        // 0.29: deliberadamente não copia Bitmap, não abre TextRecognizer e
        // não chama callback. O OCR oficial já ocorreu no MediaProjection.
    }

    fun cancelPending() = Unit

    fun resetForJourney() {
        synchronized(lock) {
            submitted = 0L
            suppressed = 0L
            lastReason = ""
        }
    }

    fun close() = Unit

    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().apply {
            put("schema", "sr-shadow-recovery-v2")
            put("policy", "0.29-single-heavy-ocr")
            put("disabled_in_029", true)
            put("submitted", submitted)
            put("suppressed_submissions", suppressed)
            // Chaves antigas mantidas para leitores de diagnóstico existentes.
            put("replaced_pending", 0)
            put("pass_attempts", 0)
            put("recovered_jobs", 0)
            put("exhausted_jobs", 0)
            put("recovered_offers", 0)
            put("busy", false)
            put("pending", false)
            put("last_recovery_elapsed_ms", 0)
            put("last_reason", lastReason)
            put("privacy", "Nenhuma captura copiada/persistida; nenhum OCR adicional executado.")
        }
    }
}
