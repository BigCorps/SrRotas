package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test

class RadarDestinationContextV1Test {
    @Test fun fromOfferRequiresDestinationCoordinatesAndEta() {
        val c=OfferContext(
            destinationLabel="Paulista",
            destinationLat=-23.56,
            destinationLng=-46.65,
            estimatedArrivalAt="2026-10-01T22:00:00Z"
        )
        val offer=RideOffer(
            observedAt="2026-10-01T21:20:00Z",sourcePackage="x",captureMethod="test",
            rawText="",fare=20.0,pickupKm=1.0,tripKm=5.0,totalKm=6.0,pickupMinutes=4,
            tripMinutes=15,totalMinutes=19,perKm=3.3,perHour=63.0,perMinute=1.05,
            estimatedCost=5.0,estimatedProfit=15.0,profitPerHour=47.0,profitPercent=75.0,
            passengerRating=null,advertisedPerKm=null,verdict="good",context=c,dedupeKey="x"
        )
        assertNotNull(RadarDestinationContextV1.fromOffer(offer))
    }
}
