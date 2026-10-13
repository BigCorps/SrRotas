package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class Field98FreshnessContractTest {
    private val androidRoot by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }
    private fun source(file: String) = File(androidRoot, "app/src/main/java/com/bigcorps/driveraimvp/$file.kt").readText()

    @Test fun etaIsStrictlyFutureForAutomaticSuggestions() {
        val now = Instant.parse("2026-10-13T14:36:04Z").toEpochMilli()
        assertEquals("eta_expired", DestinationRadarFreshnessV1.blockReason("2026-10-13T14:33:32Z", now))
        assertEquals("eta_expired", DestinationRadarFreshnessV1.blockReason("2026-10-13T14:36:04Z", now))
        assertNull(DestinationRadarFreshnessV1.blockReason("2026-10-13T14:36:05Z", now))
        assertEquals("eta_invalid", DestinationRadarFreshnessV1.blockReason("not-a-time", now))
    }

    @Test fun realBannerCannotRemainVisiblePastEta() {
        val now = Instant.parse("2026-10-13T14:36:00Z").toEpochMilli()
        assertEquals(4_000L, DestinationRadarFreshnessV1.bannerDurationMs("2026-10-13T14:36:04Z", now, 30_000L))
        assertEquals(5_000L, DestinationRadarFreshnessV1.bannerDurationMs("2026-10-13T14:37:00Z", now, 5_000L))
        assertEquals(0L, DestinationRadarFreshnessV1.bannerDurationMs("2026-10-13T14:35:59Z", now, 30_000L))
        assertEquals(0L, DestinationRadarFreshnessV1.bannerDurationMs("invalid", now, 30_000L))
        assertEquals(0L, DestinationRadarFreshnessV1.bannerDurationMs("2026-10-13T14:36:04Z", now, -1L))
        val renderer = source("DestinationRadarAssistantRendererV1")
        assertTrue(renderer.contains("eta = spec.eta"))
        assertTrue(renderer.contains("DestinationRadarFreshnessV1.bannerDurationMs"))
    }

    @Test fun deliveryRendererAndClickRevalidateFreshness() {
        val bridge = source("DestinationRadarAssistantBridgeV1")
        val renderer = source("DestinationRadarAssistantRendererV1")
        assertTrue(bridge.contains("DestinationRadarFreshnessV1.blockReason"))
        assertTrue(bridge.contains("Decision(null, temporalBlock)"))
        assertTrue(renderer.contains("DestinationRadarFreshnessV1.blockReason"))
        assertTrue(renderer.contains("RADAR_OPENED_BLOCKED"))
        assertTrue(source("FieldPipelineTraceV1").contains("\"eta_expired\""))
    }

    @Test fun requestGenerationsInvalidateOldNetworkAndOverlayCallbacks() {
        val runtime = source("DestinationRadarRuntimeV1")
        val renderer = source("DestinationRadarAssistantRendererV1")
        val integration = source("RadarContextualIntegrationV1")
        assertTrue(runtime.contains("requestSequence.get()!=requestId"))
        assertTrue(runtime.contains("requestSequence.incrementAndGet()"))
        assertTrue(runtime.contains("fun invalidatePending()"))
        assertTrue(renderer.contains("deliveryEpoch.get() != epoch"))
        assertTrue(renderer.contains("deliveryEpoch.incrementAndGet()"))
        assertTrue(integration.contains("DestinationRadarRuntimeV1.invalidatePending()"))
    }

    @Test fun captureUpdatesDoNotInvalidateAnUnchangedRide() {
        val integration = source("RadarContextualIntegrationV1")
        assertTrue(integration.contains("if (currentSignature != lastJourneySignature)"))
        assertTrue(integration.contains("snapshot.currentRide?.localOfferId"))
        assertTrue(integration.contains("snapshot.currentRide?.status?.name"))
        val runtime = source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("val etaFresh = DestinationRadarFreshnessV1.blockReason"))
        assertTrue(runtime.contains("if(etaFresh &&"))
    }

    @Test fun sameRideWithRevisedEtaCannotRenderOldAutomaticResult() {
        val runtime = source("DestinationRadarRuntimeV1")
        val bridge = source("DestinationRadarRuntimeBridgeV1")
        val renderer = source("DestinationRadarAssistantRendererV1")
        val assistant = source("DestinationRadarAssistantBridgeV1")
        assertTrue(runtime.contains("activeSpec!=spec"))
        assertTrue(runtime.contains("live!=spec || activeSpec!=spec"))
        assertTrue(runtime.contains("latestForSpec(spec:RadarDestinationSpecV1)"))
        assertTrue(bridge.contains("DestinationRadarRuntimeV1.latestForSpec(spec)"))
        assertTrue(renderer.contains("RadarDestinationContextV1.current(app) != spec"))
        assertTrue(renderer.contains("stale_destination"))
        assertTrue(assistant.contains("DestinationRadarRuntimeV1.latestForSpec(spec)"))
        val panel = source("RadarContextualPanelV1")
        assertTrue(panel.contains("RadarDestinationContextV1.current(context) != value"))
        assertTrue(panel.contains("spec != value"))
        assertTrue(panel.contains("RadarDestinationContextV1.current(context)!=spec"))
        assertFalse(panel.contains("RadarDestinationContextV1.current(context)?.localOfferId != value.localOfferId"))
    }

    @Test fun evenBackendStrongNeedsCurrentRideAndDestination() {
        val assistant = source("DestinationRadarAssistantBridgeV1")
        val earlyGuard = "if(!currentRideValid) return Decision(null,\"current_ride_changed\")"
        assertTrue(assistant.contains(earlyGuard))
        assertTrue(assistant.indexOf(earlyGuard) < assistant.indexOf("val a=result.assistant"))
        assertTrue(assistant.contains("DestinationRadarRuntimeV1.latestForSpec(spec) === result"))
    }

    @Test fun assistantShownAndCooldownRequireActualFirstVisibleDraw() {
        val renderer = source("DestinationRadarAssistantRendererV1")
        val check = renderer.substringAfter("override fun onPreDraw(): Boolean")
            .substringBefore("observer.addOnPreDrawListener(listener)")
        assertTrue(check.contains("card.width > 0 && card.height > 0"))
        assertTrue(check.contains("if (visible) onShown()"))
        assertTrue(check.indexOf("if (visible) onShown()") < check.indexOf("\"ASSISTANT_VISIBLE\""))
        val attach = renderer.substringAfter("private fun attach(")
            .substringBefore("override fun onPreDraw(): Boolean")
        assertFalse(attach.contains("card.isAttachedToWindow, onShown"))
        assertTrue(attach.contains("card.isAttachedToWindow, {}"))
        val interaction = source("DestinationRadarInteractionV1")
        assertTrue(interaction.contains("DestinationRadarAssistantBridgeV1.markShown(context,signal)"))
    }

    @Test fun reportToggleAndOperationalMarkerAreDifferentControls() {
        val hud = source("JourneyBubbleController")
        assertTrue(hud.contains("ReportSelection0211.toggle(context, offer)"))
        assertTrue(hud.contains("contentDescription = \"Corrida em andamento\""))
        assertFalse(hud.contains("isOperationalRide -> UiKit.rounded(context, UiKit.palette(context).good"))
        assertTrue(hud.contains("if (isSelected) UiKit.rounded(context, Color.WHITE"))
    }

    @Test fun rideTransitionClearsStaleAssistantButMapRemainsNavigable() {
        val integration = source("RadarContextualIntegrationV1")
        assertTrue(integration.contains("DestinationRadarAssistantRendererV1.hide()"))
        assertFalse(source("RadarDestinationContextV1").contains("DestinationRadarFreshnessV1"))
        assertFalse(source("RadarContextualPanelV1").contains("DestinationRadarFreshnessV1"))
    }
}
