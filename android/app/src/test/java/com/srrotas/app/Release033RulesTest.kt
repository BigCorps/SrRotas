package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Release033RulesTest {
    @Before fun reset() = ScreenshotStorageGuard033.resetRuntime()

    private fun shot(
        fare: Double = 34.15,
        pickup: Double? = 1.2,
        trip: Double? = 9.4,
        total: Double? = 10.6,
    ) = ScreenshotStorageGuard033.Candidate("uber", fare, pickup, trip, total)

    @Test fun repeatedFramesOfSameOfferProduceOneScreenshot() {
        assertTrue(ScreenshotStorageGuard033.allow(shot(), 1_000L))
        assertFalse(ScreenshotStorageGuard033.allow(shot(), 1_300L))
        assertFalse(ScreenshotStorageGuard033.allow(shot(trip = 9.5, total = 10.7), 2_000L))
        val s = ScreenshotStorageGuard033.snapshot()
        assertEquals(3L, s.attempts)
        assertEquals(1L, s.accepted)
        assertEquals(2L, s.duplicateSkipped)
    }

    @Test fun sameOfferCanBeCapturedAgainAfterWindow() {
        assertTrue(ScreenshotStorageGuard033.allow(shot(), 1_000L))
        assertTrue(ScreenshotStorageGuard033.allow(shot(), 61_001L))
    }

    @Test fun distinctOfferIsNotSuppressed() {
        assertTrue(ScreenshotStorageGuard033.allow(shot(), 1_000L))
        assertTrue(ScreenshotStorageGuard033.allow(shot(fare = 41.20, trip = 12.0, total = 13.2), 1_500L))
    }

    @Test fun responsiveWidthUsesTabletSpace() {
        assertEquals(388, ResponsivePolicy033.contentWidthDp(412))
        assertEquals(776, ResponsivePolicy033.contentWidthDp(800))
        assertEquals(1256, ResponsivePolicy033.contentWidthDp(1280))
    }

    @Test fun developStatusIsCompactAndNamesReader() {
        assertEquals("OK ✓ — M1/2.0", DevelopStatus033.label(ReaderLab027036.MODE_M1, true, false))
        assertEquals("OK ✓ — M2", DevelopStatus033.label(ReaderLab027036.MODE_M2, true, false))
        assertEquals("OK ✓ — M1/M2/2.0", DevelopStatus033.label(ReaderLab027036.MODE_COMPARE, true, false))
        assertEquals("⚠ Retomar — M1/2.0", DevelopStatus033.label(ReaderLab027036.MODE_M1, false, true))
    }
}
