package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferAdmissionGate030Test {
    private fun offer(
        localId: String,
        fare: Double = 10.24,
        pickupKm: Double? = 1.0,
        tripKm: Double? = 3.4,
        pickupMinutes: Int? = 4,
        tripMinutes: Int? = 10,
        pickup: String = "Rua A",
        destination: String = "Rua B",
        platform: String = "uber",
        captureMethod: String = "media-projection-ocr/uber",
        rawText: String = "UberX R$ 10,24 4 min (1,0 km) Rua A 10 min (3,4 km) Rua B Aceitar",
    ): RideOffer {
        val totalKm = if (pickupKm != null && tripKm != null) pickupKm + tripKm else tripKm
        val totalMinutes = if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else tripMinutes
        return RideOffer(
            localId = localId,
            journeyId = "journey-030",
            platform = platform,
            observedAt = "2026-09-22T12:00:00Z",
            sourcePackage = "fixture",
            captureMethod = captureMethod,
            rawText = rawText,
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            totalKm = totalKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            perKm = totalKm?.takeIf { it > 0 }?.let { fare / it },
            perHour = totalMinutes?.takeIf { it > 0 }?.let { fare / it * 60.0 },
            perMinute = totalMinutes?.takeIf { it > 0 }?.let { fare / it },
            estimatedCost = null,
            estimatedProfit = null,
            profitPerHour = null,
            profitPercent = null,
            passengerRating = null,
            advertisedPerKm = null,
            serviceType = "uberx",
            verdict = "regular",
            confidence = 0.90,
            offerType = "exclusive",
            context = OfferContext(pickupLabel = pickup, destinationLabel = destination),
            dedupeKey = localId,
        )
    }

    @Test fun identityDoesNotDependOnFareDistanceOrMinutes() {
        val a = offer("a", fare = 6.80, pickupKm = 1.0, pickupMinutes = 4)
        val b = offer("b", fare = 68.0, pickupKm = 11.0, pickupMinutes = 40)
        assertEquals(OfferAdmissionRules030.targetKey(a), OfferAdmissionRules030.targetKey(b))
    }

    @Test fun detectsFareDecimalConflict() {
        val fields = OfferAdmissionRules030.decimalConflictFields(
            offer("a", fare = 6.80),
            offer("b", fare = 68.0),
        )
        assertTrue("fare" in fields)
    }

    @Test fun detectsPickupDecimalConflict() {
        val fields = OfferAdmissionRules030.decimalConflictFields(
            offer("a", pickupKm = 1.0),
            offer("b", pickupKm = 11.0),
        )
        assertTrue("pickup_km" in fields)
    }

    @Test fun firstFareJumpIsRejectedAndRepeatedJumpCanBeConfirmed() {
        val w = AdmissionWindow030()
        val normal = offer("a", fare = 6.80)
        val shifted1 = offer("b", fare = 68.0)
        val shifted2 = offer("c", fare = 68.0)

        assertEquals(AdmissionWindow030.DecisionKind.ACCEPT_NORMAL, w.decide(normal, 1_000L).kind)
        val first = w.decide(shifted1, 1_500L)
        assertEquals(AdmissionWindow030.DecisionKind.REJECT_DECIMAL_CONFLICT, first.kind)
        assertTrue("fare" in first.conflictFields)
        val second = w.decide(shifted2, 2_000L)
        assertEquals(AdmissionWindow030.DecisionKind.ACCEPT_CONFIRMED_DECIMAL_CHANGE, second.kind)
    }

    @Test fun onePointZeroToElevenPickupDoesNotPassFirstConflict() {
        val w = AdmissionWindow030()
        val normal = offer("a", pickupKm = 1.0)
        val shifted = offer("b", pickupKm = 11.0)
        assertEquals(AdmissionWindow030.DecisionKind.ACCEPT_NORMAL, w.decide(normal, 1_000L).kind)
        val result = w.decide(shifted, 1_600L)
        assertEquals(AdmissionWindow030.DecisionKind.REJECT_DECIMAL_CONFLICT, result.kind)
        assertTrue("pickup_km" in result.conflictFields)
    }

    @Test fun differentRouteIsNotTreatedAsSameOffer() {
        val a = offer("a", fare = 6.80, pickup = "Rua A", destination = "Rua B")
        val b = offer("b", fare = 68.0, pickup = "Rua X", destination = "Rua Y")
        assertFalse(OfferAdmissionRules030.likelySameOffer(a, b))
    }

    @Test fun extremeTailNeedsSecondCompatibleObservation() {
        val w = AdmissionWindow030()
        val first = offer("a", pickupKm = 11.0, pickupMinutes = 65)
        val second = offer("b", pickupKm = 11.0, pickupMinutes = 65)
        assertEquals(AdmissionWindow030.DecisionKind.DEFER_TAIL, w.decide(first, 1_000L).kind)
        assertEquals(AdmissionWindow030.DecisionKind.ACCEPT_CONFIRMED_TAIL, w.decide(second, 2_000L).kind)
    }

    @Test fun genericOtherFallbackWithoutRouteStillRejected() {
        val bad = offer(
            localId = "a",
            platform = "other",
            captureMethod = "media-projection-ocr/other-text-fallback-0265",
            pickup = "",
            destination = "",
        )
        assertTrue(OfferAdmissionRules030.unsafeGenericFallback(bad))
        assertEquals(
            AdmissionWindow030.DecisionKind.REJECT_GENERIC_FALLBACK,
            AdmissionWindow030().decide(bad, 1_000L).kind,
        )
    }
}
