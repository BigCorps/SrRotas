package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class RadarContextualField7ContractTest {
    private val now=Instant.parse("2026-10-07T18:00:00Z").toEpochMilli()
    private val root:File by lazy { generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile){it.parentFile}
        .first { File(it,"app/build.gradle.kts").exists() } }
    private fun source(name:String)=File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private fun real()=RadarSurfaceCoordinatorV1.Request.Real(RadarContextualDemoV1.spec(),null,"hud_cta")
    @Test fun demoAndRealWaitUntilResumeAndConsumeExactlyOnce() {
        for(request in listOf(RadarSurfaceCoordinatorV1.Request.Demo("assistant_demo"),real())) {
            val c=RadarSurfaceCoordinatorV1(); c.request(request)
            assertNull(c.readyRequest()); assertFalse(c.consume(request))
            c.resume(); assertSame(request,c.readyRequest())
            assertTrue(c.consume(request)); assertFalse(c.consume(request)); assertNull(c.readyRequest())
            c.pause(); c.resume(); assertNull(c.readyRequest())
        }
    }
    @Test fun pausedPendingSurvivesButCancelledAndSupersededCallbacksCannotConsume() {
        val c=RadarSurfaceCoordinatorV1(); val first=real(); c.request(first); c.resume(); val scheduledGeneration=c.generation; c.pause()
        assertTrue(c.generation>scheduledGeneration)
        assertFalse(c.consume(first)); c.resume(); assertSame(first,c.readyRequest())
        val second=RadarSurfaceCoordinatorV1.Request.Demo("field_demo"); c.request(second)
        assertFalse(c.consume(first)); assertSame(second,c.readyRequest())
        c.cancel(); assertFalse(c.consume(second)); assertNull(c.readyRequest())
    }
    @Test fun normalRefreshPrecedesPostResumeDrainAndAutomaticRefreshPreservesDemo() {
        val activity=source("ConsolidatedMainActivity027037.kt")
        val resume=activity.substringAfter("override fun onResume()").substringBefore("override fun onPostResume()")
        assertTrue(resume.contains("refreshRadarSurface()")); assertFalse(resume.contains("scheduleRadarOpen()"))
        val post=activity.substringAfter("override fun onPostResume()").substringBefore("override fun onPause()")
        assertTrue(post.indexOf("radarSurface.resume()")<post.indexOf("scheduleRadarOpen()"))
        val refresh=activity.substringAfter("private fun refreshRadarSurface(").substringBefore("private fun statisticsContainer")
        assertTrue(refresh.contains("radarSurface.pending != null"))
        assertTrue(refresh.contains("abandonDemo || !radarContextualPanel.isDemo()"))
        assertTrue(activity.contains("refreshRadarSurface(abandonDemo = true)"))
    }
    @Test fun pauseReleasesMapWithoutAbandoningDemoAndRollbackExplicitlyCancelsIt() {
        val panel=source("RadarContextualPanelV1.kt")
        val pause=panel.substringAfter("fun setSurfaceResumed").substringBefore("fun cancelSurface")
        assertTrue(pause.contains("releaseMap(\"activity_paused\")"))
        assertFalse(pause.contains("demoMode=false"))
        val activity=source("ConsolidatedMainActivity027037.kt")
        assertTrue(activity.contains("if(abandonDemo) radarContextualPanel.cancelSurface(\"stage_changed\")"))
        assertTrue(activity.contains("if(radarSurface.pending==null) radarContextualPanel.renderPendingIfReady()"))
        assertTrue(activity.contains("radarContextualPanel.setSurfaceResumed(true)"))
    }

    @Test fun nowEntryResolvesCurrentSpecAtOneClickAndDoesNotUsePendingIntent() {
        val entry=source("RadarDestinationEntryV1.kt").substringAfter("setOnClickListener")
        assertTrue(entry.contains("RadarDestinationContextV1.current(context)"))
        assertTrue(entry.contains("onOpen(current)")); assertFalse(entry.contains("onOpen(spec)"))
        assertTrue(entry.contains("current_spec_unavailable")); assertFalse(entry.contains("PendingIntent"))
        assertTrue(source("ConsolidatedMainActivity027037.kt").contains("requestRadarReal(spec, null, \"now_entry\")"))
    }
    @Test fun surfaceRequiresAttachVisibilityAndMeasuredAreaAndRetriesAreBounded() {
        val good=RadarSurfaceCoordinatorV1.Metrics(true,true,true,320,400,320,400)
        assertTrue(good.ready)
        for(bad in listOf(good.copy(stageAttached=false),good.copy(panelAttached=false),good.copy(shown=false),
            good.copy(stageWidth=0),good.copy(stageHeight=0),good.copy(panelWidth=0),good.copy(panelHeight=0))) assertFalse(bad.ready)
        for(attempt in 1..5) assertTrue(RadarSurfaceCoordinatorV1.mayRetry(attempt))
        assertFalse(RadarSurfaceCoordinatorV1.mayRetry(6)); assertFalse(RadarSurfaceCoordinatorV1.mayRetry(7))
        val activity=source("ConsolidatedMainActivity027037.kt").substringAfter("private fun awaitRadarSurface")
        assertTrue(activity.contains("postOnAnimation")); assertTrue(activity.contains("mayRetry(attempt)"))
        assertTrue(activity.indexOf("if(!metrics.ready)")<activity.indexOf("radarContextualPanel.openDemo()"))
        assertTrue(activity.contains("surface_not_ready"))
    }
    @Test fun validEarlyBackendResponseIsStoredBeforeDeferAndStaleRideStillRejected() {
        val panel=source("RadarContextualPanelV1.kt")
        val callback=panel.substringAfter(") { response ->").substringBefore("/**\n     * Refresh")
        assertFalse(callback.substringBefore("response.onSuccess").contains("!isShown"))
        assertTrue(callback.contains("generation != requestGeneration || demoMode"))
        assertTrue(callback.contains("spec?.localOfferId != value.localOfferId"))
        assertTrue(callback.contains("current(context)?.localOfferId != value.localOfferId"))
        assertTrue(callback.indexOf("result = it")<callback.indexOf("renderOrDefer()"))
        val pending=panel.substringAfter("fun renderPendingIfReady()").substringBefore("private fun renderOrDefer")
        assertTrue(pending.contains("pendingGeneration!=requestGeneration"))
        assertTrue(pending.contains("stale_render"))
        assertTrue(pending.indexOf("!isShown")<pending.indexOf("renderResult(value)"))
        assertTrue(pending.contains("!surfaceResumed"))
        assertTrue(pending.contains("!isAttachedToWindow")); assertTrue(pending.contains("height<=0"))
        assertTrue(pending.contains("surfaceRenderDeferred()"))
        assertFalse(pending.contains("ensureMap()"))
    }
    @Test fun demoFieldAssistantAndRealHudAssistantUseSameSurfaceCoordinator() {
        val activity=source("ConsolidatedMainActivity027037.kt")
        assertTrue(activity.contains("fun openRadarContextualDemo() = requestRadarDemo(\"field_demo\")"))
        val actions=activity.substringAfter("private fun handleBubbleAction")
        assertTrue(actions.contains("requestRadarDemo(")); assertTrue(actions.contains("requestRadarReal("))
        assertFalse(actions.contains("radarContextualPanel.openDemo()"))
        assertFalse(actions.contains("radarContextualPanel.openDestination("))
        assertTrue(activity.contains("RadarSurfaceCoordinatorV1.Request.Real"))
        assertTrue(activity.contains("RadarSurfaceCoordinatorV1.Request.Demo"))
        assertTrue(source("DestinationRadarAssistantRendererV1.kt").contains("source=\"assistant_real\""))
        assertTrue(source("RadarDestinationLauncherV1.kt").contains("source=\"assistant_demo\""))
        assertTrue(source("JourneyBubbleController.kt").contains("RadarDestinationLauncherV1.openRadar(context)"))
    }
    @Test fun realReadinessRevalidatesCurrentRideAndOnlyClicksOpenMap() {
        val activity=source("ConsolidatedMainActivity027037.kt").substringAfter("private fun awaitRadarSurface")
        assertTrue(activity.contains("current?.localOfferId!=request.spec.localOfferId"))
        assertTrue(activity.contains("openDestination(current, opportunityId=request.opportunityId)"))
        val bridge=source("DestinationRadarRuntimeBridgeV1.kt")
        assertFalse(bridge.contains("openRadar(")); assertFalse(bridge.contains("startActivity("))
    }
    @Test fun etaBoundariesAllowArrivalAndTenMinuteGraceButNoMore() {
        for(count in listOf(0,1)) {
            for(delta in listOf(18L,0L,-5L,-10L)) assertNotNull(decide(count,delta).signal)
            for(delta in listOf(19L,-11L)) assertNull(decide(count,delta).signal)
            val demo=RadarContextualDemoV1.result()
            val boundary=demo.copy(opportunities=demo.opportunities.take(count),assistant=demo.assistant.copy(eligible=false),
                destinationEta=Instant.ofEpochMilli(now-600_001).toString())
            assertNull(DestinationRadarAssistantBridgeV1.decide(boundary,now,null,0,true,boundary.destinationEta,"ride").signal)
        }
    }
    private fun decide(count:Int,minutes:Long):DestinationRadarAssistantBridgeV1.Decision {
        val demo=RadarContextualDemoV1.result()
        val result=demo.copy(opportunities=demo.opportunities.take(count),assistant=demo.assistant.copy(eligible=false),
            destinationEta=Instant.ofEpochMilli(now+minutes*60_000).toString())
        return DestinationRadarAssistantBridgeV1.decide(result,now,null,0,true,result.destinationEta,"ride")
    }
    @Test fun strongUnchangedAndRegionStillHasNoFakeOpportunity() {
        val demo=RadarContextualDemoV1.result().copy(destinationEta=Instant.ofEpochMilli(now-60*60_000).toString())
        val strong=DestinationRadarAssistantBridgeV1.decide(demo,now,null,0,true,demo.destinationEta,"ride").signal!!
        assertEquals(demo.assistant.headline,strong.headline); assertEquals(demo.assistant.opportunityId,strong.opportunityId)
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.STRONG,strong.kind)
        val region=decide(0,-5).signal!!; assertNull(region.opportunityId)
        assertEquals("region",DestinationRadarInteractionV1.metadataVariant(region))
    }
    @Test fun surfaceDiagnosticsArePersistentMemoryWithoutSensitiveValuesAndFieldReservesMapArea() {
        val diag=source("RadarContextualDiagnosticV1.kt").substringAfter("fun toJson")
        for(key in listOf("eta_delta_seconds","surface_open_source","surface_open_kind","surface_open_state","surface_open_attempts",
            "surface_route_selected","surface_panel_attached","surface_panel_shown","surface_stage_width","surface_stage_height",
            "surface_panel_width","surface_panel_height","surface_render_deferred","surface_render_started","surface_render_completed",
            "surface_block_reason","demo_preview_opened","demo_preview_rendered","map_created_count","map_ready_count",
            "map_release_count","last_map_error","last_map_release_reason")) assertTrue(diag.contains("\"$key\""))
        for(key in listOf("destination_label","destination_lat","destination_lng","ocr","screenshot")) assertFalse(diag.contains("put(\"$key\""))
        val field=source("RadarContextualHomologationV1.kt")
        assertTrue(field.contains("diagnosticScroll.visibility=if(visible)")); assertTrue(field.contains("SrUi023.dp(context,110)"))
    }
    @Test fun readerBaselineShadowHybridOffAndSingleHeavyOcrRemainFrozen() {
        RadarContextualField6ContractTest().everyFrozenReaderFileIsByteIdenticalToFunctionalVc92()
        RadarContextualField6ContractTest().reader2ShadowHybridOffAndSingleHeavyOcrFrozen()
        val script=File(root,"scripts/check-radar-contextual-v6.sh").readText()
        assertTrue(script.contains("1421f512d966101cc6bbd0dfda52cf0626a9c4dd"))
    }
}
