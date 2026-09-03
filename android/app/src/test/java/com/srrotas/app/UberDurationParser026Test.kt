package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UberDurationParser026Test {
    @Test fun portugueseHourAndMinutes() {
        assertEquals(70, UberDurationParser026.parseCandidate("1 hora e 10 minutos"))
        assertEquals(80, UberDurationParser026.parseCandidate("1 hora e 20 minutos"))
        assertEquals(89, UberDurationParser026.parseCandidate("1h29"))
    }

    @Test fun commonOcrHourConfusions() {
        assertEquals(70, UberDurationParser026.parseCandidate("1 h0ra e 10 min"))
        assertEquals(80, UberDurationParser026.parseCandidate("1 hor4 e 20 m1n"))
        assertEquals(89, UberDurationParser026.parseCandidate("I h0ra e 29 minutos"))
    }

    @Test fun splitAcrossOcrLines() {
        assertEquals(
            listOf(70),
            UberDurationParser026.findAll("1 h0ra e\n10 min").map { it.minutes },
        )
    }

    @Test fun detectsUnresolvedHourEvidence() {
        val audit = UberDurationParser026.audit("1 hora e viagem estimada")
        assertTrue(audit.hasHourEvidence)
        assertFalse(audit.hasParsedHourDuration)
        assertTrue(audit.unresolvedHourEvidence)
    }

    @Test fun ordinaryMinutesRemainUnchanged() {
        val audit = UberDurationParser026.audit("2 min (0,8 km)\n29 min (21,3 km)")
        assertFalse(audit.hasHourEvidence)
        assertEquals(listOf(2, 29), audit.parsedDurations)
    }
}
