package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test

/**
 * vc106 regression: four real Uber `min • km` cards (numbers transcribed from the
 * provided screenshots; addresses replaced) plus OCR noise observed when the same
 * screenshots were run through a real OCR engine, and legacy "(km)" layouts.
 */
class UberLegResolver106Test {
    private val settings = DriverSettings(costPerKm = 0.82)
    private fun parse(text: String) = OfferParser.parse(text, AppSignals.UBER_PACKAGE, "fixture", settings)
    private fun flex(text: String) = FlexibleDriverOfferParser.parseText(text, "uber", AppSignals.UBER_PACKAGE, "fixture", settings)

    data class Case(val name: String, val text: String, val fare: Double, val pickup: Pair<Int, Double>, val trip: Pair<Int, Double>,
        val total: Pair<Int, Double>, val perMinute: Double, val perHour: Double, val perKm: Double)

    // Cluster text in card order, as the spatial isolation feeds OfferParser.
    private val black = """
        Black
        Exclusivo
        R$ 17,99
        20 min • 3.5 km • Est. de R$5,14/km
        2 min • 0.2 km
        Endereço de embarque A
        ★ 4,81
        18 minutos • 3.3 km
        Endereço de destino A
        Aceitar
    """.trimIndent()
    private val comfort = """
        Comfort
        R$ 11,99
        15 min • 3.5 km • Est. de R$3,43/km
        2 min • 0.3 km
        Endereço de embarque B
        ★ 4,97 (3445) Verificado
        13 minutos • 3.2 km
        Endereço de destino B
        Tenho interesse
    """.trimIndent()
    private val electric = """
        Electric
        Exclusivo
        R$ 36,94
        45 min • 14.2 km • Est. de R$2,60/km
        +R$ 2,25 incluído
        4 min • 0.8 km
        Endereço de embarque C
        ★ 5,00 (123) Verificado
        41 minutos • 13.4 km
        Endereço de destino C
        Aceitar
    """.trimIndent()
    private val comfortBonus = """
        Comfort
        R$ 37,27
        47 min • 12.9 km • Est. de R$2,89/km
        +R$ 3,50 incluído
        8 min • 2.4 km
        Endereço de embarque D
        ★ 5,00 (4)
        39 minutos • 10.5 km
        Endereço de destino D
        Tenho interesse
    """.trimIndent()

    private val cases = listOf(
        Case("black_01", black, 17.99, 2 to 0.2, 18 to 3.3, 20 to 3.5, 0.90, 53.97, 5.14),
        Case("comfort_02", comfort, 11.99, 2 to 0.3, 13 to 3.2, 15 to 3.5, 0.80, 47.96, 3.43),
        Case("electric_03", electric, 36.94, 4 to 0.8, 41 to 13.4, 45 to 14.2, 0.82, 49.25, 2.60),
        Case("comfort_04", comfortBonus, 37.27, 8 to 2.4, 39 to 10.5, 47 to 12.9, 0.79, 47.58, 2.89),
    )

    private fun assertCase(c: Case, offer: RideOffer?) {
        assertNotNull(c.name, offer)
        offer!!
        assertEquals(c.name, c.fare, offer.fare, 0.001)
        assertEquals("${c.name} pickup_minutes", c.pickup.first, offer.pickupMinutes)
        assertEquals("${c.name} pickup_km", c.pickup.second, offer.pickupKm!!, 0.001)
        assertEquals("${c.name} trip_minutes", c.trip.first, offer.tripMinutes)
        assertEquals("${c.name} trip_km", c.trip.second, offer.tripKm!!, 0.001)
        assertEquals("${c.name} total_minutes", c.total.first, offer.totalMinutes)
        assertEquals("${c.name} total_km", c.total.second, offer.totalKm!!, 0.001)
        assertEquals("${c.name} R$/min", c.perMinute, offer.perMinute!!, 0.005)
        assertEquals("${c.name} R$/h", c.perHour, offer.perHour!!, 0.005)
        assertEquals("${c.name} R$/km", c.perKm, offer.perKm!!, 0.005)
        assertTrue("${c.name} integrity ${OfferIntegrityGate027033.assess(offer).flags}", OfferIntegrityGate027033.assess(offer).ready)
    }

    @Test fun fourRealCardsSeparateTotalPickupAndTrip() = cases.forEach { assertCase(it, parse(it.text)) }

    @Test fun flexibleUberFallbackAlsoIgnoresHeaderTotal() = cases.forEach { c ->
        val offer = flex(c.text)!!
        assertEquals(c.name, c.pickup.first, offer.pickupMinutes)
        assertEquals(c.name, c.trip.first, offer.tripMinutes)
        assertEquals(c.name, c.total.first, offer.totalMinutes)
        assertEquals(c.name, c.total.second, offer.totalKm!!, 0.001)
    }

    @Test fun includedBonusIsNotAddedToFare() {
        assertEquals(36.94, parse(electric)!!.fare, 0.001)
        assertEquals(37.27, parse(comfortBonus)!!.fare, 0.001)
    }

