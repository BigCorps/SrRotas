package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** vc101: manual Radar refresh should not churn MapLibre, while ETA stays fail-closed. */
class Field101RadarStabilityContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }
    private fun source(name: String) =
        File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()

    @Test fun releaseVersionIsHigherThanInstalledVc100() {
        val gradle = File(root,"app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode=107;versionName=\"0.33.30-field\""))
    }

    @Test fun refreshingDestinationNeverBuildsAMap() {
        val panel=source("RadarContextualPanelV1")
        val open=panel.substringAfter("fun openDestination(").substringBefore("RadarContextualClientV1.fetch(")
        // vc107: no MapLibre to keep or churn; only the ETA ticker is reset.
        assertTrue(open.contains("releaseSurface(\"destination_changed\")"))
        assertTrue(open.indexOf("leaveDemo()") < open.indexOf("renderLoading(value)"))
        assertFalse(panel.contains("RadarMiniMapViewV1"))
    }

    @Test fun expiredEtaIsExplainedButNeverExtendedOrUsedForAutoSuggestions() {
        val panel=source("RadarContextualPanelV1")
        assertTrue(panel.contains("Assistente pausado até uma nova ETA válida"))
        assertTrue(panel.contains("ETA vencida ou inválida"))
        assertTrue(panel.contains("arrivalWithAssistantStatus(eta, System.currentTimeMillis())"))
        assertTrue(panel.contains("arrivalWithAssistantStatus(value.destinationEta, System.currentTimeMillis())"))
        assertTrue(panel.contains("DestinationRadarFreshnessV1.blockReason(value.eta, System.currentTimeMillis())"))
        val runtime=source("DestinationRadarRuntimeV1")
        assertTrue(runtime.contains("DestinationRadarFreshnessV1.blockReason(spec.eta,now)==null"))
        assertTrue(runtime.contains("if(etaFresh &&"))
        assertFalse(runtime.contains("endJourney("))
    }

    @Test fun staleEtaGateDoesNotInventTime() {
        val now=Instant.parse("2026-10-20T16:00:00Z").toEpochMilli()
        val expired=Instant.ofEpochMilli(now-548_000L).toString()
        val valid=Instant.ofEpochMilli(now+240_000L).toString()
        assertEquals("eta_expired",DestinationRadarFreshnessV1.blockReason(expired,now))
        assertNull(DestinationRadarFreshnessV1.blockReason(valid,now))
        assertEquals(0L,DestinationRadarFreshnessV1.bannerDurationMs(expired,now,30_000L))
    }

    @Test fun readerContextRemainsFrozenAndMapLibreIsGone() {
        assertFalse(File(root,"app/build.gradle.kts").readText().contains("org.maplibre"))
        val context=source("RadarDestinationContextV1")
        assertTrue(context.contains("LocalStore.get(context).offerByLocalId(ride.localOfferId)"))
        assertTrue(context.contains("FieldPipelineFactsV1.specBlock(offer)"))
    }
}
