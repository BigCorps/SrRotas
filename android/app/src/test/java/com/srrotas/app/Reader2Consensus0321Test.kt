package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Reader2Consensus0321Test {
    @Before fun reset() = Reader2Consensus0321.resetRuntime()

    private fun candidate(
        fare: Double = 34.15,
        pickupKm: Double = 1.2,
        tripKm: Double = 9.4,
        pickupMinutes: Int = 4,
        tripMinutes: Int = 22,
        pickupLabel: String = "Av. Paulista, 1000",
        destinationLabel: String = "Rua Augusta, 500",
        anchorY: Int = 300,
        confidence: Double = 0.80,
    ) = Reader2Accumulator032.Candidate(
        fare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        totalKm = pickupKm + tripKm,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        totalMinutes = pickupMinutes + tripMinutes,
        pickupLabel = pickupLabel,
        destinationLabel = destinationLabel,
        anchorY = anchorY,
        confidence = confidence,
        observations = 1,
        recoveredFields = 0,
        conflictFree = true,
        coreComplete = true,
        promotionReady = false,
    )

    private fun observation(candidate: Reader2Accumulator032.Candidate) =
        Reader2Accumulator032.Observation(
            candidates = listOf(candidate),
            sourceCandidates = 1,
            coreComplete = 1,
            promotionReady = 0,
        )

    @Test fun oneReader2OnlyCoreCandidateDoesNotBecomeReady() {
        Reader2Consensus0321.observe(observation(candidate()), emptyList(), 1800, 1_000L)
        val snapshot = Reader2Consensus0321.diagnosticSnapshot()
        assertEquals(1L, snapshot.reader2OnlyCoreSeen)
        assertEquals(0L, snapshot.consensusReadyReader2Only)
    }

    @Test fun immediateDuplicateIsSuppressedButLaterStableFrameConfirms() {
        val c = candidate()
        Reader2Consensus0321.observe(observation(c), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(observation(c.copy(anchorY = 302)), emptyList(), 1800, 1_200L)
        var snapshot = Reader2Consensus0321.diagnosticSnapshot()
        assertEquals(1L, snapshot.duplicateFrameSuppressed)
        assertEquals(0L, snapshot.consensusReadyReader2Only)

        Reader2Consensus0321.observe(observation(c.copy(anchorY = 305)), emptyList(), 1800, 1_700L)
        snapshot = Reader2Consensus0321.diagnosticSnapshot()
        assertEquals(1L, snapshot.confirmationsAccepted)
        assertEquals(1L, snapshot.consensusReadyReader2Only)
    }

    @Test fun conflictingCoreInvalidatesConsensusWindow() {
        val c = candidate()
        Reader2Consensus0321.observe(observation(c), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(observation(c.copy(tripMinutes = 30, totalMinutes = 34)), emptyList(), 1800, 1_700L)
        val snapshot = Reader2Consensus0321.diagnosticSnapshot()
        assertTrue(snapshot.conflictsDetected >= 1L)
        assertEquals(0L, snapshot.consensusReadyReader2Only)
    }

    @Test fun differentRouteStartsIndependentConsensusWindow() {
        Reader2Consensus0321.observe(observation(candidate()), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(
            observation(candidate(pickupLabel = "Rua C, 10", destinationLabel = "Rua D, 20", anchorY = 700)),
            emptyList(),
            1800,
            1_700L,
        )
        val snapshot = Reader2Consensus0321.diagnosticSnapshot()
        assertEquals(2L, snapshot.consensusWindowsStarted)
        assertEquals(0L, snapshot.consensusReadyReader2Only)
    }
}
