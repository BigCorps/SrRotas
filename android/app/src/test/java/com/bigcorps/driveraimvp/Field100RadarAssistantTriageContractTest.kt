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
        assertTrue(panel.contains("Tab.SUMMARY to \"Resumo\""))
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

    @Test fun summaryHasNoMapStageAndOnlyUsesPermittedRecentPosition() {
        val panel=source("RadarContextualPanelV1")
        val position=source("RadarVisibleLocationV1")
        // vc107: the 490dp map stage is gone; the position only feeds an explicit "Ver no Google Maps".
        assertFalse(panel.contains("private fun appendMapStage("))
        assertFalse(panel.contains("UiKit.dp(context,490)"))
        assertTrue(panel.contains("RadarVisibleLocationV1.latest(context)"))
        assertTrue(panel.contains("mapsButton(position.lat, position.lng, null)"))
        assertTrue(panel.contains("releaseSurface(\"hidden\")"))
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

    @Test fun exactRideSourceLookupAvoidsLast100OfferTruncation() {
        val resolver=source("RadarDestinationContextV1")
        assertTrue(resolver.contains("LocalStore.get(context).offerByLocalId(ride.localOfferId)"))
        assertTrue(resolver.contains("it.journeyId==snapshot.journeyId"))
        assertFalse(resolver.contains("recentOffers(100)"))
        assertTrue(resolver.contains("persisted_offer_by_id"))
        val trace=source("FieldPipelineTraceV1")
        assertTrue(trace.contains("\"persisted_offer_by_id\""))
        assertTrue(resolver.contains("FieldPipelineFactsV1.specBlock(offer)"))
    }

    @Test fun forgottenRideQuestionCannotFinishRidesAutomatically() {
        val assistant=source("ActiveAssistant026")
        val rules=source("ActiveAssistantRules026")
        assertTrue(assistant.contains("private fun maybeRemindRide("))
        assertTrue(assistant.contains("Você ainda está em corrida?"))
        assertTrue(assistant.contains("rideReminderAllowed(now,started,eta,lastShown)"))
        assertTrue(assistant.contains("current.currentRide?.localOfferId != offerId"))
        assertTrue(assistant.contains("FieldValidationPolish0265.openNowFromAssistant(context)"))
        assertTrue(assistant.contains("main.postDelayed(hide,20_000L)"))
        assertFalse(assistant.contains("JourneyCoordinator.endRide("))
        assertTrue(rules.contains("90L * 60_000L"))
        assertTrue(rules.contains("40L * 60_000L"))
        assertTrue(rules.contains("2L * 60L * 60_000L"))
    }

    @Test fun contextualAssistantStillRejectsExpiredEta() {
        val runtime=source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("DestinationRadarFreshnessV1.blockReason(spec.eta,now)==null"))
        assertTrue(runtime.contains("if(etaFresh &&"))
        assertTrue(runtime.contains("ASSISTANT_BLOCKED"))
        assertTrue(source("DestinationRadarAssistantRendererV1").contains("DestinationRadarFreshnessV1.blockReason"))
    }
}
