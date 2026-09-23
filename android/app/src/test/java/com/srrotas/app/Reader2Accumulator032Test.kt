package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Reader2Accumulator032Test {
    @Before fun reset() = Reader2Accumulator032.resetRuntime()

    private fun candidate(
        fare: Double = 34.15,
        pickupKm: Double? = null,
        tripKm: Double? = null,
        pickupMinutes: Int? = null,
        tripMinutes: Int? = null,
        pickupLabel: String? = null,
        destinationLabel: String? = null,
        anchorY: Int = 300,
        confidence: Double = 0.78,
    ): Reader2ParallelRules031.Candidate {
        val totalKm = if (pickupKm != null && tripKm != null) pickupKm + tripKm else tripKm
        val totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else tripMinutes
        val core = pickupKm != null && tripKm != null && pickupMinutes != null && tripMinutes != null &&
            !pickupLabel.isNullOrBlank() && !destinationLabel.isNullOrBlank() && totalMinutes != null
        return Reader2ParallelRules031.Candidate(
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            totalKm = totalKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            pickupLabel = pickupLabel,
            destinationLabel = destinationLabel,
            anchorY = anchorY,
            confidence = confidence,
            coreComplete = core,
            splitGeometry = false,
            lineCount = 12,
            textChars = 420,
        )
    }

    private fun frame(vararg candidates: Reader2ParallelRules031.Candidate) =
        Reader2ParallelRules031.FrameObservation(
            uberCandidate = true,
            candidates = candidates.toList(),
            longCard = true,
            lineCount = 24,
            textChars = 900,
        )

    @Test fun twoPartialFramesCanBecomeCoreComplete() {
        val first = candidate(
            pickupKm = 1.2,
            pickupMinutes = 4,
            pickupLabel = "Av. Paulista, 1000",
        )
        val second = candidate(
            tripKm = 9.4,
            tripMinutes = 22,
            destinationLabel = "Rua Augusta, 500",
        )

        val a = Reader2Accumulator032.observe(frame(first), frameHeight = 1800, nowMs = 1_000L)
        assertFalse(a.candidates.single().coreComplete)

        val b = Reader2Accumulator032.observe(frame(second), frameHeight = 1800, nowMs = 2_000L)
        val merged = b.candidates.single()
        assertTrue(merged.coreComplete)
        assertTrue(merged.promotionReady)
        assertEquals(2, merged.observations)
        assertTrue(merged.recoveredFields >= 3)
        assertEquals(26, merged.totalMinutes!!)
    }

    @Test fun oneObservationNeverBecomesPromotionReady() {
        val complete = candidate(
            pickupKm = 1.0,
            tripKm = 8.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            pickupLabel = "Rua A, 10",
            destinationLabel = "Rua B, 20",
        )
        val observed = Reader2Accumulator032.observe(frame(complete), 1800, nowMs = 1_000L)
        assertTrue(observed.candidates.single().coreComplete)
        assertFalse(observed.candidates.single().promotionReady)
    }

    @Test fun repeatedCompatibleCompleteCandidateBecomesPromotionReady() {
        val complete = candidate(
            pickupKm = 1.0,
            tripKm = 8.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            pickupLabel = "Rua A, 10",
            destinationLabel = "Rua B, 20",
        )
        Reader2Accumulator032.observe(frame(complete), 1800, nowMs = 1_000L)
        val second = Reader2Accumulator032.observe(frame(complete.copy(anchorY = 307)), 1800, nowMs = 1_700L)
        assertTrue(second.candidates.single().promotionReady)
        assertEquals(2, second.candidates.single().observations)
    }

    @Test fun conflictingGeometryDoesNotOverwriteExistingWindow() {
        val first = candidate(
            pickupKm = 1.0,
            pickupMinutes = 4,
            pickupLabel = "Rua A, 10",
        )
        val conflict = candidate(
            pickupKm = 11.0,
            pickupMinutes = 4,
            pickupLabel = "Rua A, 10",
        )
        Reader2Accumulator032.observe(frame(first), 1800, nowMs = 1_000L)
        val result = Reader2Accumulator032.observe(frame(conflict), 1800, nowMs = 1_500L)
        assertEquals(1, result.candidates.single().observations)
        assertEquals(11.0, result.candidates.single().pickupKm!!, 0.001)
        assertFalse(result.candidates.single().promotionReady)
    }

    @Test fun expiredWindowIsNotMerged() {
        val partial = candidate(pickupKm = 1.0, pickupMinutes = 4, pickupLabel = "Rua A, 10")
        Reader2Accumulator032.observe(frame(partial), 1800, nowMs = 1_000L)
        val late = Reader2Accumulator032.observe(frame(partial), 1800, nowMs = 6_000L)
        assertEquals(1, late.candidates.single().observations)
    }

    @Test fun differentLabelsPreventUnsafeMerge() {
        val first = candidate(pickupKm = 1.0, pickupMinutes = 4, pickupLabel = "Rua A, 10")
        val other = candidate(pickupKm = 1.0, pickupMinutes = 4, pickupLabel = "Rua C, 30")
        Reader2Accumulator032.observe(frame(first), 1800, nowMs = 1_000L)
        val result = Reader2Accumulator032.observe(frame(other), 1800, nowMs = 1_500L)
        assertEquals(1, result.candidates.single().observations)
    }
}
