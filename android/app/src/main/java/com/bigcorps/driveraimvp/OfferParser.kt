package com.srrotas.app

import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.round

object OfferParser {
    // Aceita confusões OCR somente dentro de candidatos numéricos (O→0, S→5).
    private val fareRegex = Regex("R\\$\\s*([0-9OSo]{1,5}(?:[.,][0-9OSo]{1,2})?)", RegexOption.IGNORE_CASE)
    private val kmRegex = Regex("([0-9OSo]{1,4}(?:[.,][0-9OSo]{1,2})?)\\s*km\\b", RegexOption.IGNORE_CASE)
    private val minRegex = Regex("([0-9OSo]{1,3})\\s*(?:min|minuto|minutos)\\b", RegexOption.IGNORE_CASE)

    fun parse(
        rawText: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        confidence: Double = 0.66,
        offerType: String = "exclusive",
    ): RideOffer? {
        val normalized = rawText
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .trim()

        if (normalized.length < 8) return null

        val fare = fareRegex.findAll(normalized)
            .mapNotNull { parseNumberCandidate(it.groupValues[1]) }
            .filter { it in 2.0..3000.0 }
            .firstOrNull() ?: return null

        val distances = kmRegex.findAll(normalized)
            .mapNotNull { parseNumberCandidate(it.groupValues[1]) }
            .filter { it in 0.1..1000.0 }
            .toList()

        val minutes = minRegex.findAll(normalized)
            .mapNotNull { parseIntCandidate(it.groupValues[1]) }
            .filter { it in 1..600 }
            .toList()

        val pickupKm: Double?
        val tripKm: Double?
        when {
            distances.size >= 2 -> {
                pickupKm = distances.first()
                tripKm = distances.last()
            }
            distances.size == 1 -> {
                pickupKm = null
                tripKm = distances.first()
            }
            else -> {
                pickupKm = null
                tripKm = null
            }
        }

        val pickupMinutes: Int?
        val tripMinutes: Int?
        when {
            minutes.size >= 2 -> {
                pickupMinutes = minutes.first()
                tripMinutes = minutes.last()
            }
            minutes.size == 1 -> {
                pickupMinutes = null
                tripMinutes = minutes.first()
            }
            else -> {
                pickupMinutes = null
                tripMinutes = null
            }
        }

        val totalKm = listOfNotNull(pickupKm, tripKm).takeIf { it.isNotEmpty() }?.sum()
        val totalMinutes = listOfNotNull(pickupMinutes, tripMinutes).takeIf { it.isNotEmpty() }?.sum()
        if (totalKm == null && totalMinutes == null) return null

        val perKm = totalKm?.takeIf { it > 0.0 }?.let { fare / it }
        val perHour = totalMinutes?.takeIf { it > 0 }?.let { fare / (it / 60.0) }
        val estimatedCost = totalKm?.let { it * settings.costPerKm }
        val estimatedProfit = estimatedCost?.let { fare - it }
        val verdict = verdict(
            fare = fare,
            pickupKm = pickupKm,
            perKm = perKm,
            perHour = perHour,
            estimatedProfit = estimatedProfit,
            settings = settings,
        )

        val dedupeMaterial = listOf(
            fare.round2().toString(),
            pickupKm?.round2()?.toString() ?: "-",
            tripKm?.round2()?.toString() ?: "-",
            totalMinutes?.toString() ?: "-",
            offerType,
        ).joinToString("|")

        return RideOffer(
            observedAt = Instant.now().toString(),
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            rawText = normalized.take(12000),
            fare = fare.round2(),
            pickupKm = pickupKm?.round2(),
            tripKm = tripKm?.round2(),
            totalKm = totalKm?.round2(),
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            perKm = perKm?.round2(),
            perHour = perHour?.round2(),
            estimatedCost = estimatedCost?.round2(),
            estimatedProfit = estimatedProfit?.round2(),
            verdict = verdict,
            confidence = confidence.coerceIn(0.0, 1.0).round2(),
            offerType = offerType,
            dedupeKey = sha256(dedupeMaterial).take(40),
        )
    }

    fun humanSummary(offer: RideOffer): String {
        val label = when (offer.verdict) {
            "boa" -> "EXCELENTE"
            "ruim" -> "ABAIXO DA META"
            else -> "ACEITÁVEL"
        }
        val km = offer.perKm?.let { "R$ ${format(it)}/km" } ?: "R$/km ?"
        val hour = offer.perHour?.let { "R$ ${format(it)}/h" } ?: "R$/h ?"
        val profit = offer.estimatedProfit?.let { "\nLucro est. R$ ${format(it)}" } ?: ""
        return "$label • R$ ${format(offer.fare)}\n$km • $hour$profit"
    }

    private fun verdict(
        fare: Double,
        pickupKm: Double?,
        perKm: Double?,
        perHour: Double?,
        estimatedProfit: Double?,
        settings: DriverSettings,
    ): String {
        if (settings.minFare > 0 && fare < settings.minFare * 0.85) return "ruim"
        if (settings.maxPickupKm > 0 && pickupKm != null && pickupKm > settings.maxPickupKm * 1.25) return "ruim"
        if (settings.minProfit > 0 && estimatedProfit != null && estimatedProfit < settings.minProfit * 0.75) return "ruim"

        val scores = mutableListOf<Double>()
        if (settings.minPerKm > 0 && perKm != null) scores += perKm / settings.minPerKm
        if (settings.minPerHour > 0 && perHour != null) scores += perHour / settings.minPerHour
        if (settings.minFare > 0) scores += fare / settings.minFare
        if (settings.minProfit > 0 && estimatedProfit != null) scores += estimatedProfit / settings.minProfit
        if (settings.maxPickupKm > 0 && pickupKm != null) {
            scores += (settings.maxPickupKm / pickupKm.coerceAtLeast(0.1)).coerceAtMost(1.5)
        }

        if (scores.isEmpty()) return "regular"
        val minScore = scores.minOrNull() ?: 0.0
        val avgScore = scores.average()
        return when {
            minScore >= 1.0 && avgScore >= 1.05 -> "boa"
            minScore >= 0.82 && avgScore >= 0.95 -> "regular"
            else -> "ruim"
        }
    }

    fun parseNumberCandidate(value: String): Double? {
        val candidate = value
            .replace('O', '0').replace('o', '0')
            .replace('S', '5').replace('s', '5')
            .trim()
        if (!candidate.any(Char::isDigit)) return null
        val cleaned = if (candidate.contains(',') && candidate.contains('.')) {
            candidate.replace(".", "").replace(',', '.')
        } else {
            candidate.replace(',', '.')
        }
        return cleaned.toDoubleOrNull()
    }

    private fun parseIntCandidate(value: String): Int? = parseNumberCandidate(value)?.toInt()
    private fun Double.round2(): Double = round(this * 100.0) / 100.0
    private fun format(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
