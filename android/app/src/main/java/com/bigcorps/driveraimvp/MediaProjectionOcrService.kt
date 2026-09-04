package com.srrotas.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor principal do Sr. Rotas.
 *
 * 0.26.3 reforça a confiabilidade de campo:
 * - watchdog fora da thread que ele vigia;
 * - recuperação de worker morto, surface sem frames e OCR sem progresso;
 * - callback do ImageReader protegido contra exceções que matariam o looper;
 * - perda da MediaProjection não encerra automaticamente a jornada.
 */
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
    private lateinit var recognizer: TextRecognizer
    private val ocrBusy = AtomicBoolean(false)
    private val frameChangeDetector = FrameChangeDetector()
    private val performance = OcrPerformanceTracker()
    private val frameLock = Any()

    /** Independente do HandlerThread de captura: continua vivo se o worker falhar. */
    private val watchdogHandler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var pendingFirstBitmap: Bitmap? = null
    private var pendingLatestBitmap: Bitmap? = null
    private var sessionJourneyId: String? = null

    @Volatile private var releasing = false
    @Volatile private var captureWidth = 1
    @Volatile private var captureHeight = 1
    @Volatile private var captureDensity = 1
    @Volatile private var capturedContentVisible = true
    @Volatile private var lastImageSeenAt = 0L
    @Volatile private var lastOcrCompletedAt = 0L
    @Volatile private var ocrStartedAt = 0L
    @Volatile private var ocrGeneration = 0L
    @Volatile private var lastRecoveryAt = 0L
    @Volatile private var lastWorkerHeartbeatAt = 0L

    private var lastFrameAt = 0L
    private var lastRawFingerprint = 0
    private var lastCandidateDiagnosticAt = 0L
    private var lastRejectedLogAt = 0L
    private var scaleLogged = false

    private val captureHealthWatchdog = object : Runnable {
        override fun run() {
            if (releasing || projection == null) return

            val now = SystemClock.elapsedRealtime()
            val currentJourney = repo.currentJourneyId().takeIf(String::isNotBlank)
            val ownsJourney =
                sessionJourneyId != null && sessionJourneyId == currentJourney

            val workerResponsive =
                workerThread?.isAlive == true &&
                    lastWorkerHeartbeatAt > 0L &&
                    now - lastWorkerHeartbeatAt <= CaptureHealthPolicy025.NO_FRAME_TIMEOUT_MS

            val action = CaptureHealthPolicy025.decide(
                CaptureHealthPolicy025.Snapshot(
                    nowMs = now,
                    projectionActive = projection != null,
                    journeyOwned = ownsJourney,
                    screenInteractive = isScreenInteractive(),
                    // isAlive não detecta looper bloqueado; o pulso do próprio
                    // worker precisa estar recente para a captura ser saudável.
                    workerAlive = workerResponsive,
                    lastImageSeenAtMs = lastImageSeenAt,
                    lastOcrCompletedAtMs = lastOcrCompletedAt,
                    ocrBusy = ocrBusy.get(),
                    ocrStartedAtMs = ocrStartedAt,
                    lastRecoveryAtMs = lastRecoveryAt,
                ),
            )

            when (action) {
                CaptureHealthPolicy025.Action.REBUILD_CAPTURE_WORKER ->
                    rebuildCaptureWorker("watchdog_worker_dead")
                CaptureHealthPolicy025.Action.REARM_CAPTURE_SURFACE ->
                    rearmCaptureSurface("watchdog_no_frames")
                CaptureHealthPolicy025.Action.RESET_OCR_PIPELINE ->
                    resetOcrPipeline(
                        if (ocrBusy.get()) "watchdog_ocr_stall"
                        else "watchdog_ocr_no_progress",
                    )
                CaptureHealthPolicy025.Action.NONE -> Unit
            }

            if (!releasing && projection != null) {
                watchdogHandler.postDelayed(
                    this,
                    CaptureHealthPolicy025.CHECK_INTERVAL_MS,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        dispatcher = OfferDispatcher(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        recognizer = newRecognizer()
        RadarHudTrace024.install(this)
        JourneyCoordinator.hydrateRuntime(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseProjection("user_stop")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> startProjectionFromIntent(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Destruição do serviço/callback do Android não transforma falha de
        // captura em encerramento de jornada. A mesma jornada pode ser recuperada.
        releaseProjection("service_destroyed", endJourneyIfOwned = false)
        if (::recognizer.isInitialized) runCatching { recognizer.close() }
        super.onDestroy()
    }

    private fun startProjectionFromIntent(intent: Intent) {
        val requestedJourney = repo.currentJourneyId().takeIf(String::isNotBlank)
        if (projection != null) {
            if (sessionJourneyId == requestedJourney) return
            releaseProjection("projection_superseded", endJourneyIfOwned = false)
        }
        releasing = false
        sessionJourneyId = requestedJourney

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = getResultData(intent) ?: run {
            LocalLog.append(this, "MediaProjection sem resultData")
            CaptureHealthState0263.markInactive(this, "missing_result_data")
            stopSelf()
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            LocalLog.append(this, "MediaProjection não autorizado: resultCode=$resultCode")
            CaptureHealthState0263.markInactive(this, "not_authorized")
            stopSelf()
            return
        }

        startAsForeground()
        frameChangeDetector.reset()
        performance.reset()
        DismissedOfferRegistry0221.reset()
        lastFrameAt = 0L
        lastRawFingerprint = 0
        lastCandidateDiagnosticAt = 0L
        lastRejectedLogAt = 0L
        scaleLogged = false
        capturedContentVisible = true
        ocrStartedAt = 0L
        lastRecoveryAt = 0L
        lastWorkerHeartbeatAt = 0L

        val metrics = resources.displayMetrics
        captureWidth = metrics.widthPixels.coerceAtLeast(1)
        captureHeight = metrics.heightPixels.coerceAtLeast(1)
        captureDensity = metrics.densityDpi.coerceAtLeast(1)

        val thread = newWorkerThread()
        val handler = Handler(thread.looper)
        workerThread = thread
        worker = handler

        val mediaProjection = runCatching {
            projectionManager.getMediaProjection(resultCode, resultData)
        }.onFailure {
            LocalLog.append(this, "Falha ao obter MediaProjection: ${it.message}")
        }.getOrNull()

        if (mediaProjection == null) {
            thread.quitSafely()
            workerThread = null
            worker = null
            CaptureHealthState0263.markInactive(this, "projection_unavailable")
            stopSelf()
            return
        }
        projection = mediaProjection

        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    LocalLog.append(
                        this@MediaProjectionOcrService,
                        "MediaProjection encerrada pelo sistema/usuário · jornada preservada para recuperação",
                    )
                    releaseProjection(
                        "projection_stopped_by_system",
                        endJourneyIfOwned = false,
                    )
                    stopSelf()
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
                    if (width <= 0 || height <= 0) return
                    if (virtualDisplay == null) {
                        captureWidth = width
                        captureHeight = height
                    } else {
                        reconfigureCapturedContent(width, height)
                    }
                }

                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        capturedContentVisible = isVisible
                        LocalLog.append(
                            this@MediaProjectionOcrService,
                            "Conteúdo compartilhado ${if (isVisible) "visível" else "oculto"} · OCR permanece monitorado",
                        )
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )

        val reader = createReader(captureWidth, captureHeight, handler)
        imageReader = reader
        virtualDisplay = runCatching {
            mediaProjection.createVirtualDisplay(
                "SrRotas-OCR",
                captureWidth,
                captureHeight,
                captureDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        }.onFailure {
            LocalLog.append(this, "Falha ao criar VirtualDisplay: ${it.message}")
        }.getOrNull()

        if (virtualDisplay == null) {
            runCatching { reader.close() }
            imageReader = null
            releaseProjection("virtual_display_start_failed")
            stopSelf()
            return
        }

        val now = SystemClock.elapsedRealtime()
        lastImageSeenAt = now
        lastOcrCompletedAt = now
        lastWorkerHeartbeatAt = now
        armWorkerHeartbeat(handler)
        repo.setProjectionActive(true)
        CaptureHealthState0263.markActive(this, sessionJourneyId)
        watchdogHandler.removeCallbacks(captureHealthWatchdog)
        watchdogHandler.postDelayed(
            captureHealthWatchdog,
            CaptureHealthPolicy025.CHECK_INTERVAL_MS,
        )

        LocalLog.append(
            this,
            "Jornada MediaProjection iniciada: ${captureWidth}x${captureHeight} @ ${captureDensity}dpi · sessão=${sessionJourneyId?.take(8) ?: "?"} · watchdog=0.26.3 independente",
        )
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun newWorkerThread(): HandlerThread =
        HandlerThread("SrRotasProjection").also { it.start() }

    /**
     * Pulso executado no próprio worker. O watchdog da main thread compara o
     * horário deste pulso; se o looper ficar bloqueado, a thread pode continuar
     * viva, mas será tratada como não responsiva e reconstruída.
     */
    private fun armWorkerHeartbeat(handler: Handler) {
        handler.post(
            object : Runnable {
                override fun run() {
                    if (worker !== handler || projection == null || releasing) return
                    lastWorkerHeartbeatAt = SystemClock.elapsedRealtime()
                    handler.postDelayed(this, 2_000L)
                }
            },
        )
    }

    private fun createReader(width: Int, height: Int, handler: Handler): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener(
                { available ->
                    runCatching { onImage(available, width, height) }
                        .onFailure {
                            LocalLog.append(
                                this,
                                "ImageReader protegido após exceção: ${it.javaClass.simpleName} · ${it.message}",
                            )
                        }
                },
                handler,
            )
        }

    private fun reconfigureCapturedContent(width: Int, height: Int) {
        val handler = worker ?: return
        handler.post {
            if (projection == null) return@post
            if (width == captureWidth && height == captureHeight) return@post

            synchronized(frameLock) { recyclePendingLocked() }
            frameChangeDetector.reset()
            val old = imageReader
            val replacement =
                runCatching { createReader(width, height, handler) }
                    .getOrNull() ?: return@post

            val changed = runCatching {
                virtualDisplay?.resize(width, height, captureDensity)
                virtualDisplay?.setSurface(replacement.surface)
            }.onFailure {
                LocalLog.append(this, "Falha ao redimensionar captura: ${it.message}")
            }.isSuccess

            if (!changed) {
                runCatching { replacement.close() }
                return@post
            }

            imageReader = replacement
            captureWidth = width
            captureHeight = height
            lastImageSeenAt = SystemClock.elapsedRealtime()
            lastFrameAt = 0L
            old?.setOnImageAvailableListener(null, null)
            runCatching { old?.close() }
            LocalLog.append(this, "Captura ajustada: ${width}x${height}")
        }
    }

    /**
     * Recria somente ImageReader/surface, preservando MediaProjection e jornada.
     */
    private fun rearmCaptureSurface(reason: String) {
        val handler = worker ?: return
        if (workerThread?.isAlive != true || projection == null || virtualDisplay == null || releasing) {
            return
        }

        val old = imageReader
        val replacement = runCatching {
            createReader(captureWidth, captureHeight, handler)
        }.onFailure {
            LocalLog.append(this, "Watchdog não criou novo ImageReader: ${it.message}")
        }.getOrNull() ?: return

        val swapped = runCatching {
            virtualDisplay?.setSurface(replacement.surface)
        }.onFailure {
            LocalLog.append(this, "Watchdog não rearmou surface: ${it.message}")
        }.isSuccess

        if (!swapped) {
            runCatching { replacement.close() }
            return
        }

        imageReader = replacement
        old?.setOnImageAvailableListener(null, null)
        runCatching { old?.close() }
        synchronized(frameLock) { recyclePendingLocked() }
        frameChangeDetector.reset()
        lastFrameAt = 0L
        lastImageSeenAt = SystemClock.elapsedRealtime()
        lastRecoveryAt = lastImageSeenAt
        LocalLog.append(
            this,
            "Watchdog 0.26.3 rearmou captura sem encerrar jornada · $reason · visibilidade=$capturedContentVisible",
        )
    }

    /**
     * Se o próprio HandlerThread morrer, um watchdog hospedado nele jamais roda.
     * Por isso a 0.26.3 reconstrói worker + ImageReader a partir da main thread.
     */
    private fun rebuildCaptureWorker(reason: String) {
        if (projection == null || virtualDisplay == null || releasing) return

        val oldReader = imageReader
        val oldThread = workerThread
        val newThread = newWorkerThread()
        val newHandler = Handler(newThread.looper)
        val replacement = runCatching {
            createReader(captureWidth, captureHeight, newHandler)
        }.onFailure {
            LocalLog.append(this, "Watchdog não recriou worker/ImageReader: ${it.message}")
        }.getOrNull()

        if (replacement == null) {
            newThread.quitSafely()
            return
        }

        val swapped = runCatching {
            virtualDisplay?.setSurface(replacement.surface)
        }.onFailure {
            LocalLog.append(this, "Watchdog não ligou surface ao novo worker: ${it.message}")
        }.isSuccess

        if (!swapped) {
            runCatching { replacement.close() }
            newThread.quitSafely()
            return
        }

        synchronized(frameLock) {
            ocrGeneration += 1L
            recyclePendingLocked()
            ocrBusy.set(false)
            ocrStartedAt = 0L
        }

        workerThread = newThread
        worker = newHandler
        imageReader = replacement
        lastWorkerHeartbeatAt = SystemClock.elapsedRealtime()
        armWorkerHeartbeat(newHandler)

        oldReader?.setOnImageAvailableListener(null, null)
        runCatching { oldReader?.close() }
        runCatching { oldThread?.quitSafely() }

        frameChangeDetector.reset()
        val now = SystemClock.elapsedRealtime()
        lastFrameAt = 0L
        lastImageSeenAt = now
        lastOcrCompletedAt = now
        lastRecoveryAt = now
        LocalLog.append(
            this,
            "Watchdog 0.26.3 reconstruiu thread de captura sem encerrar jornada · $reason",
        )
    }

    /**
     * Recupera um Task do ML Kit que não concluiu/progrediu em tempo anormal.
     */
    private fun resetOcrPipeline(reason: String) {
        if (projection == null || releasing) return

        val oldRecognizer = synchronized(frameLock) {
            val previous = recognizer
            ocrGeneration += 1L
            recognizer = newRecognizer()
            recyclePendingLocked()
            ocrBusy.set(false)
            ocrStartedAt = 0L
            previous
        }
        runCatching { oldRecognizer.close() }
        frameChangeDetector.reset()
        lastFrameAt = 0L
        val now = SystemClock.elapsedRealtime()
        lastOcrCompletedAt = now
        lastRecoveryAt = now
        LocalLog.append(
            this,
            "Watchdog 0.26.3 reiniciou pipeline OCR sem encerrar jornada · $reason",
        )
    }

    private fun onImage(reader: ImageReader, expectedWidth: Int, expectedHeight: Int) {
        var image: Image? = null
        try {
            val acquired = reader.acquireLatestImage() ?: return
            image = acquired
            val now = SystemClock.elapsedRealtime()
            lastImageSeenAt = now

            if (now - lastFrameAt < FRAME_SAMPLE_INTERVAL_MS) return
            lastFrameAt = now
            performance.sampled()

            val source = runCatching {
                imageToBitmap(acquired, expectedWidth, expectedHeight)
            }.onFailure {
                LocalLog.append(this, "Falha convertendo frame: ${it.message}")
            }.getOrNull() ?: return

            if (!frameChangeDetector.shouldProcess(source)) {
                performance.unchanged()
                source.recycle()
                return
            }
            queueOrProcess(prepareForOcr(source))
        } catch (error: Throwable) {
            LocalLog.append(
                this,
                "Frame isolado descartado sem derrubar captura: ${error.javaClass.simpleName} · ${error.message}",
            )
        } finally {
            runCatching { image?.close() }
        }
    }

    private fun prepareForOcr(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= OCR_MAX_LONG_EDGE) return source
        val scale = OCR_MAX_LONG_EDGE.toFloat() / longEdge.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(source, width, height, true)
        }.getOrNull() ?: return source
        if (scaled !== source) {
            if (!scaleLogged) {
                scaleLogged = true
                LocalLog.append(
                    this,
                    "OCR otimizado: ${source.width}x${source.height} -> ${scaled.width}x${scaled.height}",
                )
            }
            source.recycle()
        }
        return scaled
    }

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
                performance.queued(false)
            } else if (pendingLatestBitmap == null) {
                pendingLatestBitmap = bitmap
                performance.queued(false)
            } else {
                pendingLatestBitmap?.recycle()
                pendingLatestBitmap = bitmap
                performance.queued(true)
            }
        }
        if (startNow) processBitmap(bitmap)
    }

    private fun processBitmap(bitmap: Bitmap) {
        RadarHudTrace024.record(
            RadarHudTrace024.Stage.FRAME_CAPTURED,
            mapOf("width" to bitmap.width, "height" to bitmap.height),
        )
        val startedAt = SystemClock.elapsedRealtime()
        val settings = repo.load()
        var detectedOffers = 0
        val generation: Long
        val client: TextRecognizer
        synchronized(frameLock) {
            generation = ocrGeneration
            client = recognizer
            ocrStartedAt = startedAt
        }

        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (generation != ocrGeneration || projection == null) return@addOnSuccessListener

                RadarHudTrace024.recordOcr(
                    chars = result.text.length,
                    blocks = result.textBlocks.size,
                    rawText = result.text,
                )

                val spatialLines = OfferSpatialIsolation0221.lines(result)
                val fareLines = spatialLines.filter {
                    FlexibleDriverOfferParser.primaryFare(it.text) != null
                }
                val nonEmptyClusters = fareLines.count { fareLine ->
                    OfferSpatialIsolation0221.clusterAroundFare(
                        lines = spatialLines,
                        fareLine = fareLine,
                        frameWidth = bitmap.width,
                        frameHeight = bitmap.height,
                    ).isNotEmpty()
                }
                RadarHudTrace024.record(
                    RadarHudTrace024.Stage.SPATIAL_DIAGNOSTIC,
                    mapOf(
                        "lines" to spatialLines.size,
                        "fare_lines" to fareLines.size,
                        "clusters" to nonEmptyClusters,
                        "geometry_pairs" to FlexibleDriverOfferParser.geometryCount(result.text),
                        "uber_anchor" to OfferSpatialIsolation0221.hasUberOfferAnchor(result.text),
                        "99_anchor" to OfferSpatialIsolation0221.has99OfferAnchor(result.text),
                        "navigation_noise" to OfferSpatialIsolation0221.navigationNoise(spatialLines),
                    ),
                )

                val routed = DriverPlatformOfferRouter.parse(
                    result = result,
                    settings = settings,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                )
                RadarHudTrace024.recordRoute(
                    platform = routed.platform,
                    candidate = routed.candidate,
                    ownApp = routed.ownApp,
                    reason = routed.reason,
                    offers = routed.offers.size,
                )
                if (routed.ownApp) return@addOnSuccessListener

                val offers = routed.offers
                detectedOffers = offers.size
                if (offers.isNotEmpty()) {
                    offers.forEach {
                        RadarHudTrace024.recordOffer(
                            RadarHudTrace024.Stage.PARSED,
                            it,
                            routed.reason,
                        )
                    }
                    RadarHudTrace024.record(
                        RadarHudTrace024.Stage.DISPATCH_INPUT,
                        mapOf(
                            "platform" to (routed.platform ?: ""),
                            "offers" to offers.size,
                            "reason" to routed.reason.take(120),
                        ),
                    )
                    val dispatchStarted = SystemClock.elapsedRealtime()
                    dispatcher.submitStabilized(offers)
                    performance.dispatchCompleted(SystemClock.elapsedRealtime() - dispatchStarted)
                    if (settings.privateScreenshotEnabled) {
                        offers.maxByOrNull { it.confidence }
                            ?.let { PrivateScreenshotStore.save(this, bitmap, it) }
                    }
                } else if (routed.candidate) {
                    RadarHudTrace024.record(
                        RadarHudTrace024.Stage.PARSE_REJECTED,
                        mapOf(
                            "platform" to (routed.platform ?: ""),
                            "reason" to routed.reason.take(120),
                            "chars" to result.text.length,
                        ),
                    )
                    val method = routed.platform
                        ?.let { "media-projection-ocr/$it" }
                        ?: "media-projection-ocr"
                    saveCandidateDiagnosticOnce(result.text, method)
                } else {
                    logRejectedFrame(routed.reason, result.text.length)
                }
            }
            .addOnFailureListener {
                if (generation != ocrGeneration) return@addOnFailureListener
                RadarHudTrace024.record(
                    RadarHudTrace024.Stage.OCR_FAIL,
                    mapOf("error" to (it.message ?: "unknown").take(120)),
                )
                LocalLog.append(this, "OCR MediaProjection falhou: ${it.message}")
            }
            .addOnCompleteListener {
                if (generation != ocrGeneration) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    return@addOnCompleteListener
                }
                lastOcrCompletedAt = SystemClock.elapsedRealtime()
                performance.ocrCompleted(
                    lastOcrCompletedAt - startedAt,
                    detectedOffers,
                )
                val currentJourney = repo.currentJourneyId().takeIf(String::isNotBlank)
                if (projection != null && sessionJourneyId != null && sessionJourneyId == currentJourney) {
                    CaptureHealthState0263.heartbeat(this, sessionJourneyId)
                }
                finishOcr(bitmap, generation)
            }
    }

    private fun finishOcr(bitmap: Bitmap, generation: Long) {
        if (!bitmap.isRecycled) bitmap.recycle()
        if (generation != ocrGeneration) return

        var next: Bitmap? = null
        synchronized(frameLock) {
            if (generation != ocrGeneration) return@synchronized
            if (projection == null) {
                recyclePendingLocked()
                ocrBusy.set(false)
                ocrStartedAt = 0L
            } else when {
                pendingFirstBitmap != null -> {
                    next = pendingFirstBitmap
                    pendingFirstBitmap = pendingLatestBitmap
                    pendingLatestBitmap = null
                }
                pendingLatestBitmap != null -> {
                    next = pendingLatestBitmap
                    pendingLatestBitmap = null
                }
                else -> {
                    ocrBusy.set(false)
                    ocrStartedAt = 0L
                }
            }
        }
        next?.let {
            if (projection != null && generation == ocrGeneration) {
                processBitmap(it)
            } else {
                it.recycle()
                ocrBusy.set(false)
                ocrStartedAt = 0L
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
        val sanitized = DriverOcrNormalizer.sanitize(raw)
        if (sanitized.isBlank()) return
        val fp = sanitized.hashCode()
        val now = SystemClock.elapsedRealtime()
        if (fp == lastRawFingerprint ||
            now - lastCandidateDiagnosticAt < CANDIDATE_DIAGNOSTIC_INTERVAL_MS
        ) return
        lastRawFingerprint = fp
        lastCandidateDiagnosticAt = now
        dispatcher.saveDiagnostic(sanitized, method)
    }

    private fun logRejectedFrame(label: String, chars: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRejectedLogAt < REJECTED_LOG_INTERVAL_MS) return
        lastRejectedLogAt = now
        LocalLog.append(this, "FRAME ignorado · $label · $chars caracteres")
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.projection_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantém a jornada de análise OCR ativa."
                    setShowBadge(false)
                },
            )
        }
    }

    private fun releaseProjection(reason: String, endJourneyIfOwned: Boolean = true) {
        if (releasing) return
        releasing = true

        val ownedJourney = sessionJourneyId
        val currentJourney = repo.currentJourneyId().takeIf(String::isNotBlank)
        val ownsCurrentJourney = ownedJourney != null && ownedJourney == currentJourney

        if (ownsCurrentJourney || currentJourney == null) {
            repo.setProjectionActive(false)
        }
        CaptureHealthState0263.markInactive(this, reason)

        watchdogHandler.removeCallbacks(captureHealthWatchdog)
        worker?.removeCallbacks(captureHealthWatchdog)
        imageReader?.setOnImageAvailableListener(null, null)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null

        val currentProjection = projection
        projection = null
        runCatching { currentProjection?.stop() }

        synchronized(frameLock) {
            ocrGeneration += 1L
            recyclePendingLocked()
            ocrBusy.set(false)
            ocrStartedAt = 0L
        }
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        lastWorkerHeartbeatAt = 0L
        frameChangeDetector.reset()

        if (ownsCurrentJourney) {
            dispatcher.flushStabilized()
        }
        LocalLog.append(this, performance.snapshot().logLine())
        dispatcher.hideOverlay()

        if (endJourneyIfOwned && ownsCurrentJourney) {
            JourneyCoordinator.endJourney(this, reason)
            LocalLog.append(
                this,
                "Jornada encerrada pela sessão ${ownedJourney?.take(8) ?: "?"}: $reason",
            )
        } else if (ownsCurrentJourney) {
            LocalLog.append(
                this,
                "Captura encerrada sem finalizar jornada ${ownedJourney?.take(8) ?: "?"} · motivo=$reason",
            )
        } else if (ownedJourney != null && currentJourney != null && ownedJourney != currentJourney) {
            LocalLog.append(
                this,
                "Sessão OCR antiga ${ownedJourney.take(8)} encerrada sem afetar a jornada nova ${currentJourney.take(8)}",
            )
        }

        sessionJourneyId = null
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun recyclePendingLocked() {
        pendingFirstBitmap?.recycle()
        pendingLatestBitmap?.recycle()
        pendingFirstBitmap = null
        pendingLatestBitmap = null
    }

    private fun isScreenInteractive(): Boolean =
        runCatching { getSystemService(PowerManager::class.java).isInteractive }
            .getOrDefault(true)

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private fun newRecognizer(): TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}
