package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BRUberRadarParser025Test {
    @Test fun acceptsLooseGeometryWhenAdvertisedPerKmSurvivesOcr() {
        val text = """Radar de Viagens
UberX
R$ 44,18
R$ 2,21/km aprox.
3 min 1,2 km
45 minutos 18,8 km
Selecionar"""
        assertTrue(BRUberRadarParser.shouldAttemptParse(text))
    }

    @Test fun acceptsTwoCompletePairsEvenIfAdvertisedPerKmWasLost() {
        val text = """Radar de Viagens
Comfort
R$ 44,18
3 min (1,2 km)
45 minutos (18,8 km)
Selecionar"""
        assertTrue(BRUberRadarParser.shouldAttemptParse(text))
    }

    @Test fun acceptsHourDurationAsGeometry() {
        val text = """Radar de Viagens
UberX
R$ 88,00
R$ 2,00/km aprox.
2 min (1,0 km)
1 h 29 min (43,0 km)
Selecionar"""
        assertTrue(BRUberRadarParser.shouldAttemptParse(text))
    }

    @Test fun rejectsPriceAndDurationWithoutDistance() {
        val text = """Radar de Viagens
UberX
R$ 44,18
48 minutos
Selecionar"""
        assertFalse(BRUberRadarParser.shouldAttemptParse(text))
    }

    @Test fun rejectsIsolatedPriceEvenWithServiceName() {
        val text = """Radar de Viagens
UberX
R$ 44,18
Selecionar"""
        assertFalse(BRUberRadarParser.shouldAttemptParse(text))
    }
}
