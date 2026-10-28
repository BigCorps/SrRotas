package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * vc106: official M1 spatial route (UberSpatialParser0221 → cluster → OfferParser →
 * integrity gate) on SYNTHETIC geometry that mirrors the real 1188×1599 card layout.
 * Texts reproduce what real OCR produced on the provided screenshots, with
 * addresses replaced; no screenshot or private OCR is versioned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UberSpatialRegression106Test {
    private val settings = DriverSettings(costPerKm = 0.82)
    private fun l(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        SpatialOcrLine(DriverOcrNormalizer.sanitize(text), Rect(left, top, right, bottom))

    private fun route(lines: List<SpatialOcrLine>, w: Int = 1188, h: Int = 1599) =
        UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, settings, w, h)

    private val mapNoise = listOf(
        l("BAIRRO A", 192, 3, 457, 25), l("BAIRRO B", 311, 181, 1019, 210),
        l("CIDADE", 566, 340, 801, 377), l("+R$ 2,25", 371, 470, 600, 543), l("min", 4, 530, 90, 595),
    )

    @Test fun electricWithBonusAndMapNoise() {
        val offers = route(mapNoise + listOf(
            l("Electric Exclusivo", 66, 559, 1087, 598), l("R$ 36,94", 69, 641, 419, 723),
            l("45 min • 14.2 km • Est. de R$2,60/km", 66, 746, 847, 789), l("+R$ 2,25 incluído", 84, 822, 497, 866),
            l("4 min • 0.8 km", 80, 946, 467, 993), l("Endereço de embarque", 142, 1022, 942, 1068),
            l("★ 5,00 (123) Verificado", 157, 1095, 742, 1140), l("41 minutos • 13.4 km", 65, 1200, 628, 1249),
            l("Endereço de destino", 142, 1270, 1068, 1323), l("Aceitar", 511, 1456, 676, 1495),
        ))
        val o = offers.single()
        assertEquals(36.94, o.fare, 0.001)
        assertEquals(listOf(4, 41, 45), listOf(o.pickupMinutes, o.tripMinutes, o.totalMinutes))
        assertEquals(14.2, o.totalKm!!, 0.001)
        assertEquals(49.25, o.perHour!!, 0.005); assertEquals(0.82, o.perMinute!!, 0.005); assertEquals(2.60, o.perKm!!, 0.005)
        assertTrue(OfferIntegrityGate027033.assess(o).ready)
    }

    @Test fun comfortInterestCardEvenWhenCategoryPillIsLostAndMapPromoLosesPlus() {
        val lines = listOf(
            // Real OCR read the map pill "+R$ 2,25" without its "+", above the card.
            l("R$ 2,25 BAIRRO C", 264, 37, 869, 128), l("1-3 min", 144, 141, 338, 176),
            l("BAIRRO D", 16, 195, 292, 217), l("+R$ 3,25", 621, 487, 813, 533),
            l("R$ 37,27", 69, 698, 396, 780), l("47 min * 12.9 km - Est. de R$2,89/km", 66, 803, 844, 847),
            l("+R$ 3,50 incluído", 84, 879, 502, 923), l("* 8min2.4km", 80, 1010, 466, 1050),
            l("Endereço de embarque", 139, 1079, 1025, 1122), l("★ 5,00 (4)", 157, 1152, 371, 1197),
            l("O 39 minutos - 10.5 km", 65, 1257, 629, 1306), l("Endereço de destino", 142, 1335, 1043, 1380),
            l("Tenho interesse", 464, 1457, 823, 1495),
        )
        val o = route(lines, 1288).single()
        assertEquals(37.27, o.fare, 0.001)
        assertEquals(listOf(8, 39, 47), listOf(o.pickupMinutes, o.tripMinutes, o.totalMinutes))
        assertEquals(2.4, o.pickupKm!!, 0.001); assertEquals(10.5, o.tripKm!!, 0.001); assertEquals(12.9, o.totalKm!!, 0.001)
        assertEquals(47.58, o.perHour!!, 0.005); assertEquals(0.79, o.perMinute!!, 0.005); assertEquals(2.89, o.perKm!!, 0.005)
    }

    @Test fun blackAndComfortCards() {
        val black = route(mapNoise + listOf(
            l("Black Exclusivo", 66, 621, 1088, 700), l("R$ 17,99", 66, 718, 392, 808),
            l("20 min • 3.5 km • Est. de R$5,14/km", 66, 831, 817, 875), l("2 min • 0.2 km", 80, 953, 460, 993),
            l("Endereço de embarque", 140, 1022, 1075, 1068), l("★ 4,81", 157, 1156, 290, 1197),
            l("18 minutos • 3.3 km", 65, 1257, 597, 1306), l("Endereço de destino", 139, 1335, 1090, 1381), l("Aceitar", 511, 1456, 676, 1494),
        )).single()
        assertEquals(listOf(2, 18, 20), listOf(black.pickupMinutes, black.tripMinutes, black.totalMinutes))
        assertEquals(53.97, black.perHour!!, 0.005); assertEquals(5.14, black.perKm!!, 0.005)
        val comfort = route(listOf(
            l("12 min", 675, 417, 865, 447), l("Comfort", 66, 600, 330, 650), l("R$ 11,99", 69, 669, 402, 751),
            l("15 min • 3.5 km • Est. de R$3,43/km", 66, 770, 816, 819), l("2 min • 0.3 km", 80, 896, 462, 936),
            l("Endereço de embarque", 142, 965, 1041, 1008), l("★ 4,97 (3445) Verificado", 157, 1095, 775, 1140),
            l("13 minutos • 3.2 km", 65, 1200, 595, 1249), l("Endereço de destino", 139, 1271, 1074, 1321), l("Tenho interesse", 414, 1457, 773, 1495),
        )).single()
        assertEquals(listOf(2, 13, 15), listOf(comfort.pickupMinutes, comfort.tripMinutes, comfort.totalMinutes))
        assertEquals(47.96, comfort.perHour!!, 0.005); assertEquals(3.43, comfort.perKm!!, 0.005); assertEquals(0.80, comfort.perMinute!!, 0.005)
    }

    @Test fun lostPickupLegPublishesNothing() {
        val offers = route(listOf(
            l("12 min", 675, 417, 865, 447), l("Comfort", 66, 600, 330, 650), l("R$ 11,99", 69, 669, 402, 751),
            l("15 min * 3.5 km - Est. de R$3,43/km", 66, 770, 816, 819), l("* 2minºe 0.3 km", 80, 896, 462, 936),
            l("O 13 minutos - 3.2km", 65, 1200, 595, 1249), l("Tenho interesse", 414, 1457, 773, 1495),
        ))
        assertTrue("derived values must stay unavailable: $offers", offers.isEmpty())
    }

    @Test fun radarListWithOneHeaderPerFareKeepsBothCards() {
        // Two stacked cards, each with its own header: replacement rule must not merge them.
        val lines = listOf(
            l("Radar de viagens", 60, 100, 600, 140),
            l("R$ 17,99", 66, 200, 392, 260), l("20 min • 3.5 km • Est. de R$5,14/km", 66, 270, 817, 310),
            l("2 min • 0.2 km", 80, 330, 460, 370), l("18 minutos • 3.3 km", 65, 400, 597, 440), l("Selecionar", 500, 470, 680, 510),
            l("R$ 11,99", 69, 700, 402, 760), l("15 min • 3.5 km • Est. de R$3,43/km", 66, 770, 816, 810),
            l("2 min • 0.3 km", 80, 830, 462, 870), l("13 minutos • 3.2 km", 65, 900, 595, 940), l("Selecionar", 500, 970, 680, 1010),
        )
        val fares = MoneyRoleResolver030.primarySpatialFareLines(lines).map { it.text }
        assertEquals(listOf("R$ 17,99", "R$ 11,99"), fares)
    }
}
