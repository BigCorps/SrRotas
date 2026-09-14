package com.srrotas.app

import kotlin.math.abs
import kotlin.math.max

/**
 * RC3.3 — gate de integridade antes de HUD/persistência financeira.
 *
 * Não recalcula verdict nem altera fórmulas. Apenas impede que uma oferta com
 * uma das pernas ausente seja tratada como financeiramente completa.
 */
object OfferIntegrityGate027033 {
    data class Assessment(
        val ready: Boolean,
        val flags: List<String>,
    )

    private const val KM_ABSOLUTE_TOLERANCE = 0.20
    private const val KM_RELATIVE_TOLERANCE = 0.04
    private const val MINUTE_TOLERANCE = 1

    fun assess(offer: RideOffer): Assessment {
        val flags = linkedSetOf<String>()

        if (offer.fare <= 0.0) flags += "missing_fare"
        if (!positive(offer.pickupKm)) flags += "missing_pickup_km"
        if (!positive(offer.tripKm)) flags += "missing_trip_km"
        if (!positive(offer.pickupMinutes)) flags += "missing_pickup_minutes"
        if (!positive(offer.tripMinutes)) flags += "missing_trip_minutes"

        val pickupKm = offer.pickupKm
        val tripKm = offer.tripKm
        val totalKm = offer.totalKm
        if (positive(pickupKm) && positive(tripKm)) {
            val expected = pickupKm!! + tripKm!!
            if (totalKm == null || !closeEnough(totalKm, expected)) {
                flags += "geometry_km_conflict"
            }
        }

        val pickupMinutes = offer.pickupMinutes
        val tripMinutes = offer.tripMinutes
        val totalMinutes = offer.totalMinutes
        if (positive(pickupMinutes) && positive(tripMinutes)) {
            val expected = pickupMinutes!! + tripMinutes!!
            if (totalMinutes == null || abs(totalMinutes - expected) > MINUTE_TOLERANCE) {
                flags += "geometry_minutes_conflict"
            }
        }

        // Se o parser marcou total sem as duas pernas, o total não prova completude.
        if ((!positive(pickupKm) || !positive(tripKm)) && totalKm != null) {
            flags += "partial_leg_total_km"
        }
        if ((!positive(pickupMinutes) || !positive(tripMinutes)) && totalMinutes != null) {
            flags += "partial_leg_total_minutes"
        }

        return Assessment(flags.isEmpty(), flags.toList())
    }

    fun readyOffers(offers: List<RideOffer>): List<RideOffer> =
        offers.filter { assess(it).ready }

    private fun positive(value: Double?): Boolean = value != null && value > 0.0
    private fun positive(value: Int?): Boolean = value != null && value > 0

    private fun closeEnough(actual: Double, expected: Double): Boolean =
        abs(actual - expected) <= max(KM_ABSOLUTE_TOLERANCE, abs(expected) * KM_RELATIVE_TOLERANCE)
}
