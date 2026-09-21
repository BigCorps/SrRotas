package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato 0.29 para as regressões confirmadas pelo diagnóstico de campo 0.28. */
class Release029ContractTest {
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

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test fun semanticGapDoesNotResetHealthyOcr() {
        val service = source("MediaProjectionOcrService.kt")
        assertFalse(service.contains("resetOcrPipeline(\"watchdog_semantic_gap\")"))
        assertTrue(service.contains("gap semântico observado; pipeline OCR preservado"))
        assertTrue(service.contains("watchdog_ocr_stall"))
        assertTrue(service.contains("watchdog_ocr_no_progress"))
    }

    @Test fun legacyShadowRecoveryNoLongerRunsSecondMlKit() {
        val recovery = source("ShadowOfferRecovery027033.kt")
        assertFalse(recovery.contains("TextRecognition"))
        assertFalse(recovery.contains("client.process("))
        assertTrue(recovery.contains("disabled_in_029"))
        assertTrue(recovery.contains("suppressed_submissions"))
    }

    @Test fun admissionGateRunsBeforeStabilizerHudAndPersistence() {
        val dispatcher = source("OfferDispatcher.kt")
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(dispatcher.contains("OfferAdmissionGate029.admit"))
        assertTrue(dispatcher.contains("\"dispatch\""))
        assertTrue(dispatcher.contains("\"submit_stabilized\""))
        assertTrue(dispatcher.contains("\"dispatch_all\""))
        assertTrue(diagnostic.contains("offer_admission_029"))
    }

    @Test fun genericUnknownFallbackNeedsRouteContext() {
        val gate = source("OfferAdmissionGate029.kt")
        assertTrue(gate.contains("other-text-fallback"))
        assertTrue(gate.contains("pickupLabel.isNullOrBlank()"))
        assertTrue(gate.contains("destinationLabel.isNullOrBlank()"))
        assertTrue(gate.contains("REJECT_GENERIC_FALLBACK"))
    }

    @Test fun tailConfirmationHasShortBoundedWindow() {
        val gate = source("OfferAdmissionGate029.kt")
        assertTrue(gate.contains("CONFIRMATION_WINDOW_MS = 7_000L"))
        assertTrue(gate.contains("REJECT_DECIMAL_CONFLICT"))
        assertTrue(gate.contains("ACCEPT_CONFIRMED_TAIL"))
    }
}
