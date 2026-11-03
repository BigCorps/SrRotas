package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * vc109 P1 — ofertas não identificadas: etapa de descarte por código, sem texto do OCR, e a
 * correção específica do botão de ação abaixo do raio vertical em cards altos (tablet).
 * Geometria do OCR real do SM-X626B (1188×1599), endereços anonimizados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfferDrop109Test {
    private val settings = DriverSettings(costPerKm = 0.82)
    private fun line(text: String, top: Int, left: Int = 60) = SpatialOcrLine(text, Rect(left, top, left + 18 * text.length, top + 44))

    // Comfort card as read on the tablet: fare at y≈698, button at y≈1457 (> 0.41×1599 below).
    private fun tallComfort(button: String = "Tenho interesse", pickupLeg: String = "& 8 min • 2.4 km") = listOf(
        line("Veterinario CENTRO", 308), line("Pacaembu", 346), line("R$ 37,27", 698),
        line("47 min • 12.9 km • Est. de R$2,89/km", 803), line("+R$3,50 incluído", 879), line(pickupLeg, 1002),
        line("Av. Exemplo, Metrô Fictício, São Paulo", 1079), line("x 5,00 (4)", 1152), line("O 39 minutos • 10.5 km", 1257),
        line("Rua Exemplo, 76, Bairro Seis, São Paulo", 1336), line(button, 1457),
    )

    @Test fun actionButtonBelowTheRadiusNowAnchorsTheTallCard() {
        val lines = tallComfort()
        val fare = MoneyRoleResolver030.primarySpatialFareLines(lines).single()
        val cluster = OfferSpatialIsolation0221.clusterAroundFare(lines, fare, 1188, 1599)
        assertTrue(cluster.any { it.text == "Tenho interesse" })
        val offer = UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, settings, 1188, 1599).single()
        // vc106 times/fare preserved.
        assertEquals(37.27, offer.fare, 0.001)
        assertEquals(8, offer.pickupMinutes); assertEquals(39, offer.tripMinutes); assertEquals(47, offer.totalMinutes)
        assertTrue(offer.context!!.pickupLabel!!.startsWith("Av. Exemplo"))
        assertTrue(offer.context!!.destinationLabel!!.startsWith("Rua Exemplo, 76"))
    }

    @Test fun onlyTheButtonIsAddedNeverOtherTextOrAnotherCard() {
        val extra = tallComfort() + line("Texto qualquer depois do card", 1500)
        val fare = MoneyRoleResolver030.primarySpatialFareLines(extra).single()
        val cluster = OfferSpatialIsolation0221.clusterAroundFare(extra, fare, 1188, 1599)
        assertFalse(cluster.any { it.text.startsWith("Texto qualquer") })
        // A second fare below: the band is split and no tail is borrowed from the other card.
        val two = tallComfort() + line("R$ 12,00", 1520) + line("Aceitar", 1580)
        val first = MoneyRoleResolver030.primarySpatialFareLines(two).minBy { it.box.top }
        assertFalse(OfferSpatialIsolation0221.clusterAroundFare(two, first, 1188, 1599).any { it.text == "Aceitar" })
    }

    @Test fun lostOffersNameTheStageThatDroppedThem() {
        val noFare = listOf(line("Exclusivo", 640), line("R$ 1799", 718), line("20 min • 3.5 km • Est. de R$5,14/km", 831), line("Aceitar", 1456))
        assertEquals(OfferDropDiagnosis109.Stage.FARE, OfferDropDiagnosis109.diagnose(noFare, settings).stage)
        val decimalLost = tallComfort(pickupLeg = "*& 8min:24km")
        val legs = OfferDropDiagnosis109.diagnose(decimalLost, settings)
        assertEquals(OfferDropDiagnosis109.Stage.LEGS, legs.stage); assertEquals("legs_legs_disagree_with_total", legs.reason)
        val noAnchor = tallComfort(button = "Texto sem ação")
        assertEquals(OfferDropDiagnosis109.Stage.ANCHOR, OfferDropDiagnosis109.diagnose(noAnchor, settings).stage)
        assertEquals(OfferDropDiagnosis109.Stage.NONE, OfferDropDiagnosis109.diagnose(tallComfort(), settings).stage)
        // Only codes and counts are exported.
        val fields = legs.fields()
        assertTrue(fields.values.none { it is String && (it.contains("Exemplo") || it.contains("R$")) })
    }

    @Test fun diagnosisRunsOnlyInsideTheOfficialFrameScope() {
        // Outside recordOcr → recordRoute nothing is retained (history import, rescan, accessibility).
        OfferDropDiagnosis109.observeLines(tallComfort())
        assertNull(OfferDropDiagnosis109.closeFrame("uber", true, 0) { settings })
        OfferDropDiagnosis109.openFrame()
        OfferDropDiagnosis109.observeLines(tallComfort(pickupLeg = "*& 8min:24km"))
        assertEquals(OfferDropDiagnosis109.Stage.LEGS, OfferDropDiagnosis109.closeFrame("uber", true, 0) { settings }!!.stage)
        // Offers found or non-candidate frames are not diagnosed.
        OfferDropDiagnosis109.openFrame(); OfferDropDiagnosis109.observeLines(tallComfort())
        assertNull(OfferDropDiagnosis109.closeFrame("uber", true, 1) { settings })
    }

    @Test fun frozenReaderFilesStayUntouched() {
        val root = generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { java.io.File(it, "app/build.gradle.kts").exists() }
        val service = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/MediaProjectionOcrService.kt").readText()
        assertFalse(service.contains("OfferDropDiagnosis109"))
        val trace = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/RadarHudTrace024.kt").readText()
        assertTrue(trace.contains("OfferDropDiagnosis109.openFrame()")); assertTrue(trace.contains("offer_drop_totals_109"))
    }
}
