package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CardStabilizerTest {
    private fun offer(
        fare: Double, pickup: Double?, trip: Double?, total: Double?,
        pickupMin: Int?, tripMin: Int?, advertised: Double?, service: String,
        confidence: Double, rating: Double? = 4.94,
    ) = RideOffer(
        observedAt=Instant.EPOCH.toString(), sourcePackage="fixture", captureMethod="fixture", rawText="fixture",
        fare=fare, pickupKm=pickup, tripKm=trip, totalKm=total, pickupMinutes=pickupMin, tripMinutes=tripMin,
        totalMinutes=listOfNotNull(pickupMin,tripMin).sum().takeIf{it>0},
        perKm=if(total!=null&&total>0) fare/total else null, perHour=null, perMinute=null,
        estimatedCost=null, estimatedProfit=null, profitPerHour=null, profitPercent=null,
        passengerRating=rating, advertisedPerKm=advertised, serviceType=service, verdict="boa",
        confidence=confidence, offerType="exclusive", dedupeKey="x"
    )

    @Test fun keepsBestOfReal3658Sequence(){
        val s=CardStabilizer(750)
        s.submit(listOf(offer(36.00,2.1,8.0,10.1,5,20,null,"electric",.88)),1000)
        s.submit(listOf(offer(36.58,2.1,8.0,10.1,5,20,3.62,"electric",.99)),1323)
        s.submit(listOf(offer(30.00,2.1,8.0,10.1,5,20,null,"electric",.83)),1590)
        val out=s.drainReady(1750)
        assertEquals(1,out.size);assertEquals(36.58,out.single().offer.fare,.001);assertEquals(3,out.single().samples)
    }

    @Test fun keepsBestOfReal1899Sequence(){
        val s=CardStabilizer(750)
        s.submit(listOf(offer(18.90,2.5,2.3,4.8,8,10,null,"electric",.87)),1000)
        s.submit(listOf(offer(18.00,2.5,2.3,4.8,8,10,3.96,"unknown",.96)),1275)
        s.submit(listOf(offer(18.99,2.5,2.3,4.8,8,10,3.96,"electric",.99)),1524)
        assertEquals(18.99,s.drainReady(1750).single().offer.fare,.001)
    }

    @Test fun partial3IsReplacedByFull1473(){
        val s=CardStabilizer(750)
        s.submit(listOf(offer(3.00,null,3.8,3.8,null,10,null,"unknown",.67,4.0)),1000)
        s.submit(listOf(offer(14.73,.6,3.8,4.4,3,16,3.35,"comfort",.99,4.84)),1229)
        assertEquals(14.73,s.drainReady(1750).single().offer.fare,.001)
    }

    @Test fun partial14IsReplacedByFull1577(){
        val s=CardStabilizer(750)
        s.submit(listOf(offer(14.00,null,5.3,5.3,7,14,null,"unknown",.61,null)),1000)
        s.submit(listOf(offer(15.77,1.5,5.3,6.8,6,15,2.32,"electric",.99,4.96)),1234)
        assertEquals(15.77,s.drainReady(1750).single().offer.fare,.001)
    }

    @Test fun sameRadarBatchDoesNotCollapseTwoCards(){
        val s=CardStabilizer(750)
        val a=offer(10.00,1.0,3.0,4.0,4,10,2.50,"comfort",.99)
        val b=offer(12.00,1.0,3.0,4.0,4,10,3.00,"comfort",.99)
        s.submit(listOf(a,b),1000)
        val out=s.drainReady(1750)
        assertEquals(2,out.size);assertTrue(out.map{it.offer.fare}.containsAll(listOf(10.0,12.0)))
    }

    @Test fun singleShortOfferIsNeverDiscarded(){
        val s=CardStabilizer(750)
        s.submit(listOf(offer(9.90,1.0,2.0,3.0,3,7,3.30,"black",.99)),1000)
        assertTrue(s.drainReady(1600).isEmpty())
        assertEquals(9.90,s.drainReady(1750).single().offer.fare,.001)
    }
}
