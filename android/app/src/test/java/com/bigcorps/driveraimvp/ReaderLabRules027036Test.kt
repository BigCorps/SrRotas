package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLabRules027036Test {
    private fun c(
        fare: Double = 25.0,
        pickupKm: Double? = 1.2,
        tripKm: Double? = 8.4,
        totalKm: Double? = 9.6,
        pickupMinutes: Int? = 4,
        totalMinutes: Int? = 26,
        pickup: String? = "Av Paulista",
        destination: String? = "Moema",
        at: Long = 100_000L,
    ) = ReaderLabRules027036.Candidate("uber", fare, pickupKm, tripKm, totalKm, pickupMinutes, totalMinutes, pickup, destination, at)

    @Test fun sameOfferToleratesSmallReaderDifferences() {
        assertTrue(ReaderLabRules027036.sameOffer(c(), c(pickupKm=1.3,totalKm=9.8,pickupMinutes=5,at=108_000L)))
    }

    @Test fun differentFareIsNotMatched() {
        assertFalse(ReaderLabRules027036.sameOffer(c(), c(fare=31.0,at=105_000L)))
    }

    @Test fun coreRequiresFiveStrategicFields() {
        val full=ReaderLabRules027036.completeness(c())
        assertEquals(5, full.score)
        assertTrue(full.complete)
        val partial=ReaderLabRules027036.completeness(c(destination=null,totalMinutes=null))
        assertEquals(3, partial.score)
        assertFalse(partial.complete)
    }
}
