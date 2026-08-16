package com.srrotas.app

import android.app.*
import android.content.Context
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

/**
 * Motor principal do Sr. Rotas Alpha.
 * Uma jornada = uma sessão de MediaProjection autorizada pelo usuário.
 * Processa ~1 frame/s. Screenshot só é armazenada se o usuário ativar a opção privada.
 */
class MediaProjectionOcrService : Service() {
    companion object {
        const val ACTION_START = "com.srrotas.app.action.START_PROJECTION"
        const val ACTION_STOP = "com.srrotas.app.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "sr_rotas_projection"
        private const val NOTIFICATION_ID = 2701
        private const val FRAME_INTERVAL_MS = 1100L
    }

    private lateinit var repo: SettingsRepository
    private lateinit var dispatcher: OfferDispatcher
    private lateinit var projectionManager: MediaProjectionManager
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var lastFrameAt = 0L
    private var lastRawFingerprint = 0

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        dispatcher = OfferDispatcher(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
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
            stopSelf()
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            LocalLog.append(this, "MediaProjection não autorizado: resultCode=$resultCode")
            stopSelf()
            return
        }

        startAsForeground()

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

        // Em APIs atuais getMediaProjection() é nullable. Mantemos uma referência local
        // não-nula para registrar callback e criar o VirtualDisplay com segurança.
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
                LocalLog.append(
                    this@MediaProjectionOcrService,
                    "MediaProjection encerrado pelo sistema/usuário"
                )
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        projection = mediaProjection

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "SrRotas-OCR",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )

        reader.setOnImageAvailableListener(
            { availableReader -> onImage(availableReader, width, height) },
            handler
        )

        repo.setProjectionActive(true)
        LocalLog.append(this, "Jornada MediaProjection iniciada: ${width}x${height} @ ${density}dpi")
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun onImage(reader: ImageReader, expectedWidth: Int, expectedHeight: Int) {
        val image = reader.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()

        if (now - lastFrameAt < FRAME_INTERVAL_MS || !ocrBusy.compareAndSet(false, true)) {
            image.close()
            return
        }

        lastFrameAt = now

        val bitmap = runCatching {
            imageToBitmap(image, expectedWidth, expectedHeight)
        }.getOrNull()

        image.close()

        if (bitmap == null) {
            ocrBusy.set(false)
            return
        }

        val settings = repo.load()

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val offers = SpatialOfferParser.parse(
                    result = result,
                    sourcePackage = AppSignals.UBER_PACKAGE,
                    captureMethod = "media-projection-ocr",
                    settings = settings,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                )

                if (offers.isNotEmpty()) {
                    dispatcher.dispatchAll(offers)
                    if (settings.privateScreenshotEnabled) {
                        offers.maxByOrNull { it.confidence }?.let { PrivateScreenshotStore.save(this, bitmap, it) }
                    }
                } else {
                    saveDiagnosticOnce(result.text, "media-projection-ocr")
                }
            }
            .addOnFailureListener {
                LocalLog.append(this, "OCR MediaProjection falhou: ${it.message}")
            }
            .addOnCompleteListener {
                bitmap.recycle()
                ocrBusy.set(false)
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

    private fun saveDiagnosticOnce(raw: String, method: String) {
        if (raw.isBlank()) return

        val fp = raw.hashCode()
        if (fp == lastRawFingerprint) return

        lastRawFingerprint = fp
        dispatcher.saveDiagnostic(raw, method)
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
        val openIntent = Intent(this, MainActivity::class.java)

        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
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
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.projection_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantém a jornada de análise OCR ativa."
                    setShowBadge(false)
                }
            )
        }
    }

    private fun releaseProjection(reason: String) {
        repo.setProjectionActive(false)

        imageReader?.setOnImageAvailableListener(null, null)

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        val current = projection
        projection = null
        runCatching { current?.stop() }

        workerThread?.quitSafely()
        workerThread = null
        worker = null

        dispatcher.hideOverlay()
        JourneyCoordinator.endJourney(this, reason)

        LocalLog.append(this, "Jornada encerrada: $reason")
        sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
}
