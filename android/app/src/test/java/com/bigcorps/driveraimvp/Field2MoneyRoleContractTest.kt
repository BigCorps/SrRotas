package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato de escopo da 0.30.0 Field2 — Money Roles. */
class Field2MoneyRoleContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .flatMap { base -> sequenceOf(
                File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                File(base, "src/main/java/com/bigcorps/driveraimvp"),
                File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
            ) }
            .firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Fontes Sr. Rotas não encontrados: ${cwd.path}")
    }

    private fun source(name: String): String = File(root, name).readText()

    @Test fun uberUsesMoneyRolesBeforeCreatingFareAnchors() {
        val spatial = source("UberSpatialParser0221.kt")
        val isolation = source("OfferSpatialIsolation0221.kt")
        assertTrue(spatial.contains("MoneyRoleResolver030.primarySpatialFareLines(lines)"))
        assertTrue(spatial.contains("MoneyRoleResolver030.primarySpatialFareLines(pane)"))
        assertTrue(isolation.contains("MoneyRoleResolver030.primarySpatialFareLines(lines)"))
        assertTrue(isolation.contains("MoneyRoleResolver030.looksUberContext(lines)"))
    }

    @Test fun detectorNoLongerOwnsNaivePrimaryMoneySelection() {
        val detector = source("UberOfferDetector.kt")
        assertTrue(detector.contains("MoneyRoleResolver030.primaryFare"))
        assertTrue(detector.contains("MoneyRoleResolver030.isPrimaryFareLine"))
        assertFalse(detector.contains("lines.firstOrNull(::isPrimaryFareLine)"))
    }

    @Test fun reader2MoneyShadowHasNoOfficialSideEffects() {
        val shadow = source("Reader2MoneyShadow030.kt")
        assertTrue(shadow.contains("shadowPrimaryFare"))
        assertFalse(shadow.contains("LocalStore"))
        assertFalse(shadow.contains("BackendClient"))
        assertFalse(shadow.contains("TextRecognition"))
        assertFalse(shadow.contains("TextRecognizer"))
        assertFalse(shadow.contains("saveOffer("))
        assertFalse(shadow.contains("sendOffer("))
    }

    @Test fun combinedDiagnosticExportsField2MoneyShadow() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("reader2_money_shadow_030_field2"))
        assertTrue(diagnostic.contains("Reader2MoneyShadow030.toJson()"))
    }

    @Test fun historyAndFinancialFormulasRemainOutsideField2() {
        val history = source("RideHistoryPanel027035.kt")
        assertFalse(history.contains("MoneyRoleResolver030"))
        assertFalse(history.contains("Reader2MoneyShadow030"))
        val parser = source("OfferParser.kt")
        assertTrue(parser.contains("val fare = detected.fare"))
        assertTrue(parser.contains("val perHour = fare / (totalMinutes / 60.0)"))
        assertTrue(parser.contains("val perMinute = fare / totalMinutes"))
    }
}
