package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MultiplatformOfferParserTest {
    private val settings = DriverSettings(costPerKm = 0.85)

    @Test
    fun parsesReal99PlusMetersFixture() {
        val offer = FlexibleDriverOfferParser.parseText(
            rawText = """
                Solicitações
                Corridas Entregas
                Plus Nova
                R$25,00   R$9,80/km
                4,81 · 237 corridas · Perfil Essencial
                (8 min 591 m) Rua Prof. Atílio Innocenti, 165, Vila Nova Conceição
                (20 min 2 km) Rua Gomes de Carvalho, 62, Vila Olímpia
                Escolher
            """.trimIndent(),
            platform = "99",
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture",
            settings = settings,
        )

        assertNotNull(offer)
        offer!!
        assertEquals("99", offer.platform)
        assertEquals("99plus", offer.serviceType)
        assertEquals(25.0, offer.fare, 0.01)
        assertEquals(0.59, offer.pickupKm!!, 0.01)
        assertEquals(2.0, offer.tripKm!!, 0.01)
        assertEquals(2.59, offer.totalKm!!, 0.01)
        assertEquals(28, offer.totalMinutes)
        assertEquals(9.65, offer.perKm!!, 0.02)
        assertEquals(53.57, offer.perHour!!, 0.02)
        assertEquals(0.89, offer.perMinute!!, 0.02)
        assertEquals(4.81, offer.passengerRating!!, 0.01)
        assertEquals(9.80, offer.advertisedPerKm!!, 0.01)
        assertEquals("sr-rotas-multi-v0.22.1", offer.parserVersion)
    }

    @Test
    fun parses99WhenTimeAndDistanceAreSplitAcrossOcrLines() {
        val offer = FlexibleDriverOfferParser.parse99FlexibleText(
            rawText = """
                Google Maps
                Solicitações
                Plus Nova
                R$13,70   R$3,83/km
                4 min
                Embarque
                680 m
                12 min
                Destino
                2,9 km
                Escolher
            """.trimIndent(),
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture-split",
            settings = settings,
            navigationNoise = true,
        )

        assertNotNull(offer)
        offer!!
        assertEquals("99", offer.platform)
        assertEquals("99plus", offer.serviceType)
        assertEquals(13.70, offer.fare, 0.01)
        assertEquals(0.68, offer.pickupKm!!, 0.01)
        assertEquals(2.90, offer.tripKm!!, 0.01)
        assertEquals(3.58, offer.totalKm!!, 0.01)
        assertEquals(16, offer.totalMinutes)
        assertEquals("sr-rotas-multi-v0.27.0-99-flex", offer.parserVersion)
    }

    @Test
    fun parses99InNavigationNoiseWithServiceAndAdvertisedRateEvenIfChooseIsMissed() {
        val offer = FlexibleDriverOfferParser.parse99FlexibleText(
            rawText = """
                Waze
                Plus Nova
                R$13,70   R$3,83/km
                4 min
                680 m
                12 min
                2,9 km
            """.trimIndent(),
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture-split",
            settings = settings,
            navigationNoise = true,
        )

        assertNotNull(offer)
        assertEquals("99plus", offer!!.serviceType)
    }

    @Test
    fun rejectsNavigationScreenWithLooseNumbersButNo99Anchor() {
        val offer = FlexibleDriverOfferParser.parse99FlexibleText(
            rawText = """
                Google Maps
                R$13,70   R$3,83/km
                4 min
                680 m
                12 min
                2,9 km
            """.trimIndent(),
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture-generic",
            settings = settings,
            navigationNoise = true,
        )
        assertNull(offer)
    }



    @Test
    fun parses99FromTrustedPaneWhenIdentityTokenDropsForOneFrame() {
        val offer = FlexibleDriverOfferParser.parse99FlexibleText(
            rawText = """
                R$13,70   R$3,83/km
                4 min
                680 m
                12 min
                2,9 km
            """.trimIndent(),
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture-pane-memory",
            settings = settings,
            navigationNoise = true,
            trustedPane = true,
        )

        assertNotNull(offer)
        offer!!
        assertEquals("99", offer.platform)
        assertEquals(13.70, offer.fare, 0.01)
        assertEquals(3.58, offer.totalKm!!, 0.01)
        assertEquals(16, offer.totalMinutes)
    }

    @Test
    fun trusted99PaneStillRejectsExplicitUberAnchor() {
        val offer = FlexibleDriverOfferParser.parse99FlexibleText(
            rawText = """
                Comfort
                R$13,70   R$3,83/km
                4 min
                680 m
                12 min
                2,9 km
                Aceitar
            """.trimIndent(),
            sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
            captureMethod = "fixture-pane-memory",
            settings = settings,
            navigationNoise = true,
            trustedPane = true,
        )

        assertNull(offer)
    }

    @Test
    fun rejectsGenericScreenWithoutTwoGeometryPairs() {
        val offer = FlexibleDriverOfferParser.parseText(
            rawText = "R$ 25,00\nOferta disponível\n8 min 591 m\nEscolher",
            platform = "other",
            sourcePackage = "inferred:driver-app",
            captureMethod = "fixture",
            settings = settings,
        )
        assertNull(offer)
    }
}
