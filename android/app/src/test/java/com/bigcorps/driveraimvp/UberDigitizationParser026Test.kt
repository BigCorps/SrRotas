package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UberDigitizationParser026Test {
    private val capturedAt = Instant.parse("2026-09-04T12:00:00Z")

    @Test
    fun parsesSessionSummaryLegacyLayout() {
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

    @Test
    fun parsesRealSessionSummaryScreenshots() {
        val cases = listOf(
            Triple(
                """
                Resumo da sessão
                2 de set., 07:21 - 2 de set., 16:45
                R$ 409,63
                Viagens concluídas
                15
                Viagens oferecidas
                78
                Bom demais! Você aceitou e concluiu 13 viagens seguidas.
                """.trimIndent(),
                409.63,
                15 to 78,
            ),
            Triple(
                """
                Resumo da sessão
                2 de set., 16:55 - 2 de set., 18:13
                R$ 55,26
                Viagens concluídas
                2
                Viagens oferecidas
                7
                Você não teve cancelamentos nesta sessão. Muito bem!
                """.trimIndent(),
                55.26,
                2 to 7,
            ),
            Triple(
                """
                Resumo da sessão
                3 de set., 07:24 - 3 de set., 18:29
                R$ 445,20
                Viagens concluídas
                14
                Viagens oferecidas
                80
                """.trimIndent(),
                445.20,
                14 to 80,
            ),
        )
        cases.forEach { (raw, earnings, counts) ->
            val parsed = UberDigitizationParser026.parseSession(raw, capturedAt)
            assertEquals(earnings, parsed.earnings!!, 0.001)
            assertEquals(counts.first, parsed.completedTrips)
            assertEquals(counts.second, parsed.offeredTrips)
            assertTrue(parsed.startedAt?.contains("2026-09-") == true)
            assertTrue(parsed.endedAt?.contains("2026-09-") == true)
        }
    }

    @Test
    fun parsesHistoryCardsWithDurationDistanceSurgeAndOneHourRide() {
        val parsed = UberDigitizationParser026.parseRides(
            """
            Ganhos
            qui., 3 de set.
            R$ 23,51
            Black · 12 min 48 segundos · 3.57 km · 16:34
            R$ 2,75 Preço dinâmico
            Rua Tucumã, Pinheiros - São Paulo - SP, 05455-010, BR
            Avenida Nove de Julho, Jardim Paulista - São Paulo - SP, 01406-000, BR
            R$ 10,19
            Black · 4 min 18 segundos · 0.74 km · 11:35
            Alameda Lorena, Jardim Paulista - São Paulo - SP, 01424-000, BR
            Alameda Joaquim Eugênio de Lima, Jardim Paulista - São Paulo - SP, 01403-003, BR
            R$ 68,77
            UberX · 1 h 6 min · 35.45 km · 10:27
            Terminal 2, Aeroporto Internacional de São Paulo (GRU), Cumbica - Guarulhos - SP, 07190-100, BR
            Alameda Lorena, Jardim Paulista - São Paulo - SP
            """.trimIndent(),
            capturedAt,
        )
        assertEquals(3, parsed.size)
        val black = parsed.first { kotlin.math.abs(it.fare - 23.51) < 0.001 }
        assertEquals(768, black.durationSeconds)
        assertEquals(3.57, black.distanceKm!!, 0.001)
        assertEquals(2.75, black.surgeAmount!!, 0.001)
        assertTrue(black.pickupLabel?.contains("Tucumã") == true)
        val longRide = parsed.first { kotlin.math.abs(it.fare - 68.77) < 0.001 }
        assertEquals(3960, longRide.durationSeconds)
        assertEquals(35.45, longRide.distanceKm!!, 0.001)
    }

    @Test
    fun cancellationDoesNotBecomeCompletedRide() {
        val parsed = UberDigitizationParser026.parseRides(
            """
            Ganhos
            sex., 4 de set.
            R$ 0,00
            UberX · 2 min 10 segundos · 0.40 km · 12:03
            Corrida cancelada pelo usuário
            Rua A, Centro - São Paulo - SP
            Rua B, Centro - São Paulo - SP
            """.trimIndent(),
            capturedAt,
        ).single()
        assertEquals(UberCompletedRide026.STATUS_CANCELLED, parsed.rideStatus)
        assertEquals(0.0, parsed.fare, 0.001)
    }

    @Test
    fun historyDoesNotTreatDynamicPriceAsPrimaryFare() {
        val parsed = UberDigitizationParser026.parseRides(
            """
            Ganhos
            3 de set.
            R$ 23,54
            Comfort · 19 min 54 segundos · 4.78 km · 17:45
            R$ 1,75 Preço dinâmico
            Rua A, São Paulo - SP
            Rua B, São Paulo - SP
            """.trimIndent(),
            capturedAt,
        ).single()
        assertEquals(23.54, parsed.fare, 0.001)
        assertEquals(1.75, parsed.surgeAmount!!, 0.001)
        assertEquals("comfort", parsed.serviceType)
    }
}
