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
        val json = Reader2Consensus0321.toJson()
        assertEquals(1L, json.getLong("reader2_only_core_seen"))
        assertEquals(0L, json.getLong("consensus_ready_reader2_only"))
    }

    @Test fun immediateDuplicateIsSuppressedButLaterStableFrameConfirms() {
        val c = candidate()
        Reader2Consensus0321.observe(observation(c), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(observation(c.copy(anchorY = 302)), emptyList(), 1800, 1_200L)
        var json = Reader2Consensus0321.toJson()
        assertEquals(1L, json.getLong("duplicate_frame_suppressed"))
        assertEquals(0L, json.getLong("consensus_ready_reader2_only"))

        Reader2Consensus0321.observe(observation(c.copy(anchorY = 305)), emptyList(), 1800, 1_700L)
        json = Reader2Consensus0321.toJson()
        assertEquals(1L, json.getLong("confirmations_accepted"))
        assertEquals(1L, json.getLong("consensus_ready_reader2_only"))
    }

    @Test fun conflictingCoreInvalidatesConsensusWindow() {
        val c = candidate()
        Reader2Consensus0321.observe(observation(c), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(observation(c.copy(tripMinutes = 30, totalMinutes = 34)), emptyList(), 1800, 1_700L)
        val json = Reader2Consensus0321.toJson()
        assertTrue(json.getLong("conflicts_detected") >= 1L)
        assertEquals(0L, json.getLong("consensus_ready_reader2_only"))
    }

    @Test fun differentRouteStartsIndependentConsensusWindow() {
        Reader2Consensus0321.observe(observation(candidate()), emptyList(), 1800, 1_000L)
        Reader2Consensus0321.observe(
            observation(candidate(pickupLabel = "Rua C, 10", destinationLabel = "Rua D, 20", anchorY = 700)),
            emptyList(),
            1800,
            1_700L,
        )
        val json = Reader2Consensus0321.toJson()
        assertEquals(2L, json.getLong("consensus_windows_started"))
        assertEquals(0L, json.getLong("consensus_ready_reader2_only"))
    }
}
