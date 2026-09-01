package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferContextQuality0242Test {
    @Test
    fun blocksUiNoise() {
        assertFalse(
            OfferContextQuality0242.canGeocode(
                "Fique online até",
            ),
        )
        assertFalse(
            OfferContextQuality0242.canGeocode(
                "Caixa de entrada",
            ),
        )
        assertFalse(
            OfferContextQuality0242.canGeocode(
                "12:32 seg., 31 de ago.",
            ),
        )
    }

    @Test
    fun acceptsNormalAddress() {
        assertTrue(
            OfferContextQuality0242.canGeocode(
                "Avenida Paulista, 2300, São Paulo",
            ),
        )
    }

    @Test
    fun farWeakSideIsRejected() {
        assertFalse(
            OfferContextQuality0242.keepPairSide(
                "R. Pe. Chico",
                "R. Palestra Itália, Perdizes, São Paulo",
                500.0,
            ),
        )
    }
}
