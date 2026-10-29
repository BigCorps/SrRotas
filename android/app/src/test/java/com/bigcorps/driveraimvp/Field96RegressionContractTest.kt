package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** JVM: heurística/lista reais + contratos Android de snapshot, lifecycle, persistência e congelamento. */
class Field96RegressionContractTest {
    private val root by lazy { generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").exists() } }
    private fun source(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private val hud get() = source("JourneyBubbleController.kt")
    @Test fun lastEpisodeSurvivesProcessThroughPreferencesAndJsonReadsIt() {
        val episode=source("RadarMapLastEpisodeV1.kt")
        for(t in listOf("getSharedPreferences","putString(KEY", "app_version", "episode_started_at","source", "released_reason")) assertTrue(episode.contains(t))
        assertTrue(source("RadarContextualDiagnosticV1.kt").contains("put(\"last_map_episode\", RadarMapLastEpisodeV1.read(context))"))
        // vc107: no map episode is created any more; the diagnostic keeps reading the last stored one.
        assertFalse(source("RadarContextualPanelV1.kt").contains("RadarMiniMapViewV1"))
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
        for(t in listOf("ESTOU NESSA CORRIDA","TROCAR PARA ESTA CORRIDA","CONFIRMAR TROCA","Radar","REALIZADA","NÃO REALIZADA")) assertTrue(t,controls.contains(t))
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
        assertTrue(absent.contains("pill(context, \"Em curso\"")); assertTrue(absent.contains("contentDescription = \"Corrida em andamento")); assertFalse(absent.contains("currentRideActions("))
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
    @Test fun journeySubmenuUsesExistingFreshCaptureActionWithM2Guard() {
        val chrome=source("FloatingWindowChrome023.kt")
        assertTrue(chrome.contains("R.drawable.sr23_float_restart,\"Reiniciar captura\""))
        assertFalse(chrome.contains("android.R.drawable.ic_popup_sync,\"Reiniciar captura\""))
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
    @Test fun screenshotApplicationIsStillDeferredAndNotInReader() {
        for(n in listOf("RadarContextualPanelV1.kt","JourneyBubbleController.kt")) {
            assertFalse(source(n).contains("REVISAR LEITURA")); assertFalse(source(n).contains("VER CAPTURA"))
        }
        assertTrue(File(root.parentFile,"README-CONTINUIDADE.md").readText().contains("Plano histórico vc96: Offer Screenshot Review V1"))
    }
}
