package com.srrotas.app

/** Anonymized vc108 fixtures (no real addresses, screenshots or identifiers). */
internal object Fixtures108 {
    fun offer(
        localId: String,
        fare: Double,
        observedAt: String = "2026-10-29T12:00:00Z",
        journeyId: String? = "journey-108",
        context: OfferContext? = null,
        serviceType: String = "uberx",
    ) = RideOffer(
        localId = localId, journeyId = journeyId, observedAt = observedAt,
        sourcePackage = AppSignals.UBER_PACKAGE, captureMethod = "mediaprojection", rawText = "fixture",
        fare = fare, pickupKm = 0.8, tripKm = 6.0, totalKm = 6.8, pickupMinutes = 3, tripMinutes = 15, totalMinutes = 18,
        perKm = fare / 6.8, perHour = fare / 18 * 60, perMinute = fare / 18, estimatedCost = 5.0, estimatedProfit = fare - 5.0,
        profitPerHour = null, profitPercent = null, passengerRating = null, advertisedPerKm = null,
        serviceType = serviceType, verdict = "boa", confidence = 0.95, dedupeKey = "dedupe-$localId", context = context,
    )
}
