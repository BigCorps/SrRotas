package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberDurationParser025Test {
    @Test fun parsesPlainMinutes() {
        assertEquals(34, UberDurationParser025.parseCandidate("34 minutos"))
    }

    @Test fun parsesCompactHourAndMinutes() {
        assertEquals(89, UberDurationParser025.parseCandidate("1h29"))
        assertEquals(89, UberDurationParser025.parseCandidate("1h29min"))
    }

    @Test fun parsesSpacedHourAndMinutes() {
        assertEquals(89, UberDurationParser025.parseCandidate("1 h 29 min"))
    }

    @Test fun parsesPortugueseHourPhrase() {
        assertEquals(65, UberDurationParser025.parseCandidate("1 hora e 5 minutos"))
        assertEquals(120, UberDurationParser025.parseCandidate("2 horas"))
    }

    @Test fun keepsOcrNumericRecovery() {
        assertEquals(89, UberDurationParser025.parseCandidate("I h 29 min"))
        assertEquals(11, UberDurationParser025.parseCandidate("1l minutos"))
    }

    @Test fun rejectsImpossibleHourMinuteComposition() {
        assertNull(UberDurationParser025.parseCandidate("1 h 89 min"))
        assertNull(UberDurationParser025.parseCandidate("9 horas"))
    }
}
