package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class RadarContextualField5ContractTest {
    private val now = Instant.parse("2026-10-06T18:00:00Z").toEpochMilli()
    private fun radar(eligible: Boolean = false, count: Int = 1, minutes: Long = 10): RadarContextualResultV1 {
        val demo = RadarContextualDemoV1.result()
        return demo.copy(destinationEta = Instant.ofEpochMilli(now + minutes * 60_000).toString(),
            opportunities = demo.opportunities.take(count),
            assistant = demo.assistant.copy(eligible = eligible))
    }
    private fun decision(result: RadarContextualResultV1, last: String? = null, at: Long = 0, valid: Boolean = true) =
        DestinationRadarAssistantBridgeV1.decide(result, now, last, at, valid, result.destinationEta)

    @Test fun preservesBackendStrongPayload() {
        val result = radar(true)
        val d = decision(result)
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.STRONG, d.signal?.kind)
        assertEquals(result.assistant.headline, d.signal?.headline)
        assertEquals(result.assistant.actionLabel, d.signal?.action)
        assertEquals(result.assistant.opportunityId, d.signal?.opportunityId)
        assertEquals("backend_strong", d.reason)
    }
    @Test fun neutralDiscoveryWithOpportunityNearArrival() {
        val d = decision(radar())
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.DISCOVERY, d.signal?.kind)
        assertEquals("Há oportunidades próximas ao seu destino. Quer ver?", d.signal?.headline)
        assertEquals("Ver", d.signal?.action)
        assertEquals("discovery_opportunity_available", d.reason)
    }
    @Test fun emptyResultNeverAlerts() {
        val d = decision(radar(count = 0))
        assertNull(d.signal)
        assertEquals("backend_not_eligible_no_opportunities", d.reason)
    }
    @Test fun discoveryEtaBoundariesAndInvalidEta() {
        for (minute in listOf(-11L, 19L)) {
            assertNull(decision(radar(minutes = minute)).signal)
            assertEquals("outside_discovery_eta_window", decision(radar(minutes = minute)).reason)
        }
        for (minute in listOf(-10L, 0L, 18L)) assertNotNull(decision(radar(minutes = minute)).signal)
        assertNull(decision(radar().copy(destinationEta = "invalid")).signal)
    }
    @Test fun opportunityCooldownSharedByStrongAndDiscovery() {
        for (strong in listOf(false, true)) {
            val r = radar(strong)
            val d = decision(r, r.opportunities.first().id, now - 1000)
            assertNull(d.signal)
            assertEquals("cooldown_mesma_oportunidade", d.reason)
            assertNotNull(decision(r, r.opportunities.first().id, now - 20*60_000).signal)
        }
    }
    @Test fun changedRideCannotProduceDiscovery() { assertNull(decision(radar(), valid = false).signal) }

    private fun line(text: String, top: Int) = UberM1TemporalRecoveryV1.Line(text, 100, top, 400, top+20)
    private fun frame(at: Long = 100, fare: Double = 20.0, trip: Boolean = false) =
        UberM1TemporalRecoveryV1.Frame("journey|generation", at, 1000, 1000, true, true, false,
            listOf(fare), 250.0, 110.0, listOf(line("UberX Exclusivo", 40), line("R$ 20,00", 100),
                if(trip) line("20 min (8,0 km)", 340) else line("5 min (2,0 km)", 240), line("Aceitar", 500)))
    private fun pair(second: UberM1TemporalRecoveryV1.Frame): UberM1TemporalRecoveryV1.Observation {
        val recovery = UberM1TemporalRecoveryV1()
        assertNull(recovery.observe(frame()).lines)
        return recovery.observe(second)
    }
    @Test fun compatibleComplementaryFramesUseOfficialParserAndIntegrity() {
        val merged = pair(frame(at = 1000, trip = true)).lines
        assertNotNull(merged)
        val offer = OfferParser.parse(merged!!.joinToString("\n") { it.text }, AppSignals.UBER_PACKAGE,
            "media-projection-ocr/uber-m1-temporal", DriverSettings(), confidence = .8)
        assertNotNull(offer)
        assertTrue(OfferIntegrityGate027033.assess(offer!!).ready)
        assertFalse(OfferIntegrityGate027033.assess(offer.copy(pickupMinutes = null, pickupKm = null)).ready)
    }
    @Test fun differentFareNeverCombines() {
        val d=pair(frame(at=1000,fare=21.0,trip=true)); assertNull(d.lines); assertEquals(1,d.conflict)
    }
    @Test fun expiredWindowAndChangedJourneyNeverCombine() {
        val d=pair(frame(at=2101,trip=true)); assertNull(d.lines); assertEquals(1,d.stale)
        assertNull(pair(frame(at=1000,trip=true).copy(scope="another-journey")).lines)
    }
    @Test fun multipleFaresOrNoAnchorOrNavigationCannotCombine() {
        assertNull(pair(frame(at=1000,trip=true).copy(fares=listOf(20.0,21.0))).lines)
        assertNull(pair(frame(at=1000,trip=true).copy(anchor=false)).lines)
        assertNull(pair(frame(at=1000,trip=true).copy(navigationNoise=true)).lines)
    }
    @Test fun oneIncompleteOrRepeatedFrameNeverFormsCandidate() {
        val r=UberM1TemporalRecoveryV1()
        assertNull(r.observe(frame()).lines)
        assertNull(r.observe(frame(at=1000)).lines)
    }
    @Test fun geometryRotationFarePositionAndSameSlotConflictsFailClosed() {
        assertNull(pair(frame(at=1000,trip=true).copy(width=1200)).lines)
        assertNull(pair(frame(at=1000,trip=true).copy(fareX=500.0)).lines)
        assertNull(pair(frame(at=1000).copy(lines=frame().lines.map {
            if(it.top==240) it.copy(text="8 min (3,0 km)") else it
        })).lines)
    }
    @Test fun leavingCandidateContextClearsMemory() {
        val r=UberM1TemporalRecoveryV1()
        r.observe(frame())
        r.observe(frame(at=500).copy(candidate=false))
        assertNull(r.observe(frame(at=1000,trip=true)).lines)
    }

    @Test fun expiryCannotEraseNewerFrameAndCompleteSingleFrameIsNotRecovered() {
        val r=UberM1TemporalRecoveryV1()
        r.observe(frame())
        r.expire(100)
        assertNull(r.observe(frame(at=1000,trip=true)).lines)
        r.expire(100) // old timer cannot clear the newer buffered frame
        assertNotNull(r.observe(frame(at=1500)).lines)
        val complete=frame().copy(lines=frame().lines+line("20 min (8,0 km)",340))
        assertNull(UberM1TemporalRecoveryV1().observe(complete).lines)
    }

    private val root: File by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(name: String) = File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    @Test fun recoveredOffersUseExistingIntegrityAdmissionAndDedupeWithoutReader2() {
        val service=source("MediaProjectionOcrService.kt")
        assertTrue(service.contains("OfferContextExtractor0221.attach"))
        assertTrue(service.indexOf("val temporalOffers =") < service.indexOf("offers.associateWith(OfferIntegrityGate027033::assess)"))
        assertTrue(service.contains("dispatcher.submitStabilized(completeOffers)"))
        val dispatcher=source("OfferDispatcher.kt")
        assertTrue(dispatcher.contains("OfferAdmissionGate029.admit"))
        assertTrue(dispatcher.contains("OfferDeduplicator"))
        val temporal=source("UberM1TemporalRecoveryV1.kt")
        assertFalse(temporal.contains("Reader2"))
        assertFalse(temporal.contains("TextRecognizer"))
        assertFalse(temporal.contains("Bitmap"))
        assertTrue(source("Reader2Accumulator032.kt").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321.kt").contains("put(\"controlled_hybrid_effect\", false)"))
    }
    @Test fun previewIsLocalVisibleScrolledAndDiagnosticAndAssistantDemoRemains() {
        val panel=source("RadarContextualPanelV1.kt")
        val demo=panel.substringAfter("fun openDemo()").substringBefore("fun focusOpportunity")
        assertFalse(demo.contains("fetch("))
        assertTrue(demo.contains("renderOrDefer()"))
        assertTrue(panel.contains("smoothScrollTo(0, 0)"))
        assertTrue(panel.contains("demoPreviewRendered"))
        val activity=source("ConsolidatedMainActivity027037.kt").substringAfter("fun openRadarContextualDemo()").substringBefore("fun toggleJourney")
        assertTrue(activity.contains("refreshRadarSurface(forceContextual = true"))
        assertTrue(activity.contains("radarContextualPanel.visibility = View.VISIBLE"))
        assertTrue(activity.contains("demoPreviewOpened"))
        assertTrue(activity.contains("Prévia DEMO aberta"))
        assertTrue(source("DestinationRadarAssistantRendererV1.kt").contains("onView = { RadarDestinationLauncherV1.openDemo(app) }"))
        val bridge=source("DestinationRadarRuntimeBridgeV1.kt")
        assertTrue(bridge.contains("JourneyBubbleController.radarUpdated"))
        assertFalse(bridge.contains("openRadar("))
        assertFalse(bridge.contains("startActivity("))
    }
}
