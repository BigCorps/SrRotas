package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CaptureResilience0311ContractTest {
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

    @Test fun supervisorNeverTriesSilentProjectionRecoveryAfterProjectionEnded() {
        val supervisor = source("ReaderRecoverySupervisor027036.kt")
        assertTrue(supervisor.contains("CaptureResilience0311.sync(context)"))
        assertTrue(supervisor.contains("journeyId == null || !projectionActive"))
        val inactiveGuard = supervisor.indexOf("journeyId == null || !projectionActive")
        val actionRecover = supervisor.indexOf("MediaProjectionOcrService.ACTION_RECOVER")
        assertTrue(inactiveGuard >= 0)
        assertTrue(actionRecover > inactiveGuard)
    }

    @Test fun recoveryFlowPreservesJourneyAndRequestsFreshConsent() {
        val controls = source("DiagnosticControls0270.kt")
        assertTrue(controls.contains("CaptureResilience0311.markResumeRequested"))
        assertTrue(controls.contains("createScreenCaptureIntent"))
        assertTrue(controls.contains("CaptureResilience0311.markResumeAuthorized"))
        assertTrue(controls.contains("Retomada cancelada. A jornada continua aberta."))
        assertFalse(controls.contains("JourneyCoordinator.endJourney"))
    }

    @Test fun nowPanelExposesRecoveryWithoutReplacingJourneyControl() {
        val now = source("NowPanel027037.kt")
        assertTrue(now.contains("sr0311_resume_capture"))
        assertTrue(now.contains("CaptureResilience0311.needsRecovery"))
        assertTrue(now.contains("Captura interrompida · jornada preservada"))
        assertTrue(now.contains("toggleJourneyFromNow"))
    }

    @Test fun diagnosticExportsCaptureResilience() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(diagnostic.contains("capture_resilience_0311"))
        assertTrue(diagnostic.contains("CaptureResilience0311.toJson(context)"))
    }

    @Test fun captureResilienceStoresNoSensitivePayload() {
        val resilience = source("CaptureResilience0311.kt")
        listOf("raw_text", "screenshot", "latitude", "longitude", "address").forEach { forbidden ->
            assertFalse("Capture resilience não deve persistir $forbidden", resilience.contains("putString(\"$forbidden"))
        }
        assertTrue(resilience.contains("silent_token_reuse"))
        assertTrue(resilience.contains("requires_user_consent_for_new_projection"))
    }
}
