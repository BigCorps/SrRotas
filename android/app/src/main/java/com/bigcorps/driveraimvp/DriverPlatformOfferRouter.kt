package com.srrotas.app

import com.google.mlkit.vision.text.Text
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Roteador multiplataforma com isolamento espacial por card/painel. */
object DriverPlatformOfferRouter {
    data class RoutedResult(
        val platform: String?,
        val offers: List<RideOffer>,
        val candidate: Boolean,
        val ownApp: Boolean = false,
        val reason: String = "contexto desconhecido",
    )

    fun parse(
        result: Text,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): RoutedResult {
        val raw = result.text
        val lower = DriverOcrNormalizer.sanitize(raw).lowercase()
        if (lower.isBlank()) return RoutedResult(null, emptyList(), false, reason = "ocr vazio")

        // Não bloqueia o OCR inteiro só porque o Sr. Rotas está visível em
        // outra janela do tablet. Primeiro procuramos cards reais de motorista.
        if (looksLike99(lower)) {
            val offers = FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (offers.isNotEmpty()) {
                return RoutedResult("99", offers, true, reason = "candidato 99")
            }
        }

        val uberOffers = UberSpatialParser0221.parse(
            result = result,
            settings = settings,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        if (uberOffers.isNotEmpty()) {
            return RoutedResult("uber", uberOffers, true, reason = "candidato Uber isolado")
        }

        // 0.26.1: se o frame traz âncora inequívoca da Uber, mas ainda não tem
        // geometria suficiente para fechar a oferta, aguardamos outro frame.
        // Antes, esse mesmo quadro podia cair no fallback genérico e ser salvo
        // como `other`, criando duplicata e combinações cruzadas no histórico.
        val uberAnchored = OfferSpatialIsolation0221.hasUberOfferAnchor(raw)
        if (uberAnchored) {
            return RoutedResult(
                platform = "uber",
                offers = emptyList(),
                candidate = true,
                reason = "candidato Uber aguardando frame completo",
            )
        }

        val inferred = inferGenericPlatform(lower)
        if (FlexibleDriverOfferParser.looksLikeCandidate(raw)) {
            val offers = FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = inferred,
                sourcePackage = AppSignals.inferredPackage(inferred),
                captureMethod = "media-projection-ocr/$inferred",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (offers.isNotEmpty()) {
                return RoutedResult(
                    inferred,
                    offers,
                    true,
                    reason = "candidato genérico/$inferred",
                )
            }
        }

        val gate = UberScreenGate.classify(raw)
        if (gate == UberScreenGate.Kind.OWN_APP) {
            return RoutedResult(
                null,
                emptyList(),
                false,
                ownApp = true,
                reason = "interface Sr. Rotas",
            )
        }

        val candidate =
            looksLike99(lower) || FlexibleDriverOfferParser.looksLikeCandidate(raw)
        val reason = when (gate) {
            UberScreenGate.Kind.IDLE_OR_HOME -> "home/ocioso"
            UberScreenGate.Kind.FOREIGN_UI -> "outra interface"
            else -> if (
                OfferSpatialIsolation0221.navigationNoise(
                    OfferSpatialIsolation0221.lines(result),
                )
            ) {
                "tela dividida sem card isolado"
            } else {
                "contexto desconhecido"
            }
        }
        return RoutedResult(null, emptyList(), candidate, reason = reason)
    }

    private fun looksLike99(lower: String): Boolean {
        val strong = listOf(
            "perfil essencial",
            "plus nova",
            "99pop",
            "99 pop",
            "99plus",
            "99 plus",
            "99moto",
            "99 moto",
            "99táxi",
            "99taxi",
            "99electric",
            "99 entrega",
        ).any(lower::contains)
        if (strong) return true

        val action = lower.contains("escolher")
        val rideContext =
            lower.contains("corridas") ||
                lower.contains("solicitações") ||
                lower.contains("solicitacoes")
        val metrics =
            lower.contains("/km") && FlexibleDriverOfferParser.geometryCount(lower) >= 2
        return action && (rideContext || metrics)
    }

    private fun inferGenericPlatform(lower: String): String = when {
        lower.contains("indrive") || lower.contains("in drive") -> "indrive"
        lower.contains("maxim") -> "maxim"
        else -> "other"
    }
}

object DriverOcrNormalizer {
    fun sanitize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .lines()
        .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()
}

/** Extrator financeiro para 99 e fallback genérico. */
object FlexibleDriverOfferParser {
    private val moneyRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val advertisedRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*/\\s*km",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Aceita, entre outros:
     * 8 min (0,7 km)
     * (8 min 591 m)
     * 8 min · 591 m
     * 20 min 2 km
     */
    internal val geometryRegex = Regex(
        "(?:\\(\\s*)?([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\s*(?:[·•\\-–—]?\\s*)?(?:\\(\\s*)?([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\s*\\)?",
        RegexOption.IGNORE_CASE,
    )

