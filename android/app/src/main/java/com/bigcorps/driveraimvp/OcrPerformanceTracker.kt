package com.srrotas.app

import java.util.Locale

/**
 * Métricas locais e leves para validar o Offer Engine antes de congelá-lo.
 * Não envia dados, não guarda OCR e não participa da decisão financeira.
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

    @Synchronized fun reset() {
        sampledFrames = 0
        unchangedFrames = 0
        ocrRuns = 0
        queuedFrames = 0
        replacedPendingFrames = 0
        offerFrames = 0
        offersDetected = 0
        totalOcrMs = 0
        maxOcrMs = 0
    }

    @Synchronized fun sampled() { sampledFrames++ }
    @Synchronized fun unchanged() { unchangedFrames++ }

    @Synchronized fun queued(replacedPrevious: Boolean) {
        queuedFrames++
        if (replacedPrevious) replacedPendingFrames++
    }

    @Synchronized fun ocrCompleted(durationMs: Long, detectedOffers: Int) {
        ocrRuns++
        val safe = durationMs.coerceAtLeast(0L)
        totalOcrMs += safe
        if (safe > maxOcrMs) maxOcrMs = safe
        if (detectedOffers > 0) {
            offerFrames++
            offersDetected += detectedOffers
        }
    }

    @Synchronized fun snapshot(): Snapshot {
        val average = if (ocrRuns == 0L) 0.0 else totalOcrMs.toDouble() / ocrRuns
        return Snapshot(
            sampledFrames = sampledFrames,
            unchangedFrames = unchangedFrames,
            ocrRuns = ocrRuns,
            queuedFrames = queuedFrames,
            replacedPendingFrames = replacedPendingFrames,
            offerFrames = offerFrames,
            offersDetected = offersDetected,
            averageOcrMs = average,
            maxOcrMs = maxOcrMs,
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
    ) {
        fun logLine(): String =
            "DESEMPENHO OCR · amostras=$sampledFrames · inalterados=$unchangedFrames · " +
                "ocr=$ocrRuns · fila=$queuedFrames · substituídos=$replacedPendingFrames · " +
                "frames_com_oferta=$offerFrames · ofertas_detectadas=$offersDetected · " +
                "ocr_médio=${String.format(Locale.US, "%.0f", averageOcrMs)}ms · ocr_máx=${maxOcrMs}ms"
    }
}
