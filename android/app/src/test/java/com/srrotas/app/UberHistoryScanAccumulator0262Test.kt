package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UberHistoryScanAccumulator0262Test {
    @Test
    fun overlappingFramesDoNotDuplicateSameRide() {
        val first = """
            Ganhos
            qui., 3 de set.
            R$ 23,51
            Black · 12 min 48 segundos · 3.57 km · 16:34
            Rua Tucumã, Pinheiros - São Paulo - SP
            Avenida Nove de Julho, Jardim Paulista - São Paulo - SP
            R$ 10,19
            Black · 4 min 18 segundos · 0.74 km · 11:35
            Alameda Lorena, Jardim Paulista - São Paulo - SP
            Alameda Joaquim Eugênio de Lima, Jardim Paulista - São Paulo - SP
        """.trimIndent()
        val second = """
            Ganhos
            qui., 3 de set.
            R$ 23,51
            Black · 12 min 48 segundos · 3.57 km · 16:34
            Rua Tucumã, Pinheiros - São Paulo - SP
            Avenida Nove de Julho, Jardim Paulista - São Paulo - SP
        """.trimIndent()
        val result = UberHistoryScanAccumulator0262.parseFrames(
            listOf(first, second),
            Instant.parse("2026-09-04T12:00:00Z"),
        )
        assertEquals(2, result.rides.size)
        assertEquals(2, result.framesWithRides)
        assertTrue(result.rides.any { kotlin.math.abs(it.fare - 23.51) < 0.001 })
    }
}
