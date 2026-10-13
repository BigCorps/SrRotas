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
        assertTrue(panel.contains("Tab.EVENTS to \"Eventos\""))
        assertTrue(panel.contains("if (eventContexts(value).isNotEmpty())"))
        assertTrue(panel.contains("RadarContextualEventEligibilityV1.eligible("))
        assertTrue(panel.contains("val mapView = ensureMap()"))
        assertTrue(panel.contains("if (tab == Tab.MAP)"))
        assertTrue(panel.contains("UiKit.dp(context, 490)"))
        assertTrue(panel.contains("RadarCompactOverlayV1.probability"))
        assertTrue(panel.contains("body.addView(UiKit.margin(cards, top = 8))"))
        assertTrue(panel.contains("renderDetail()"))
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
