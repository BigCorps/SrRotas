package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationContinuityHudRules025Test {
    private fun insight(level: String, pct: Double? = null) =
        DestinationContinuityInsight0211(
            kind = if (pct == null) "historical_indicator" else "probability",
            level = level,
            probabilityPct = pct,
            samples = 32,
            confidence = "low",
            source = "sr_rotas_seed",
            regionLabel = "Itaim Bibi",
            wording = "Histórico; não garante nova corrida.",
        )

    @Test fun highIsGoodWithoutChangingFinancialVerdict() {
        assertEquals(2, DestinationContinuityHudRules025.grade(insight("high")))
        assertEquals("72% · Alta", DestinationContinuityHudRules025.value(insight("high", 72.2)))
    }

    @Test fun mediumIsWarningAndLowIsBad() {
        assertEquals(1, DestinationContinuityHudRules025.grade(insight("medium")))
        assertEquals(0, DestinationContinuityHudRules025.grade(insight("low")))
    }

    @Test fun insufficientIsNeutral() {
        assertNull(DestinationContinuityHudRules025.grade(insight("insufficient")))
        assertEquals("Dados insuf.", DestinationContinuityHudRules025.value(insight("insufficient")))
    }
}
