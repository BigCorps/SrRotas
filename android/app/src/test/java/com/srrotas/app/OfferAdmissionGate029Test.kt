package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferAdmissionGate029Test {
    private fun offer(
        platform: String = "uber",
        captureMethod: String = "media-projection-ocr/uber",
        fare: Double = 10.24,
        pickupKm: Double? = 1.0,
        tripKm: Double? = 3.4,
        pickupMinutes: Int? = 4,
        tripMinutes: Int? = 10,
        context: OfferContext? = OfferContext(
            pickupLabel = "Rua A",
            destinationLabel = "Rua B",
        ),
        rawText: String = "R$ 10,24 Rua A 1,0 km 4 min Rua B 3,4 km 10 min",
    ): RideOffer {
        val totalKm =
            if (pickupKm != null && tripKm != null) pickupKm + tripKm else null
        val totalMinutes =
            if (pickupMinutes != null && tripMinutes != null) pickupMinutes + tripMinutes else null
        return RideOffer(
            journeyId = "journey-029",
            platform = platform,
            observedAt = "2026-09-21T18:00:00Z",
            sourcePackage = "test",
            captureMethod = captureMethod,
            rawText = rawText,
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            totalKm = totalKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            perKm = totalKm?.takeIf { it > 0.0 }?.let { fare / it },
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
            confidence = 0.82,
            offerType = "exclusive",
            context = context,
            dedupeKey = "test-${pickupKm}-${pickupMinutes}-${tripKm}-${tripMinutes}",
        )
    }

    @Test fun normalOfferIsAcceptedImmediately() {
        val w = TailConfirmationWindow029()
        assertEquals(
            TailConfirmationWindow029.Decision.ACCEPT_NORMAL,
            w.decide(offer(), 1_000L),
        )
    }

    @Test fun firstExtremeTailWaitsAndSecondCompatibleObservationConfirms() {
        val w = TailConfirmationWindow029()
        val tail = offer(pickupKm = 11.0, pickupMinutes = 65)
        assertEquals(
            TailConfirmationWindow029.Decision.DEFER_TAIL,
            w.decide(tail, 1_000L),
        )
        assertEquals(
            TailConfirmationWindow029.Decision.ACCEPT_CONFIRMED_TAIL,
            w.decide(tail.copy(dedupeKey = "second"), 2_000L),
        )
    }

    @Test fun decimalJumpAgainstRecentObservationIsRejected() {
        val w = TailConfirmationWindow029()
        val normal = offer(pickupKm = 1.0, pickupMinutes = 4)
        val shifted = offer(
            pickupKm = 11.0,
            pickupMinutes = 4,
            rawText = "R$ 10,24 Rua A 11 km 4 min Rua B 3,4 km 10 min",
        )

        assertEquals(
            TailConfirmationWindow029.Decision.ACCEPT_NORMAL,
            w.decide(normal, 1_000L),
        )
        assertEquals(
            TailConfirmationWindow029.Decision.REJECT_DECIMAL_CONFLICT,
            w.decide(shifted, 1_600L),
        )
    }

    @Test fun lowerObservationCanReplacePreviouslyDeferredDecimalConflict() {
        val w = TailConfirmationWindow029()
        val shifted = offer(pickupKm = 11.0, pickupMinutes = 4)
        val normal = offer(
            pickupKm = 1.0,
            pickupMinutes = 4,
            rawText = "R$ 10,24 Rua A 1,0 km 4 min Rua B 3,4 km 10 min",
        )

        assertEquals(
            TailConfirmationWindow029.Decision.DEFER_TAIL,
            w.decide(shifted, 1_000L),
        )
        assertEquals(
            TailConfirmationWindow029.Decision.ACCEPT_REPLACES_CONFLICT,
            w.decide(normal, 1_700L),
        )
    }

    @Test fun genericTextFallbackWithoutBothRouteLabelsIsNotOfficial() {
        val incomplete = offer(
            platform = "other",
            captureMethod = "media-projection-ocr/other-text-fallback-0265",
            context = OfferContext(pickupLabel = null, destinationLabel = null),
        )
        assertTrue(OfferAdmissionRules029.unsafeGenericFallback(incomplete))

        val complete = incomplete.copy(
            context = OfferContext(
                pickupLabel = "Rua A",
                destinationLabel = "Rua B",
            ),
        )
        assertFalse(OfferAdmissionRules029.unsafeGenericFallback(complete))
    }

    @Test fun confirmationExpires() {
        val w = TailConfirmationWindow029()
        val tail = offer(pickupKm = 11.0, pickupMinutes = 65)
        assertEquals(
            TailConfirmationWindow029.Decision.DEFER_TAIL,
            w.decide(tail, 1_000L),
        )
        assertEquals(
            TailConfirmationWindow029.Decision.DEFER_TAIL,
            w.decide(tail.copy(dedupeKey = "late"), 9_000L),
        )
    }
}
