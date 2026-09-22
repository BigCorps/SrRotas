package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato arquitetural da 0.30.0 Field — Core Reader. */
class Release030ContractTest {
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

    @Test fun dispatcherContractRemainsOn029Facade() {
        val dispatcher = source("OfferDispatcher.kt")
        val gate029 = source("OfferAdmissionGate029.kt")
        assertTrue(dispatcher.contains("OfferAdmissionGate029.admit"))
        assertTrue(gate029.contains("OfferAdmissionGate030.admit"))
        assertTrue(gate029.contains("runtime_delegated_to_030"))
    }

    @Test fun reader2ShadowSharesSpatialEvidenceWithoutSecondOcr() {
        val uber = source("UberSpatialParser0221.kt")
        val shadow = source("Reader2Shadow030.kt")
        assertTrue(uber.contains("Reader2Shadow030.captureSpatial"))
        assertTrue(shadow.contains("shared_m1_spatial_ocr"))
        assertTrue(shadow.contains("second_ocr"))
        assertFalse(shadow.contains("TextRecognition.getClient"))
        assertFalse(shadow.contains("TextRecognizer"))
        assertFalse(shadow.contains("client.process("))
    }

    @Test fun reader2ShadowCannotPersistOrDriveHud() {
        val shadow = source("Reader2Shadow030.kt")
        assertFalse(shadow.contains("LocalStore"))
        assertFalse(shadow.contains("BackendClient"))
        assertFalse(shadow.contains("OverlayController"))
        assertFalse(shadow.contains("sendOffer("))
        assertFalse(shadow.contains("saveOffer("))
        assertTrue(shadow.contains("official_persistence"))
        assertTrue(shadow.contains("admission_influence"))
    }

    @Test fun decimalIdentityExcludesSuspectNumbersAndCoversFare() {
        val gate = source("OfferAdmissionGate030.kt")
        assertTrue(gate.contains("fun targetKey"))
        assertTrue(gate.contains("if (factorTenish(a.fare, b.fare)) add(\"fare\")"))
        assertTrue(gate.contains("REJECT_DECIMAL_CONFLICT"))
        assertTrue(gate.contains("ACCEPT_CONFIRMED_DECIMAL_CHANGE"))
        val targetBlock = gate.substringAfter("fun targetKey").substringBefore("fun routeSignature")
        assertFalse(targetBlock.contains("offer.fare"))
        assertFalse(targetBlock.contains("offer.pickupKm"))
        assertFalse(targetBlock.contains("offer.totalMinutes"))
        assertFalse(targetBlock.contains("offer.serviceType"))
    }

    @Test fun combinedDiagnosticExports030AndReader2Shadow() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("offer_admission_030"))
        assertTrue(diagnostic.contains("reader2_shadow_030"))
        assertTrue(diagnostic.contains("OfferAdmissionGate030.toJson"))
        assertTrue(diagnostic.contains("Reader2Shadow030.toJson"))
    }

    @Test fun coreReleaseDoesNotWireReader2IntoStableHistory() {
        val history = source("RideHistoryPanel027035.kt")
        assertFalse(history.contains("Reader2Shadow030"))
        assertFalse(history.contains("OfferAdmissionGate030"))
        assertTrue(history.contains("RideOperationalStatus.NOT_COMPLETED"))
    }
}
