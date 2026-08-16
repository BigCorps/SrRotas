package com.bigcorps.driveraimvp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class DriverAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_CAPTURE_UPDATED = "com.bigcorps.driveraimvp.CAPTURE_UPDATED"
        const val UBER_PACKAGE = "com.ubercab.driver"
    }

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var overlay: OverlayController
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)

    private var lastNodeReadAt = 0L
    private var lastScreenshotAt = 0L
    private var lastOfferKey = ""
    private var lastOfferAt = 0L
    private var lastRawFingerprint = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepo = SettingsRepository(this)
        overlay = OverlayController(this)
        LocalLog.append(this, "AccessibilityService conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != UBER_PACKAGE) return

        val settings = settingsRepo.load()
        if (!settings.consentAccepted) return

        val now = System.currentTimeMillis()
        if (now - lastNodeReadAt < 250) return
        lastNodeReadAt = now

        val root = rootInActiveWindow
        val nodeText = root?.let { extractText(it) }.orEmpty()
        root?.recycle()

        if (nodeText.isNotBlank()) {
            saveDiagnostic(nodeText, "accessibility-tree")
            val parsed = OfferParser.parse(nodeText, packageName, "accessibility-tree", settings)
            if (parsed != null) {
                handleOffer(parsed)
                return
            }
        }

        if (settings.ocrEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (now - lastScreenshotAt >= 1600) {
                lastScreenshotAt = now
                captureForOcr(packageName, settings)
            }
        }
    }

    override fun onInterrupt() {
        overlay.hide()
        LocalLog.append(this, "AccessibilityService interrompido")
    }

    override fun onDestroy() {
        overlay.hide()
        runCatching { recognizer.close() }
        super.onDestroy()
    }

    private fun extractText(root: AccessibilityNodeInfo): String {
        val lines = ArrayList<String>(80)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        var visited = 0

        while (queue.isNotEmpty() && visited < 700) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        return lines.distinct().joinToString("\n")
    }

    private fun captureForOcr(packageName: String, settings: DriverSettings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!ocrBusy.compareAndSet(false, true)) return

        overlay.hide()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()

                if (bitmap == null) {
                    ocrBusy.set(false)
                    return
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        if (text.isNotBlank()) {
                            saveDiagnostic(text, "screenshot-ocr")
                            OfferParser.parse(text, packageName, "screenshot-ocr", settings)?.let(::handleOffer)
                        }
                    }
                    .addOnFailureListener {
                        LocalLog.append(this@DriverAccessibilityService, "OCR falhou: ${it.message}")
                    }
                    .addOnCompleteListener {
                        bitmap.recycle()
                        ocrBusy.set(false)
                    }
            }

            override fun onFailure(errorCode: Int) {
                LocalLog.append(this@DriverAccessibilityService, "Screenshot falhou: código $errorCode")
                ocrBusy.set(false)
            }
        })
    }

    private fun saveDiagnostic(raw: String, method: String) {
        val fingerprint = raw.hashCode()
        if (fingerprint == lastRawFingerprint) return
        lastRawFingerprint = fingerprint
        val trimmed = raw.take(6000)
        settingsRepo.saveLatestCapture("Texto detectado; aguardando parser.", trimmed, method)
        LocalLog.append(this, "Captura $method (${trimmed.length} chars): ${trimmed.replace('\n', ' ').take(700)}")
        sendBroadcast(android.content.Intent(ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }

    private fun handleOffer(offer: RideOffer) {
        val now = System.currentTimeMillis()
        if (offer.dedupeKey == lastOfferKey && now - lastOfferAt < 30_000) return
        lastOfferKey = offer.dedupeKey
        lastOfferAt = now

        val summary = OfferParser.humanSummary(offer)
        settingsRepo.saveLatestCapture(summary, offer.rawText, offer.captureMethod)
        LocalLog.append(this, "OFERTA: $summary")
        overlay.show(offer)
        BackendClient.sendOffer(this, offer)
        sendBroadcast(android.content.Intent(ACTION_CAPTURE_UPDATED).setPackage(packageName))
    }
}
