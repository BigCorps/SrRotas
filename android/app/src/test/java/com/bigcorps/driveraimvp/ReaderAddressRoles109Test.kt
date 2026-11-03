package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * vc109 P0 — busca × destino por PAPEL de cada geometria, com padrões do OCR real dos quatro
 * cards de campo (separadores `*`, `:`, `é`, `&`, decimal perdido, avaliação como texto, rótulos
 * do mapa acima do card). Endereços anonimizados; geometria vertical do tablet 1188×1599.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderAddressRoles109Test {
    private val at = "2026-11-03T12:00:00Z"
    private val map = listOf("Veterinario CENTRO", "Pacaembu", "VILA EXEMPLO Bairro Mapa", "+R$ 25) GLICÉRIO")

    private fun lines(rows: List<String>) = rows.mapIndexed { i, t -> SpatialOcrLine(t, Rect(60, 200 + i * 60, 60 + 18 * t.length, 236 + i * 60)) }
    private fun ctx(rows: List<String>) = OfferContextExtractor0221.extract(lines(rows), at, 20)
    private fun legacy(rows: List<String>) = OfferContextEngine.extract(rows.mapIndexed { i, t -> ContextOcrLine(t, i * 10) }, at, 20)

    // Real OCR shapes (addresses replaced).
    private val black = map + listOf("Exclusivo x", "- R$17,99", "20 min * 3.5 km - Est. de R$5,14/km", "*& 2min-0.2km",
        "Shopping Exemplo Sul, Saída - Loja Fictícia,", "Bela Vista", "x 4,81", "O 18 minutos 3.3 km", "Al. Exemplo, 1.471, Bairro Dois, São Paulo")
    private val comfort = map + listOf("R$ 11,99", "15 min é 3.5 km - Est. de R$3,43/km", "& 2min- 0.3km", "Rua Doutor Exemplo, Vila Um,",
        "São Paulo", "x 4,97 (3445) O Verificado", "O 13 minutos : 3.2 km", "Avenida Exemplo Cabral, 199, Bairro Três,", "São Paulo", "Tenho interesse")
    private val electric = map + listOf("Exclusivo x 5", "R$ 36,94 c", "45 min * 14.2 km - Est. de R$2,60/km", "à +R$ 2,25 incluído",
        "2 & 4min- 0.8 km", "R. Exemplo Cintra, Bairro Quatro, São Paulo", "x 5,00 (123) O Verificado", "O 41 minutos - 13.4km",
        "Rua São Fictício, 337, Bairro Cinco, São", "Paulo")
    private val bonus = map + listOf("R$ 37,27", "47 min * 12.9 km - Est. de R$2,89/km", "à +R$3,50 incluído", "*& 8min:24km",
        "Av. Dr. Exemplo, Metrô Fictício, São Paulo", "x 5,00 (4)", "O 39 minutos : 10.5 km", "Rua Exemplo Inoue, 76, Bairro Seis, São Paulo", "Tenho interesse")

    private val expected = mapOf(
        black to ("Shopping Exemplo Sul" to "Al. Exemplo"), comfort to ("Rua Doutor Exemplo" to "Avenida Exemplo Cabral"),
        electric to ("R. Exemplo Cintra" to "Rua São Fictício"), bonus to ("Av. Dr. Exemplo" to "Rua Exemplo Inoue"),
    )

    private fun assertRoles(rows: List<String>, pickup: String?, destination: String?) {
        for (c in listOf(ctx(rows), legacy(rows))) {
            if (pickup == null) assertNull("pickup must be unavailable: ${c?.pickupLabel}", c?.pickupLabel)
            else assertTrue("pickup ${c?.pickupLabel}", c?.pickupLabel.orEmpty().startsWith(pickup))
            if (destination == null) assertNull("destination must be unavailable: ${c?.destinationLabel}", c?.destinationLabel)
            else assertTrue("destination ${c?.destinationLabel}", c?.destinationLabel.orEmpty().startsWith(destination))
        }
    }

    @Test fun fourRealLayoutsKeepPickupAndDestinationApart() = expected.forEach { (rows, e) -> assertRoles(rows, e.first, e.second) }

    @Test fun wrappedAddressesJoinButRatingsAndMapTextNeverDo() {
        assertEquals("Rua São Fictício, 337, Bairro Cinco, São Paulo", ctx(electric)!!.destinationLabel)
        assertEquals("Rua Doutor Exemplo, Vila Um, São Paulo", ctx(comfort)!!.pickupLabel)
        assertEquals("R. Exemplo Cintra, Bairro Quatro, São Paulo", ctx(electric)!!.pickupLabel)
        for (rows in expected.keys) {
            val c = ctx(rows)!!
            map.forEach { m -> assertFalse(c.pickupLabel.orEmpty().contains(m)); assertFalse(c.destinationLabel.orEmpty().contains(m)) }
        }
    }

    @Test fun headerPlusOneRealLegNeverPutsThePickupInTheDestination() {
        // The reported scenario: OCR keeps the header total and only ONE leg.
        for ((rows, e) in expected) {
            val tripLeg = rows.first { it.contains("minutos") }
            val pickupLeg = rows.first { it.contains("min") && !it.contains("minutos") && !it.contains("Est.") }
            // Trip leg lost → destination unavailable (was: pickup address shown as destination).
            val noTrip = ctx(rows - tripLeg)
            assertNull(noTrip?.destinationLabel)
            noTrip?.pickupLabel?.let { assertTrue(it.startsWith(e.first)) }
            // Pickup leg lost → pickup unavailable, destination still right.
            assertRoles(rows - pickupLeg, null, e.second)
        }
    }

    @Test fun legLinesWithoutRoleEvidenceDoNotGetAnAddress() {
        val single = listOf("R$ 20,00", "2min- 0.3km", "Rua Alfa, 10", "Avenida Beta, 20")
        assertRoles(single, null, null)
        // No geometry at all: map/other texts are not promoted (was: first/last line).
        assertRoles(listOf("R$ 20,00", "Bairro Mapa Um", "Rua Alfa, 10", "Avenida Beta, 20", "Aceitar"), null, null)
    }

    @Test fun destinationStopsAtTheButtonAndCityAloneIsNotADestination() {
        assertRoles(listOf("R$ 11,99", "2 min • 0.3 km", "Rua Alfa, 10", "13 minutos • 3.2 km", "São Paulo", "Tenho interesse"), "Rua Alfa", null)
        assertRoles(listOf("R$ 11,99", "2 min • 0.3 km", "Rua Alfa, 10", "13 minutos • 3.2 km", "Tenho interesse", "Avenida Fora do Card, 1"), "Rua Alfa", null)
    }

    @Test fun legacyAndNinetyNineLayoutsStillWork() {
        assertRoles(listOf("UberX", "R$ 15,40", "5 min (1,8 km) de distância", "Rua Gama, 45", "Viagem de 14 min (6,2 km)", "Rua Delta, 300", "Aceitar"),
            "Rua Gama", "Rua Delta")
        assertRoles(listOf("Plus Nova", "(8 min 591 m) Rua Exemplo Um, 165, Vila Dois", "(20 min 2 km) Rua Exemplo Três, 62, Vila Quatro", "Escolher"),
            "Rua Exemplo Um", "Rua Exemplo Três")
        assertRoles(listOf("R$ 32,40", "Retirada", "Av. Exemplo, 1000", "8 min (2,0 km)", "Destino", "Rua Exemplo, 1500", "22 min (8,5 km)"),
            "Av. Exemplo", "Rua Exemplo")
    }

    @Test fun mapsButtonsUseTheRightLegAndNothingWhenUnavailable() {
        val c = ctx(electric)!!
        val pickup = OfferMaps.searchIntent(c.pickupLabel, c.pickupLat, c.pickupLng)!!.dataString!!
        val destination = OfferMaps.searchIntent(c.destinationLabel, c.destinationLat, c.destinationLng)!!.dataString!!
        assertTrue(pickup, pickup.contains("Cintra")); assertFalse(pickup.contains("Fict%C3%ADcio"))
        assertTrue(destination, destination.contains("Fict%C3%ADcio")); assertFalse(destination.contains("Cintra"))
        val combined = CombinedRoute0212.intent(c)!!.dataString!!
        assertTrue(combined, combined.substringAfter("destination=").startsWith("Rua"))
        assertTrue(combined, combined.substringAfter("waypoints=").startsWith("R."))
        // Destination unavailable → no destination search and no combined route.
        val partial = ctx(electric - "O 41 minutos - 13.4km")!!
        assertNull(partial.destinationLabel)
        assertNull(OfferMaps.searchIntent(partial.destinationLabel, null, null))
        assertNull(CombinedRoute0212.intent(partial))
    }

    @Test fun offersWithoutContextFromTheFallbackPathGetItFromTheirCardText() {
        val raw = (electric - map).joinToString("\n")
        val offer = Fixtures108.offer("fb1", 36.94, observedAt = at).copy(rawText = raw, context = null)
        val done = OfferContextCompletion109.complete(offer)
        assertTrue(done.context!!.pickupLabel!!.startsWith("R. Exemplo Cintra"))
        assertTrue(done.context!!.destinationLabel!!.startsWith("Rua São Fictício"))
        assertEquals(OfferContextCompletion109.SOURCE, done.context!!.geocodeSource)
        // Times/fare untouched; existing context never replaced; other platforms untouched.
        assertEquals(offer.copy(context = null), done.copy(context = null))
        val existing = offer.copy(context = OfferContext(pickupLabel = "Rua Já Lida, 1"))
        assertSame(existing, OfferContextCompletion109.complete(existing))
        val ninetyNine = offer.copy(platform = "99")
        assertSame(ninetyNine, OfferContextCompletion109.complete(ninetyNine))
    }
}
