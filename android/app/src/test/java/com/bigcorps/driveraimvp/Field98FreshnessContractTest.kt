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
        val runtime = source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("val etaFresh = DestinationRadarFreshnessV1.blockReason"))
        assertTrue(runtime.contains("if(etaFresh &&"))
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
