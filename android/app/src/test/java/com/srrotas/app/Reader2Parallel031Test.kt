package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Reader2Parallel031Test {
    private fun line(text: String, y: Int, left: Int = 80, right: Int = 920) =
        Reader2ParallelRules031.Line(text, left, y, right, y + 40)

    @Test fun longCardWithSplitGeometryBuildsCoreCandidateBeforeM1() {
        val longNote = "Acesso ao condomínio pela portaria lateral, apresentar identificação ao segurança e seguir até o bloco indicado. ".repeat(7)
        val lines = listOf(
            line("Electric", 100),
            line("R$ 34,15", 150),
            line("R$ 3,75 /km aprox.", 200),
            line("4,86", 250),
            line("Verificado", 300),
            line("Turbo Mais", 350),
            line("R$ 6,57", 400),
            line("4 min", 470),
            line("1,2 km", 520),
            line("Rua das Flores, 120 - Centro", 570),
            line(longNote, 620),
            line("33 min", 760),
            line("7,9 km", 810),
            line("Avenida Paulista, 1500 - Bela Vista", 860),
            line("Aceitar", 930),
        )

        val result = Reader2ParallelRules031.inspect(lines, frameWidth = 1080, frameHeight = 1920)
        assertTrue(result.uberCandidate)
        assertTrue(result.longCard)
        assertEquals(1, result.candidates.size)
        val c = result.candidates.single()
        assertEquals(34.15, c.fare, 0.001)
        assertEquals(4, c.pickupMinutes)
        assertEquals(1.2, c.pickupKm ?: 0.0, 0.001)
        assertEquals(33, c.tripMinutes)
        assertEquals(7.9, c.tripKm ?: 0.0, 0.001)
        assertEquals(37, c.totalMinutes)
        assertEquals("Rua das Flores, 120 - Centro", c.pickupLabel)
        assertEquals("Avenida Paulista, 1500 - Bela Vista", c.destinationLabel)
        assertTrue(c.coreComplete)
        assertTrue(c.splitGeometry)
    }

    @Test fun turboValueNeverBecomesPrimaryFare() {
        val lines = listOf(
            line("Electric", 100),
            line("R$ 34,15", 150),
            line("Turbo Mais", 200),
            line("R$ 6,57", 250),
            line("4 min (1,2 km)", 330),
            line("Rua A, 10", 380),
            line("33 min (7,9 km)", 470),
            line("Rua B, 20", 520),
            line("Aceitar", 580),
        )
        val result = Reader2ParallelRules031.inspect(lines, 1080, 1920)
        assertEquals(1, result.candidates.size)
        assertEquals(34.15, result.candidates.single().fare, 0.001)
    }

    @Test fun reader2CanBuildCandidateWithoutStrictSameLineGeometry() {
        val lines = listOf(
            line("UberX", 100),
            line("R$ 21,80", 150),
            line("6 min", 230),
            line("2,1 km", 275),
            line("Rua Origem, 45", 330),
            line("18 min", 430),
            line("5,4 km", 475),
            line("Avenida Destino, 900", 530),
            line("Aceitar", 600),
        )
        val result = Reader2ParallelRules031.inspect(lines, 1080, 1920)
        val candidate = result.candidates.singleOrNull()
        assertNotNull(candidate)
        assertTrue(candidate!!.coreComplete)
        assertTrue(candidate.splitGeometry)
    }

    @Test fun twoRealCardsRemainTwoCandidates() {
        val lines = listOf(
            line("Radar de viagens", 50),
            line("R$ 18,50", 100),
            line("3 min (1,0 km)", 160),
            line("Rua Um, 10", 210),
            line("12 min (4,0 km)", 270),
            line("Rua Dois, 20", 320),
            line("Selecionar", 370),
            line("R$ 27,90", 450),
            line("5 min (1,8 km)", 510),
            line("Rua Três, 30", 560),
            line("20 min (6,0 km)", 620),
            line("Rua Quatro, 40", 670),
            line("Selecionar", 720),
        )
        val result = Reader2ParallelRules031.inspect(lines, 1080, 1920)
        assertEquals(2, result.candidates.size)
        assertEquals(listOf(18.5, 27.9), result.candidates.map { it.fare })
        assertTrue(result.candidates.all { it.coreComplete })
    }

    @Test fun homeScreenDoesNotBecomeReader2Candidate() {
        val lines = listOf(
            line("Você está online", 100),
            line("R$ 120,00 ganhos hoje", 160),
            line("Procurando viagens", 220),
        )
        val result = Reader2ParallelRules031.inspect(lines, 1080, 1920)
        assertFalse(result.uberCandidate)
        assertTrue(result.candidates.isEmpty())
    }
}
