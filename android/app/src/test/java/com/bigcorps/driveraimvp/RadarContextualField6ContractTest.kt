package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.security.MessageDigest

class RadarContextualField6ContractTest {
    private val now=Instant.parse("2026-10-07T18:00:00Z").toEpochMilli()
    private fun result(strong:Boolean=false,count:Int=0,minutes:Long=10):RadarContextualResultV1 {
        val demo=RadarContextualDemoV1.result()
        return demo.copy(destinationEta=Instant.ofEpochMilli(now+minutes*60_000).toString(),
            opportunities=demo.opportunities.take(count),assistant=demo.assistant.copy(eligible=strong))
    }
    private fun decide(r:RadarContextualResultV1=result(),id:String?="ride-A",valid:Boolean=true,
        last:String?=null,at:Long=0)=DestinationRadarAssistantBridgeV1.decide(r,now,last,at,valid,r.destinationEta,id)

    @Test fun strongPayloadAndOpportunityCooldownPreserved() {
        val r=result(strong=true,count=1)
        val s=decide(r).signal!!
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.STRONG,s.kind)
        assertEquals(r.assistant.headline,s.headline)
        assertEquals(r.assistant.actionLabel,s.action)
        assertEquals(r.assistant.opportunityId,s.opportunityId)
        assertNull(decide(r,last=s.cooldownKey,at=now-1000).signal)
    }
    @Test fun discoveryPayloadAndWindowPreserved() {
        val s=decide(result(count=1)).signal!!
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.DISCOVERY,s.kind)
        assertEquals("Há oportunidades próximas ao seu destino. Quer ver?",s.headline)
        assertEquals("Ver",s.action)
        assertNotNull(s.opportunityId)
        assertNull(decide(result(count=1,minutes=19)).signal)
    }
    @Test fun emptySuccessfulCurrentRideGeneratesRegionWithoutFakeOpportunity() {
        val s=decide().signal!!
        assertEquals(DestinationRadarAssistantBridgeV1.Kind.REGION,s.kind)
        assertEquals("Radar analisou sua região de chegada. Quer ver?",s.headline)
        assertEquals("Ver região",s.action)
        assertNull(s.opportunityId)
        assertEquals("region:ride-A",s.cooldownKey)
    }
    @Test fun regionRequiresValidEtaIncludingOperationalBoundaries() {
        for(minute in listOf(-11L,19L)) assertNull(decide(result(minutes=minute)).signal)
        for(minute in listOf(-10L,0L,18L)) assertNotNull(decide(result(minutes=minute)).signal)
        assertNull(decide(result().copy(destinationEta="invalid")).signal)
    }
    @Test fun regionCooldownIsPerRideAndExpiresAtTwentyMinutes() {
        val key=decide().signal!!.cooldownKey
        assertNull(decide(last=key,at=now-1000).signal)
        assertEquals("cooldown_mesma_corrida",decide(last=key,at=now-1000).reason)
        assertNotNull(decide(id="ride-B",last=key,at=now-1000).signal)
        assertNotNull(decide(last=key,at=now-20*60_000).signal)
    }
    @Test fun changedRideAndMissingIdentityCannotGenerateRegion() {
        assertNull(decide(valid=false).signal)
        assertNull(decide(id=null).signal)
        assertNull(decide(id="").signal)
    }
    @Test fun regionClickLaunchesWithNullIdOnlyForSameRide() {
        val s=decide().signal!!
        var clicked=0; val ids=mutableListOf<String?>()
        assertTrue(DestinationRadarInteractionV1.viewIfCurrentRide("ride-A","ride-A",s,{clicked++},{ids.add(it)}))
        assertEquals(1,clicked); assertEquals(listOf<String?>(null),ids)
        assertFalse(DestinationRadarInteractionV1.viewIfCurrentRide("ride-A","ride-B",s,{clicked++},{ids.add(it)}))
        assertFalse(DestinationRadarInteractionV1.viewIfCurrentRide("ride-A",null,s,{clicked++},{ids.add(it)}))
        assertEquals(1,clicked); assertEquals(1,ids.size)
        val renderer=source("DestinationRadarAssistantRendererV1.kt")
        assertTrue(renderer.contains("viewIfCurrentRide("))
        assertTrue(renderer.contains("launch = { RadarDestinationLauncherV1.openRadar(app, it, source=\"assistant_real\") }"))
    }
    @Test fun zeroOpportunitiesPreservesDestinationMapAndExplicitEmptyState() {
        val r=result(); val screen=RadarContextualPresenterV1.map(r)
        assertEquals(r.destinationLat,screen.destinationLat,0.0)
        assertEquals(r.destinationLng,screen.destinationLng,0.0)
        assertTrue(screen.markers.isEmpty()); assertTrue(screen.cards.isEmpty())
        val panel=source("RadarContextualPanelV1.kt").substringAfter("private fun renderResult")
        assertTrue(panel.indexOf("mapView.render(")<panel.indexOf("value.opportunities.isEmpty()"))
        assertTrue(panel.contains("Nenhuma oportunidade mapeada nesta região no momento."))
        val map=source("RadarMiniMapViewV1.kt")
        assertTrue(map.contains("CameraUpdateFactory.newLatLngZoom(points.first(), 13.5)"))
    }
    @Test fun runtimeNeverAutoLaunchesAndOnlySuccessfulCurrentResultSignals() {
        val bridge=source("DestinationRadarRuntimeBridgeV1.kt")
        assertFalse(bridge.contains("openRadar(")); assertFalse(bridge.contains("startActivity("))
        val runtime=source("DestinationRadarRuntimeV1.kt")
        val success=runtime.substringAfter("r.onSuccess").substringBefore("}.onFailure")
        assertTrue(success.contains("live?.localOfferId!=spec.localOfferId"))
        assertTrue(success.contains("DestinationRadarAssistantBridgeV1.signal"))
        assertFalse(runtime.substringAfter("}.onFailure").contains("BridgeV1.signal"))
        assertTrue(source("DestinationRadarAssistantBridgeV1.kt").contains("latestFor(spec.localOfferId) === result"))
    }
    @Test fun fieldR3MigrationPromotesExactlyOnceAndRespectsLaterManualStage() {
        val before=RadarContextualFlagsV1.Field93State(false,true,true,false)
        val migrated=RadarContextualFlagsV1.migrateField93(before,true,93)
        assertTrue(migrated.done); assertTrue(migrated.assistant)
        assertEquals(migrated,RadarContextualFlagsV1.migrateField93(migrated,true,93))
        val manualR3=migrated.copy(assistant=false)
        assertEquals(manualR3,RadarContextualFlagsV1.migrateField93(manualR3,true,93))
    }
    @Test fun migrationDoesNotEnableUiRuntimeOrNormalBuildDefaults() {
        for(ui in listOf(false,true)) for(runtime in listOf(false,true)) {
            val state=RadarContextualFlagsV1.Field93State(false,ui,runtime,false)
            val after=RadarContextualFlagsV1.migrateField93(state,true,93)
            assertEquals(ui,after.ui); assertEquals(runtime,after.runtime)
            assertEquals(ui && runtime,after.assistant); assertTrue(after.done)
            assertEquals(state,RadarContextualFlagsV1.migrateField93(state,false,93))
            assertEquals(state,RadarContextualFlagsV1.migrateField93(state,true,92))
        }
    }
    @Test fun rollbackRemainsOffAfterMigrationAndConsumesPendingMigration() {
        val rollback=RadarContextualFlagsV1.Field93State(true,false,false,false)
        assertEquals(rollback,RadarContextualFlagsV1.migrateField93(rollback,true,93))
        val flags=source("RadarContextualFlagsV1.kt")
        val disable=flags.substringAfter("fun disableAll").substringBefore("fun stage")
        assertTrue(disable.contains("manualStageChosen(context)"))
        for(flag in listOf("UI","RUNTIME","ASSISTANT")) assertTrue(disable.contains("putBoolean($flag, false)"))
        assertTrue(source("RadarContextualIntegrationV1.kt").substringAfter("fun rollback").contains("DestinationRadarAssistantRendererV1.hide()"))
        assertTrue(source("DestinationRadarAssistantRendererV1.kt").contains("!RadarContextualIntegrationV1.assistantAllowed(app)"))
    }
    @Test fun hudAndDiagnosticExposeRegionWithoutSensitivePayload() {
        val hud=source("JourneyBubbleController.kt")
        assertTrue(hud.contains("Radar: região analisada · nenhuma oportunidade mapeada"))
        assertTrue(hud.contains("VER REGIÃO DO DESTINO"))
        val json=source("RadarContextualDiagnosticV1.kt").substringAfter("fun toJson")
        for(key in listOf("field_r4_migration_applied","assistant_kind","region_signal_generated","region_signal_rendered","region_view_clicked"))
            assertTrue(json.contains("\"$key\""))
        assertFalse(json.contains("put(\"destination_label\""))
        assertFalse(json.contains("put(\"destination_lat\""))
        assertFalse(json.contains("put(\"destination_lng\""))
    }
    @Test fun reader2ShadowHybridOffAndSingleHeavyOcrFrozen() {
        assertTrue(source("Reader2Accumulator032.kt").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321.kt").contains("put(\"controlled_hybrid_effect\", false)"))
        assertFalse(source("UberM1TemporalRecoveryV1.kt").contains("TextRecognition"))
        assertFalse(source("UberM1TemporalRecoveryV1.kt").contains("Reader2"))
    }
    @Test fun detachedHostOrCardNeverAcknowledgesOrConsumesCooldown() {
        val region=decide().signal!!
        assertNull(region.opportunityId)
        for(host in listOf(false,true)) for(card in listOf(false,true)) {
            var shown=0; var cooldownMarks=0
            val accepted=DestinationRadarInteractionV1.shownIfAttached(host,card) {
                shown++; cooldownMarks++
            }
            assertEquals(host && card,accepted)
            assertEquals(if(host && card) 1 else 0,shown)
            assertEquals(shown,cooldownMarks)
        }
        val interaction=source("DestinationRadarInteractionV1.kt")
        assertTrue(interaction.substringAfter("fun shown(context").substringBefore("fun ignore")
            .contains("markShown(context,signal)"))
    }
    @Test fun rendererChecksHostBeforeAddingAndCardBeforeShownAndTimeout() {
        val attach=source("DestinationRadarAssistantRendererV1.kt")
            .substringAfter("private fun attach(").substringBefore("private fun hideNow")
        val hostCheck=attach.indexOf("if (!column.isAttachedToWindow)")
        val add=attach.indexOf("column.addView(")
        val ack=attach.indexOf("DestinationRadarInteractionV1.shownIfAttached(")
        val timer=attach.indexOf("val task = Runnable")
        assertTrue(hostCheck>=0 && hostCheck<add && add<ack && ack<timer)
        assertTrue(attach.contains("column.isAttachedToWindow, card.isAttachedToWindow, onShown"))
        assertTrue(attach.substring(hostCheck,add).contains("hud_host_not_attached"))
        val failedAttach=attach.substring(ack,timer)
        assertTrue(failedAttach.contains("hideNow()"))
        assertTrue(failedAttach.contains("hud_card_not_attached"))
        assertTrue(failedAttach.contains("return"))
        assertFalse(attach.contains("onShown()")) // única chamada fica atrás do gate puro
        val cleanup=source("DestinationRadarAssistantRendererV1.kt").substringAfter("private fun hideNow")
        assertTrue(cleanup.contains("current = null")); assertTrue(cleanup.contains("removeView(view)"))
    }
    @Test fun windowManagerFailureClearsEveryHostReferenceAndVisualState() {
        val hud=source("JourneyBubbleController.kt")
        val failure=hud.substringAfter("runCatching { wm.addView(outer, lp) }")
            .substringAfter(".onFailure {").substringBefore("refreshNow(context)")
        for(ref in listOf("root","mainColumn","panel","bubble","railHost","params","windowManager","appContext",
            "expandedOfferId","deepExpandedOfferId","lastVisualSignature"))
            assertTrue("dangling $ref",failure.contains("$ref = null"))
        assertTrue(failure.contains("stopWatcher()"))
        assertTrue(failure.contains("expanded = false")); assertTrue(failure.contains("messagesOpen = false"))
        assertTrue(failure.contains("return"))
        assertTrue(hud.contains("if (root == null || watcherRunning) return"))
    }
    @Test fun regionVariantMetadataKeepsNullOpportunityAndAllThreeEvents() {
        val signal=decide().signal!!
        assertEquals("region",DestinationRadarInteractionV1.metadataVariant(signal))
        assertNull(signal.opportunityId)
        for(kind in listOf(DestinationRadarAssistantBridgeV1.Kind.STRONG,DestinationRadarAssistantBridgeV1.Kind.DISCOVERY))
            assertNull(DestinationRadarInteractionV1.metadataVariant(signal.copy(kind=kind,opportunityId="real-opportunity")))
        val interaction=source("DestinationRadarInteractionV1.kt")
        assertTrue(interaction.contains("JSONObject().apply { metadataVariant(signal)?.let { put(\"variant\",it) } }"))
        for(event in listOf("assistant_shown","assistant_ignored","assistant_viewed"))
            assertTrue(interaction.contains("track(context,\"$event\",spec,result,signal.opportunityId,metadata=metadata(signal))"))
    }
    @Test fun nullDecisionPreservesSelectionAndSeparatesCurrentFromLastRenderedKind() {
        fun field(name:String)=RadarContextualDiagnosticV1.javaClass.getDeclaredField(name)
            .apply { isAccessible=true }.get(RadarContextualDiagnosticV1)
        RadarContextualDiagnosticV1.rideChanged()
        try {
            val strong=result(strong=true,count=1)
            val signal=decide(strong).signal!!
            RadarContextualDiagnosticV1.assistantDecision(strong,"backend_strong",signal)
            RadarContextualDiagnosticV1.assistantRendered(signal.kind)
            RadarContextualDiagnosticV1.userSelectedOpportunity("real-selection")
            RadarContextualDiagnosticV1.acceptResult(result().copy(assistant=result().assistant.copy(opportunityId=null)),"runtime")
            RadarContextualDiagnosticV1.assistantDecision(result(),"cooldown_mesma_corrida",null)
            assertNull(field("assistantKind"))
            assertEquals("strong",field("lastRenderedAssistantKind"))
            assertEquals("real-selection",field("selectedOpportunityId"))
            RadarContextualDiagnosticV1.assistantDecision(result(),"region_signal_generated",decide().signal)
            assertEquals("region",field("assistantKind"))
            assertEquals("strong",field("lastRenderedAssistantKind")) // ainda não renderizado
        } finally { RadarContextualDiagnosticV1.rideChanged() }
    }

    private val androidRoot:File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(name:String)=File(androidRoot,"app/src/main/java/com/bigcorps/driveraimvp/$name").readText()

    // SHA-256 extraídos do GitHub vc92 funcional 1421f512d966101cc6bbd0dfda52cf0626a9c4dd.
    // Manifest estático permite executar também em checkout CI shallow, sem rede no teste.
    private val readerBaseline=mapOf(
        "DriverPlatformOfferRouter.kt" to "e0ebecac9e2142ee98c3ce13843ed2066e28745d7f47d36af3ad104726a2ff23",
        "MediaProjectionOcrService.kt" to "a73d51333ed32a23ce80b0e7ad3a03cb12834ab7dab84d979f5aaab6e70bc3ac",
        "MoneyRoleResolver030.kt" to "ce83510560d523985cd343c0ffce3b361a1ce793f3a30b5ea2777dd573053cf0",
        "OfferAdmissionGate029.kt" to "ec1f67ac26dec4fa2ea1625e74d09d0b6f61e606e6779d5a23e1db6a084fcebf",
        "OfferAdmissionGate030.kt" to "4fe3571af5aacf20a2d282e84bd1688918fb37ea2677a873ab4d44f2a8550a4f",
        "OfferDeduplicator.kt" to "71283cc15cf2dbe78e9b9696769e921e84731b30b3972dc4114ea9fe1357f8f7",
        "OfferIntegrityGate027033.kt" to "6e6764c4346b32bcfd995dff5192b3393fd54db827b8d130db041df937bc5f15",
        "OfferParser.kt" to "7694c08b13f99e7704c696f05ec5c3a5309987601663563220d99b7c49005979",
        "Reader2Accumulator032.kt" to "d49a3e3a38835fc04786f27b556e109c718a6c1da4ae099a5def92bfc15789dd",
        "Reader2Consensus0321.kt" to "871b6d37d793a9b9ab3e9723d8434fffd17198e20ef40fc5bb5c2564b39a2041",
        "Reader2MoneyShadow030.kt" to "a88197dc9e6b852ddd2ceeeb979deefb40ebbb7e56376d3d7389ee8e0bd0a5b5",
        "Reader2Parallel031.kt" to "9521a47c88e7fa08d3bb277e6899f4c5ae080e7d24120e8cd2ea681cbed26877",
        "Reader2Shadow030.kt" to "7391a785328f42301a48926ec636a0230b3c0286218eac23cc49d973a78ce961",
        "ReaderLab027036.kt" to "f81a64df31b9d1bf5ca889a894bc3584366fcdb5fa36361cdcc90651c5a90237",
        "ShadowOfferRecovery027033.kt" to "061a92b02b9d5298cfbdb2fbcd10db0587aac4fe4aac62df21cce1ec23896efb",
        "UberM1TemporalRecoveryV1.kt" to "a2be91326a9c100d67a862de39d3ce39eea9404e6c385f5a3e98c833453a68a6",
        "UberScreenGate.kt" to "92ce768e1cd918d1810c0880693ee7774f456ecbd22cc42cfd532b4a233720de",
        "UberSpatialParser0221.kt" to "55f80e36fb935f765ec183cff491156d96f8f88886310dea041c13f671ca303e",
    )
    @Test fun everyFrozenReaderFileIsByteIdenticalToFunctionalVc92() {
        val folder=File(androidRoot,"app/src/main/java/com/bigcorps/driveraimvp")
        val prefixes=listOf("OfferIntegrityGate","OfferAdmission","Reader2","ShadowOfferRecovery")
        val actual=folder.walkTopDown().filter { it.isFile &&
            (it.name in readerBaseline || prefixes.any { prefix->it.name.startsWith(prefix) }) }.toList()
        assertEquals(readerBaseline.keys,actual.map { it.name }.toSet())
        for(file in actual) {
            val hash=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
            assertEquals("READER VC92 CONGELADO: ${file.name}",readerBaseline[file.name],hash)
        }
        assertTrue(File(androidRoot,"scripts/check-radar-contextual-v5.sh").readText().contains("1421f512d966101cc6bbd0dfda52cf0626a9c4dd"))
    }
}
