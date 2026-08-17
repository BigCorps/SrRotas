package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test

class OcrPerformanceTrackerTest {
    @Test fun tracksQueueAndOcrLatencyWithoutUserData() {
        val tracker = OcrPerformanceTracker()
        tracker.sampled()
        tracker.sampled()
        tracker.unchanged()
        tracker.queued(replacedPrevious = false)
        tracker.queued(replacedPrevious = true)
        tracker.ocrCompleted(durationMs = 120, detectedOffers = 0)
        tracker.ocrCompleted(durationMs = 280, detectedOffers = 2)

        val s = tracker.snapshot()
        assertEquals(2, s.sampledFrames)
        assertEquals(1, s.unchangedFrames)
        assertEquals(2, s.ocrRuns)
        assertEquals(2, s.queuedFrames)
        assertEquals(1, s.replacedPendingFrames)
        assertEquals(1, s.offerFrames)
        assertEquals(2, s.offersDetected)
        assertEquals(200.0, s.averageOcrMs, 0.01)
        assertEquals(280, s.maxOcrMs)
        assertFalse(s.logLine().contains("R$"))
    }
}