    private val rating99Regex = Regex(
        "\\b([45](?:[.,][0-9]{1,2})?)\\s*[·•]\\s*[0-9]{1,6}\\s*(?:corridas?|viagens?)",
        RegexOption.IGNORE_CASE,
    )
    private val ratingClassicRegex = Regex(
        "\\b([45](?:[.,][0-9]{1,2})?)\\s*\\(\\s*[0-9]{1,6}\\s*\\)",
        RegexOption.IGNORE_CASE,
    )

    data class TimeDistance(val minutes: Int, val km: Double)

    fun looksLikeCandidate(rawText: String): Boolean {
        val text = DriverOcrNormalizer.sanitize(rawText)
        if (primaryFare(text) == null) return false
        if (geometryCount(text) < 2) return false
        val lower = text.lowercase()
        return listOf(
            "aceitar", "escolher", "selecionar", "pegar",
            "corrida", "corridas", "viagem", "viagens", "oferta", "solicitação", "solicitacao",
        ).any(lower::contains) || advertisedRegex.containsMatchIn(text)
    }

    internal fun geometryCount(rawText: String): Int =
        geometryRegex.findAll(DriverOcrNormalizer.sanitize(rawText)).count()

    fun parseSpatial(
        result: Text,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val lines = OfferSpatialIsolation0221.lines(result)
        if (lines.isEmpty()) return emptyList()
        val fareLines = lines.filter { primaryFare(it.text) != null }
        if (fareLines.isEmpty()) return emptyList()

        val strict = OfferSpatialIsolation0221.navigationNoise(lines)
        return fareLines.sortedBy { it.box.centerY() }.mapNotNull { fareLine ->
            val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                lines = lines,
                fareLine = fareLine,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (cluster.isEmpty()) return@mapNotNull null
            val text = cluster.joinToString("\n") { it.text }
            if (!clusterCandidate(platform, text, strict)) return@mapNotNull null

            parseText(
                rawText = text,
                platform = platform,
                sourcePackage = sourcePackage,
                captureMethod = captureMethod,
                settings = settings,
            )?.let { OfferContextExtractor0221.attach(it, cluster) }
        }.distinctBy(OfferDeduplicator::semanticKey)
    }

    private fun clusterCandidate(platform: String, text: String, strict: Boolean): Boolean {
        if (geometryCount(text) < 2 || primaryFare(text) == null) return false
        if (platform == "99") {
            val base = OfferSpatialIsolation0221.has99OfferAnchor(text)
            return base && (!strict || text.contains("escolher", ignoreCase = true))
        }
        val lower = text.lowercase()
        val action = listOf("aceitar", "escolher", "selecionar", "pegar").any(lower::contains)
        val platformAnchor = when (platform) {
            "indrive" -> lower.contains("indrive") || lower.contains("in drive")
            "maxim" -> lower.contains("maxim")
            else -> false
        }
        return action || platformAnchor
    }

