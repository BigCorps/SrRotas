package com.srrotas.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Captura manual da Uber.
 *
 * Sessão: um único quadro.
 * Histórico: mantém MediaProjection enquanto o motorista rola, amostra no
 * máximo um quadro a cada ~1,1 s e encerra somente por ação do usuário/timeout.
 */
class UberDigitizationCaptureService026 : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP_HISTORY = "com.srrotas.app.STOP_UBER_HISTORY_SCAN"
        private const val CHANNEL = "sr_uber_digitization"
        private const val NOTIF = 2607
        private const val PREFS = "sr_uber_history_scan_0262"
        private const val KEY_ACTIVE = "active"
        private const val MIN_FRAME_INTERVAL_MS = 1_100L
        private const val MAX_HISTORY_MS = 120_000L
        private const val MAX_FRAMES = 100

        fun historyActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

        fun stopHistory(context: Context) {
            val intent = Intent(context, UberDigitizationCaptureService026::class.java).setAction(ACTION_STOP_HISTORY)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var mode = UberDigitizationParser026.MODE_SESSION
    private var stopping = false
    private var singleFrameTaken = false
    private var ocrBusy = false
    private var lastFrameAt = 0L
    private val historyFrames = mutableListOf<String>()
    private val historyHashes = linkedSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_HISTORY) {
            // startForegroundService() exige que o serviço entre em foreground
            // mesmo se este processo tiver sido recriado com um estado antigo.
            startForegroundCompat(history = true)
            if (historyActive(this) && projection != null) {
                mode = UberDigitizationParser026.MODE_HISTORY
                finishHistory("manual")
            } else {
                setHistoryActive(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        mode = intent?.getStringExtra(UberDigitizationActivity026.EXTRA_MODE)
            ?: UberDigitizationParser026.MODE_SESSION
        startForegroundCompat(mode == UberDigitizationParser026.MODE_HISTORY)

        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = parcelableIntent(intent)
        if (code != Activity.RESULT_OK || data == null) {
            if (mode == UberDigitizationParser026.MODE_HISTORY) setHistoryActive(false)
            finishSession("")
            return START_NOT_STICKY
        }

        if (mode == UberDigitizationParser026.MODE_HISTORY) {
            setHistoryActive(true)
            main.postDelayed({
                if (!stopping && historyActive(this)) finishHistory("timeout")
            }, MAX_HISTORY_MS)
        }
        capture(code, data)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!stopping) {
            setHistoryActive(false)
            cleanup(stopProjection = true)
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun parcelableIntent(source: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= 33) source?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else source?.getParcelableExtra(EXTRA_RESULT_DATA)

    private fun capture(code: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(code, data)
        projection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    projection = null
                    if (stopping) return
                    if (mode == UberDigitizationParser026.MODE_HISTORY) {
                        finishHistory("projection_stopped", stopProjection = false)
                    } else {
                        finishSession("", stopProjection = false)
                    }
                }
            },
            main,
        )

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (stopping) return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (mode == UberDigitizationParser026.MODE_HISTORY) {
                    if (ocrBusy || now - lastFrameAt < MIN_FRAME_INTERVAL_MS || historyFrames.size >= MAX_FRAMES) {
                        return@setOnImageAvailableListener
                    }
                } else if (singleFrameTaken || ocrBusy) {
                    return@setOnImageAvailableListener
                }

                lastFrameAt = now
                if (mode != UberDigitizationParser026.MODE_HISTORY) singleFrameTaken = true
                ocrBusy = true
                val bitmap = bitmapFromImage(image, width, height) ?: run {
                    ocrBusy = false
                    return@setOnImageAvailableListener
                }
                ocr(bitmap)
            } finally {
                image.close()
            }
        }, main)

        display = projection?.createVirtualDisplay(
            "SrRotasUberScan",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null,
        )

        if (mode != UberDigitizationParser026.MODE_HISTORY) {
            main.postDelayed({ if (!stopping && !singleFrameTaken) finishSession("") }, 5_000)
        }
    }

    private fun bitmapFromImage(
        image: android.media.Image,
        width: Int,
        height: Int,
    ): Bitmap? = runCatching {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (bitmap !== padded) padded.recycle()
        bitmap
    }.getOrNull()

    private fun ocr(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val text = result.text.orEmpty()
                if (mode == UberDigitizationParser026.MODE_HISTORY) {
                    addHistoryFrame(text)
                } else {
                    finishSession(text)
                }
            }
            .addOnFailureListener {
                if (mode != UberDigitizationParser026.MODE_HISTORY) finishSession("")
            }
            .addOnCompleteListener {
                bitmap.recycle()
                recognizer.close()
                ocrBusy = false
            }
    }

    private fun addHistoryFrame(text: String) {
        if (text.length < 20 || historyFrames.size >= MAX_FRAMES) return
        val normalized = text.replace(Regex("\\s+"), " ").trim().take(20_000)
        val hash = sha256(normalized)
        if (historyHashes.add(hash)) historyFrames += text.take(20_000)
    }

    private fun finishSession(text: String, stopProjection: Boolean = true) {
        if (stopping) return
        stopping = true
        sendBroadcast(
            Intent(UberDigitizationActivity026.ACTION_RESULT)
                .setPackage(packageName)
                .putExtra(UberDigitizationActivity026.EXTRA_TEXT, text),
        )
        cleanup(stopProjection)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishHistory(reason: String, stopProjection: Boolean = true) {
        if (stopping) return
        stopping = true
        setHistoryActive(false)
        val file = File(cacheDir, "uber-history-scan-${UUID.randomUUID()}.json")
        runCatching {
            file.writeText(
                org.json.JSONObject().apply {
                    put("reason", reason)
                    put("frames", JSONArray().apply { historyFrames.forEach(::put) })
                }.toString(),
            )
        }
        cleanup(stopProjection)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        startActivity(
            Intent(this, UberDigitizationActivity026::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(UberDigitizationActivity026.EXTRA_MODE, UberDigitizationParser026.MODE_HISTORY)
                .putExtra(UberDigitizationActivity026.EXTRA_SCAN_FILE, file.name),
        )
    }

    private fun cleanup(stopProjection: Boolean) {
        display?.release()
        display = null
        reader?.close()
        reader = null
        val current = projection
        projection = null
        if (stopProjection) runCatching { current?.stop() }
    }

    private fun setHistoryActive(active: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    private fun startForegroundCompat(history: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Digitalização da Uber", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sr. Rotas")
            .setContentText(if (history) "Digitalizando histórico · role a tela da Uber" else "Lendo resumo da sessão da Uber…")
            .setOngoing(true)

        if (history) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val pending = PendingIntent.getService(
                this,
                26072,
                Intent(this, UberDigitizationCaptureService026::class.java).setAction(ACTION_STOP_HISTORY),
                flags,
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Finalizar",
                    pending,
                ).build(),
            )
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIF, notification)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
