package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class Field97RegressionContractTest {
    private val root by lazy { generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
        .first { File(it,"app/build.gradle.kts").exists() } }
    private fun source(name: String) = File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()
    private fun offer(context: OfferContext? = null) = RideOffer(
        observedAt="2026-10-09T12:00:00Z",sourcePackage="com.ubercab.driver",captureMethod="media-projection",rawText="",
        fare=40.0,pickupKm=2.0,tripKm=8.0,totalKm=10.0,pickupMinutes=5,tripMinutes=20,totalMinutes=25,
        perKm=4.0,perHour=96.0,perMinute=1.6,estimatedCost=8.5,estimatedProfit=31.5,profitPerHour=75.6,
        profitPercent=78.75,passengerRating=4.95,advertisedPerKm=null,verdict="boa",context=context,dedupeKey="test-vc97")
    private val now=Instant.parse("2026-10-09T12:00:00Z").toEpochMilli()
    private fun decision(minutes: Int, strong: Boolean=false, opportunities: Boolean=false) =
        DestinationRadarAssistantBridgeV1.decide(RadarContextualDemoV1.result().let {
            it.copy(opportunities=if(opportunities) it.opportunities else emptyList(),assistant=it.assistant.copy(eligible=strong))
        },now,null,0,true,Instant.ofEpochMilli(now+minutes*60_000L).toString(),"ride-a")

    @Test fun labelPresentDoesNotMeanResolvedCoordinates() {
        val c=OfferContext(pickupLabel="Origem",destinationLabel="Destino",geocodeStatus="partial")
        assertEquals("GEOCODE_PARTIAL",FieldPipelineFactsV1.geocodeStage(c.geocodeStatus))
        assertEquals("destination_coordinates_missing_or_invalid",FieldPipelineFactsV1.specBlock(offer(c)))
        assertNull(RadarDestinationContextV1.fromOffer(offer(c)))
    }
    @Test fun pendingFailedAndUnknownGeocodeRemainDistinctFromReader() {
        for(s in listOf("pending","failed","unresolved")) {
            assertEquals("GEOCODE_MISSING",FieldPipelineFactsV1.geocodeStage(s))
            assertEquals(s,FieldPipelineFactsV1.geocodeReason(s))
        }
        assertEquals("unknown",FieldPipelineFactsV1.geocodeReason("private free text"))
    }
    @Test fun specIdentityAndInvalidInputsAreObservable() {
        assertEquals("source_offer_missing",FieldPipelineFactsV1.specBlock(null))
        assertEquals("context_missing",FieldPipelineFactsV1.specBlock(offer()))
        val valid=offer(OfferContext(destinationLat=-23.0,destinationLng=-46.0,estimatedArrivalAt="2026-10-09T12:00:00Z"))
        assertNull(FieldPipelineFactsV1.specBlock(valid))
        assertEquals(valid.localId,RadarDestinationContextV1.fromOffer(valid)?.localOfferId)
        assertEquals("eta_missing_or_invalid",FieldPipelineFactsV1.specBlock(valid.copy(context=valid.context!!.copy(estimatedArrivalAt="invalid"))))
        assertFalse(FieldPipelineFactsV1.coordinatesValid(Double.NaN,0.0))
        assertFalse(FieldPipelineFactsV1.coordinatesValid(0.0,181.0))
    }
    @Test fun etaSignsAndBoundariesDoNotRelaxPolicy() {
        assertEquals(-300L,FieldPipelineFactsV1.etaDeltaSeconds(Instant.ofEpochMilli(now-300_000).toString(),now))
        for(m in listOf(-10,-5,0,18)) assertEquals(DestinationRadarAssistantBridgeV1.Kind.REGION,decision(m).signal?.kind)
        for(m in listOf(-11,19)) {
            assertNull(decision(m).signal)
            assertEquals("outside_region_eta_window",decision(m).reason)
        }
    }
    @Test fun highBaselineWithoutPoisNeverInventsStrong() {
        val r=RadarContextualDemoV1.result().let { it.copy(opportunities=emptyList(),baseline=it.baseline.copy(probabilityPct=99.0),assistant=it.assistant.copy(eligible=false)) }
        val d=DestinationRadarAssistantBridgeV1.decide(r,now,null,0,true,Instant.ofEpochMilli(now).toString(),"a")
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.REGION,d.signal?.kind)
        assertNull(d.signal?.opportunityId)
    }
    @Test fun strongStillComesFromBackendAndDiscoveryFromExistingOpportunity() {
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.STRONG,decision(30,strong=true,opportunities=true).signal?.kind)
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.DISCOVERY,decision(0,opportunities=true).signal?.kind)
    }
    @Test fun zeroOpportunityReasonAndHttpAreTracedWithoutInventingCause() {
        val trace=source("FieldPipelineTraceV1")
        assertTrue(trace.contains("zero_reason_not_exposed_by_backend"))
        assertTrue(trace.contains("returned_opportunity_pois_not_catalogue_count"))
        assertTrue(source("RadarContextualClientV1").contains("response.status.toLong()"))
        assertTrue(trace.contains("BASELINE_SAMPLES"))
    }
    @Test fun traceStaysBoundedPersistentAndDistinctFromTraditionalAssistant() {
        val trace=source("FieldPipelineTraceV1")
        for(t in listOf("MAX_EPISODES = 12","MAX_EVENTS = 64","RETENTION_MS = 7", "ArrayBlockingQueue<Runnable>(128)",
            "getSharedPreferences", "putString(\"episodes\"", "layers.put(safeStage", "destination_contextual_only", "see_existing_active_assistant_diagnostic_and_history")) assertTrue(t,trace.contains(t))
        assertTrue(source("RadarContextualDiagnosticV1").contains("FieldPipelineTraceV1.snapshot(context)"))
        assertTrue(trace.contains("inferred_only_for_persisted_capture_offers_not_rejected_frames"))
    }
    @Test fun retentionBoundsMemoryKeepsRecentLruAndExpiresOldEpisodes() {
        assertEquals(setOf(2,3),FieldPipelineFactsV1.retainedEpisodeIndices(listOf(1L,90L,95L,99L),100L,2,20L))
        assertEquals(emptySet<Int>(),FieldPipelineFactsV1.retainedEpisodeIndices(listOf(1L,101L),100L,12,20L))
        assertEquals(emptySet<Int>(),FieldPipelineFactsV1.retainedEpisodeIndices(listOf(99L),100L,0,20L))
    }
    @Test fun journalOnlySerializesFactsNotOfferContentOrRawErrors() {
        val trace=source("FieldPipelineTraceV1")
        for(t in listOf("rawText", "put(\"lat", "put(\"lng", "put(\"label", "put(\"token", "put(\"bitmap", ".message", "BackendClient", "TelemetryV1.track")) assertFalse(t,trace.contains(t))
        assertTrue(trace.contains("reason.takeIf { it in reasons } ?: \"unknown\""))
        assertTrue(trace.contains("stage.takeIf { it in stages } ?: return"))
        assertTrue(trace.contains("SHA-256")); assertTrue(trace.contains("take(12)"))
    }
    @Test fun staleResponsesKeepCapturedTraceIdentity() {
        assertTrue(source("DestinationRadarRuntimeV1").contains("traceOfferId=spec.localOfferId"))
        assertTrue(source("RadarContextualPanelV1").contains("traceOfferId = value.localOfferId"))
        assertTrue(source("RadarContextualClientV1").contains("FieldPipelineTraceV1.result(app,traceOfferId"))
        assertTrue(source("RadarDestinationContextV1").contains("latest_offer_snapshot"))
        assertTrue(source("RadarDestinationContextV1").contains("persisted_recent_offer"))
    }
    @Test fun visibleEventRequiresActualAttachedShownMeasuredCard() {
        val renderer=source("DestinationRadarAssistantRendererV1")
        val post=renderer.substringAfter("        // Pre-draw").substringBefore("        val task =")
        for(t in listOf("current === card", "column.isAttachedToWindow", "card.isAttachedToWindow", "column.isShown", "card.isShown", "card.width > 0", "card.height > 0", "ASSISTANT_VISIBLE_BLOCKED")) assertTrue(t,post.contains(t))
        assertTrue(renderer.contains("ASSISTANT_RENDER_ATTEMPT"))
        assertTrue(source("DestinationRadarRuntimeBridgeV1").contains("assistant_disabled"))
    }
    @Test fun clickStillUsesSameRideGuardAndCannotAutoLaunch() {
        val renderer=source("DestinationRadarAssistantRendererV1")
        assertTrue(renderer.contains("DestinationRadarInteractionV1.viewIfCurrentRide("))
        assertTrue(renderer.contains("ASSISTANT_VIEW_CLICKED"))
        val runtime=source("DestinationRadarRuntimeBridgeV1")+source("DestinationRadarRuntimeV1")
        assertFalse(runtime.contains("openRadar(")); assertFalse(runtime.contains("startActivity("))
    }
    @Test fun markerDiversityIsNotConclusiveCartographyEvidence() {
        assertFalse(FieldPipelineFactsV1.cartographyProvenByNonblankBitmap())
        assertTrue(source("RadarMiniMapViewV1").contains("bitmap_visible_cartography_unverified"))
        assertTrue(source("RadarMapBitmapSurfaceV1").contains("isClickable = false"))
        assertTrue(source("RadarMiniMapViewV1").contains("https://tiles.openfreemap.org/styles/liberty"))
    }
    @Test fun menuStartsCollapsedTogglesAndClosesOnAction() {
        val s=HudJourneyMenuStateV1();assertFalse(s.expanded)
        s.toggle();assertTrue(s.expanded);s.toggle();assertFalse(s.expanded)
        s.toggle();s.close();assertFalse(s.expanded)
        val chrome=source("FloatingWindowChrome023")
        for(t in listOf("closeJourney(); actions.play()", "closeJourney(); actions.pause()", "closeJourney(); actions.stop()",
            "closeJourney(); DiagnosticQuickActions0270.restartReading(context)", "closeJourney(); actions.rescan()")) assertTrue(t,chrome.contains(t))
        assertTrue(source("JourneyBubbleController").contains("FloatingWindowChrome023.collapseJourneyMenu(panel)"))
    }
    @Test fun permanentRowDoesNotContainSeparateOperationalControls() {
        val row=source("FloatingWindowChrome023").substringAfter("\"Jornada · expandir/recolher\"").substringBefore("addView(actionRow")
        assertFalse(row.contains("sr23_float_pause"));assertFalse(row.contains("sr23_float_stop"))
        for(t in listOf("Foto / Rescan","sr23_float_history","Digitalizar Uber","sr23_float_message","Diagnóstico")) assertTrue(row.contains(t))
    }
    @Test fun rescanLeaseExcludesLiveAndOtherScansUntilRealCompletion() {
        val gate=ScreenshotRescanLeaseV1()
        assertTrue(gate.acquire(false));assertFalse(gate.acquire(false));assertFalse(gate.foreignStart())
        gate.complete();assertTrue(gate.foreignStart());assertFalse(gate.acquire(false))
    }
    @Test fun foreignWorkerHistoryRequiresColdProcessNotAssumedDrain() {
        val gate=ScreenshotRescanLeaseV1()
        assertTrue(gate.foreignStart());assertFalse(gate.acquire(false))
        gate.complete();assertFalse(gate.acquire(false))
        assertTrue(ScreenshotRescanLeaseV1().acquire(false))
    }
    @Test fun openJourneyOrServiceBlocksWithoutStoppingCapture() {
        assertFalse(ScreenshotRescanLeaseV1().acquire(true))
        val guard=source("ScreenshotRescanGateV1")
        for(t in listOf("repo.isProjectionActive()", "repo.currentJourneyId().isNotBlank()", "getRunningServices", "MODE_M1")) assertTrue(t,guard.contains(t))
        assertFalse(guard.contains("stopService("));assertFalse(guard.contains("ACTION_STOP"))
    }
    @Test fun atomicAcquisitionClosesRescanVersusStartRace() {
        repeat(50) {
            val gate=ScreenshotRescanLeaseV1();val go=CountDownLatch(1)
            val rescan=AtomicBoolean();val foreign=AtomicBoolean()
            val a=Thread { go.await();rescan.set(gate.acquire(false)) }
            val b=Thread { go.await();foreign.set(gate.foreignStart()) }
            a.start();b.start();go.countDown();a.join();b.join()
            assertTrue(rescan.get() xor foreign.get())
        }
    }
    @Test fun legitimateStartCallsitesParticipateBeforeRequestingWorker() {
        for(n in listOf("ConsolidatedMainActivity027037","DiagnosticControls0270","UberDigitizationActivity026","HistoricalScreenshotImporter"))
            assertTrue(n,source(n).contains("ScreenshotRescanGateV1.allowForeignStart("))
        assertTrue(source("SrRotasApplication").contains("ScreenshotRescanGateV1.seed(this)"))
    }
    @Test fun uriIsOneImageReadOnlyAndActivityNotExported() {
        val ui=source("OfferRescanActivityV1")
        for(t in listOf("ACTION_OPEN_DOCUMENT", "CATEGORY_OPENABLE", "FLAG_GRANT_READ_URI_PERMISSION", "EXTRA_ALLOW_MULTIPLE,false", "uri.scheme != \"content\"", "openInputStream(uri)")) assertTrue(t,ui.contains(t))
        for(t in listOf("FLAG_GRANT_WRITE", "takePersistableUriPermission", "MediaStore", "openOutputStream")) assertFalse(t,ui.contains(t))
        assertTrue(File(root,"app/src/main/AndroidManifest.xml").readText().contains(".OfferRescanActivityV1\" android:exported=\"false\""))
    }
    @Test fun previewAndConfirmationPrecedeManualSingleOcr() {
        val ui=source("OfferRescanActivityV1")
        assertTrue(ui.contains("Confirmar rescan desta imagem"));assertTrue(ui.contains("setImageBitmap(image)"))
        val load=ui.substringAfter("private fun load(").substringBefore("private fun scan()")
        assertFalse(load.contains("TextRecognition"));assertFalse(load.contains("process(InputImage"))
        assertEquals(1,Regex("TextRecognition.getClient").findAll(ui).count())
    }
    @Test fun leaseAndBitmapSurviveUiTimeoutUntilActualOcrCompletion() {
        val scan=source("OfferRescanActivityV1").substringAfter("private fun scan()")
        assertTrue(scan.indexOf("Tasks.await(")<scan.indexOf("ScreenshotRescanGateV1.complete()"))
        assertTrue(scan.indexOf("recognizer.close()")<scan.indexOf("ScreenshotRescanGateV1.complete()"))
        assertTrue(scan.indexOf("image.recycle()")<scan.indexOf("ScreenshotRescanGateV1.complete()"))
        assertFalse(scan.substringAfter("override fun onDestroy()").contains("ScreenshotRescanGateV1.complete()"))
        assertFalse(scan.substringAfter("warning = Runnable").substringBefore("executor.execute").contains("complete()"))
        assertTrue(scan.contains("WeakReference(this)"))
    }
    @Test fun rescanHasNoOfficialMutationOrNetworkSideEffects() {
        val ui=source("OfferRescanActivityV1")
        for(t in listOf("saveOffer(", "saveOrUpdateContext", "markDoingRide", "sendOffer", "BackendClient", "OfferAdmission", "HistoricalScreenshotImporter.import", "TelemetryV1.track", "Geocoder", "compress(")) assertFalse(t,ui.contains(t))
        assertTrue(ui.contains("SpatialOfferParser.parse("));assertTrue(ui.contains("offers.size <= 1"))
    }
    @Test fun imageBoundsAndExifAreHandledBeforeRecognition() {
        val image=source("ScreenshotRescanImageV1")
        for(t in listOf("16 * 1024 * 1024", "40_000_000L", "inJustDecodeBounds = true", "inSampleSize = sample", "1800", "TAG_ORIENTATION", "bitmap.recycle()")) assertTrue(t,image.contains(t))
    }
    @Test fun privateIndexIsExactNewCaptureOnlyAndRetentionFollowsCache() {
        val index=source("PrivateScreenshotIndexV1")
        assertTrue(index.contains("index.optString(fullOfferId)"));assertTrue(index.contains("it.parentFile == dir && it.isFile"))
        assertTrue(index.contains("index.length() > 30"));assertFalse(index.contains("MediaStore"));assertFalse(index.contains("take(8)"))
        assertTrue(source("PrivateScreenshotStore").contains("PrivateScreenshotIndexV1.saved(context, offer.localId, savedFile)"))
        assertTrue(source("PrivateScreenshotStore").contains("PrivateScreenshotIndexV1.clear(context)"))
    }
    @Test fun comparisonsDistinguishEqualRecoveredDifferentMissing() {
        assertEquals("igual",ScreenshotRescanComparisonV1.status("x"," x "))
        assertEquals("recuperado",ScreenshotRescanComparisonV1.status(null,"x"))
        assertEquals("diferente",ScreenshotRescanComparisonV1.status("x","y"))
        assertEquals("ausente",ScreenshotRescanComparisonV1.status("x",null))
        assertTrue(ScreenshotRescanComparisonV1.describe(offer(),offer()).contains("ainda insuficiente"))
    }
    @Test fun rollbackBlocksNewExecutionWithoutMutatingJourney() {
        val guard=source("ScreenshotRescanGateV1")
        assertTrue(guard.contains("lease.acquire(!enabled(context) || liveBlocked(context))"))
        assertTrue(guard.contains("putBoolean(\"enabled\",enabled)"))
        assertTrue(source("OfferRescanActivityV1").contains("Desativar rescan (rollback)"))
    }
    @Test fun readerSemanticsAndHybridRemainFrozen() {
        Field95RegressionContractTest.assertServiceReaderSemantics(source("MediaProjectionOcrService"))
        assertTrue(source("Reader2Accumulator032").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321").contains("put(\"controlled_hybrid_effect\", false)"))
    }
}