    fun parseText(
        rawText: String,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
    ): RideOffer? {
        val text = DriverOcrNormalizer.sanitize(rawText)
        val fare = primaryFare(text) ?: return null
        val pairs = geometryPairs(text)
        if (pairs.size < 2) return null

        val pickup = pairs[0]
        val trip = pairs[1]
        val totalKm = pickup.km + trip.km
        val totalMinutes = pickup.minutes + trip.minutes
        if (totalKm <= 0.0 || totalMinutes <= 0) return null

        val perKm = fare / totalKm
        val perHour = fare / (totalMinutes / 60.0)
        val perMinute = fare / totalMinutes
        if (!OfferValidator.isPlausible("exclusive", fare, totalKm, totalMinutes, perKm, perHour, perMinute)) {
            return null
        }

        val advertised = advertisedRegex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        if (advertised != null && advertised > 0.0) {
            val delta = abs(advertised - perKm) / advertised
            if (delta > 0.25) return null
        }

        val rating = rating99Regex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
            ?: ratingClassicRegex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        val service = serviceType(text, platform)

        val estimatedCost = totalKm * settings.costPerKm
        val estimatedProfit = fare - estimatedCost
        val profitPerHour = estimatedProfit / (totalMinutes / 60.0)
        val profitPercent = if (fare > 0.0) estimatedProfit / fare * 100.0 else null

        var confidence = if (platform == "99") 0.84 else 0.74
        if (advertised != null) confidence += 0.05
        if (rating != null) confidence += 0.03
        if (service != "unknown") confidence += 0.03

        val observed = Instant.now()
        val bucket = observed.epochSecond / 120L
        val dedupe = sha256(
            listOf(
                platform,
                round2(fare),
                round2(pickup.km),
                round2(trip.km),
                round2(totalKm),
                totalMinutes,
                bucket,
            ).joinToString("|"),
        ).take(40)

        return RideOffer(
            observedAt = observed.toString(),
            platform = platform,
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            rawText = text.take(12000),
            fare = round2(fare),
            pickupKm = round2(pickup.km),
            tripKm = round2(trip.km),
            totalKm = round2(totalKm),
            pickupMinutes = pickup.minutes,
            tripMinutes = trip.minutes,
            totalMinutes = totalMinutes,
            perKm = round2(perKm),
            perHour = round2(perHour),
            perMinute = round2(perMinute),
            estimatedCost = round2(estimatedCost),
            estimatedProfit = round2(estimatedProfit),
            profitPerHour = round2(profitPerHour),
            profitPercent = profitPercent?.let(::round2),
            passengerRating = rating?.let(::round2),
            advertisedPerKm = advertised?.let(::round2),
            serviceType = service,
            verdict = "regular",
            confidence = confidence.coerceAtMost(0.97),
            offerType = "exclusive",
            parserVersion = "sr-rotas-multi-v0.22.1",
            dedupeKey = dedupe,
        )
    }

    internal fun primaryFare(text: String): Double? {
        val normalized = DriverOcrNormalizer.sanitize(text)
        for (match in moneyRegex.findAll(normalized)) {
            val start = match.range.first
            val before = normalized.substring(maxOf(0, start - 2), start).trim()
            if (before.endsWith("+")) continue
            val afterStart = match.range.last + 1
            val after = normalized.substring(afterStart, minOf(normalized.length, afterStart + 10))
            if (Regex("^\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(after)) continue
            val value = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: continue
            if (value in 2.0..1000.0) return value
        }
        return null
    }

    private fun geometryPairs(text: String): List<TimeDistance> =
        geometryRegex.findAll(text).mapNotNull { match ->
            val minutes = OfferParser.parseNumberCandidate(match.groupValues[1])?.toInt() ?: return@mapNotNull null
            val rawDistance = OfferParser.parseNumberCandidate(match.groupValues[2]) ?: return@mapNotNull null
            val unit = match.groupValues[3].lowercase()
            val km = if (unit == "m") rawDistance / 1000.0 else rawDistance
            if (minutes !in 1..360 || km !in 0.05..500.0) null else TimeDistance(minutes, km)
        }.toList()

    private fun serviceType(text: String, platform: String): String {
        val lower = text.lowercase()
        if (platform == "99") {
            return when {
                lower.contains("plus nova") || lower.contains("99plus") || lower.contains("99 plus") -> "99plus"
                lower.contains("99pop") || lower.contains("99 pop") -> "99pop"
                lower.contains("99moto") || lower.contains("99 moto") -> "99moto"
                lower.contains("99táxi") || lower.contains("99taxi") -> "99taxi"
                lower.contains("99electric") -> "99electric"
                lower.contains("99 entrega") || lower.contains("99entrega") -> "99entrega"
                else -> "99"
            }
        }
        return when {
            lower.contains("indrive") || lower.contains("in drive") -> "indrive"
            lower.contains("maxim") -> "maxim"
            else -> "unknown"
        }
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

