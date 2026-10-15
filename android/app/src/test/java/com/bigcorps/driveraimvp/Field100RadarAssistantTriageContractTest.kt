package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Post-vc99 regression contract: compact tabs and opted-in idle Assistant rehydration.
 * Contextual ETA gate, Reader and MapLibre remain untouched in this increment. */
class Field100RadarAssistantTriageContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }
    private fun source(name: String) =
        File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()

    @Test fun radarTabUsesShortLabelWhileKeepingTwoEvidenceAreasIndependentForNow() {
        val panel=source("RadarContextualPanelV1")
        assertTrue(panel.contains("Tab.MAP to \"Mapa\""))
        assertTrue(panel.contains("Tab.OPPORTUNITIES to \"Oportunidades\""))
        assertTrue(panel.contains("Tab.EVENTS to \"Áreas\""))
        assertFalse(panel.contains("Tab.EVENTS to \"Áreas de interesse\""))
        assertTrue(panel.contains("private fun tabRow(): View"))
        assertTrue(panel.contains("RadarContextualEventEligibilityV1.eligible("))
    }

    @Test fun optedInAssistantIsRehydratedAfterApplicationAndActivityResume() {
        val app=source("SrRotasApplication")
        val activity=source("ConsolidatedMainActivity027037")
        val assistant=source("ActiveAssistant026")
        assertTrue(app.contains("ActiveAssistantPolish0265.install(this)"))
        assertTrue(app.contains("ActiveAssistant026.ensureRunning(this)"))
        assertTrue(app.indexOf("ActiveAssistantPolish0265.install(this)") <
            app.indexOf("ActiveAssistant026.ensureRunning(this)"))
        val resume=activity.substringAfter("override fun onResume()").substringBefore("override fun onPause()")
        assertTrue(resume.contains("ActiveAssistant026.ensureRunning(this)"))
        assertTrue(resume.contains("RadarContextualIntegrationV1.syncRuntime(this)"))
        assertTrue(assistant.contains("if (!isEnabled(context)) return"))
        assertTrue(assistant.contains("if (SettingsRepository(app).currentJourneyId().isBlank()) return"))
        assertTrue(assistant.contains("if (running) return"))
        assertTrue(assistant.contains("snapshot.currentRide != null"))
        assertFalse(resume.contains("endJourney("))
    }

    @Test fun mapKeeps490dpFrameAndOnlyUsesPermittedRecentPosition() {
        val panel=source("RadarContextualPanelV1")
        val position=source("RadarVisibleLocationV1")
        assertTrue(panel.contains("private fun appendMapStage("))
        assertTrue(panel.contains("private fun appendUnavailableMap("))
        assertTrue(panel.contains("UiKit.dp(context,490)"))
        assertTrue(panel.contains("RadarVisibleLocationV1.latest(context)"))
        assertTrue(panel.contains("lastMapFocus"))
        assertTrue(panel.contains("position?.let"))
        assertTrue(panel.contains("userMarker"))
        assertTrue(panel.contains("if (it != RadarVisibleLocationV1.MARKER_ID) select(it)"))
        assertFalse(panel.contains("releaseMap(\"idle\")"))
        assertTrue(panel.contains("releaseMap(\"hidden\")"))
        assertTrue(panel.contains("!surfaceResumed || !isShown || !isAttachedToWindow"))
        assertTrue(position.contains("Manifest.permission.ACCESS_COARSE_LOCATION"))
        assertTrue(position.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
        assertTrue(position.contains("getLastKnownLocation(provider)"))
        assertTrue(position.contains("MAX_AGE_MS = 20L * 60_000L"))
        assertFalse(position.contains("requestLocationUpdates("))
        assertFalse(position.contains("SharedPreferences"))
    }

    @Test fun iconBorderReflectsCanonicalRideAndUpdatesWhenRideChanges() {
        val hud=source("JourneyBubbleController")
        assertTrue(hud.contains("val rideActive=JourneyCoordinator.snapshot(context).isDoingRide"))
        assertTrue(hud.contains("val stateLine=when {"))
        assertTrue(hud.contains("JourneyCoordinator.snapshot(context).isDoingRide,"))
        assertTrue(hud.contains("icon.contentDescription = if(rideActive)"))
        assertTrue(hud.contains("detail?.background = UiKit.rounded(context, surface, 20, line, 1)"))
    }

    @Test fun contextualAssistantStillRejectsExpiredEta() {
        val runtime=source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("DestinationRadarFreshnessV1.blockReason(spec.eta,now)==null"))
        assertTrue(runtime.contains("if(etaFresh &&"))
        assertTrue(runtime.contains("ASSISTANT_BLOCKED"))
        assertTrue(source("DestinationRadarAssistantRendererV1").contains("DestinationRadarFreshnessV1.blockReason"))
    }
}
