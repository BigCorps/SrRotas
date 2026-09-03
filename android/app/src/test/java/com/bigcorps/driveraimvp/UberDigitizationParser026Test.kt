package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UberDigitizationParser026Test {
    @Test fun parsesSessionSummary() {
        val parsed = UberDigitizationParser026.parseSession(
            """
            Resumo da atividade
            18:10 - 23:40
            Ganhos R$ 186,50
            Viagens concluídas 8
            Solicitações 13
            """.trimIndent(),
            Instant.parse("2026-09-02T23:50:00Z"),
        )
        assertEquals(186.50, parsed.earnings!!, 0.001)
        assertEquals(8, parsed.completedTrips)
        assertEquals(13, parsed.offeredTrips)
        assertTrue(parsed.confidence >= .8)
    }

    @Test fun parsesHistoryWithoutDuplicatingSameCard() {
        val parsed = UberDigitizationParser026.parseRides(
            """
            Atividade
            02/09 21:14
            UberX
            R$ 34,90
            Rua A → Rua B
            02/09 22:02
            Comfort
            R$ 51,20
            Rua C → Rua D
            """.trimIndent(),
            Instant.parse("2026-09-03T01:00:00Z"),
        )
        assertEquals(2, parsed.size)
        assertEquals("uberx", parsed[0].serviceType)
        assertEquals("comfort", parsed[1].serviceType)
    }
}
