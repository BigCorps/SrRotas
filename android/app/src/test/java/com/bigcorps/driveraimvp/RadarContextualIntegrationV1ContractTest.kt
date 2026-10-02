package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadarContextualIntegrationV1ContractTest {
    private val sourceRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }
            .firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Fontes Android não encontradas.")
    }

    private fun source(name: String) = File(sourceRoot, name).readText()

    @Test
    fun shellKeepsLegacyRollbackAndWiresContextualRadar() {
        val s = source("ConsolidatedMainActivity027037.kt")
        assertTrue(s.contains("RadarPanel027035"))
        assertTrue(s.contains("RadarContextualPanelV1"))
        assertTrue(s.contains("RadarContextualHostV1"))
        assertTrue(s.contains("RadarDestinationEntryV1"))
        assertTrue(s.contains("refreshRadarSurface"))
        assertTrue(s.contains("openRadarDestination"))
    }

    @Test
    fun destinationResolutionUsesCurrentRideIdentity() {
        val s = source("RadarDestinationContextV1.kt")
        assertTrue(s.contains("snapshot.currentRide"))
        assertTrue(s.contains("ride.localOfferId"))
        assertTrue(s.contains("it.localId==ride.localOfferId"))
        assertFalse(s.contains("latestOffer ?:"))
    }

    @Test
    fun flagsAreOffByDefaultAndDependenciesAreOrdered() {
        val s = source("RadarContextualFlagsV1.kt")
        assertTrue(s.contains("getBoolean(UI, false)"))
        assertTrue(s.contains("getBoolean(RUNTIME, false)"))
        assertTrue(s.contains("getBoolean(ASSISTANT, false)"))
        assertTrue(s.contains("enableUiOnly"))
        assertTrue(s.contains("enableRuntime"))
        assertTrue(s.contains("enableAssistant"))
    }

    @Test
    fun runtimeIsInstalledButDormantUntilFlag() {
        val app = source("SrRotasApplication.kt")
        val integration = source("RadarContextualIntegrationV1.kt")
        assertTrue(app.contains("RadarContextualIntegrationV1.syncRuntime(this)"))
        assertTrue(integration.contains("RadarContextualFlagsV1.runtimeEnabled"))
        assertTrue(integration.contains("DestinationRadarRuntimeV1.stop()"))
    }

    @Test
    fun destinationAssistantReusesExistingJourneyBubbleHost() {
        val s = source("DestinationRadarAssistantRendererV1.kt")
        assertTrue(s.contains("JourneyBubbleController.show"))
        assertTrue(s.contains("mainColumn"))
        assertFalse(s.contains("WindowManager.LayoutParams"))
        assertFalse(s.contains("getSystemService(WindowManager"))
    }

    @Test
    fun fieldTesterHasDemoAndImmediateRollback() {
        val s = source("RadarContextualHomologationV1.kt")
        assertTrue(s.contains("Prévia DEMO"))
        assertTrue(s.contains("Assistente DEMO"))
        assertTrue(s.contains("Rollback"))
        assertTrue(s.contains("RadarContextualIntegrationV1.rollback"))
    }
}
