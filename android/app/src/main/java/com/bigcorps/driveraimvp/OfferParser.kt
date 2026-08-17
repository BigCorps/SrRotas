package com.srrotas.app

import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object OfferParser {
    fun parse(
        rawText: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        confidence: Double = 0.66,
        offerType: String = "exclusive",
    ): RideOffer? {
        val normalized = rawText.replace('\u00A0', ' ').replace(Regex("[ \\t]+"), " ").trim()
        val detected = UberOfferDetector.detect(normalized, offerType) ?: return null
        val fare = detected.fare

        var pickupKm: Double? = null
        var tripKm: Double? = null
        var pickupMinutes: Int? = null
        var tripMinutes: Int? = null
        if (detected.pairs.size >= 2) {
            pickupMinutes = detected.pairs[0].minutes
            pickupKm = detected.pairs[0].km
            tripMinutes = detected.pairs[1].minutes
            tripKm = detected.pairs[1].km
        } else if (detected.pairs.size == 1) {
            tripMinutes = detected.pairs[0].minutes
            tripKm = detected.pairs[0].km
        } else {
            val (distances, minutes) = UberOfferDetector.fallbackDistancesAndMinutes(normalized)
            when {
                distances.size >= 2 -> { pickupKm = distances[0]; tripKm = distances[1] }
                distances.size == 1 -> tripKm = distances[0]
            }
            when {
                minutes.size >= 2 -> { pickupMinutes = minutes[0]; tripMinutes = minutes[1] }
                minutes.size == 1 -> tripMinutes = minutes[0]
            }
        }

        val totalKm = listOfNotNull(pickupKm, tripKm).takeIf { it.isNotEmpty() }?.sum()
        val totalMinutes = listOfNotNull(pickupMinutes, tripMinutes).takeIf { it.isNotEmpty() }?.sum()
        if (totalKm == null || totalMinutes == null || totalKm <= 0.0 || totalMinutes <= 0) return null

        val perKm = fare / totalKm
        val perHour = fare / (totalMinutes / 60.0)
        val perMinute = fare / totalMinutes

        if (!OfferValidator.isPlausible(detected.offerType, fare, totalKm, totalMinutes, perKm, perHour, perMinute)) {
            return null
        }

        val advertised = detected.advertisedPerKm
        var adjustedConfidence = (confidence * 0.45 + detected.confidence * 0.55)
        if (advertised != null && advertised > 0.0) {
            val delta = abs(advertised - perKm) / advertised
            when {
                delta <= 0.08 -> adjustedConfidence += 0.07
                delta <= 0.20 -> adjustedConfidence += 0.02
                delta > 0.40 -> return null
                else -> adjustedConfidence -= 0.08
            }
        }

        val estimatedCost = totalKm * settings.costPerKm
        val estimatedProfit = fare - estimatedCost
        val profitPerHour = estimatedProfit / (totalMinutes / 60.0)
        val profitPercent = if (fare > 0) estimatedProfit / fare * 100.0 else null
        val verdict = verdict(
            fare, pickupKm, perKm, perHour, perMinute, detected.passengerRating,
            estimatedProfit, profitPerHour, profitPercent, settings,
        )

        val observed = Instant.now()
        // A chave do backend é idempotente por uma janela de 2 minutos, em vez
        // de impedir para sempre uma oferta legitimamente idêntica no futuro.
        val occurrenceBucket = observed.epochSecond / 120L
        val dedupeMaterial = listOf(
            fare.round2(), pickupKm?.round2(), tripKm?.round2(), totalKm.round2(),
            detected.passengerRating?.round2(), advertised?.round2(), occurrenceBucket,
        ).joinToString("|")

        return RideOffer(
            observedAt = observed.toString(),
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            rawText = normalized.take(12000),
            fare = fare.round2(),
            pickupKm = pickupKm?.round2(),
            tripKm = tripKm?.round2(),
            totalKm = totalKm.round2(),
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            perKm = perKm.round2(),
            perHour = perHour.round2(),
            perMinute = perMinute.round2(),
            estimatedCost = estimatedCost.round2(),
            estimatedProfit = estimatedProfit.round2(),
            profitPerHour = profitPerHour.round2(),
            profitPercent = profitPercent?.round2(),
            passengerRating = detected.passengerRating?.round2(),
            advertisedPerKm = advertised?.round2(),
            serviceType = detected.serviceType,
            verdict = verdict,
            confidence = adjustedConfidence.coerceIn(0.0, 0.99).round2(),
            offerType = detected.offerType,
            dedupeKey = sha256(dedupeMaterial).take(40),
        )
    }

    fun humanSummary(offer: RideOffer): String {
        val label = when (offer.verdict) { "boa" -> "BOA"; "ruim" -> "ABAIXO DA META"; else -> "ATENÇÃO" }
        val minute = offer.perMinute?.let { "R$ ${format(it)}/min" } ?: "R$/min ?"
        val km = offer.perKm?.let { "R$ ${format(it)}/km" } ?: "R$/km ?"
        val hour = offer.perHour?.let { "R$ ${format(it)}/h" } ?: "R$/h ?"
        val rating = offer.passengerRating?.let { " • ★ ${format(it)}" } ?: ""
        val profit = offer.estimatedProfit?.let { "\nLucro est. R$ ${format(it)}" } ?: ""
        return "$label • R$ ${format(offer.fare)}\n$minute • $km\n$hour$rating$profit"
    }

    private fun verdict(
        fare: Double,
        pickupKm: Double?,
        perKm: Double,
        perHour: Double,
        perMinute: Double,
        rating: Double?,
        profit: Double,
        profitPerHour: Double,
        profitPercent: Double?,
        settings: DriverSettings,
    ): String {
        if (settings.minFare > 0 && fare < settings.minFare) return "ruim"
        if (settings.maxPickupKm > 0 && pickupKm != null && pickupKm > settings.maxPickupKm) return "ruim"
        if (settings.minProfit > 0 && profit < settings.minProfit) return "ruim"

        val grades = mutableListOf<Int>()
        grades += gradeHigher(perKm, settings.redPerKmBelow, settings.minPerKm)
        grades += gradeHigher(perHour, settings.redPerHourBelow, settings.minPerHour)
        grades += gradeHigher(perMinute, settings.redPerMinuteBelow, settings.minPerMinute)
        if (rating != null) grades += gradeHigher(rating, settings.redRatingBelow, settings.goodRatingFrom)
        if (settings.minProfitPerHour > 0 || settings.redProfitPerHourBelow > 0) {
            grades += gradeHigher(profitPerHour, settings.redProfitPerHourBelow, settings.minProfitPerHour)
        }
        if (profitPercent != null && (settings.minProfitPercent > 0 || settings.redProfitPercentBelow > 0)) {
            grades += gradeHigher(profitPercent, settings.redProfitPercentBelow, settings.minProfitPercent)
        }
        val active = grades.filter { it >= 0 }
        if (active.isEmpty()) return "regular"
        if (active.any { it == 0 }) return "ruim"
        return if (active.count { it == 2 } >= (active.size + 1) / 2) "boa" else "regular"
    }

    fun gradeHigher(value: Double?, redBelow: Double, goodFrom: Double): Int {
        if (value == null || (redBelow <= 0 && goodFrom <= 0)) return -1
        if (redBelow > 0 && value < redBelow) return 0
        if (goodFrom > 0 && value >= goodFrom) return 2
        return 1
    }

    fun parseNumberCandidate(value: String): Double? {
        val candidate = value.replace('O', '0').replace('o', '0').replace('S', '5').replace('s', '5').trim()
        if (!candidate.any(Char::isDigit)) return null
        val cleaned = if (candidate.contains(',') && candidate.contains('.')) {
            candidate.replace(".", "").replace(',', '.')
        } else {
            candidate.replace(',', '.')
        }
        return cleaned.toDoubleOrNull()
    }

    private fun Double.round2() = round(this * 100.0) / 100.0
    private fun format(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
