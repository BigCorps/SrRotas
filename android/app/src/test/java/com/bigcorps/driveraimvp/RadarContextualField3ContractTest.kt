package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadarContextualField3ContractTest {
    private val androidRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .firstOrNull { base ->
                File(base, "app/build.gradle.kts").isFile &&
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp/SrRotasApplication.kt").isFile
            }
            ?: error("Raiz Android não encontrada.")
    }

    private val sourceRoot = File(androidRoot, "app/src/main/java/com/bigcorps/driveraimvp")
    private fun source(name: String) = File(sourceRoot, name).readText()

    @Test
    fun hudHasExplicitRideAcceptanceAndRadarCta() {
        val hud = source("JourneyBubbleController.kt")
        assertTrue(hud.contains("ESTOU NESSA CORRIDA"))
        assertTrue(hud.contains("JourneyCoordinator.markDoingRide"))
        assertTrue(hud.contains("RadarContextualDiagnosticV1.rideMarkRequested"))
        assertTrue(hud.contains("RadarContextualDiagnosticV1.rideMarkSucceeded"))
        assertTrue(hud.contains("RadarDestinationLauncherV1.openRadar"))
    }

    @Test
    fun assistantAndHudUsePendingIntentLauncherForAndroidBalRules() {
        val launcher = source("RadarDestinationLauncherV1.kt")
        val renderer = source("DestinationRadarAssistantRendererV1.kt")

        assertTrue(launcher.contains("PendingIntent.getActivity"))
        assertTrue(launcher.contains("setPendingIntentCreatorBackgroundActivityStartMode"))
        assertTrue(launcher.contains("setPendingIntentBackgroundActivityStartMode"))
        assertTrue(launcher.contains("BUBBLE_ACTION_RADAR_DEMO"))
        assertTrue(renderer.contains("RadarDestinationLauncherV1.openRadar"))
        assertTrue(renderer.contains("RadarDestinationLauncherV1.openDemo"))
        assertFalse(renderer.contains("context.startActivity("))
    }

    @Test
    fun radarSurfaceHasNoMapAndStillReleasesWhenItLeavesVisibility() {
        val panel = source("RadarContextualPanelV1.kt")
        assertFalse(panel.contains("RadarMiniMapViewV1"))
        assertFalse(panel.contains("ensureMap()"))
        assertTrue(panel.contains("private fun releaseSurface(@Suppress(\"UNUSED_PARAMETER\") reason:String"))
        assertTrue(panel.contains("override fun onVisibilityChanged"))
        assertTrue(panel.contains("renderPendingIfReady()"))
    }

    @Test
    fun diagnosticProvesRideToBackendToMapChain() {
        val diag = source("RadarContextualDiagnosticV1.kt")
        val runtime = source("DestinationRadarRuntimeV1.kt")

        assertTrue(diag.contains("ride_mark_requested"))
        assertTrue(diag.contains("ride_mark_success"))
        assertTrue(diag.contains("spec_resolved"))
        assertTrue(diag.contains("backend_query"))
        assertTrue(diag.contains("map_ready"))
        assertTrue(diag.contains("map_active_ms_current"))
        assertTrue(runtime.contains("RadarContextualDiagnosticV1.specResolved"))
        // vc107: map counters stay in the diagnostic and now prove that no map is created.
        assertFalse(source("RadarContextualPanelV1.kt").contains("RadarContextualDiagnosticV1.mapReady"))
    }
}
