package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PickupPresentation0211Test {
    @Test fun classifiesSearchWithoutChangingOfferEngine() {
        assertEquals("OK", PickupPresentation0211.grade(2.0, 4, 4.0, 8).label)
        assertEquals("Média", PickupPresentation0211.grade(3.2, 6, 4.0, 8).label)
        assertEquals("Alta", PickupPresentation0211.grade(4.1, 5, 4.0, 8).label)
        assertEquals("Alta", PickupPresentation0211.grade(2.0, 9, 4.0, 8).label)
    }

    @Test fun unavailableMetricDoesNotInventData() {
        assertEquals("—", PickupPresentation0211.grade(null, null, 4.0, 8).label)
        assertEquals("OK", PickupPresentation0211.grade(null, 3, 4.0, 8).label)
    }
}
