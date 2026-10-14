package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class Field98RadarPresentationTest {
    private val androidRoot by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }
    private fun source(file: String) =
        File(androidRoot, "app/src/main/java/com/bigcorps/driveraimvp/$file.kt").readText()

    @Test fun mapOverlayUsesRealEvidenceInsteadOfInventingProbability() {
        assertNull(RadarCompactOverlayV1.probability(null, 50))
        assertNull(RadarCompactOverlayV1.probability(90.0, 0))
        assertNull(RadarCompactOverlayV1.probability(Double.NaN, 30))
        assertNull(RadarCompactOverlayV1.probability(110.0, 20))
        assertEquals("Prob. 96% · até 10 min", RadarCompactOverlayV1.probability(96.0, 23))
    }

    @Test fun addressFitsCompactLineAndArrivalUsesLocalZone() {
        assertEquals("Destino da corrida", RadarCompactOverlayV1.address(null))
        assertTrue(RadarCompactOverlayV1.address("A".repeat(100)).length <= 55)
        val eta = "2026-10-13T14:36:04Z"
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = Instant.parse("2026-10-13T14:31:04Z").toEpochMilli()
        assertEquals("ETA 5 min · Chegada 11:36", RadarCompactOverlayV1.arrival(eta, now, zone))
        assertEquals("ETA vencida · Chegada 11:36", RadarCompactOverlayV1.arrival(eta, now + 350_000, zone))
        assertEquals("Chegada indisponível", RadarCompactOverlayV1.arrival("invalid", now, zone))
    }

    @Test fun tabsReuseMapAndKeepExistingPoiDetails() {
        val panel = source("RadarContextualPanelV1")
        assertTrue(panel.contains("Tab.MAP to \"Mapa\""))
        assertTrue(panel.contains("Tab.OPPORTUNITIES to \"Oportunidades\""))
        assertTrue(panel.contains("Tab.EVENTS to \"Áreas de interesse\""))
        assertTrue(panel.contains("private fun tabRow(): View"))
        assertFalse(panel.contains("if (eventContexts(value).isNotEmpty()) availableTabs"))
        assertTrue(panel.contains("body.addView(tabRow())"))
        assertTrue(panel.contains("renderLoading(value)"))
        assertTrue(panel.contains("RadarContextualEventEligibilityV1.eligible("))
        assertTrue(panel.contains("val mapView = ensureMap()"))
        assertTrue(panel.contains("if (tab == Tab.MAP)"))
        assertTrue(panel.contains("UiKit.dp(context, 490)"))
        assertTrue(panel.contains("RadarCompactOverlayV1.probability"))
        assertTrue(panel.contains("body.addView(UiKit.margin(cards, top = 8))"))
        assertTrue(panel.contains("renderDetail()"))
    }

    @Test fun fieldRuntimeControlsAreHiddenBehindSmallDiagnosticToggle() {
        val homologation=source("RadarContextualHomologationV1")
        assertTrue(homologation.contains("private val technicalContent = LinearLayout(context)"))
        assertTrue(homologation.contains("visibility=View.GONE"))
        assertTrue(homologation.contains("button(\"⚙ Diagnóstico\")"))
        assertTrue(homologation.contains("technicalContent.addView(row1"))
        assertTrue(homologation.contains("technicalContent.addView(row2"))
        assertTrue(homologation.contains("RadarContextualFlagsV1.enableAssistant(context)"))
        assertTrue(homologation.contains("RadarContextualIntegrationV1.rollback(context)"))
    }

    @Test fun tabsStayAvailableInIdleLoadingAndRealResult() {
        val panel=source("RadarContextualPanelV1")
        val idle=panel.substringAfter("private fun renderIdle()").substringBefore("private fun renderLoading(")
        val loading=panel.substringAfter("private fun renderLoading(").substringBefore("private fun addHeader(")
        val real=panel.substringAfter("private fun renderResult(").substringBefore("private fun eventContexts(")
        assertTrue(idle.contains("body.addView(tabRow())"))
        assertTrue(loading.contains("body.addView(tabRow())"))
        assertTrue(real.contains("body.addView(tabRow())"))
        assertFalse(real.contains("if (tab == Tab.EVENTS && eventContexts(value).isEmpty())"))
        assertTrue(real.contains("eventContexts(value)"))
        assertTrue(idle.contains("Tab.EVENTS ->"))
    }

    @Test fun eventEvidenceMustBeTimeRelevant() {
        val now = Instant.parse("2026-10-13T14:00:00Z").toEpochMilli()
        val eta = "2026-10-13T14:10:00Z"
        val eligible = RadarContextualEventEligibilityV1::eligible
        assertTrue(eligible("event", "Estádio", "2026-10-13T14:05:00Z", "2026-10-13T14:17:00Z", .8, eta, now))
        assertFalse(eligible("event", "Antigo", "2026-10-13T12:00:00Z", "2026-10-13T12:30:00Z", .8, eta, now))
        assertFalse(eligible("event", "Inválido", "não é data", "2026-10-13T14:17:00Z", .8, eta, now))
        assertFalse(eligible("event", "Sem evidência", "2026-10-13T14:05:00Z", "2026-10-13T14:17:00Z", 0.0, eta, now))
    }

    @Test fun readerAndMapProviderHaveNotChangedInPresentationPass() {
        assertTrue(source("RadarMiniMapViewV1").contains("tiles.openfreemap.org/styles/liberty"))
        assertFalse(source("RadarMiniMapViewV1").contains("GoogleMap("))
        assertFalse(source("RadarContextualPanelV1").contains("startActivityForResult"))
    }
}
