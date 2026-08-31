package com.srrotas.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitSemantics0241Test {
    @Test
    fun rideProfitCannotExceedFareWithNonNegativeCost() {
        val fare = 23.39
        val totalKm = 2.70
        val costPerKm = 0.82
        val cost = totalKm * costPerKm
        val profit = fare - cost
        assertTrue(profit <= fare)
    }

    @Test
    fun hourlyRateMayBeNumericallyAboveFare() {
        val fare = 23.39
        val profit = 21.18
        val minutes = 10.0
        val hourlyRate = profit / (minutes / 60.0)
        assertTrue(hourlyRate > fare)
    }
}