    @Test fun headerOrderDoesNotDecideTheRoles() {
        // pickup → trip → total (header last) and header between the legs.
        val headerLast = "Black\nExclusivo\nR$ 17,99\n2 min • 0.2 km\n18 minutos • 3.3 km\n20 min • 3.5 km • Est. de R$5,14/km\nAceitar"
        val headerMiddle = "Black\nExclusivo\nR$ 17,99\n2 min • 0.2 km\n20 min • 3.5 km • Est. de R$5,14/km\n18 minutos • 3.3 km\nAceitar"
        assertCase(cases[0], parse(headerLast))
        assertCase(cases[0], parse(headerMiddle))
        // Without the "Est. de" marker the total is still identified by arithmetic.
        val noMarkerFirst = "Black\nExclusivo\nR$ 17,99\n20 min • 3.5 km\n2 min • 0.2 km\n18 minutos • 3.3 km\nAceitar"
        val noMarkerLast = "Black\nExclusivo\nR$ 17,99\n2 min • 0.2 km\n18 minutos • 3.3 km\n20 min • 3.5 km\nAceitar"
        assertCase(cases[0], parse(noMarkerFirst))
        assertCase(cases[0], parse(noMarkerLast))
    }

    @Test fun ocrSeparatorsAndDecimalVariantsObservedInRealOcr() {
        // Real OCR of the screenshots read "•" as "*", "-", "«", ":" and glued tokens.
        val noisy = "Comfort\nR$ 37,27\n47 min * 12.9 km - Est. de R$2,89/km\n+R$ 3,50 incluído\n* 8min2.4km\n★ 5,00 (4)\nO 39 minutos - 10.5 km\nTenho interesse"
        assertCase(cases[3], parse(noisy))
        val comma = "Electric\nExclusivo\nR$ 36,94\n45 min « 14,2 km • Est. de R$2,60/km\n4min: 0,8 km\n41 minutos · 13,4 km\nAceitar"
        assertCase(cases[2], parse(comma))
        val glued = "Black\nExclusivo\nR$ 17,99\n20 min * 3.5 km - Est. de R$5,14/km\n2min-0.2km\nO 18 minutos 3.3 km\nAceitar"
        assertCase(cases[0], parse(glued))
    }

    @Test fun mapEtaPromotionsAndRatingsNeverBecomeGeometry() {
        val contaminated = "+R$ 2,25\n1-3 min\nA 12 min\n+R$ 3,25\n" + comfortBonus
        assertCase(cases[3], parse(contaminated))
        val withMapEta = "12 min\n" + comfort
        assertCase(cases[1], parse(withMapEta))
    }

    @Test fun missingLegKeepsDerivedValuesUnavailable() {
        // Real OCR read "2 min • 0.3 km" as "* 2minºe 0.3 km": only total + trip survive.
        val lostPickup = "Comfort\nR$ 11,99\n15 min * 3.5 km - Est. de R$3,43/km\n* 2minºe 0.3 km\nA 12 min\nO 13 minutos - 3.2km\nTenho interesse"
        assertNull(parse(lostPickup))
        assertNull(flex(lostPickup))
        // Header total without any leg.
        assertNull(parse("Black\nExclusivo\nR$ 17,99\n20 min • 3.5 km • Est. de R$5,14/km\nAceitar"))
        // Legs that contradict the header total.
        assertNull(parse("Black\nExclusivo\nR$ 17,99\n20 min • 3.5 km • Est. de R$5,14/km\n2 min • 0.2 km\n25 minutos • 3.3 km\nAceitar"))
        // Three legs without a total relation.
        assertNull(parse("Black\nExclusivo\nR$ 17,99\n2 min • 0.2 km\n18 minutos • 3.3 km\n7 min • 1.0 km\nAceitar"))
        // Order-only fallback with header-like third duration is refused.
        assertNull(parse("Black\nExclusivo\nR$ 17,99\n20 min\n3.5 km\n2 min\n0.2 km\n18 minutos\n3.3 km\nAceitar"))
    }

    @Test fun legacyParenthesisLayoutsAreUnchanged() {
        val legacy = "UberX\nExclusivo\nR$ 15,40\n5 min (1,8 km) de distância\nEndereço A\nViagem de 14 min (6,2 km)\nEndereço B\nAceitar"
        val offer = parse(legacy)!!
        assertEquals(5, offer.pickupMinutes); assertEquals(1.8, offer.pickupKm!!, 0.001)
        assertEquals(14, offer.tripMinutes); assertEquals(6.2, offer.tripKm!!, 0.001)
        assertEquals(19, offer.totalMinutes)
        assertTrue(UberLegResolver106.resolve(legacy) is UberLegResolver106.Resolution.None)
    }

    @Test fun resolverStatesItsEvidence() {
        val r = UberLegResolver106.resolve(black) as UberLegResolver106.Resolution.Resolved
        assertEquals("header_total+two_legs", r.evidence)
        assertEquals(20, r.total!!.minutes)
        assertTrue(UberLegResolver106.resolve("1-3 min\n12 min") is UberLegResolver106.Resolution.None)
    }

    @Test fun interestActionAnchorsTheCard() {
        assertEquals(UberScreenGate.Kind.OFFER_CANDIDATE, UberScreenGate.classify("R$ 11,99\nTenho interesse"))
        assertTrue(OfferSpatialIsolation0221.hasExplicitUberCardAnchor("Tenho interesse"))
    }
}
