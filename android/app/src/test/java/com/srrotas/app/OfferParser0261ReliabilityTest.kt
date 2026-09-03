package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfferParser0261ReliabilityTest {
    private val settings = DriverSettings(costPerKm = 0.85)

    @Test
    fun rejectsThreeHundredMinuteOcrWithoutHourEvidence() {
        val parsed = OfferParser.parse(
            """Electric
Exclusivo
R$ 37,48
5 min (1,0 km)
295 minutos (6,1 km)
Aceitar""",
            "com.ubercab.driver",
            "fixture",
            settings,
        )
        assertNull(parsed)
    }

    @Test
    fun keepsLegitimateOneHourTwentyNineMinuteOffer() {
        val parsed = OfferParser.parse(
            """UberX
Exclusivo
R$ 88,00
R$ 2,00/km aprox.
2 min (1,0 km)
1 h 29 min (43,0 km)
Aceitar""",
            "com.ubercab.driver",
            "fixture",
            settings,
        )
        assertNotNull(parsed)
        assertEquals(91, parsed!!.totalMinutes)
        assertEquals(44.0, parsed.totalKm!!, .01)
    }
}
