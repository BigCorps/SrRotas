package com.srrotas.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class OfferDeduplicatorTest {
    @Before fun reset(){ OfferDeduplicator.reset() }

    private fun offer(
        service:String="black",
        type:String="radar",
        minutes:Int=24,
        rating:Double?=4.65,
        advertised:Double?=4.16,
    )=RideOffer(
        observedAt=Instant.EPOCH.toString(),sourcePackage="fixture",captureMethod="fixture",rawText="fixture",
        fare=20.37,pickupKm=1.1,tripKm=3.8,totalKm=4.9,pickupMinutes=5,tripMinutes=minutes-5,totalMinutes=minutes,
        perKm=4.16,perHour=50.92,perMinute=0.85,estimatedCost=4.17,estimatedProfit=16.20,profitPerHour=40.5,profitPercent=79.5,
        passengerRating=rating,advertisedPerKm=advertised,serviceType=service,verdict="ruim",confidence=.9,offerType=type,dedupeKey="server-key"
    )

    @Test fun ignoresAlternatingClassificationOfSameCard(){
        assertTrue(OfferDeduplicator.shouldEmit(offer(service="unknown"),1_000L))
        assertFalse(OfferDeduplicator.shouldEmit(offer(service="comfort"),6_000L))
        assertFalse(OfferDeduplicator.shouldEmit(offer(service="black",type="exclusive"),10_000L))
    }

    @Test fun ignoresRatingAndAdvertisedMetricFlicker(){
        assertTrue(OfferDeduplicator.shouldEmit(offer(rating=4.84,advertised=5.71),1_000L))
        assertFalse(OfferDeduplicator.shouldEmit(offer(rating=null,advertised=5.70),8_000L))
    }

    @Test fun allowsSameCardAfterWindow(){
        assertTrue(OfferDeduplicator.shouldEmit(offer(),1_000L))
        assertTrue(OfferDeduplicator.shouldEmit(offer(),62_000L))
    }
}
