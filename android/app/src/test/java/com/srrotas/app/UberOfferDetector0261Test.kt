package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UberOfferDetector0261Test {
    @Test
    fun bareDollarFareStillAnchorsUberSpatialCard() {
        assertTrue(UberOfferDetector.isPrimaryFareLine("$ 17,99"))
    }

    @Test
    fun bonusAndAdvertisedRateAreNotPrimaryFare() {
        assertFalse(UberOfferDetector.isPrimaryFareLine("+$ 2,35 incluído"))
        assertFalse(UberOfferDetector.isPrimaryFareLine("$ 2,55/km aprox."))
    }
}
