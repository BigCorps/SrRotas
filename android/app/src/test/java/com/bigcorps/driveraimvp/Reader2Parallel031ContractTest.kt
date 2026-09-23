package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Reader2Parallel031ContractTest {
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

    @Test fun reader2ReceivesSpatialFrameBeforeM1FareSelection() {
        val uber = source("UberSpatialParser0221.kt")
        val reader2 = uber.indexOf("Reader2Parallel031.inspectFrame")
        val m1Fare = uber.indexOf("MoneyRoleResolver030.primarySpatialFareLines(lines)")
        assertTrue(reader2 >= 0)
        assertTrue(m1Fare >= 0)
        assertTrue("Reader 2 precisa observar antes da seleção M1", reader2 < m1Fare)
        assertTrue(uber.contains("Reader2Parallel031.observeM1"))
    }

    @Test fun parallelBuilderIsIndependentFromM1Parser() {
        val parallel = source("Reader2Parallel031.kt")
        assertFalse(parallel.contains("OfferParser.parse("))
        assertFalse(parallel.contains("UberOfferDetector.detect("))
        assertFalse(parallel.contains("Reader2ShadowRules030.fromSpatial"))
        assertFalse(parallel.contains("MoneyRoleResolver030"))
        assertTrue(parallel.contains("independent_candidate_builder"))
        assertTrue(parallel.contains("can_observe_m1_rejected_frames"))
    }

    @Test fun parallelReaderHasNoOfficialSideEffectsOrSecondOcr() {
        val parallel = source("Reader2Parallel031.kt")
        listOf(
            "TextRecognition.getClient",
            "TextRecognizer",
            "client.process(",
            "LocalStore",
            "BackendClient",
            "OverlayController",
            "sendOffer(",
            "saveOffer(",
            "OfferDispatcher",
        ).forEach { forbidden ->
            assertFalse("Reader 2.0 paralelo não pode usar $forbidden", parallel.contains(forbidden))
        }
        assertTrue(parallel.contains("second_ocr"))
        assertTrue(parallel.contains("official_persistence"))
        assertTrue(parallel.contains("admission_influence"))
    }

    @Test fun diagnosticExportsParallel031() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("reader2_parallel_031"))
        assertTrue(diagnostic.contains("Reader2Parallel031.toJson()"))
    }

    @Test fun historyRemainsFrozenFrom031() {
        val history = source("RideHistoryPanel027035.kt")
        assertFalse(history.contains("Reader2Parallel031"))
        assertTrue(history.contains("RideOperationalStatus.NOT_COMPLETED"))
    }
}
