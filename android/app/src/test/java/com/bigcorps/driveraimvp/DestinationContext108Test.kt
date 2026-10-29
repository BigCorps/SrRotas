package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * vc108 — destino nos cards Uber `min • km`.
 *
 * Causa demonstrada: o cabeçalho de total (`20 min • 3.5 km • Est. de R$x/km`) também casa
 * com a geometria e virava a âncora 0. Busca ficava nula, o "destino" passava a ser o
 * endereço de busca e a igualdade busca==destino apagava o destino real (após a viagem).
 * Fixtures anonimizadas com a geometria vertical dos cards reais (tablet 1188×1599).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DestinationContext108Test {
    private val observedAt = "2026-10-29T12:00:00Z"

    private fun card(vararg rows: String, left: Int = 60, step: Int = 46): List<SpatialOcrLine> =
        rows.mapIndexed { i, text -> SpatialOcrLine(text, Rect(left, 400 + i * step, left + 40 + text.length * 17, 400 + i * step + 34)) }

    private fun modern(pickup: List<String>, destination: List<String>, header: Boolean = true) = card(*(
        listOf("Black", "Exclusivo", "R$ 17,99") +
            (if (header) listOf("20 min • 3.5 km • Est. de R$5,14/km") else emptyList()) +
            listOf("2 min • 0.2 km") + pickup + listOf("★ 4,81", "18 minutos • 3.3 km") + destination + listOf("Aceitar")
        ).toTypedArray())

    private fun extract(lines: List<SpatialOcrLine>) = OfferContextExtractor0221.extract(lines, observedAt, 20)

    @Test fun headerTotalNoLongerShiftsPickupAndDestination() {
        val ctx = extract(modern(listOf("Rua Alfa, 120 - Bairro Um"), listOf("Avenida Beta, 900 - Bairro Dois")))!!
        assertEquals("Rua Alfa, 120 - Bairro Um", ctx.pickupLabel)
        assertEquals("Avenida Beta, 900 - Bairro Dois", ctx.destinationLabel)
        assertTrue(ctx.contextConfidence >= 0.95)
        assertEquals("pending", ctx.geocodeStatus)
    }

    @Test fun sameResultWithoutHeaderAndWithTotalIdentifiedOnlyByArithmetic() {
        val noHeader = extract(modern(listOf("Rua Alfa, 120"), listOf("Avenida Beta, 900"), header = false))!!
        assertEquals("Rua Alfa, 120", noHeader.pickupLabel); assertEquals("Avenida Beta, 900", noHeader.destinationLabel)
        // OCR lost the "Est. de" marker: total is the exact sum of the two legs.
        val bare = extract(card("R$ 17,99", "20 min • 3.5 km", "2 min • 0.2 km", "Rua Alfa, 120", "★ 4,81",
            "18 minutos • 3.3 km", "Avenida Beta, 900", "Aceitar"))!!
        assertEquals("Rua Alfa, 120", bare.pickupLabel); assertEquals("Avenida Beta, 900", bare.destinationLabel)
    }

    @Test fun wrappedLongAndNumberlessDestinations() {
        val wrapped = extract(modern(listOf("Rua Alfa, 120 -"), listOf("Avenida Brigadeiro Exemplo Comprido, 3.477 -", "Jardim Exemplo, São Paulo - SP")))!!
        assertEquals("Avenida Brigadeiro Exemplo Comprido, 3.477 - Jardim Exemplo, São Paulo - SP", wrapped.destinationLabel)
        val noNumber = extract(modern(listOf("Rua Alfa, 120"), listOf("Shopping Exemplo Norte")))!!
        assertEquals("Shopping Exemplo Norte", noNumber.destinationLabel)
        assertNotEquals(noNumber.pickupLabel, noNumber.destinationLabel)
    }

    @Test fun missingDestinationStaysMissingAndIsNeverThePickup() {
        val ctx = extract(modern(listOf("Rua Alfa, 120"), emptyList()))!!
        assertEquals("Rua Alfa, 120", ctx.pickupLabel)
        assertNull(ctx.destinationLabel)
        // No combined route without a destination; the pickup search still works.
        assertNull(CombinedRoute0212.intent(ctx))
        assertNotNull(OfferMaps.searchIntent(ctx.pickupLabel, null, null))
    }

    @Test fun legacyParenthesisLayoutDoesNotTakeConnectorWordsAsPickup() {
        val ctx = extract(card("UberX", "R$ 15,40", "5 min (1,8 km) de distância", "Rua Gama, 45", "Viagem de 14 min (6,2 km)",
            "Rua Delta, 300", "Aceitar"))!!
        assertEquals("Rua Gama, 45", ctx.pickupLabel)
        assertEquals("Rua Delta, 300", ctx.destinationLabel)
    }

    @Test fun neighbourhoodNamesStartingLikeLabelsAreNotTreatedAsLabels() {
        val ctx = OfferContextEngine.extract(listOf(
            ContextOcrLine("R$ 22,00", 100), ContextOcrLine("4 min (1,0 km)", 150), ContextOcrLine("Rua Alfa, 10", 180),
            ContextOcrLine("16 min (5,0 km)", 230), ContextOcrLine("Paraíso, São Paulo", 260)), observedAt, 20)!!
        assertEquals("Rua Alfa, 10", ctx.pickupLabel)
        assertEquals("Paraíso, São Paulo", ctx.destinationLabel)
    }

    @Test fun combinedAndDirectRoutesUseTheRightLegs() {
        val ctx = extract(modern(listOf("Rua Alfa, 120"), listOf("Avenida Beta, 900")))!!
        val combined = CombinedRoute0212.intent(ctx)!!.dataString!!
        assertTrue(combined, combined.contains("destination=Avenida%20Beta") || combined.contains("destination=Avenida+Beta"))
        assertTrue(combined, combined.contains("waypoints=Rua%20Alfa") || combined.contains("waypoints=Rua+Alfa"))
        val direct = OfferMaps.searchIntent(ctx.destinationLabel, null, null)!!.dataString!!
        assertTrue(direct, direct.contains("Avenida"))
        // Verified coordinates win over text in both commands.
        val geo = ctx.copy(pickupLat = -23.5, pickupLng = -46.6, destinationLat = -23.6, destinationLng = -46.7, geocodeStatus = "resolved")
        val combinedGeo = CombinedRoute0212.intent(geo)!!.dataString!!
        assertTrue(combinedGeo, combinedGeo.contains("-23.6") && combinedGeo.contains("-23.5"))
    }

    @Test fun lowConfidenceFallbackIsNotPromotedToDestination() {
        // Only one geometry line: the legacy engine may guess but a weak destination is not published.
        val ctx = extract(card("R$ 9,90", "7 min • 2.0 km", "Texto qualquer da tela", "Outro texto genérico"))
        assertNull(ctx?.destinationLabel?.takeIf { (ctx.contextConfidence) >= 0.80 })
    }

    @Test fun legacyEngineUsedByGalleryRescanAlsoIgnoresTheHeaderTotal() {
        // SpatialOfferParser (histórico / releitura da galeria) chama OfferContextEngine diretamente.
        val lines = modern(listOf("Rua Alfa, 120"), listOf("Avenida Beta, 900"))
            .map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) }
        val ctx = OfferContextEngine.extract(lines, observedAt, 20)!!
        assertEquals("Rua Alfa, 120", ctx.pickupLabel)
        assertEquals("Avenida Beta, 900", ctx.destinationLabel)
        assertTrue(ctx.contextConfidence >= 0.86)
    }

    @Test fun officialSpatialRouteKeepsTimesAndNowCarriesTheDestination() {
        val lines = modern(listOf("Rua Alfa, 120 - Bairro Um"), listOf("Avenida Beta, 900 - Bairro Dois"))
        val offers = UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, DriverSettings(costPerKm = 0.82), 1188, 1599)
        val offer = offers.single()
        // Reader vc106 unchanged: total × busca × viagem.
        assertEquals(17.99, offer.fare, 0.001)
        assertEquals(2, offer.pickupMinutes); assertEquals(18, offer.tripMinutes); assertEquals(20, offer.totalMinutes)
        assertEquals("Rua Alfa, 120 - Bairro Um", offer.context?.pickupLabel)
        assertEquals("Avenida Beta, 900 - Bairro Dois", offer.context?.destinationLabel)
        assertNotNull(CombinedRoute0212.intent(offer.context))
    }

    @Test fun geocodeFailureKeepsTextWithoutInventingCoordinates() {
        // Geocoder sem resultado: status "unresolved" mantém os textos e nenhuma coordenada.
        val r = extract(modern(listOf("Rua Alfa, 120"), listOf("Avenida Beta, 900")))!!.copy(geocodeStatus = "unresolved")
        assertNull(r.destinationLat); assertNull(r.pickupLat)
        assertNull(OfferContextQuality0242.confirmedDisplayLabel(r, pickup = false))
        assertTrue(OfferContextQuality0242.canGeocode(r.destinationLabel))
        // Maps continua possível por texto (rota direta e combinada), nunca por coordenadas inventadas.
        val combined = CombinedRoute0212.intent(r)!!.dataString!!
        assertFalse(combined, Regex("-?\\d+\\.\\d{3,},-?\\d+\\.\\d{3,}").containsMatchIn(combined))
        // Partial geocode: only the verified side carries coordinates.
        val partial = r.copy(destinationLat = -23.6, destinationLng = -46.7, geocodeStatus = "partial")
        assertEquals("Avenida Beta, 900", OfferContextQuality0242.confirmedDisplayLabel(partial, pickup = false))
        assertNull(OfferContextQuality0242.confirmedDisplayLabel(partial, pickup = true))
    }

    @Test fun twoGeometryCardsNeverLoseAnAnchor() {
        // 99/legacy style: a geometry line that also carries R$/km is not a removable total.
        assertTrue(OfferContextEngine.totalGeometry(listOf("8 min (2,1 km) R$ 1,90/km", "20 min (7,0 km)")).isEmpty())
        val ctx = extract(card("R$ 14,00", "8 min • 2.1 km • R$1,90/km", "Rua Alfa, 120", "20 min • 7.0 km", "Avenida Beta, 900"))!!
        assertEquals("Rua Alfa, 120", ctx.pickupLabel); assertEquals("Avenida Beta, 900", ctx.destinationLabel)
    }
}
