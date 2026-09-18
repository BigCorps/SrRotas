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
 * Método 2 experimental da RC3.6.
 *
 * Observa somente Uber Driver, não executa ações e não despacha ofertas para a
 * base oficial. A árvore é tentada primeiro; screenshot da janela + ML Kit é
 * fallback local. Resultado alimenta apenas ReaderLab027036 (shadow mode).
 */
class DriverAccessibilityService : AccessibilityService() {
    private lateinit var repo: SettingsRepository
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val ocrBusy = AtomicBoolean(false)
    private var lastNodeReadAt = 0L
    private var lastScreenshotAt = 0L
    private var lastRawFingerprint = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = SettingsRepository(this)
        LocalLog.append(this, "RC3.6 Reader M2 conectado · Accessibility shadow")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::repo.isInitialized) return
        if (event.packageName?.toString() != AppSignals.UBER_PACKAGE) return
        if (!ReaderLab027036.m2Enabled(this)) return
        if (!ReaderLab027036.disclosureAccepted(this)) return
        if (!repo.load().consentAccepted) return
        if (repo.currentJourneyId().isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastNodeReadAt < 260L) return
        lastNodeReadAt = now

        val root = rootInActiveWindow ?: return
        val bounds = Rect().also(root::getBoundsInScreen)
        ReaderLab027036.updateUberBounds(this, bounds)
        val windowId = root.windowId
        val tree = extractTree(root)
        val nodeText = tree.text
        @Suppress("DEPRECATION")
        runCatching { root.recycle() }

        var treeSucceeded = false
        if (nodeText.isNotBlank() && UberScreenGate.classify(nodeText) == UberScreenGate.Kind.OFFER_CANDIDATE) {
            OfferParser.parse(
                rawText = nodeText,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "accessibility-tree-rc36",
                settings = repo.load(),
                confidence = 0.72,
                offerType = if (nodeText.contains("radar de viagens", true) || nodeText.contains("selecionar", true)) "radar" else "exclusive",
            )?.let { parsed ->
                val context = OfferContextExtractor0221.extract(tree.spatial, parsed.observedAt, parsed.totalMinutes)
                parsed.copy(captureMethod = "accessibility-tree-rc36", context = context)
            }?.let { offer ->
                ReaderLab027036.recordM2(this, offer)
                treeSucceeded = true
            }
        }

        // Mesmo quando a árvore funciona, uma amostra visual ocasional ajuda a
        // medir campos que a árvore pode omitir. Mantemos throttle conservador.
        val visualInterval = if (treeSucceeded) 2_500L else 900L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && now - lastScreenshotAt >= visualInterval) {
            lastScreenshotAt = now
            captureForOcr(windowId, bounds)
        } else if (!treeSucceeded && nodeText.isNotBlank()) {
            saveDiagnosticOnce(nodeText, "accessibility-tree-candidate-rc36")
        }
    }

    override fun onInterrupt() {
        LocalLog.append(this, "RC3.6 Reader M2 interrompido pelo Android")
    }

    override fun onDestroy() {
        runCatching { recognizer.close() }
        super.onDestroy()
    }

    private data class TreeSnapshot(val text: String, val spatial: List<SpatialOcrLine>)

    private fun extractTree(root: AccessibilityNodeInfo): TreeSnapshot {
        val lines = ArrayList<String>(120)
        val spatial = ArrayList<SpatialOcrLine>(120)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        @Suppress("DEPRECATION")
        queue.add(AccessibilityNodeInfo.obtain(root))
        var visited = 0
        while (queue.isNotEmpty() && visited < 900) {
            val node = queue.removeFirst()
            visited++
            val box = Rect().also(node::getBoundsInScreen)
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { value ->
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!ocrBusy.compareAndSet(false, true)) return

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val raw = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                if (raw == null) {
                    ocrBusy.set(false)
                    return
                }

                val windowSpecific = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                val bitmap = if (windowSpecific) raw else cropFullDisplay(raw, bounds)
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val routed = DriverPlatformOfferRouter.parse(
                            result = result,
                            settings = repo.load(),
                            frameWidth = bitmap.width,
                            frameHeight = bitmap.height,
                        )
                        val spatial = OfferSpatialIsolation0221.lines(result)
                        val uber = routed.offers.filter { it.platform.equals("uber", true) }
                            .map { offer ->
                                val enriched = if (offer.context?.hasTextContext() == true) offer else OfferContextExtractor0221.attach(offer, spatial)
                                enriched.copy(sourcePackage = AppSignals.UBER_PACKAGE, captureMethod = "accessibility-window-ocr/uber-rc36")
                            }
                        if (uber.isNotEmpty()) {
                            uber.forEach { ReaderLab027036.recordM2(this@DriverAccessibilityService, it) }
                            if (repo.load().privateScreenshotEnabled) {
                                uber.maxByOrNull { it.confidence }?.let { PrivateScreenshotStore.save(this@DriverAccessibilityService, bitmap, it) }
                            }
                        } else if (routed.candidate) {
                            saveDiagnosticOnce(result.text, "accessibility-window-ocr/uber-rc36")
                        }
                    }
                    .addOnFailureListener {
                        LocalLog.append(this@DriverAccessibilityService, "Reader M2 OCR falhou: ${it.message}")
                    }
                    .addOnCompleteListener {
                        if (bitmap !== raw && !bitmap.isRecycled) bitmap.recycle()
                        if (!raw.isRecycled) raw.recycle()
                        ocrBusy.set(false)
                    }
            }

            override fun onFailure(errorCode: Int) {
                LocalLog.append(this@DriverAccessibilityService, "Reader M2 screenshot falhou: código $errorCode")
                ocrBusy.set(false)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            takeScreenshotOfWindow(windowId, mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        }
    }

    private fun cropFullDisplay(source: Bitmap, bounds: Rect): Bitmap {
        val l = bounds.left.coerceIn(0, source.width - 1)
        val t = bounds.top.coerceIn(0, source.height - 1)
        val r = bounds.right.coerceIn(l + 1, source.width)
        val b = bounds.bottom.coerceIn(t + 1, source.height)
        val w = r - l
        val h = b - t
        if (w < source.width * .30 || h < source.height * .30) return source
        if (l == 0 && t == 0 && r == source.width && b == source.height) return source
        return runCatching { Bitmap.createBitmap(source, l, t, w, h) }.getOrDefault(source)
    }

    private fun saveDiagnosticOnce(raw: String, method: String) {
        val clean = DriverOcrNormalizer.sanitize(raw)
        if (clean.isBlank()) return
        val fp = clean.hashCode()
        if (fp == lastRawFingerprint) return
        lastRawFingerprint = fp
        SettingsRepository(this).saveLatestCapture(
            "Reader M2 detectou conteúdo candidato sem oferta completa.",
            clean,
            method,
        )
        LocalLog.append(this, "ReaderLab M2 diagnóstico · $method · ${clean.replace('\n', ' ').take(500)}")
    }
}
