package com.bigcorps.driveraimvp

import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.round

object OfferParser {
    private val fareRegex = Regex("R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val kmRegex = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*km\\b", RegexOption.IGNORE_CASE)
    private val minRegex = Regex("([0-9]{1,3})\\s*(?:min|minuto|minutos)\\b", RegexOption.IGNORE_CASE)

    fun parse(
        rawText: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
    ): RideOffer? {
        val normalized = rawText
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .trim()

        if (normalized.length < 8) return null

        val fare = fareRegex.findAll(normalized)
            .mapNotNull { parseNumber(it.groupValues[1]) }
            .filter { it in 2.0..2000.0 }
            .firstOrNull() ?: return null

        val distances = kmRegex.findAll(normalized)
            .mapNotNull { parseNumber(it.groupValues[1]) }
            .filter { it in 0.1..1000.0 }
            .toList()

        val minutes = minRegex.findAll(normalized)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..600 }
            .toList()

        // Layouts do Uber costumam trazer distância/tempo até o embarque e da viagem.
        // Mantemos heurística simples e deixamos o texto bruto disponível para calibração.
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

        // Para ser considerado uma oferta útil precisamos de pelo menos valor + uma métrica de percurso.
        if (totalKm == null && totalMinutes == null) return null

        val perKm = totalKm?.takeIf { it > 0.0 }?.let { fare / it }
        val perHour = totalMinutes?.takeIf { it > 0 }?.let { fare / (it / 60.0) }
        val estimatedCost = totalKm?.let { it * settings.costPerKm }
        val estimatedProfit = estimatedCost?.let { fare - it }
        val verdict = verdict(perKm, perHour, settings)

        val dedupeMaterial = listOf(
            fare.round2().toString(),
            totalKm?.round2()?.toString() ?: "-",
            totalMinutes?.toString() ?: "-",
            normalized.take(320),
        ).joinToString("|")

        return RideOffer(
            observedAt = Instant.now().toString(),
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            rawText = normalized.take(6000),
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
            dedupeKey = sha256(dedupeMaterial).take(40),
        )
    }

    fun humanSummary(offer: RideOffer): String {
        val km = offer.perKm?.let { "R$ ${format(it)}/km" } ?: "R$/km ?"
        val hour = offer.perHour?.let { "R$ ${format(it)}/h" } ?: "R$/h ?"
        val profit = offer.estimatedProfit?.let { " • lucro est. R$ ${format(it)}" } ?: ""
        return "${offer.verdict.uppercase(Locale.ROOT)} • R$ ${format(offer.fare)} • $km • $hour$profit"
    }

    private fun verdict(perKm: Double?, perHour: Double?, settings: DriverSettings): String {
        val kmScore = perKm?.let { it / settings.minPerKm }
        val hourScore = perHour?.let { it / settings.minPerHour }
        val scores = listOfNotNull(kmScore, hourScore)
        if (scores.isEmpty()) return "regular"
        val minScore = scores.minOrNull() ?: 0.0
        val maxScore = scores.maxOrNull() ?: 0.0
        return when {
            minScore >= 1.0 -> "boa"
            maxScore >= 1.0 || minScore >= 0.85 -> "regular"
            else -> "ruim"
        }
    }

    private fun parseNumber(value: String): Double? {
        val cleaned = if (value.contains(',') && value.contains('.')) {
            value.replace(".", "").replace(',', '.')
        } else {
            value.replace(',', '.')
        }
        return cleaned.toDoubleOrNull()
    }

    private fun Double.round2(): Double = round(this * 100.0) / 100.0
    private fun format(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
