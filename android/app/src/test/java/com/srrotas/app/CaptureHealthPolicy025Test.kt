package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHealthPolicy025Test {
    private fun snapshot(
        now: Long = 30_000L,
        projection: Boolean = true,
        journey: Boolean = true,
        interactive: Boolean = true,
        lastImage: Long = 25_000L,
        ocrBusy: Boolean = false,
        ocrStarted: Long = 0L,
        lastRecovery: Long = 0L,
    ) = CaptureHealthPolicy025.Snapshot(
        nowMs = now,
        projectionActive = projection,
        journeyOwned = journey,
        screenInteractive = interactive,
        lastImageSeenAtMs = lastImage,
        ocrBusy = ocrBusy,
        ocrStartedAtMs = ocrStarted,
        lastRecoveryAtMs = lastRecovery,
    )

    @Test
    fun healthyCaptureDoesNothing() {
        assertEquals(CaptureHealthPolicy025.Action.NONE, CaptureHealthPolicy025.decide(snapshot()))
    }

    @Test
    fun noFramesForTwelveSecondsRearmsSurface() {
        assertEquals(
            CaptureHealthPolicy025.Action.REARM_CAPTURE_SURFACE,
            CaptureHealthPolicy025.decide(snapshot(lastImage = 18_000L)),
        )
    }

    @Test
    fun screenOffDoesNotCreateRecoveryLoop() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(snapshot(interactive = false, lastImage = 1_000L)),
        )
    }

    @Test
    fun missingOwnedJourneyDoesNotTouchProjection() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(snapshot(journey = false, lastImage = 1_000L)),
        )
    }

    @Test
    fun stuckOcrIsResetBeforeSurfaceRecovery() {
        assertEquals(
            CaptureHealthPolicy025.Action.RESET_OCR_PIPELINE,
            CaptureHealthPolicy025.decide(
                snapshot(
                    lastImage = 29_000L,
                    ocrBusy = true,
                    ocrStarted = 12_000L,
                ),
            ),
        )
    }

    @Test
    fun recoveryCooldownPreventsRapidRepeatedRearms() {
        assertEquals(
            CaptureHealthPolicy025.Action.NONE,
            CaptureHealthPolicy025.decide(
                snapshot(
                    lastImage = 1_000L,
                    lastRecovery = 25_000L,
                ),
            ),
        )
    }
}
