package com.srrotas.app

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

/**
 * Motor auxiliar. MediaProjection é o fluxo principal.
 * Na 0.5 a árvore de acessibilidade não compete com MediaProjection durante uma jornada.
 */
class DriverAccessibilityService : AccessibilityService() {
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var dispatcher: OfferDispatcher
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)

    private var lastNodeReadAt = 0L
    private var lastScreenshotAt = 0L
    private var lastRawFingerprint = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepo = SettingsRepository(this)
        dispatcher = OfferDispatcher(this)
        LocalLog.append(this, "AccessibilityService auxiliar conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != AppSignals.UBER_PACKAGE) return

        val settings = settingsRepo.load()
        if (!settings.consentAccepted) return

        val now = System.currentTimeMillis()
        if (now - lastNodeReadAt < 300) return
        lastNodeReadAt = now

        val root = rootInActiveWindow
        val nodeText = root?.let { extractText(it) }.orEmpty()
        root?.recycle()

        if (nodeText.isNotBlank()) {
            saveDiagnosticOnce(nodeText, "accessibility-tree")
        }

        // MediaProjection é a fonte de verdade visual durante a jornada.
        // A árvore continua útil para diagnóstico, mas não despacha ofertas concorrentes.
        if (settingsRepo.isProjectionActive()) return

        if (nodeText.isNotBlank() && UberScreenGate.classify(nodeText) == UberScreenGate.Kind.OFFER_CANDIDATE) {
            val parsed = OfferParser.parse(
                nodeText,
                packageName,
                "accessibility-tree",
                settings,
                confidence = 0.72,
                offerType = if (nodeText.contains("radar de viagens", true) || nodeText.contains("selecionar", true)) "radar" else "exclusive",
            )
            if (parsed != null) {
                dispatcher.dispatch(parsed)
                return
            }
        }

        if (settings.ocrEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && now - lastScreenshotAt >= 1800) {
            lastScreenshotAt = now
            captureForOcr(packageName, settings)
        }
    }

    override fun onInterrupt() {
        dispatcher.hideOverlay()
        LocalLog.append(this, "AccessibilityService auxiliar interrompido")
    }

    override fun onDestroy() {
        if (::dispatcher.isInitialized) dispatcher.hideOverlay()
        runCatching { recognizer.close() }
        super.onDestroy()
    }

    private fun extractText(root: AccessibilityNodeInfo): String {
        val lines = ArrayList<String>(100)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return lines.distinct().joinToString("\n")
    }

    private fun captureForOcr(packageName: String, settings: DriverSettings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!ocrBusy.compareAndSet(false, true)) return

        dispatcher.hideOverlay()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                if (bitmap == null) { ocrBusy.set(false); return }

                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val gate = UberScreenGate.classify(result.text)
                        val offers = if (gate == UberScreenGate.Kind.OFFER_CANDIDATE) {
                            SpatialOfferParser.parse(
                                result = result,
                                sourcePackage = packageName,
                                captureMethod = "accessibility-screenshot-ocr",
                                settings = settings,
                                frameWidth = bitmap.width,
                                frameHeight = bitmap.height,
                            )
                        } else emptyList()
                        if (offers.isNotEmpty()) {
                            dispatcher.dispatchAll(offers)
                            if (settings.privateScreenshotEnabled) {
                                offers.maxByOrNull { it.confidence }?.let { PrivateScreenshotStore.save(this@DriverAccessibilityService, bitmap, it) }
                            }
                        } else if (gate != UberScreenGate.Kind.OWN_APP) {
                            saveDiagnosticOnce(result.text, "accessibility-screenshot-ocr")
                        }
                    }
                    .addOnFailureListener { LocalLog.append(this@DriverAccessibilityService, "OCR auxiliar falhou: ${it.message}") }
                    .addOnCompleteListener { bitmap.recycle(); ocrBusy.set(false) }
            }

            override fun onFailure(errorCode: Int) {
                LocalLog.append(this@DriverAccessibilityService, "Screenshot auxiliar falhou: código $errorCode")
                ocrBusy.set(false)
            }
        })
    }

    private fun saveDiagnosticOnce(raw: String, method: String) {
        if (raw.isBlank()) return
        val fingerprint = raw.hashCode()
        if (fingerprint == lastRawFingerprint) return
        lastRawFingerprint = fingerprint
        dispatcher.saveDiagnostic(raw, method)
    }
}
