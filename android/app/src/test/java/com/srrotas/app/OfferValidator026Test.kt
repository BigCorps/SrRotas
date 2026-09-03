package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferValidator026Test {
    @Test fun rejectsClearlyImpossibleAverageForTotalOffer() {
        val fare = 47.95
        val km = 15.9
        val min = 8
        assertFalse(
            OfferValidator.isPlausible(
                "exclusive", fare, km, min,
                fare / km, fare / (min / 60.0), fare / min,
            ),
        )
    }

    @Test fun rejectsCrossCardNearStationaryForTwoHours() {
        val fare = 10.19
        val km = 0.74
        val min = 121
        assertFalse(
            OfferValidator.isPlausible(
                "exclusive", fare, km, min,
                fare / km, fare / (min / 60.0), fare / min,
            ),
        )
    }

    @Test fun keepsLongHighwayRidePossible() {
        val fare = 206.82
        val km = 114.0
        val min = 96
        assertTrue(
            OfferValidator.isPlausible(
                "radar", fare, km, min,
                fare / km, fare / (min / 60.0), fare / min,
            ),
        )
    }
}
