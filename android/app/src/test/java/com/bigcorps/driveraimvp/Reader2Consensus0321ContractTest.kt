package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Reader2Consensus0321ContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base -> sequenceOf(
                File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                File(base, "src/main/java/com/bigcorps/driveraimvp"),
                File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
            ) }.toList()
        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}")
    }

    private fun source(name: String): String = File(root, name).readText()

    @Test fun consensusObservesAfterAccumulatorAndM1Result() {
        val uber = source("UberSpatialParser0221.kt")
        assertTrue(uber.contains("Reader2Consensus0321.observe(accumulated, offers, frameHeight)"))
        assertTrue(uber.indexOf("Reader2Accumulator032.observeM1") < uber.indexOf("Reader2Consensus0321.observe"))
    }

    @Test fun consensusHasNoOfficialSideEffectsOrSecondOcr() {
        val consensus = source("Reader2Consensus0321.kt")
        listOf(
            "TextRecognition.getClient", "TextRecognizer", "client.process(",
            "LocalStore", "BackendClient", "OverlayController", "sendOffer(",
            "saveOffer(", "OfferDispatcher",
        ).forEach { forbidden ->
            assertFalse("Consensus não pode usar $forbidden", consensus.contains(forbidden))
        }
        assertTrue(consensus.contains("controlled_hybrid_effect\", false"))
        assertTrue(consensus.contains("MIN_DISTINCT_MS = 450L"))
        assertTrue(consensus.contains("WINDOW_MS = 8_000L"))
    }

    @Test fun diagnosticExportsConsensusAndRuntimeResetIncludesIt() {
        assertTrue(source("ReaderLabCombinedDiagnostic0270361.kt").contains("reader2_consensus_0321"))
        assertTrue(source("OfferAdmissionGate030.kt").contains("Reader2Consensus0321.resetRuntime()"))
    }

    @Test fun historyRemainsFrozen() {
        val history = source("RideHistoryPanel027035.kt")
        assertFalse(history.contains("Reader2Consensus0321"))
        assertTrue(history.contains("RideOperationalStatus.NOT_COMPLETED"))
    }
}
