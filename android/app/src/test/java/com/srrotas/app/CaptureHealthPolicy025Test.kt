package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHealthPolicy025Test {
    private fun snapshot(
        now: Long = 30_000L,
        projection: Boolean = true,
        journey: Boolean = true,
        interactive: Boolean = true,
        workerAlive: Boolean = true,
        lastImage: Long = 25_000L,
        lastOcrCompleted: Long = 25_000L,
        ocrBusy: Boolean = false,
        ocrStarted: Long = 0L,
        lastRecovery: Long = 0L,
    ) = CaptureHealthPolicy025.Snapshot(
        nowMs = now,
        projectionActive = projection,
        journeyOwned = journey,
        screenInteractive = interactive,
        workerAlive = workerAlive,
        lastImageSeenAtMs = lastImage,
        lastOcrCompletedAtMs = lastOcrCompleted,
        ocrBusy = ocrBusy,
        ocrStartedAtMs = ocrStarted,
        lastRecoveryAtMs = lastRecovery,
    )

    @Test fun healthyCaptureDoesNothing() {
        assertEquals(CaptureHealthPolicy025.Action.NONE, CaptureHealthPolicy025.decide(snapshot()))
    }

    @Test fun deadWorkerIsRebuilt() {
        assertEquals(
            CaptureHealthPolicy025.Action.REBUILD_CAPTURE_WORKER,
            CaptureHealthPolicy025.decide(snapshot(workerAlive = false)),
        )
    }

    @Test fun noFramesForTwelveSecondsRearmsSurface() {
        assertEquals(
            CaptureHealthPolicy025.Action.REARM_CAPTURE_SURFACE,
            CaptureHealthPolicy025.decide(snapshot(lastImage = 18_000L)),
        )
    }

    @Test fun screenOffDoesNotCreateRecoveryLoop() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(snapshot(interactive = false, workerAlive = false, lastImage = 1_000L)),
        )
    }

    @Test fun missingOwnedJourneyDoesNotTouchProjection() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(snapshot(journey = false, workerAlive = false, lastImage = 1_000L)),
        )
    }

    @Test fun stuckOcrIsResetBeforeSurfaceRecovery() {
        assertEquals(
            CaptureHealthPolicy025.Action.RESET_OCR_PIPELINE,
            CaptureHealthPolicy025.decide(
                snapshot(lastImage = 29_000L, ocrBusy = true, ocrStarted = 12_000L),
            ),
        )
    }

    @Test fun framesWithoutOcrProgressResetPipeline() {
        assertEquals(
            CaptureHealthPolicy025.Action.RESET_OCR_PIPELINE,
            CaptureHealthPolicy025.decide(
                snapshot(lastImage = 29_500L, lastOcrCompleted = 5_000L),
            ),
        )
    }

    @Test fun recoveryCooldownPreventsRapidRepeatedRecovery() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(
                snapshot(workerAlive = false, lastImage = 1_000L, lastRecovery = 25_000L),
            ),
        )
    }
}
