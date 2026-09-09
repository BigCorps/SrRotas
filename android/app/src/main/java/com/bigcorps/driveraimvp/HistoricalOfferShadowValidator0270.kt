package com.srrotas.app

import kotlin.math.abs
import kotlin.math.max

/**
 * Validador SHADOW da 0.27.0-alpha2.
 *
 * A distribuição abaixo veio dos 38.771 registros históricos válidos
 * importados de screenshots reais. Os percentis servem apenas como sinal
 * observacional; nenhum deles bloqueia oferta, muda confidence, verdict ou HUD.
 */
object HistoricalOfferShadowValidator0270 {
    data class Result(
        val tailSignals: Set<String>,
        val consistencySignals: Set<String>,
    ) {
        val allSignals: List<String>
            get() =
                (tailSignals + consistencySignals)
                    .toList()
    }

    fun evaluate(offer: RideOffer): Result {
        val tails =
            linkedSetOf<String>()

        val consistency =
            linkedSetOf<String>()

        // Percentis históricos válidos (P01 / P99).
        if (offer.fare < 6.60) {
            tails += "fare_below_historical_p01"
        }
        if (offer.fare > 98.81) {
            tails += "fare_above_historical_p99"
        }

        offer.pickupKm?.let {
            if (it > 3.60) {
                tails += "pickup_km_above_historical_p99"
            }
        }

        offer.tripKm?.let {
            if (it < 0.80) {
                tails += "trip_km_below_historical_p01"
            }
            if (it > 32.066) {
                tails += "trip_km_above_historical_p99"
            }
        }

        offer.pickupMinutes?.let {
            if (it < 1) {
                tails += "pickup_min_below_historical_p01"
            }
            if (it > 12) {
                tails += "pickup_min_above_historical_p99"
            }
        }

        offer.tripMinutes?.let {
            if (it < 3) {
                tails += "trip_min_below_historical_p01"
            }
            if (it > 57) {
                tails += "trip_min_above_historical_p99"
            }
        }

        // Coerência interna. Também é somente diagnóstico nesta alpha.
        val componentKm =
            if (
                offer.pickupKm != null &&
                offer.tripKm != null
            ) {
                offer.pickupKm + offer.tripKm
            } else {
                null
            }

        if (
            componentKm != null &&
            offer.totalKm != null
        ) {
            val tolerance =
                max(
                    1.0,
                    componentKm * 0.15,
                )

            if (
                abs(
                    offer.totalKm -
                        componentKm,
                ) > tolerance
            ) {
                consistency +=
                    "total_km_component_mismatch"
            }
        }

        val componentMinutes =
            if (
                offer.pickupMinutes != null &&
                offer.tripMinutes != null
            ) {
                offer.pickupMinutes +
                    offer.tripMinutes
            } else {
                null
            }

        if (
            componentMinutes != null &&
            offer.totalMinutes != null
        ) {
            val tolerance =
                max(
                    5.0,
                    componentMinutes * 0.15,
                )

            if (
                abs(
                    offer.totalMinutes -
                        componentMinutes,
                ) > tolerance
            ) {
                consistency +=
                    "total_minutes_component_mismatch"
            }
        }

        val computedPerKm =
            offer.totalKm
                ?.takeIf { it > 0.05 }
                ?.let { offer.fare / it }

        if (
            computedPerKm != null &&
            offer.perKm != null
        ) {
            val tolerance =
                max(
                    0.45,
                    computedPerKm * 0.20,
                )

            if (
                abs(
                    offer.perKm -
                        computedPerKm,
                ) > tolerance
            ) {
                consistency +=
                    "per_km_internal_mismatch"
            }
        }

        return Result(
            tailSignals = tails,
            consistencySignals = consistency,
        )
    }
}
