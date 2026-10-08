package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** JVM: heurística/lista reais + contratos Android de snapshot, lifecycle, persistência e congelamento. */
class Field96RegressionContractTest {
    private val root by lazy { generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").exists() } }
    private fun source(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private val mini get() = source("RadarMiniMapViewV1.kt")
    private val layer get() = source("RadarMapBitmapSurfaceV1.kt")
    private val hud get() = source("JourneyBubbleController.kt")
    private fun healthy(pixel: (Int, Int) -> Int) = RadarMapBitmapHealthV1.nonblank(120,120,pixel)
    @Test fun invalidSizeNeverReadsPixels() {
        assertFalse(RadarMapBitmapHealthV1.nonblank(0,100) { _,_ -> error("must not read") })
        assertFalse(RadarMapBitmapHealthV1.nonblank(100,-1) { _,_ -> error("must not read") })
    }
    @Test fun transparentSnapshotIsBlank() { assertFalse(healthy { _,_ -> 0x00eeeeee }) }
    @Test fun whiteSnapshotIsBlank() { assertFalse(healthy { _,_ -> -1 }) }
    @Test fun uniformNonwhiteSnapshotIsAlsoBlank() { assertFalse(healthy { _,_ -> 0xff949494.toInt() }) }
    @Test fun almostUniformWithOnlyOneMarkerRemainsBlank() {
        assertFalse(healthy { x,y -> if(x < 10 && y < 10) 0xffe53935.toInt() else -1 })
    }
    @Test fun paleGrayscaleCartographyDoesNotNeedColor() {
        assertTrue(healthy { x,y -> val c = 190+(x/10+y/10)%6*10; 0xff000000.toInt() or (c shl 16) or (c shl 8) or c })
    }
    @Test fun samplesStaySmallEvenForLargeBitmap() {
        var reads=0
        assertTrue(RadarMapBitmapHealthV1.nonblank(4000,3000) { x,y -> reads++; val c=130+(x+y)%100; 0xff000000.toInt() or (c shl 16) or (c shl 8) or c })
        assertEquals(144,reads)
    }
    @Test fun mapProviderAndVersionAreUnchanged() {
        assertTrue(mini.contains("https://tiles.openfreemap.org/styles/liberty"))
        assertTrue(File(root,"app/build.gradle.kts").readText().contains("android-sdk-opengl:13.6.1"))
        assertFalse(layer.contains("WebView")); assertFalse(layer.contains("GoogleMap"))
    }
    @Test fun fullNativeCallbackRequestsSnapshotAndCannotHideFallback() {
        val fully=mini.substringAfter("mapView.addOnDidFinishRenderingMapListener").substringBefore("mapView.addOnRenderErrorListener")
        assertTrue(fully.contains("requestBitmap()")); assertFalse(fully.contains("View.GONE"))
        assertTrue(mini.contains("addOnCameraIdleListener")); assertTrue(layer.contains("map.snapshot { bitmap ->"))
    }
    @Test fun onlyHealthyLiveBitmapIsShown() {
        val callback=layer.substringAfter("map.snapshot { bitmap ->").substringBefore("private fun independent(")
        assertTrue(callback.contains("if (healthy(bitmap))"))
        assertTrue(callback.indexOf("healthy(bitmap)") < callback.indexOf("showBitmap(bitmap)"))
        assertTrue(callback.contains("live_snapshot_success"))
        assertTrue(layer.contains("setImageBitmap(display)"))
    }
    @Test fun blankLiveSnapshotFallsBackRatherThanSucceeding() {
        val blank=layer.substringAfter("event(\"live_snapshot_blank\")").substringBefore("}.onFailure")
        assertTrue(blank.contains("independent(map, state, token)")); assertFalse(blank.contains("showBitmap"))
    }
    @Test fun snapshotterUsesSameStyleCameraAndProjectsPois() {
        assertTrue(mini.contains("RadarMapBitmapSurfaceV1(context, STYLE_URI"))
        for(t in listOf("MapSnapshotter.Options(w, h)","withStyle(styleUri)","withCameraPosition(camera)","pixelForLatLng", "dot(state.centerLat", "state.markers.forEach")) assertTrue(t,layer.contains(t))
        assertTrue(layer.indexOf("healthy(snapshot.bitmap)") < layer.indexOf("decorate(snapshot, state)"))
    }
    @Test fun newGenerationCancelsBeforeStartingSingleSnapshotter() {
        val request=layer.substringAfter("fun request(").substringBefore("private fun valid(")
        assertTrue(request.contains("cancelWork()"))
        val independent=layer.substringAfter("private fun independent(").substringBefore("private fun decorate(")
        assertTrue(independent.indexOf("snapshotter?.cancel()") < independent.indexOf("val task = MapSnapshotter"))
        assertEquals(1,Regex("val task = MapSnapshotter").findAll(layer).count())
    }
    @Test fun lateCallbacksCheckGenerationAndTaskIdentity() {
        assertTrue(layer.contains("token == generation"))
        assertTrue(layer.contains("if (!valid(token) || !livePending) return@post"))
        assertTrue(layer.contains("if (!valid(token) || snapshotter !== task) return@post"))
    }
    @Test fun releaseCancelsAndClearsBitmap() {
        val release=layer.substringAfter("fun release()").substringBefore("override fun onDetachedFromWindow")
        assertTrue(release.contains("released = true; cancelWork()"))
        assertTrue(release.contains("setImageDrawable(null)"))
        assertTrue(layer.substringAfter("fun cancelWork()").contains("snapshotter?.cancel()"))
        assertTrue(mini.substringAfter("fun release(").substringBefore("fun render(").contains("mapBitmapSurface.release()"))
    }
    @Test fun detachCancelsDebounceTimeoutAndSnapshotter() {
        assertTrue(layer.contains("override fun onDetachedFromWindow() { cancelWork()"))
        val cancel=layer.substringAfter("fun cancelWork()").substringBefore("fun release()")
        for(t in listOf("generation++","removeCallbacks","cancelTimeout()","snapshotter?.cancel()")) assertTrue(cancel.contains(t))
    }
    @Test fun snapshotTimeoutsAreBoundedAndDoNotRetryInLoop() {
        assertTrue(layer.contains("3_000L")); assertTrue(layer.contains("12_000L"))
        assertFalse(layer.contains("while (")); assertFalse(layer.contains("postOnAnimation"))
        val failure=layer.substringAfter("private fun failure(").substringBefore("private fun armTimeout(")
        assertFalse(failure.contains("request(")); assertFalse(failure.contains("independent("))
    }
    @Test fun emptyBitmapLayerRemainsMeasuredForSnapshotter() {
        val init=layer.substringAfter("    init {").substringBefore("    fun request(")
        assertTrue(init.contains("visibility = INVISIBLE")); assertFalse(init.contains("visibility = GONE"))
        assertTrue(layer.contains("isAttachedToWindow && width > 0 && height > 0"))
    }
    @Test fun fullCallbackRequestsOncePerStateRevisionAndIgnoresOldState() {
        val full=mini.substringAfter("mapView.addOnDidFinishRenderingMapListener").substringBefore("mapView.addOnRenderErrorListener")
        assertTrue(full.contains("if (eligibleRevision != stateRevision) return@post"))
        assertTrue(full.contains("fullSnapshotRevision != stateRevision"))
        assertTrue(full.contains("fullSnapshotRevision = stateRevision"))
    }
    @Test fun bitmapMemoryIsBoundedAndNeverPersisted() {
        assertTrue(layer.contains("1024.0")); assertTrue(layer.contains("Bitmap.createScaledBitmap"))
        for(t in listOf("compress(","FileOutputStream","getSharedPreferences","TextRecognition","track(")) assertFalse(t,layer.contains(t))
    }
    @Test fun stateUpdateAndCameraIdleDebounceSnapshot() {
        assertTrue(mini.substringAfter("fun render(").substringBefore("private fun renderOnMap").contains("mapBitmapSurface.cancelWork()"))
        assertTrue(mini.contains("postDelayed(stateSnapshot, 8_000L)"))
        assertTrue(layer.contains("handler.postDelayed(it, 450L)"))
    }
    @Test fun doubleFailureAlwaysShowsTextEvenWithOldImage() {
        val failure=layer.substringAfter("private fun failure(").substringBefore("private fun armTimeout(")
        assertTrue(failure.contains("setImageDrawable(null)")); assertTrue(failure.contains("fallback_text_shown"))
        assertTrue(failure.contains("unavailable()"))
        assertTrue(mini.contains("Mapa indisponível neste aparelho.")); assertTrue(mini.contains("Maps/Waze continuam funcionando."))
    }
    @Test fun hiddenSurfaceCannotRequestAndPassiveImageKeepsGestures() {
        assertTrue(mini.substringAfter("private fun requestBitmap(").substringBefore("private fun renderSurfaceReady").contains("!renderSurfaceReady()"))
        assertTrue(layer.contains("ready()")); assertTrue(layer.contains("isClickable = false")); assertTrue(layer.contains("isFocusable = false"))
        assertTrue(mini.indexOf("addView(mapBitmapSurface") < mini.indexOf("            fallback,"))
    }
    @Test fun lastEpisodeSurvivesProcessThroughPreferencesAndJsonReadsIt() {
        val episode=source("RadarMapLastEpisodeV1.kt")
        for(t in listOf("getSharedPreferences","putString(KEY", "app_version", "episode_started_at","source", "released_reason")) assertTrue(episode.contains(t))
        assertTrue(source("RadarContextualDiagnosticV1.kt").contains("put(\"last_map_episode\", RadarMapLastEpisodeV1.read(context))"))
        assertTrue(source("RadarContextualPanelV1.kt").contains("demo = demoMode"))
    }
    @Test fun episodeOnlyAcceptsSanitizedWhitelistedDataAndIgnoresOldEpisodes() {
        val episode=source("RadarMapLastEpisodeV1.kt")
        assertTrue(episode.contains("if (data.optLong(\"episode_started_at\") != episode) return"))
        assertTrue(episode.contains("else -> return"))
        for(t in listOf("centerLat","centerLng","destinationLabel","markers","Bitmap","http://","https://","raw")) assertFalse(t,episode.contains(t))
    }
    @Test fun collapsedCardHasOnlyHeaderAndConditionalExpansion() {
        val card=hud.substringAfter("private fun offerRow(").substringBefore("private fun operationalRideControls(")
        assertFalse(card.contains("operationalRideControls("))
        assertTrue(card.contains("card.addView(top)")); assertTrue(card.contains("if (expandedOfferId == offer.localId) card.addView(expandedOffer"))
    }
    @Test fun allOperationalActionsBelongToExpandedContent() {
        val expanded=hud.substringAfter("private fun expandedOffer(").substringBefore("private fun routeActions(")
        assertTrue(expanded.contains("box.addView(operationalRideControls(context, offer, outcome))"))
        val controls=hud.substringAfter("private fun operationalRideControls(").substringBefore("private fun expandedOffer(")
        for(t in listOf("ESTOU NESSA CORRIDA","TROCAR PARA ESTA CORRIDA","CONFIRMAR TROCA","CORRIDA ATIVA","REALIZADA","NÃO REALIZADA","VER REGIÃO DO DESTINO")) assertTrue(t,controls.contains(t))
    }
    @Test fun currentRideOccupiesOneOfLimitedNormalCards() {
        assertEquals(listOf("A","B"),HudDisplayOffersV1.select(listOf("B","C"),"A",2) { it })
        assertTrue(hud.contains("store.offerByLocalId(it.localOfferId)")); assertTrue(hud.contains("prefs.offerCount()) { it.localId }"))
    }
    @Test fun currentRideAndRecentNeverDuplicate() {
        assertEquals(listOf("A","B","C"),HudDisplayOffersV1.select(listOf("B","A","C","B"),"A",3) { it })
    }
    @Test fun missingSourceKeepsRecentLimitAndOnlyCompactPill() {
        assertEquals(listOf("B"),HudDisplayOffersV1.select(listOf("B","C"),null,1) { it })
        assertEquals(emptyList<String>(),HudDisplayOffersV1.select(listOf("B"),"A",0) { it })
        val absent=hud.substringAfter("if (active != null && sourceOffer == null)").substringBefore("if (offers.isEmpty())")
        assertTrue(absent.contains("pill(context, \"CORRIDA ATIVA\"")); assertFalse(absent.contains("currentRideActions("))
        assertFalse(absent.contains("compactButton("))
    }
    @Test fun reportSelectionNeverMarksCurrentRide() {
        val card=hud.substringAfter("private fun offerRow(").substringBefore("private fun operationalRideControls(")
        assertTrue(card.contains("ReportSelection0211.toggle")); assertFalse(card.contains("markDoingRide("))
    }
    @Test fun replacementConfirmationStillNeedsExplicitSecondTap() {
        val c=HudRideReplacementConfirmationV1()
        assertEquals(HudRideReplacementConfirmationV1.Click.ARMED,c.click("J","A","B",0))
        assertEquals(HudRideReplacementConfirmationV1.Click.CONFIRMED,c.click("J","A","B",1))
        assertTrue(hud.contains("allowReplace = confirmation == HudRideReplacementConfirmationV1.Click.CONFIRMED"))
    }
    @Test fun footerHasNoPermanentCaptureRestart() {
        val footer=hud.substringAfter("private fun footerControls(").substringBefore("private fun rebuildMessageRail(")
        assertFalse(footer.contains("Reiniciar captura")); assertFalse(footer.contains("CaptureRecoveryActivity")); assertTrue(footer.contains("return bar"))
    }
    @Test fun bugMenuUsesExistingFreshCaptureActionWithM2Guard() {
        val chrome=source("FloatingWindowChrome023.kt")
        assertTrue(chrome.contains("menuButton(context, \"Reiniciar captura\""))
        assertTrue(chrome.contains("DiagnosticQuickActions0270.restartReading(context)"))
        val quick=source("DiagnosticControls0270.kt").substringAfter("fun restartReading(").substringBefore("fun exportDiagnostic(")
        assertTrue(quick.contains("MODE_M2")); assertTrue(quick.contains("return")); assertTrue(quick.contains("CaptureRecoveryActivity0270.open(context)"))
    }
    @Test fun captureAndReaderSemanticsRemainFrozen() {
        Field95RegressionContractTest.assertServiceReaderSemantics(source("MediaProjectionOcrService.kt"))
        assertTrue(source("Reader2Accumulator032.kt").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321.kt").contains("put(\"controlled_hybrid_effect\", false)"))
        assertEquals(1,Regex("TextRecognition\\.getClient\\(").findAll(source("MediaProjectionOcrService.kt")).count())
    }
    @Test fun screenshotReplayIsPlannedOnly() {
        for(n in listOf("RadarMapBitmapSurfaceV1.kt","JourneyBubbleController.kt")) {
            assertFalse(source(n).contains("REVISAR LEITURA")); assertFalse(source(n).contains("VER CAPTURA"))
        }
        assertTrue(File(root.parentFile,"README-CONTINUIDADE.md").readText().contains("Próximo bloco isolado: Offer Screenshot Review V1"))
    }
}
