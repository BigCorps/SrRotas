package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject

/**
 * RC3.3 — segunda/terceira/quarta leitura da MESMA captura.
 *
 * Não acessa galeria e não persiste a imagem. Mantém somente uma captura privada
 * em memória por vez e a substitui pela mais recente enquanto houver trabalho.
 */
object ShadowOfferRecovery027033 {
    private const val MAX_PASSES = 4

    private data class Job(
        val bitmap: Bitmap,
        val settings: DriverSettings,
        val journeyId: String?,
        val reason: String,
        val callback: (List<RideOffer>) -> Unit,
    )

    private val lock = Any()
    private var recognizer: TextRecognizer? = null
    private var active: Job? = null
    private var pending: Job? = null

    private var submitted = 0L
    private var replaced = 0L
    private var passAttempts = 0L
    private var recoveredJobs = 0L
    private var exhaustedJobs = 0L
    private var recoveredOffers = 0L
    private var lastRecoveryAt = 0L

    fun submit(
        context: Context,
        source: Bitmap,
        settings: DriverSettings,
        journeyId: String?,
        reason: String,
        callback: (List<RideOffer>) -> Unit,
    ) {
        if (source.isRecycled) return
        val copy = runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        val job = Job(copy, settings, journeyId, reason.take(100), callback)
        var start: Job? = null
        synchronized(lock) {
            submitted += 1L
            if (active == null) {
                active = job
                start = job
            } else {
                pending?.bitmap?.let { if (!it.isRecycled) it.recycle() }
                if (pending != null) replaced += 1L
                pending = job
            }
        }
        start?.let { process(context.applicationContext, it, 0, linkedMapOf()) }
    }

    fun cancelPending() {
        synchronized(lock) {
            pending?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            pending = null
        }
    }

    fun resetForJourney() {
        cancelPending()
        synchronized(lock) {
            submitted = 0L
            replaced = 0L
            passAttempts = 0L
            recoveredJobs = 0L
            exhaustedJobs = 0L
            recoveredOffers = 0L
            lastRecoveryAt = 0L
        }
    }

    fun close() {
        cancelPending()
        synchronized(lock) {
            recognizer?.let { runCatching { it.close() } }
            recognizer = null
        }
    }

    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().apply {
            put("schema", "sr-shadow-recovery-v1")
            put("submitted", submitted)
            put("replaced_pending", replaced)
            put("pass_attempts", passAttempts)
            put("recovered_jobs", recoveredJobs)
            put("exhausted_jobs", exhaustedJobs)
            put("recovered_offers", recoveredOffers)
            put("busy", active != null)
            put("pending", pending != null)
            put("last_recovery_elapsed_ms", lastRecoveryAt)
        }
    }

    private fun process(
        context: Context,
        job: Job,
        pass: Int,
        found: LinkedHashMap<String, RideOffer>,
    ) {
        if (pass >= MAX_PASSES) {
            finish(context, job, found.values.toList())
            return
        }
        val variant = makeVariant(job.bitmap, pass) ?: run {
            process(context, job, pass + 1, found)
            return
        }
        synchronized(lock) { passAttempts += 1L }
        val client = synchronized(lock) {
            recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
        }
        client.process(InputImage.fromBitmap(variant, 0))
            .addOnSuccessListener { result ->
                val routed = DriverPlatformOfferRouter.parse(
                    result = result,
                    settings = job.settings,
                    frameWidth = variant.width,
                    frameHeight = variant.height,
                )
                routed.offers.forEach { offer ->
                    if (OfferIntegrityGate027033.assess(offer).ready) {
                        found[offer.dedupeKey.ifBlank { offer.localId }] = offer
                    }
                }
            }
            .addOnFailureListener {
                LocalLog.append(context, "RC3.3 shadow OCR pass ${pass + 1} falhou: ${it.message}")
            }
            .addOnCompleteListener {
                if (variant !== job.bitmap && !variant.isRecycled) variant.recycle()
                if (found.isNotEmpty()) finish(context, job, found.values.toList())
                else process(context, job, pass + 1, found)
            }
    }

    private fun finish(context: Context, job: Job, offers: List<RideOffer>) {
        if (!job.bitmap.isRecycled) job.bitmap.recycle()
        var next: Job? = null
        synchronized(lock) {
            if (offers.isNotEmpty()) {
                recoveredJobs += 1L
                recoveredOffers += offers.size
                lastRecoveryAt = SystemClock.elapsedRealtime()
            } else {
                exhaustedJobs += 1L
            }
            if (active === job) active = null
            next = pending
            pending = null
            if (next != null) active = next
        }

        if (offers.isNotEmpty()) {
            job.callback(offers)
        } else {
            ReaderAutoFailure027033.mark(context, "shadow_exhausted:${job.reason}")
        }
        next?.let { process(context, it, 0, linkedMapOf()) }
    }

    private fun makeVariant(source: Bitmap, pass: Int): Bitmap? = runCatching {
        when (pass) {
            0 -> source
            1 -> Bitmap.createBitmap(source, 0, 0, (source.width * 0.62f).toInt().coerceAtLeast(1), source.height)
            2 -> {
                val width = (source.width * 0.62f).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(source, source.width - width, 0, width, source.height)
            }
            else -> {
                val scale = if (maxOf(source.width, source.height) < 1900) 1.25f else 0.88f
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
        }
    }.getOrNull()
}
