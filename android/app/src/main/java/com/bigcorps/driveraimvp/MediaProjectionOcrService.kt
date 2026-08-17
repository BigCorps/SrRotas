package com.srrotas.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Motor principal do Sr. Rotas Alpha. */
class MediaProjectionOcrService : Service() {
    companion object {
        const val ACTION_START = "com.srrotas.app.action.START_PROJECTION"
        const val ACTION_STOP = "com.srrotas.app.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "sr_rotas_projection"
        private const val NOTIFICATION_ID = 2701

        private const val FRAME_SAMPLE_INTERVAL_MS = 250L
        private const val OCR_MAX_LONG_EDGE = 2100
        private const val CANDIDATE_DIAGNOSTIC_INTERVAL_MS = 1_500L
        private const val REJECTED_LOG_INTERVAL_MS = 10_000L
    }

    private lateinit var repo: SettingsRepository
    private lateinit var dispatcher: OfferDispatcher
    private lateinit var projectionManager: MediaProjectionManager
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)
    private val frameChangeDetector = FrameChangeDetector()
    private val performance = OcrPerformanceTracker()
    private val frameLock = Any()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    // 0.5.3: preserva o PRIMEIRO frame novo e também o MAIS RECENTE.
    private var pendingFirstBitmap: Bitmap? = null
    private var pendingLatestBitmap: Bitmap? = null

    private var lastFrameAt = 0L
    private var lastRawFingerprint = 0
    private var lastCandidateDiagnosticAt = 0L
    private var lastRejectedLogAt = 0L
    private var scaleLogged = false

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        dispatcher = OfferDispatcher(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START -> startProjectionFromIntent(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        releaseProjection("service_destroyed")
        runCatching { recognizer.close() }
        super.onDestroy()
    }

    private fun startProjectionFromIntent(intent: Intent) {
        if (projection != null) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = getResultData(intent) ?: run {
            LocalLog.append(this, "MediaProjection sem resultData")
            stopSelf(); return
        }
        if (resultCode != Activity.RESULT_OK) {
            LocalLog.append(this, "MediaProjection não autorizado: resultCode=$resultCode")
            stopSelf(); return
        }

        startAsForeground()
        frameChangeDetector.reset()
        performance.reset()
        lastFrameAt = 0L
        lastRawFingerprint = 0
        lastCandidateDiagnosticAt = 0L
        lastRejectedLogAt = 0L
        scaleLogged = false

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        val thread = HandlerThread("SrRotasProjection").also { it.start() }
        workerThread = thread
        val handler = Handler(thread.looper)
        worker = handler

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            LocalLog.append(this, "Falha ao obter MediaProjection")
            runCatching { reader.close() }
            imageReader = null
            thread.quitSafely()
            workerThread = null
            worker = null
            stopSelf()
            return
        }

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                LocalLog.append(this@MediaProjectionOcrService, "MediaProjection encerrado pelo sistema/usuário")
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        projection = mediaProjection
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "SrRotas-OCR", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        )
        reader.setOnImageAvailableListener({ availableReader -> onImage(availableReader, width, height) }, handler)

        repo.setProjectionActive(true)
        LocalLog.append(this, "Jornada MediaProjection iniciada: ${width}x${height} @ ${density}dpi")
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun onImage(reader: ImageReader, expectedWidth: Int, expectedHeight: Int) {
        val image = reader.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_SAMPLE_INTERVAL_MS) {
            image.close()
            return
        }
        lastFrameAt = now
        performance.sampled()

        val source = runCatching { imageToBitmap(image, expectedWidth, expectedHeight) }.getOrNull()
        image.close()
        if (source == null) return

        if (!frameChangeDetector.shouldProcess(source)) {
            performance.unchanged()
            source.recycle()
            return
        }

        val prepared = prepareForOcr(source)
        queueOrProcess(prepared)
    }

    /**
     * Redução moderada de pixels: mantém proporção e nunca aumenta bitmap.
     * Em 1080x2400 vira ~945x2100. O objetivo é aliviar ML Kit sem
     * transformar o texto em miniatura.
     */
    private fun prepareForOcr(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= OCR_MAX_LONG_EDGE) return source

        val scale = OCR_MAX_LONG_EDGE.toFloat() / longEdge.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrNull()
            ?: return source

        if (scaled !== source) {
            if (!scaleLogged) {
                scaleLogged = true
                LocalLog.append(this, "OCR otimizado: ${source.width}x${source.height} -> ${scaled.width}x${scaled.height}")
            }
            source.recycle()
        }
        return scaled
    }

    /**
     * Dois slots, sem fila infinita:
     * 1) primeiro frame novo enquanto OCR está ocupado;
     * 2) frame mais recente.
     * Só o segundo slot pode ser substituído.
     */
    private fun queueOrProcess(bitmap: Bitmap) {
        if (projection == null) {
            bitmap.recycle()
            return
        }

        var startNow = false
        synchronized(frameLock) {
            if (!ocrBusy.get()) {
                ocrBusy.set(true)
                startNow = true
            } else if (pendingFirstBitmap == null) {
                pendingFirstBitmap = bitmap
                performance.queued(replacedPrevious = false)
            } else if (pendingLatestBitmap == null) {
                pendingLatestBitmap = bitmap
                performance.queued(replacedPrevious = false)
            } else {
                pendingLatestBitmap?.recycle()
                pendingLatestBitmap = bitmap
                performance.queued(replacedPrevious = true)
            }
        }
        if (startNow) processBitmap(bitmap)
    }

    private fun processBitmap(bitmap: Bitmap) {
        val startedAt = SystemClock.elapsedRealtime()
        val settings = repo.load()
        var detectedOffers = 0

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val gate = UberScreenGate.classify(result.text)
                if (gate == UberScreenGate.Kind.OWN_APP) return@addOnSuccessListener

                val offers = if (gate == UberScreenGate.Kind.OFFER_CANDIDATE) {
                    SpatialOfferParser.parse(
                        result = result,
                        sourcePackage = AppSignals.UBER_PACKAGE,
                        captureMethod = "media-projection-ocr",
                        settings = settings,
                        frameWidth = bitmap.width,
                        frameHeight = bitmap.height,
                    )
                } else emptyList()

                detectedOffers = offers.size
                if (offers.isNotEmpty()) {
                    dispatcher.submitStabilized(offers)
                    if (settings.privateScreenshotEnabled) {
                        offers.maxByOrNull { it.confidence }?.let { PrivateScreenshotStore.save(this, bitmap, it) }
                    }
                } else {
                    when (gate) {
                        UberScreenGate.Kind.OFFER_CANDIDATE ->
                            saveCandidateDiagnosticOnce(result.text, "media-projection-ocr")
                        UberScreenGate.Kind.IDLE_OR_HOME,
                        UberScreenGate.Kind.UNKNOWN,
                        UberScreenGate.Kind.FOREIGN_UI ->
                            logRejectedFrame(gate, result.text.length)
                        UberScreenGate.Kind.OWN_APP -> Unit
                    }
                }
            }
            .addOnFailureListener { LocalLog.append(this, "OCR MediaProjection falhou: ${it.message}") }
            .addOnCompleteListener {
                performance.ocrCompleted(SystemClock.elapsedRealtime() - startedAt, detectedOffers)
                finishOcr(bitmap)
            }
    }

    private fun finishOcr(bitmap: Bitmap) {
        bitmap.recycle()
        var next: Bitmap? = null

        synchronized(frameLock) {
            if (projection == null) {
                recyclePendingLocked()
                ocrBusy.set(false)
            } else {
                when {
                    pendingFirstBitmap != null -> {
                        next = pendingFirstBitmap
                        pendingFirstBitmap = pendingLatestBitmap
                        pendingLatestBitmap = null
                    }
                    pendingLatestBitmap != null -> {
                        next = pendingLatestBitmap
                        pendingLatestBitmap = null
                    }
                    else -> ocrBusy.set(false)
                }
            }
        }

        next?.let {
            if (projection != null) {
                processBitmap(it)
            } else {
                it.recycle()
                ocrBusy.set(false)
            }
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + (rowPadding / pixelStride)
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun saveCandidateDiagnosticOnce(raw: String, method: String) {
        val sanitized = BRUberLineSanitizer.sanitize(raw)
        if (sanitized.isBlank()) return
        val fp = sanitized.hashCode()
        val now = SystemClock.elapsedRealtime()
        if (fp == lastRawFingerprint || now - lastCandidateDiagnosticAt < CANDIDATE_DIAGNOSTIC_INTERVAL_MS) return
        lastRawFingerprint = fp
        lastCandidateDiagnosticAt = now
        dispatcher.saveDiagnostic(sanitized, method)
    }

    private fun logRejectedFrame(kind: UberScreenGate.Kind, chars: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRejectedLogAt < REJECTED_LOG_INTERVAL_MS) return
        lastRejectedLogAt = now
        val label = when (kind) {
            UberScreenGate.Kind.IDLE_OR_HOME -> "home/ocioso"
            UberScreenGate.Kind.UNKNOWN -> "contexto desconhecido"
            UberScreenGate.Kind.FOREIGN_UI -> "outra interface"
            else -> kind.name.lowercase()
        }
        LocalLog.append(this, "FRAME ignorado · $label · $chars caracteres")
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.projection_notification_title))
            .setContentText(getString(R.string.projection_notification_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.projection_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Mantém a jornada de análise OCR ativa."
                    setShowBadge(false)
                },
            )
        }
    }

    private fun releaseProjection(reason: String) {
        repo.setProjectionActive(false)
        imageReader?.setOnImageAvailableListener(null, null)
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { imageReader?.close() }; imageReader = null
        val current = projection; projection = null
        runCatching { current?.stop() }

        synchronized(frameLock) { recyclePendingLocked() }

        workerThread?.quitSafely(); workerThread = null; worker = null
        frameChangeDetector.reset()

        // Não perde o último card ainda dentro da janela de estabilização.
        dispatcher.flushStabilized()

        LocalLog.append(this, performance.snapshot().logLine())

        dispatcher.hideOverlay()
        JourneyCoordinator.endJourney(this, reason)
        LocalLog.append(this, "Jornada encerrada: $reason")
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun recyclePendingLocked() {
        pendingFirstBitmap?.recycle()
        pendingLatestBitmap?.recycle()
        pendingFirstBitmap = null
        pendingLatestBitmap = null
    }

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else intent.getParcelableExtra(EXTRA_RESULT_DATA)
}
