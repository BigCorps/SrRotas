package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato P0 0.33.2 — capture continuity + crash observability. */
class Release0332P0ContractTest {
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

    @Test fun recoveryUsesDedicatedHighPriorityChannel() {
        val controls = source("DiagnosticControls0270.kt")
        assertTrue(controls.contains("sr_rotas_capture_recovery_0332"))
        assertTrue(controls.contains("NotificationManager.IMPORTANCE_HIGH"))
        assertTrue(controls.contains("CaptureRecoveryActivity0270::class.java"))
        assertTrue(controls.contains("\"Retomar captura\""))
        assertTrue(controls.contains("setOngoing(true)"))
        assertTrue(controls.contains("Notification.CATEGORY_ERROR"))
        assertTrue(controls.contains("fresh_consent_required"))
        assertTrue(controls.contains("silent_projection_restart"))
        assertFalse(
            "A notificação de recovery não pode voltar ao canal LOW do FGS.",
            controls.contains("private const val CHANNEL_ID = \"sr_rotas_projection\""),
        )
    }

    @Test fun crashDiagnosticExportsOnlySanitizedWhitelistedFields() {
        val beta = source("BetaTelemetry.kt")
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(beta.contains("LAST_CRASH"))
        assertTrue(beta.contains("fun crashDiagnostic"))
        assertTrue(beta.contains("exception_class"))
        assertTrue(beta.contains("\"stack\""))
        assertTrue(beta.contains("age_ms"))
        assertFalse(beta.contains("raw_text"))
        assertFalse(beta.contains("latitude"))
        assertFalse(beta.contains("longitude"))
        assertTrue(diagnostic.contains("crash_observability_0332"))
        assertTrue(diagnostic.contains("BetaTelemetry.crashDiagnostic(context)"))
    }

    @Test fun combinedDiagnosticExportsRecoveryNotificationTelemetry() {
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        val controls = source("DiagnosticControls0270.kt")
        assertTrue(diagnostic.contains("capture_recovery_notification_0332"))
        assertTrue(diagnostic.contains("DiagnosticNotification0270.toJson(context)"))
        assertTrue(controls.contains("notifications_enabled"))
        assertTrue(controls.contains("notification_active"))
        assertTrue(controls.contains("posted_episodes"))
    }

    @Test fun freshAndroidConsentContractRemainsIntact() {
        val controls = source("DiagnosticControls0270.kt")
        assertTrue(controls.contains("createScreenCaptureIntent"))
        assertTrue(controls.contains("CaptureResilience0311.markResumeAuthorized"))
        assertTrue(controls.contains("Retomada cancelada. A jornada continua aberta."))
        assertFalse(controls.contains("JourneyCoordinator.endJourney"))
    }
}
