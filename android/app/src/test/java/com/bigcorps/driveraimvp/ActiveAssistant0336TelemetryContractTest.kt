package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 0.33.6 — observabilidade do Assistente Ativo sem alterar o motor/ranking. */
class ActiveAssistant0336TelemetryContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }.toList()
        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}")
    }

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test fun telemetryIsInstalledByCanonicalPolish() {
        val polish = source("ActiveAssistantPolish0265.kt")
        assertTrue(polish.contains("ActiveAssistantTelemetry0336.install(application)"))
        assertTrue(polish.contains("ActiveAssistantTelemetry0336.markDecorated(context)"))
        assertTrue(polish.contains("ActiveAssistantTelemetry0336.markIgnore(context)"))
        assertTrue(polish.contains("ActiveAssistantTelemetry0336.markView(context)"))
        assertTrue(polish.contains("ActiveAssistantTelemetry0336.markOutsideDismiss(context)"))
    }

    @Test fun diagnosticExportsAssistant0336() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("active_assistant_0336"))
        assertTrue(diagnostic.contains("ActiveAssistantTelemetry0336.toJson(context)"))
    }

    @Test fun telemetryDoesNotExportSensitiveContent() {
        val telemetry = source("ActiveAssistantTelemetry0336.kt")
        assertTrue(telemetry.contains("exports_sensitive_content\", false"))
        assertTrue(telemetry.contains("exports_region\", false"))
        assertTrue(telemetry.contains("exports_ocr\", false"))
        assertTrue(telemetry.contains("exports_coordinates\", false"))
        assertFalse(telemetry.contains("raw_text"))
        assertFalse(telemetry.contains("pickup_lat"))
        assertFalse(telemetry.contains("destination_lat"))
    }

    @Test fun polishKeepsExplicitActionsAndNoSilentRideState() {
        val polish = source("ActiveAssistantPolish0265.kt")
        assertTrue(polish.contains("label = \"IGNORAR\""))
        assertTrue(polish.contains("label = \"VER\""))
        assertTrue(polish.contains("FieldValidationPolish0265.openNowFromAssistant(context)"))
        assertFalse(polish.contains("Você está fazendo uma corrida?"))
        assertFalse(polish.contains("suppressAsRide"))
    }
}
