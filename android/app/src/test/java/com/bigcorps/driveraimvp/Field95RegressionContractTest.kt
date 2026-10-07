package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** JVM: decisões reais + contratos de integração Android, sem segundo OCR ou backend. */
class Field95RegressionContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(name:String)=File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private fun readyGate()=RadarMapRenderGateV1().apply { styleLoaded(); submitted() }

    @Test fun styleLoadedAloneKeepsFallback() {
        val g=RadarMapRenderGateV1(); g.styleLoaded()
        assertTrue(g.fallbackVisible); assertFalse(g.fully(true,true))
    }
    @Test fun firstFrameIsNotFullCartography() {
        val g=readyGate(); assertTrue(g.frame(true)); assertTrue(g.fallbackVisible)
        assertFalse(g.fully(false,true))
    }
    @Test fun fullRenderAfterDestinationSubmissionHidesFallback() {
        val g=readyGate(); assertTrue(g.fully(true,true)); assertFalse(g.fallbackVisible)
        assertFalse(g.fully(true,true)) // não contar duas vezes
    }
    @Test fun hiddenSurfaceCannotConfirmRender() {
        val g=readyGate(); assertFalse(g.frame(false)); assertFalse(g.fully(true,false))
        assertTrue(g.fallbackVisible)
    }
    @Test fun timeoutKeepsFallbackEvenWithLateNativeCallback() {
        val g=readyGate(); assertTrue(g.timeout()); assertTrue(g.fallbackVisible)
        assertFalse(g.fully(true,true)); assertFalse(g.timeout())
    }
    @Test fun loadErrorKeepsFallback() {
        val g=RadarMapRenderGateV1(); assertTrue(g.fail()); g.styleLoaded()
        assertFalse(g.fully(true,true)); assertTrue(g.fallbackVisible)
    }
    @Test fun renderErrorKeepsFallback() {
        val g=readyGate(); assertTrue(g.fail()); assertFalse(g.fully(true,true))
        assertTrue(g.fallbackVisible)
    }
    @Test fun releasePreventsTimeoutAndLateRender() {
        val g=readyGate(); g.release(); assertFalse(g.timeout()); assertFalse(g.fully(true,true))
        val s=source("RadarMiniMapViewV1.kt")
        assertTrue(s.substringAfter("fun release(").substringBefore("fun render(").contains("removeCallbacks(renderTimeout)"))
        assertTrue(s.substringAfter("override fun onDetachedFromWindow()").substringBefore("fun release(").contains("removeCallbacks(renderTimeout)"))
    }
    @Test fun preStyleNativeCallbackCannotBecomeSuccessInLaterPost() {
        val s=source("RadarMiniMapViewV1.kt").substringAfter("mapView.addOnDidFinishRenderingMapListener").substringBefore("mapView.addOnRenderErrorListener")
        assertTrue(s.indexOf("val eligible = styleReady && renderGate.submitted") < s.indexOf("post {"))
        assertTrue(s.contains("eligible && !released && renderGate.fully"))
    }
    @Test fun timeoutIsBoundedAndStartsOnAttach() {
        val s=source("RadarMiniMapViewV1.kt")
        assertTrue(s.contains("8_000L")); assertTrue(s.contains("renderDeadlineMs == 0L"))
        assertTrue(s.substringAfter("override fun onAttachedToWindow()").substringBefore("override fun onDetached").contains("armRenderTimeout()"))
    }
    @Test fun installedMapListenersAndProviderArePreserved() {
        val s=source("RadarMiniMapViewV1.kt")
        for (token in listOf("addOnDidFinishLoadingMapListener","OnDidFinishRenderingFrameListener", "addOnDidFinishRenderingMapListener","addOnRenderErrorListener","addOnDidFailLoadingMapListener", "https://tiles.openfreemap.org/styles/liberty")) assertTrue(token,s.contains(token))
        assertTrue(File(root,"app/build.gradle.kts").readText().contains("org.maplibre.gl:android-sdk-opengl:13.6.1"))
        assertFalse(s.substringAfter("ready.setStyle(").substringBefore("override fun onMeasure").contains("View.GONE"))
    }
    @Test fun mapCreationStillUsesSurfaceReadinessAndNoAutoLaunch() {
        val panel=source("RadarContextualPanelV1.kt")
        assertTrue(panel.contains("!isAttachedToWindow")); assertTrue(panel.contains("stage.height<=0"))
        val runtime=source("DestinationRadarRuntimeBridgeV1.kt")
        assertFalse(runtime.contains("openRadar(")); assertFalse(runtime.contains("startActivity("))
    }
    private fun choose(current:String?, selected:String, status:RideOperationalStatus?=RideOperationalStatus.OFFERED,
                       state:JourneyOperationalState=JourneyOperationalState.ACTIVE, sameJourney:Boolean=true)=
        JourneyStateMachine.explicitRideSelection(state,sameJourney,current,selected,status)
    @Test fun firstRideCanBeSelected() { assertEquals(JourneyStateMachine.RideSelection.START,choose(null,"A")) }
    @Test fun secondRideCanReplaceOnlyExplicitly() { assertEquals(JourneyStateMachine.RideSelection.REPLACE,choose("A","B")) }
    @Test fun sameRideSelectionIsIdempotent() { assertEquals(JourneyStateMachine.RideSelection.SAME,choose("A","A",RideOperationalStatus.DOING_RIDE)) }
    @Test fun anotherJourneyOrInactiveJourneyIsRejected() {
        assertEquals(JourneyStateMachine.RideSelection.REJECT,choose("A","B",sameJourney=false))
        for(state in listOf(JourneyOperationalState.PAUSED,JourneyOperationalState.ENDED,JourneyOperationalState.NOT_STARTED))
            assertEquals(JourneyStateMachine.RideSelection.REJECT,choose("A","B",state=state))
    }
    @Test fun finishedOfferCannotBeReopenedAsNewRide() {
        for(status in listOf(RideOperationalStatus.COMPLETED,RideOperationalStatus.CANCELLED,RideOperationalStatus.NOT_COMPLETED))
            assertEquals(JourneyStateMachine.RideSelection.REJECT,choose("A","B",status))
    }
    @Test fun replacementPersistsOldNotCompletedThenNewDoingInTransaction() {
        val s=source("JourneyCoordinator.kt").substringAfter("fun markDoingRide(").substringBefore("fun completeCurrentRide(")
        assertTrue(source("JourneyCoordinator.kt").contains("@Synchronized\n    fun markDoingRide("))
        assertTrue(s.contains("db.beginTransaction()")); assertTrue(s.contains("db.setTransactionSuccessful()"))
        assertTrue(s.contains("db.endTransaction()")); assertTrue(s.contains("replaced_by_new_ride"))
        assertTrue(s.indexOf("RideOperationalStatus.NOT_COMPLETED") < s.indexOf("RideOperationalStatus.DOING_RIDE"))
        assertTrue(s.contains("runtimeRide = outcome")); assertTrue(s.contains("RideSelection.SAME) return previous"))
        assertTrue(source("RadarDestinationContextV1.kt").contains("currentRide"))
    }
    @Test fun completeAndCancelClearCurrentRideAndPermitThirdRide() {
        val s=source("JourneyCoordinator.kt")
        assertTrue(s.substringAfter("fun completeCurrentRide(").substringBefore("private fun finishCurrentRide(").contains("RideOperationalStatus.COMPLETED"))
        assertTrue(s.substringAfter("fun cancelCurrentRide(").substringBefore("private fun finishCurrentRide(").contains("RideOperationalStatus.CANCELLED"))
        assertTrue(s.substringAfter("private fun finishCurrentRide(").substringBefore("fun correctRide(").contains("runtimeRide = null"))
        assertEquals(JourneyStateMachine.RideSelection.START,choose(null,"C"))
    }
    @Test fun operationalActionsAreOnNormalCardAndReportCheckmarkIsSeparate() {
        val s=source("JourneyBubbleController.kt")
        val card=s.substringAfter("private fun offerRow(").substringBefore("private fun operationalRideControls(")
        assertTrue(card.contains("card.addView(operationalRideControls"))
        assertTrue(card.contains("ReportSelection0211.toggle")); assertFalse(card.contains("markDoingRide("))
        assertTrue(source("JourneyBubbleController.kt").contains("offers.none { it.localId == active.localOfferId }"))
        val controls=s.substringAfter("private fun operationalRideControls(").substringBefore("private fun expandedOffer(")
        for(t in listOf("CORRIDA ATIVA","REALIZADA","NÃO REALIZADA","ESTOU NESSA CORRIDA","RadarDestinationLauncherV1.openRadar","RadarContextualIntegrationV1.onOperationalStateChanged")) assertTrue(t,controls.contains(t))
        assertFalse(s.contains("OUTRA CORRIDA ATIVA"))
    }
    @Test fun forceFalseKeepsExistingSameJourneySession() {
        assertTrue(FieldCaptureLifecycleV1.reuseExisting(false,true,true))
        assertFalse(FieldCaptureLifecycleV1.reuseExisting(false,true,false))
        assertFalse(FieldCaptureLifecycleV1.reuseExisting(false,false,true))
    }
    @Test fun forceTrueNeverReusesDegradedProjection() {
        assertFalse(FieldCaptureLifecycleV1.reuseExisting(true,true,true))
        assertFalse(FieldCaptureLifecycleV1.reuseExisting(true,false,true))
    }
    @Test fun freshProjectionReleasesOldBeforeUsingFreshResultData() {
        val s=source("MediaProjectionOcrService.kt").substringAfter("private fun startProjectionFromIntent(").substringBefore("private fun newWorkerThread(")
        assertTrue(s.contains("getResultData(intent)")); assertTrue(s.contains("getMediaProjection(resultCode, resultData)"))
        assertTrue(s.indexOf("releaseProjection(\"projection_superseded\"") < s.indexOf("getMediaProjection(resultCode, resultData)"))
        assertTrue(s.contains("endJourneyIfOwned = false")); assertTrue(s.contains("endJourneyIfOwned = !forceFresh"))
        assertFalse(s.contains("endJourney("))
    }
    @Test fun staleStopCannotKillReplacementSession() {
        val old=Any(); val fresh=Any()
        assertFalse(FieldCaptureLifecycleV1.currentCallback(old,fresh))
        assertFalse(FieldCaptureLifecycleV1.currentCallback(old,null))
        assertTrue(FieldCaptureLifecycleV1.currentCallback(fresh,fresh))
        val stop=source("MediaProjectionOcrService.kt").substringAfter("override fun onStop()").substringBefore("override fun onCapturedContentResize")
        assertTrue(stop.indexOf("currentCallback(") < stop.indexOf("releaseProjection("))
        assertTrue(stop.substringBefore("releaseProjection(").contains("stale_projection_callback_ignored"))
        assertTrue(stop.substringBefore("releaseProjection(").contains("return"))
    }
    @Test fun queuedResizeKeepsOriginalProjectionIdentity() {
        val resize=source("MediaProjectionOcrService.kt").substringAfter("private fun reconfigureCapturedContent(").substringBefore("private fun", "")
        assertTrue(resize.contains("val expectedProjection = projection ?: return"))
        assertTrue(resize.contains("currentCallback(expectedProjection, projection)"))
        assertTrue(resize.indexOf("currentCallback(expectedProjection, projection)") < resize.indexOf("virtualDisplay?.resize"))
    }
    @Test fun actualCurrentStopStillPreservesJourneyAndStopsService() {
        val stop=source("MediaProjectionOcrService.kt").substringAfter("override fun onStop()").substringBefore("override fun onCapturedContentResize")
        assertTrue(stop.contains("projection_stopped_by_system")); assertTrue(stop.contains("endJourneyIfOwned = false"))
        assertTrue(stop.contains("stopSelf()"))
    }
    @Test fun hudRestartAsksFreshConsentAndM2DoesNotOfferIt() {
        val hud=source("JourneyBubbleController.kt").substringAfter("private fun footerControls(").substringBefore("private fun rebuildMessageRail(")
        assertTrue(hud.contains("ReaderLab027036.m1Enabled(context)")); assertTrue(hud.contains("enabled = active || paused"))
        assertTrue(hud.contains("CaptureRecoveryActivity0270.open(context, source = \"hud_quick_restart\")"))
        assertFalse(hud.contains("ACTION_RECOVER"))
        val activity=source("DiagnosticControls0270.kt").substringAfter("class CaptureRecoveryActivity0270").substringBefore("class DiagnosticExportActivity0270")
        assertTrue(activity.contains("createScreenCaptureIntent")); assertTrue(activity.contains("EXTRA_FORCE_FRESH_PROJECTION, true"))
        assertTrue(activity.contains("MODE_M2")); assertTrue(activity.contains("recoveryJourneyId"))
        assertTrue(source("ConsolidatedMainActivity027037.kt").contains("EXTRA_FORCE_FRESH_PROJECTION, wasRecovery"))
    }
    @Test fun technicalRecoveryDoesNotAskConsent() {
        val service=source("MediaProjectionOcrService.kt")
        val action=service.substringAfter("ACTION_RECOVER -> {").substringBefore("ACTION_START ->")
        assertTrue(action.contains("manualRecoverReader(")); assertFalse(action.contains("startActivity("))
        val recovery=service.substringAfter("private fun manualRecoverReader(").substringBefore("private fun","")
        assertFalse(recovery.contains("createScreenCaptureIntent"))
    }
    @Test fun captureDiagnosticExportsOnlyWhitelistedCounters() {
        FieldCaptureRecoveryDiagnosticV1.record("raw_unknown_event")
        assertFalse(FieldCaptureRecoveryDiagnosticV1.snapshot().containsKey("raw_unknown_event"))
        val before=FieldCaptureRecoveryDiagnosticV1.snapshot().getValue("technical_recovery_health_restored")
        FieldCaptureRecoveryDiagnosticV1.record("technical_recovery_requested")
        FieldCaptureRecoveryDiagnosticV1.health(false)
        assertEquals(before,FieldCaptureRecoveryDiagnosticV1.snapshot().getValue("technical_recovery_health_restored"))
        FieldCaptureRecoveryDiagnosticV1.health(true)
        FieldCaptureRecoveryDiagnosticV1.health(true)
        assertEquals(before+1,FieldCaptureRecoveryDiagnosticV1.snapshot().getValue("technical_recovery_health_restored"))
    }
    @Test fun freshConsentDoesNotClaimTechnicalRecoverySuccess() {
        val before=FieldCaptureRecoveryDiagnosticV1.snapshot().getValue("technical_recovery_health_restored")
        FieldCaptureRecoveryDiagnosticV1.record("technical_recovery_requested")
        FieldCaptureRecoveryDiagnosticV1.record("fresh_projection_requested")
        FieldCaptureRecoveryDiagnosticV1.health(true)
        assertEquals(before,FieldCaptureRecoveryDiagnosticV1.snapshot().getValue("technical_recovery_health_restored"))
    }
    @Test fun readerSemanticsAreFrozenAtVc92() { assertServiceReaderSemantics(source("MediaProjectionOcrService.kt")) }
    @Test fun serviceReaderMutationIsRejectedByBaselineHash() {
        val original=source("MediaProjectionOcrService.kt")
        val changed=original.replace("private fun processBitmap", "private fun processBitmap_MUTATED")
        assertNotEquals(original,changed)
        var rejected=false
        try { assertServiceReaderSemantics(changed) } catch (_:AssertionError) { rejected=true }
        assertTrue("OCR pipeline alteration must fail",rejected)
    }
    @Test fun reader2ShadowHybridOffAndSingleHeavyOcrRemain() {
        assertTrue(source("Reader2Accumulator032.kt").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321.kt").contains("put(\"controlled_hybrid_effect\", false)"))
        assertEquals(1,Regex("TextRecognition\\.getClient\\(").findAll(source("MediaProjectionOcrService.kt")).count())
    }
    companion object {
        fun assertServiceReaderSemantics(text:String) {
            var s=text
            val a=s.indexOf("    private fun startProjectionFromIntent(intent: Intent) {")
            val b=s.indexOf("        startAsForeground()",a)
            s=s.substring(0,a)+"<SESSION_LIFECYCLE>\n"+s.substring(b)
            val inserts=listOf(
                "        const val EXTRA_FORCE_FRESH_PROJECTION = \"force_fresh_projection\"\n",
                "                FieldCaptureRecoveryDiagnosticV1.record(\"technical_recovery_requested\")\n",
                "        if (forceFresh) FieldCaptureRecoveryDiagnosticV1.record(\"fresh_projection_started\")\n",
                "                    if (!FieldCaptureLifecycleV1.currentCallback(mediaProjection, projection)) return\n",
                "                    if (!FieldCaptureLifecycleV1.currentCallback(mediaProjection, projection)) {\n"+
                    "                        FieldCaptureRecoveryDiagnosticV1.record(\"stale_projection_callback_ignored\")\n"+
                    "                        return\n                    }\n",
            )
            inserts.forEach { assertTrue(it,s.contains(it)); s=s.replace(it,"") }
            s=s.replace("releaseProjection(\"virtual_display_start_failed\", endJourneyIfOwned = !forceFresh)","releaseProjection(\"virtual_display_start_failed\")")
            s=s.replace("""    private fun reconfigureCapturedContent(width: Int, height: Int) {
        val handler = worker ?: return
        val expectedProjection = projection ?: return
        handler.post {
            if (!FieldCaptureLifecycleV1.currentCallback(expectedProjection, projection)) return@post""","""    private fun reconfigureCapturedContent(width: Int, height: Int) {
        val handler = worker ?: return
        handler.post {
            if (projection == null) return@post""")
            val hash=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
            assertEquals("Service Reader semantics vc92, lifecycle exception only", "fbc6915e3c2a66e1d0c62832129b0b48fa92dfe11cc96f53643bdbfdb1f5ba5d",hash)
        }
    }
}
