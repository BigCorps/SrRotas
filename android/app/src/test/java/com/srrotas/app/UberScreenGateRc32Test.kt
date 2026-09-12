package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UberScreenGateRc32Test {
    @Test
    fun categoryPopupWithoutOfferGeometryIsNotAnOffer() {
        assertEquals(
            UberScreenGate.Kind.UNKNOWN,
            UberScreenGate.classify("Comfort\nR$ 18,50"),
        )
    }

    @Test
    fun categoryWithFullFinancialGeometryCanStillBeAnOffer() {
        assertEquals(
            UberScreenGate.Kind.OFFER_CANDIDATE,
            UberScreenGate.classify(
                "Comfort\nR$ 18,50\n5 min 2 km\n12 min 6 km",
            ),
        )
    }

    @Test
    fun explicitAcceptActionStillIdentifiesOffer() {
        assertEquals(
            UberScreenGate.Kind.OFFER_CANDIDATE,
            UberScreenGate.classify("UberX\nR$ 18,50\nAceitar"),
        )
    }
}
