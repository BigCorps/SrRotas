package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPerformanceTrackerTest {
    @Test
    fun tracksDispatchCostSeparatelyFromOcr() {
        val tracker = OcrPerformanceTracker()

        tracker.sampled()
        tracker.ocrCompleted(240, 1)
        tracker.dispatchCompleted(18)
        tracker.dispatchCompleted(150)

        val snapshot = tracker.snapshot()

        assertEquals(1L, snapshot.ocrRuns)
        assertEquals(2L, snapshot.dispatchRuns)
        assertEquals(84.0, snapshot.averageDispatchMs, 0.001)
        assertEquals(150L, snapshot.maxDispatchMs)
        assertEquals(1L, snapshot.slowDispatches)
        assertTrue(snapshot.logLine().contains("dispatch_médio=84ms"))
    }
}
