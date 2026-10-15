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

    @Test fun contextualAssistantStillRejectsExpiredEta() {
        val runtime=source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("DestinationRadarFreshnessV1.blockReason(spec.eta,now)==null"))
        assertTrue(runtime.contains("if(etaFresh &&"))
        assertTrue(runtime.contains("ASSISTANT_BLOCKED"))
        assertTrue(source("DestinationRadarAssistantRendererV1").contains("DestinationRadarFreshnessV1.blockReason"))
    }
}
