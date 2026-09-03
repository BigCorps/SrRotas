package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationContinuityPresentation0211Test {
    @Test
    fun realProbabilityShowsPercentageAndLevel() {
        val value = DestinationContinuityInsight0211(
            kind = "probability",
            level = "high",
            probabilityPct = 67.4,
            samples = 42,
            confidence = "low",
            source = "personal_exposure",
            regionLabel = "Pinheiros",
            wording = "",
        )
        assertEquals("Prob. novas corridas: 67% · Alta", DestinationContinuityPresentation0211.hudLabel(value))
        assertEquals("Probabilidade de novas corridas: 67% · Alta", DestinationContinuityPresentation0211.cardTitle(value))
    }

    @Test
    fun historicalSeedNeverInventsPercentage() {
        val value = DestinationContinuityInsight0211(
            kind = "historical_indicator",
            level = "medium",
            probabilityPct = null,
            samples = 188,
            confidence = "medium",
            source = "sr_rotas_seed",
            regionLabel = "Moema",
            wording = "Recorrência histórica moderada.",
        )
        assertEquals("Prob. novas corridas: Média", DestinationContinuityPresentation0211.hudLabel(value))
        assertEquals("Probabilidade de novas corridas: Média", DestinationContinuityPresentation0211.cardTitle(value))
    }
}
