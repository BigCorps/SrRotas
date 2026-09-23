package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Reader2Accumulator032ContractTest {
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

    @Test fun accumulatorRunsBeforeM1FareSelectionAndComparesAfterM1() {
        val uber = source("UberSpatialParser0221.kt")
        val inspect = uber.indexOf("Reader2Accumulator032.observe(parallel")
        val m1Fare = uber.indexOf("MoneyRoleResolver030.primarySpatialFareLines(lines)")
        assertTrue(inspect >= 0)
        assertTrue(m1Fare >= 0)
        assertTrue(inspect < m1Fare)
        assertTrue(uber.contains("Reader2Accumulator032.observeM1"))
    }

    @Test fun accumulatorHasNoOfficialSideEffectsOrSecondOcr() {
        val accumulator = source("Reader2Accumulator032.kt")
        listOf(
            "TextRecognition.getClient", "TextRecognizer", "client.process(",
            "LocalStore", "BackendClient", "OverlayController", "sendOffer(",
            "saveOffer(", "OfferDispatcher",
        ).forEach { forbidden ->
            assertFalse("Accumulator não pode usar $forbidden", accumulator.contains(forbidden))
        }
        assertTrue(accumulator.contains("promotion_effect\", false"))
        assertTrue(accumulator.contains("observations >= 2"))
        assertTrue(accumulator.contains("WINDOW_MS = 4_500L"))
    }

    @Test fun diagnosticExportsAccumulator032() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("reader2_accumulator_032"))
        assertTrue(diagnostic.contains("Reader2Accumulator032.toJson()"))
    }

    @Test fun runtimeResetIncludesParallelAndAccumulator() {
        val admission = source("OfferAdmissionGate030.kt")
        assertTrue(admission.contains("Reader2Parallel031.resetRuntime()"))
        assertTrue(admission.contains("Reader2Accumulator032.resetRuntime()"))
    }

    @Test fun historyRemainsFrozen() {
        val history = source("RideHistoryPanel027035.kt")
        assertFalse(history.contains("Reader2Accumulator032"))
        assertTrue(history.contains("RideOperationalStatus.NOT_COMPLETED"))
    }
}
