package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureResilience0311Test {
    @Test fun recoveryAppearsOnlyForOpenM1JourneyWithoutProjection() {
        assertTrue(CaptureResilienceRules0311.shouldShowRecovery(true, true, false))
        assertFalse(CaptureResilienceRules0311.shouldShowRecovery(true, true, true))
        assertFalse(CaptureResilienceRules0311.shouldShowRecovery(false, true, false))
        assertFalse(CaptureResilienceRules0311.shouldShowRecovery(true, false, false))
    }

    @Test fun knownInterruptionReasonsStayAuditable() {
        assertEquals("projection_stopped_by_system", CaptureResilienceRules0311.reasonBucket("projection_stopped_by_system"))
        assertEquals("service_destroyed", CaptureResilienceRules0311.reasonBucket("service_destroyed"))
        assertEquals("other", CaptureResilienceRules0311.reasonBucket("missing_result_data"))
    }

    @Test fun downtimeNeverBecomesNegative() {
        assertEquals(5_000L, CaptureResilienceRules0311.durationMs(10_000L, 15_000L))
        assertEquals(0L, CaptureResilienceRules0311.durationMs(0L, 15_000L))
        assertEquals(0L, CaptureResilienceRules0311.durationMs(20_000L, 15_000L))
    }
}
