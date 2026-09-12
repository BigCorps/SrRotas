package com.srrotas.app

import com.google.mlkit.vision.text.Text
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs
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

        // RC3: Uber e 99 são avaliadas no MESMO frame. Isso é essencial para
        // o modo tela inteira/split-screen: um card válido de uma plataforma não
        // pode impedir a outra de ser analisada no mesmo ciclo de OCR.
        val ninetyNineScreen = looksLike99(lower)
        val ninetyNineCandidate =
            ninetyNineScreen && FlexibleDriverOfferParser.primaryFare(raw) != null
        var ninetyNineOffers = if (ninetyNineCandidate) {
            FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
        } else {
            emptyList()
        }

        if (
            ninetyNineOffers.isEmpty() &&
            ninetyNineCandidate &&
            FlexibleDriverOfferParser.looksLikeCandidate(raw) &&
            safeWholeFrameFallback(raw)
        ) {
            FlexibleDriverOfferParser.parseText(
                rawText = raw,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99-text-fallback-0265",
                settings = settings,
            )?.let { ninetyNineOffers = listOf(it) }
        }

        // Caminho Uber preservado: a RC3 não muda UberSpatialParser nem
        // OfferParser. Apenas deixa de retornar cedo por causa da 99.
        var uberOffers = UberSpatialParser0221.parse(
            result = result,
            settings = settings,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        val uberAnchored = OfferSpatialIsolation0221.hasUberOfferAnchor(raw)
        if (uberOffers.isEmpty() && uberAnchored) {
            // Backup textual conservador existente: uma tarifa principal apenas.
            if (FlexibleDriverOfferParser.primaryFareCount(raw) == 1) OfferParser.parse(
                rawText = raw,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "media-projection-ocr/uber-text-fallback-0265",
                settings = settings,
            )?.let { uberOffers = listOf(it) }
        }

        if (ninetyNineOffers.isNotEmpty() || uberOffers.isNotEmpty()) {
            val combined = (ninetyNineOffers + uberOffers)
                .distinctBy(OfferDeduplicator::semanticKey)
            val platform = when {
                ninetyNineOffers.isNotEmpty() && uberOffers.isNotEmpty() -> "multi"
                ninetyNineOffers.isNotEmpty() -> "99"
                else -> "uber"
            }
            val reason = when {
                ninetyNineOffers.isNotEmpty() && uberOffers.isNotEmpty() ->
                    "candidatos Uber + 99 isolados no mesmo frame"
                ninetyNineOffers.isNotEmpty() && uberAnchored ->
                    "candidato 99 isolado + Uber aguardando frame completo"
                ninetyNineOffers.isNotEmpty() &&
                    ninetyNineOffers.any { it.parserVersion == "sr-rotas-multi-v0.27.0-99-flex" } ->
                    "candidato 99 flex em tela dividida"
                ninetyNineOffers.isNotEmpty() -> "candidato 99"
                ninetyNineCandidate -> "candidato Uber isolado + 99 pendente"
                else -> "candidato Uber isolado"
            }
            return RoutedResult(platform, combined, true, reason = reason)
        }

        // Se nenhuma plataforma fechou um card, preservamos o motivo específico
        // para que o diagnóstico diferencie 99 incompleta de Uber incompleta.
        if (ninetyNineCandidate) {
            return RoutedResult(
                "99",
                emptyList(),
                true,
                reason = "candidato 99 aguardando geometria completa",
            )
        }
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
            if (safeWholeFrameFallback(raw)) FlexibleDriverOfferParser.parseText(
                rawText = raw,
                platform = inferred,
                sourcePackage = AppSignals.inferredPackage(inferred),
                captureMethod = "media-projection-ocr/$inferred-text-fallback-0265",
                settings = settings,
            )?.let {
                return RoutedResult(
                    inferred,
                    listOf(it),
                    true,
                    reason = "fallback textual/$inferred",
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

        val candidate = ninetyNineCandidate || FlexibleDriverOfferParser.looksLikeCandidate(raw)
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

        // "Escolher" é distintivo da oferta da 99. Em split-screen o OCR pode
        // separar "4 min" e "680 m" em linhas diferentes; por isso a detecção
        // usa a geometria flexível somente quando também existe tarifa principal.
        val action = lower.contains("escolher")
        val rideContext =
            lower.contains("corridas") ||
                lower.contains("solicitações") ||
                lower.contains("solicitacoes")
        val fare = FlexibleDriverOfferParser.primaryFare(lower) != null
        val looseMetrics = FlexibleDriverOfferParser.geometryCount99Flexible(lower) >= 2
        val advertised = lower.contains("/km")
        return action && fare && (rideContext || looseMetrics || advertised)
    }

    private fun safeWholeFrameFallback(raw: String): Boolean =
        FlexibleDriverOfferParser.primaryFareCount(raw) == 1 &&
            FlexibleDriverOfferParser.geometryCount(raw) in 2..3

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

    internal val geometryRegex = Regex(
        "(?:\\(\\s*)?([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\s*(?:[·•\\-–—]?\\s*)?(?:\\(\\s*)?([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\s*\\)?",
        RegexOption.IGNORE_CASE,
    )

    // RC3 — somente 99. O ML Kit frequentemente entrega, no tablet/split-screen,
    // tempo e distância como linhas independentes. Mantemos o cluster espacial e
    // reconstruímos apenas as duas geometrias dentro dele.
    private val durationTokenRegex = Regex(
        "\\b([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val distanceTokenRegex = Regex(
        "\\b([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\b",
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
    private data class OrderedDuration(val line: Int, val minutes: Int)
    private data class OrderedDistance(val line: Int, val km: Double)

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

    internal fun geometryCount99Flexible(rawText: String): Int =
        geometryPairs99Flexible(DriverOcrNormalizer.sanitize(rawText)).size

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

            val parsed = if (platform == "99") {
                parse99FlexibleText(
                    rawText = text,
                    sourcePackage = sourcePackage,
                    captureMethod = captureMethod,
                    settings = settings,
                    navigationNoise = strict,
                )
            } else {
                if (!clusterCandidate(platform, text, strict, geometryCount(text))) {
                    null
                } else {
                    parseText(
                        rawText = text,
                        platform = platform,
                        sourcePackage = sourcePackage,
                        captureMethod = captureMethod,
                        settings = settings,
                    )
                }
            }

            parsed?.let { OfferContextExtractor0221.attach(it, cluster) }
        }.distinctBy(OfferDeduplicator::semanticKey)
    }

    /**
     * Parser 99 específico para o cluster já isolado. Primeiro tenta a geometria
     * clássica. Só se ela vier incompleta reconstrói tempo/distância entre linhas.
     */
    internal fun parse99FlexibleText(
        rawText: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        navigationNoise: Boolean = false,
    ): RideOffer? {
        val text = DriverOcrNormalizer.sanitize(rawText)
        val strictPairs = geometryPairs(text)
        val pairs = if (strictPairs.size >= 2) strictPairs else geometryPairs99Flexible(text)
        if (!clusterCandidate("99", text, navigationNoise, pairs.size)) return null
        val version =
            if (strictPairs.size >= 2) "sr-rotas-multi-v0.22.1"
            else "sr-rotas-multi-v0.27.0-99-flex"
        return buildOffer(
            text = text,
            platform = "99",
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            settings = settings,
            pairs = pairs,
            parserVersion = version,
        )
    }

    private fun clusterCandidate(
        platform: String,
        text: String,
        strict: Boolean,
        geometryPairs: Int,
    ): Boolean {
        if (geometryPairs < 2 || primaryFare(text) == null) return false
        if (platform == "99") {
            val lower = text.lowercase()
            val choose = lower.contains("escolher")
            val service = listOf(
                "plus nova", "99plus", "99 plus", "99pop", "99 pop",
                "99moto", "99 moto", "99taxi", "99táxi", "99electric", "99 entrega",
            ).any(lower::contains)
            val profile = lower.contains("perfil essencial")
            val request = lower.contains("solicitações") || lower.contains("solicitacoes") || lower.contains("corridas")
            val advertised = advertisedRegex.containsMatchIn(text)
            if (!(choose || service || profile)) return false

            // Quando Waze/Maps também está no frame, não aceitamos um card só
            // porque existem números. Exigimos a ação da 99 OU identidade de
            // serviço acompanhada de R$/km. Isso preserva isolamento entre apps.
            return if (strict) {
                choose || (service && advertised) || (profile && advertised)
            } else {
                choose || request || advertised || service || profile
            }
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
        val pairs = geometryPairs(text)
        if (pairs.size < 2) return null
        return buildOffer(
            text = text,
            platform = platform,
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            settings = settings,
            pairs = pairs,
            parserVersion = "sr-rotas-multi-v0.22.1",
        )
    }

    private fun buildOffer(
        text: String,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        pairs: List<TimeDistance>,
        parserVersion: String,
    ): RideOffer? {
        val fare = primaryFare(text) ?: return null
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
        if (parserVersion.endsWith("99-flex")) confidence -= 0.02

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
            confidence = confidence.coerceIn(0.50, 0.97),
            offerType = "exclusive",
            parserVersion = parserVersion,
            dedupeKey = dedupe,
        )
    }

    internal fun primaryFareCount(text: String): Int {
        val normalized = DriverOcrNormalizer.sanitize(text)
        var count = 0
        for (match in moneyRegex.findAll(normalized)) {
            val start = match.range.first
            val before = normalized.substring(maxOf(0, start - 2), start).trim()
            if (before.endsWith("+")) continue
            val afterStart = match.range.last + 1
            val after = normalized.substring(afterStart, minOf(normalized.length, afterStart + 10))
            if (Regex("^\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(after)) continue
            val value = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: continue
            if (value in 2.0..1000.0) count++
        }
        return count
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

    /**
     * Reconstrói pares somente dentro do texto de um cluster 99. Cada duração é
     * associada à distância livre mais próxima (mesma linha ou até três linhas),
     * impedindo que endereços distantes e o R$/km sejam tratados como geometria.
     */
    private fun geometryPairs99Flexible(text: String): List<TimeDistance> {
        val lines = DriverOcrNormalizer.sanitize(text).lines()
        val durations = mutableListOf<OrderedDuration>()
        val distances = mutableListOf<OrderedDistance>()

        lines.forEachIndexed { index, line ->
            durationTokenRegex.findAll(line).forEach { match ->
                val value = OfferParser.parseNumberCandidate(match.groupValues[1])?.toInt() ?: return@forEach
                if (value in 1..360) durations += OrderedDuration(index, value)
            }

            // Linhas monetárias nunca representam distância da rota. Isso evita
            // interpretar "R$ 3,83 km" como distância caso o OCR perca a barra.
            if (line.contains("R$", ignoreCase = true) || line.contains('$')) {
                return@forEachIndexed
            }
            distanceTokenRegex.findAll(line).forEach { match ->
                val raw = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: return@forEach
                val unit = match.groupValues[2].lowercase()
                val km = if (unit == "m") raw / 1000.0 else raw
                if (km in 0.05..500.0) distances += OrderedDistance(index, km)
            }
        }

        if (durations.size < 2 || distances.size < 2) return emptyList()

        val used = BooleanArray(distances.size)
        val pairs = mutableListOf<Pair<Int, TimeDistance>>()
        durations.forEach { duration ->
            val best = distances.indices
                .asSequence()
                .filter { !used[it] }
                .map { index ->
                    val distance = distances[index]
                    val lineDelta = abs(distance.line - duration.line)
                    Triple(index, distance, lineDelta)
                }
                .filter { it.third <= 3 }
                .minWithOrNull(
                    compareBy<Triple<Int, OrderedDistance, Int>> { it.third }
                        .thenBy { if (it.second.line < duration.line) 1 else 0 }
                        .thenBy { it.second.line },
                )
                ?: return@forEach

            used[best.first] = true
            pairs += duration.line to TimeDistance(duration.minutes, best.second.km)
        }

        return pairs.sortedBy { it.first }.map { it.second }.take(3)
    }

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
