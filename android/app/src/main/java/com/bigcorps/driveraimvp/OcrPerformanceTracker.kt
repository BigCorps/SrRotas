package com.srrotas.app

import java.util.Locale

/**
 * Métricas locais e leves de performance.
 * Não envia OCR bruto nem participa da decisão financeira.
 */
class OcrPerformanceTracker {
    private var sampledFrames = 0L
    private var unchangedFrames = 0L
    private var ocrRuns = 0L
    private var queuedFrames = 0L
    private var replacedPendingFrames = 0L
    private var offerFrames = 0L
    private var offersDetected = 0L
    private var totalOcrMs = 0L
    private var maxOcrMs = 0L

    // 0.16: mede somente o trecho OCR -> dispatcher, para detectar regressão.
    private var dispatchRuns = 0L
    private var totalDispatchMs = 0L
    private var maxDispatchMs = 0L
    private var slowDispatches = 0L

    @Synchronized
    fun reset() {
        sampledFrames = 0
        unchangedFrames = 0
        ocrRuns = 0
        queuedFrames = 0
        replacedPendingFrames = 0
        offerFrames = 0
        offersDetected = 0
        totalOcrMs = 0
        maxOcrMs = 0
        dispatchRuns = 0
        totalDispatchMs = 0
        maxDispatchMs = 0
        slowDispatches = 0
    }

    @Synchronized fun sampled() { sampledFrames++ }
    @Synchronized fun unchanged() { unchangedFrames++ }

    @Synchronized
    fun queued(replacedPrevious: Boolean) {
        queuedFrames++
        if (replacedPrevious) replacedPendingFrames++
    }

    @Synchronized
    fun ocrCompleted(durationMs: Long, detectedOffers: Int) {
        ocrRuns++
        val safe = durationMs.coerceAtLeast(0L)
        totalOcrMs += safe
        if (safe > maxOcrMs) maxOcrMs = safe
        if (detectedOffers > 0) {
            offerFrames++
            offersDetected += detectedOffers
        }
    }

    @Synchronized
    fun dispatchCompleted(durationMs: Long) {
        dispatchRuns++
        val safe = durationMs.coerceAtLeast(0L)
        totalDispatchMs += safe
        if (safe > maxDispatchMs) maxDispatchMs = safe
        if (safe >= 120L) slowDispatches++
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val averageOcr =
            if (ocrRuns == 0L) 0.0 else totalOcrMs.toDouble() / ocrRuns
        val averageDispatch =
            if (dispatchRuns == 0L) 0.0 else totalDispatchMs.toDouble() / dispatchRuns

        return Snapshot(
            sampledFrames = sampledFrames,
            unchangedFrames = unchangedFrames,
            ocrRuns = ocrRuns,
            queuedFrames = queuedFrames,
            replacedPendingFrames = replacedPendingFrames,
            offerFrames = offerFrames,
            offersDetected = offersDetected,
            averageOcrMs = averageOcr,
            maxOcrMs = maxOcrMs,
            dispatchRuns = dispatchRuns,
            averageDispatchMs = averageDispatch,
            maxDispatchMs = maxDispatchMs,
            slowDispatches = slowDispatches,
        )
    }

    data class Snapshot(
        val sampledFrames: Long,
        val unchangedFrames: Long,
        val ocrRuns: Long,
        val queuedFrames: Long,
        val replacedPendingFrames: Long,
        val offerFrames: Long,
        val offersDetected: Long,
        val averageOcrMs: Double,
        val maxOcrMs: Long,
        val dispatchRuns: Long,
        val averageDispatchMs: Double,
        val maxDispatchMs: Long,
        val slowDispatches: Long,
    ) {
        fun logLine(): String =
            "DESEMPENHO OCR · amostras=$sampledFrames · inalterados=$unchangedFrames · " +
                "ocr=$ocrRuns · fila=$queuedFrames · substituídos=$replacedPendingFrames · " +
                "frames_com_oferta=$offerFrames · ofertas_detectadas=$offersDetected · " +
                "ocr_médio=${String.format(Locale.US, "%.0f", averageOcrMs)}ms · " +
                "ocr_máx=${maxOcrMs}ms · dispatch=$dispatchRuns · " +
                "dispatch_médio=${String.format(Locale.US, "%.0f", averageDispatchMs)}ms · " +
                "dispatch_máx=${maxDispatchMs}ms · dispatch_lento=$slowDispatches"
    }
}
