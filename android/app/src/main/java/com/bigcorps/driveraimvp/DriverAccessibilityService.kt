package com.srrotas.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M2 consolidado.
 * Compare = árvore somente, para não disputar ML Kit com M1.
 * M2 isolado = árvore + screenshot/OCR, sem persistência na base oficial.
 */
class DriverAccessibilityService : AccessibilityService() {
    private lateinit var repo: SettingsRepository
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)
    private val preview by lazy { OverlayController(this) }
    private var lastNodeReadAt = 0L
    private var lastScreenshotAt = 0L
    private var lastRawFingerprint = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = SettingsRepository(this)
        ReaderLabTelemetry0270361.serviceConnected(this)
        LocalLog.append(this, "RC3.7 Reader M2 conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::repo.isInitialized) return
        if (event.packageName?.toString() != AppSignals.UBER_PACKAGE) return
        ReaderLabTelemetry0270361.uberEvent(this)
        val mode = ReaderLab027036.mode(this)
        if (mode == ReaderLab027036.MODE_M1) return
        if (!ReaderLab027036.disclosureAccepted(this) || !repo.load().consentAccepted || repo.currentJourneyId().isBlank()) return
        ReaderLabTelemetry0270361.eligibleEvent(this)

        val now = System.currentTimeMillis()
        val nodeThrottle = if (mode == ReaderLab027036.MODE_COMPARE) 650L else 300L
        if (now - lastNodeReadAt < nodeThrottle) return
        lastNodeReadAt = now

        val root = rootInActiveWindow ?: return
        ReaderLabTelemetry0270361.treeAttempt(this)
        val bounds = Rect().also(root::getBoundsInScreen)
        ReaderLab027036.updateUberBounds(this, bounds)
        val windowId = root.windowId
        val tree = extractTree(root, if (mode == ReaderLab027036.MODE_COMPARE) 600 else 900)
        @Suppress("DEPRECATION")
        runCatching { root.recycle() }

        var treeSucceeded = false
        if (tree.text.isNotBlank() && UberScreenGate.classify(tree.text) == UberScreenGate.Kind.OFFER_CANDIDATE) {
            ReaderLabTelemetry0270361.treeCandidate(this)
            OfferParser.parse(
                rawText = tree.text,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "accessibility-tree-rc37",
                settings = repo.load(),
                confidence = 0.72,
                offerType = if (tree.text.contains("radar de viagens", true) || tree.text.contains("selecionar", true)) "radar" else "exclusive",
            )?.let { parsed ->
                val context = OfferContextExtractor0221.extract(tree.spatial, parsed.observedAt, parsed.totalMinutes)
                parsed.copy(captureMethod = "accessibility-tree-rc37", context = context)
            }?.let { offer ->
                ReaderLabTelemetry0270361.treeOffer(this)
                ReaderLab027036.recordM2(this, offer)
                if (mode == ReaderLab027036.MODE_M2) preview.show(offer)
                treeSucceeded = true
            }
        }

        // Comparativo deliberadamente não roda um segundo ML Kit: mede a árvore
        // sem concorrer com o OCR do M1. OCR M2 só existe no modo isolado.
        if (mode == ReaderLab027036.MODE_COMPARE) {
            if (!treeSucceeded && tree.text.isNotBlank()) logCandidate(tree.text, "accessibility-tree-compare")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && now - lastScreenshotAt >= if (treeSucceeded) 2_500L else 1_100L) {
            lastScreenshotAt = now
            captureForOcr(windowId, bounds)
        } else if (!treeSucceeded && tree.text.isNotBlank()) {
            saveM2Diagnostic(tree.text, "accessibility-tree-m2")
        }
    }

    override fun onInterrupt() { LocalLog.append(this, "RC3.7 Reader M2 interrompido pelo Android") }
    override fun onDestroy() { runCatching { recognizer.close() }; super.onDestroy() }

    private data class TreeSnapshot(val text: String, val spatial: List<SpatialOcrLine>)

    private fun extractTree(root: AccessibilityNodeInfo, maxNodes: Int): TreeSnapshot {
        val lines = ArrayList<String>(120)
        val spatial = ArrayList<SpatialOcrLine>(120)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        @Suppress("DEPRECATION")
        queue.add(AccessibilityNodeInfo.obtain(root))
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst(); visited++
            val box = Rect().also(node::getBoundsInScreen)
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).map { it.trim() }.filter { it.isNotBlank() }.forEach { value ->
                lines += value
                if (box.width() > 0 && box.height() > 0) spatial += SpatialOcrLine(value, Rect(box))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            @Suppress("DEPRECATION")
            runCatching { node.recycle() }
        }
        return TreeSnapshot(lines.distinct().joinToString("\n"), spatial.distinctBy { "${it.text}|${it.box}" })
    }

    private fun captureForOcr(windowId: Int, bounds: Rect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !ocrBusy.compareAndSet(false, true)) return
        ReaderLabTelemetry0270361.screenshotAttempt(this)
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                ReaderLabTelemetry0270361.screenshotSuccess(this@DriverAccessibilityService)
                val buffer = screenshot.hardwareBuffer
                val raw = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                if (raw == null) { ocrBusy.set(false); return }
                val windowSpecific = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                val bitmap = if (windowSpecific) raw else cropFullDisplay(raw, bounds)
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val routed = DriverPlatformOfferRouter.parse(result, repo.load(), bitmap.width, bitmap.height)
                        if (routed.candidate) ReaderLabTelemetry0270361.visualCandidate(this@DriverAccessibilityService)
                        val spatial = OfferSpatialIsolation0221.lines(result)
                        val uber = routed.offers.filter { it.platform.equals("uber", true) }.map { offer ->
                            val enriched = if (offer.context?.hasTextContext() == true) offer else OfferContextExtractor0221.attach(offer, spatial)
                            enriched.copy(sourcePackage = AppSignals.UBER_PACKAGE, captureMethod = "accessibility-window-ocr/uber-rc37")
                        }
                        if (uber.isNotEmpty()) {
                            uber.forEach { offer ->
                                ReaderLabTelemetry0270361.visualOffer(this@DriverAccessibilityService)
                                ReaderLab027036.recordM2(this@DriverAccessibilityService, offer)
                                preview.show(offer)
                            }
                            if (repo.load().privateScreenshotEnabled) uber.maxByOrNull { it.confidence }?.let { PrivateScreenshotStore.save(this@DriverAccessibilityService, bitmap, it) }
                        } else if (routed.candidate) saveM2Diagnostic(result.text, "accessibility-window-ocr/uber-rc37")
                    }
                    .addOnFailureListener { LocalLog.append(this@DriverAccessibilityService, "Reader M2 OCR falhou: ${it.message}") }
                    .addOnCompleteListener {
                        if (bitmap !== raw && !bitmap.isRecycled) bitmap.recycle()
                        if (!raw.isRecycled) raw.recycle()
                        ocrBusy.set(false)
                    }
            }

            override fun onFailure(errorCode: Int) {
                ReaderLabTelemetry0270361.screenshotFailure(this@DriverAccessibilityService, errorCode)
                LocalLog.append(this@DriverAccessibilityService, "Reader M2 screenshot falhou: código $errorCode")
                ocrBusy.set(false)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) takeScreenshotOfWindow(windowId, mainExecutor, callback)
        else {
            @Suppress("DEPRECATION")
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        }
    }

    private fun cropFullDisplay(source: Bitmap, bounds: Rect): Bitmap {
        val l = bounds.left.coerceIn(0, source.width - 1); val t = bounds.top.coerceIn(0, source.height - 1)
        val r = bounds.right.coerceIn(l + 1, source.width); val b = bounds.bottom.coerceIn(t + 1, source.height)
        val w = r - l; val h = b - t
        if (w < source.width * .30 || h < source.height * .30) return source
        if (l == 0 && t == 0 && r == source.width && b == source.height) return source
        return runCatching { Bitmap.createBitmap(source, l, t, w, h) }.getOrDefault(source)
    }

    private fun logCandidate(raw: String, method: String) {
        val clean = DriverOcrNormalizer.sanitize(raw)
        if (clean.isBlank()) return
        val fp = clean.hashCode(); if (fp == lastRawFingerprint) return; lastRawFingerprint = fp
        LocalLog.append(this, "ReaderLab M2 shadow · $method · ${clean.replace('\n', ' ').take(500)}")
    }

    private fun saveM2Diagnostic(raw: String, method: String) {
        val clean = DriverOcrNormalizer.sanitize(raw)
        if (clean.isBlank()) return
        val fp = clean.hashCode(); if (fp == lastRawFingerprint) return; lastRawFingerprint = fp
        SettingsRepository(this).saveLatestCapture("Reader M2 detectou conteúdo candidato sem oferta completa.", clean, method)
        LocalLog.append(this, "ReaderLab M2 diagnóstico · $method · ${clean.replace('\n', ' ').take(500)}")
    }
}
