package com.srrotas.app

object UberOfferDetector {
    data class TimeDistance(val minutes: Int, val km: Double)
    data class Detection(
        val fare: Double,
        val advertisedPerKm: Double?,
        val passengerRating: Double?,
        val serviceType: String,
        val offerType: String,
        val pairs: List<TimeDistance>,
        val confidence: Double,
    )

    data class GeometryEvidence(
        val pairedDurationDistanceCount: Int,
        val distanceCount: Int,
        val durationCount: Int,
        val hasAdvertisedPerKm: Boolean,
    )

    private val moneyRegex = Regex("R\\$\\s*([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,2})?)", RegexOption.IGNORE_CASE)
    private val advertisedRegex = Regex("R\\$\\s*([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*/\\s*km", RegexOption.IGNORE_CASE)
    private val pairRegex = Regex(
        "(${UberDurationParser025.durationPattern})\\s*\\(\\s*([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*km\\s*\\)",
        RegexOption.IGNORE_CASE,
    )
    private val ratingRegex = Regex("\\b([45](?:[.,][0-9]{1,2})?)\\s*\\(\\s*[0-9]{1,6}\\s*\\)")
    private val rangeMinutesRegex = Regex("\\b[0-9]{1,2}\\s*-\\s*[0-9]{1,2}\\s*min\\b", RegexOption.IGNORE_CASE)
    private val plainKmRegex = Regex("\\b([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*km\\b", RegexOption.IGNORE_CASE)

    fun detect(rawText: String, hintedOfferType: String = "exclusive"): Detection? {
        val text = normalize(rawText)
        if (text.length < 12) return null
        val lower = text.lowercase()

        val negativeHome = listOf(
            "registro de viagens", "tendências de ganhos", "tendencias de ganhos",
            "você está online", "voce esta online", "você está offline", "voce esta offline",
            "procurando viagens", "página inicial", "pagina inicial", "uber pro",
        ).any(lower::contains)

        val hasRadar = lower.contains("radar de viagens") || lower.contains("selecionar")
        val hasExclusive = lower.contains("exclusivo") || lower.contains("aceitar")
        val serviceType = when {
            lower.contains("priority") -> "priority"
            lower.contains("electric") -> "electric"
            lower.contains("comfort") -> "comfort"
            lower.contains("black") -> "black"
            lower.contains("uber moto") || lower.contains("ubermoto") -> "moto"
            lower.contains("uberx") -> "uberx"
            else -> "unknown"
        }
        val offerType = when {
            hasRadar -> "radar"
            hasExclusive -> "exclusive"
            hintedOfferType == "radar" -> "radar"
            else -> "exclusive"
        }

        val fare = primaryFare(text) ?: return null
        val advertised = advertisedRegex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        val pairs = pairRegex.findAll(text).mapNotNull { match ->
            val minutes = UberDurationParser025.parseCandidate(match.groupValues[1]) ?: return@mapNotNull null
            val km = OfferParser.parseNumberCandidate(match.groupValues[2]) ?: return@mapNotNull null
            if (minutes !in 1..360 || km !in 0.1..500.0) null else TimeDistance(minutes, km)
        }.toList()
        val rating = ratingRegex.findAll(text)
            .mapNotNull { OfferParser.parseNumberCandidate(it.groupValues[1]) }
            .firstOrNull { it in 4.0..5.0 }

        val geometryFallback = pairs.isNotEmpty() || hasPlainGeometry(text)
        val strongCardAnchor = hasRadar || hasExclusive || serviceType != "unknown" || advertised != null
        if (!geometryFallback || !strongCardAnchor) return null
        if (negativeHome && !hasRadar && !hasExclusive && serviceType == "unknown") return null

        var confidence = 0.58
        if (hasRadar || hasExclusive) confidence += 0.10
        if (serviceType != "unknown") confidence += 0.08
        if (advertised != null) confidence += 0.08
        if (pairs.isNotEmpty()) confidence += 0.08
        if (pairs.size >= 2) confidence += 0.05
        if (rating != null) confidence += 0.03

        return Detection(fare, advertised, rating, serviceType, offerType, pairs, confidence.coerceAtMost(0.98))
    }

    fun isPrimaryFareLine(rawLine: String): Boolean {
        val line = BRUberLineSanitizer.sanitize(rawLine)
        val l = line.trim().lowercase()
        if (!l.contains("r$")) return false
        if (l.contains("+r$")) return false
        if (l.contains("/km") || l.contains("aprox")) return false
        if (l.contains("incluído") || l.contains("incluido") || l.contains("registro de viagens") || l.contains("ganhos")) return false
        val value = moneyRegex.find(line)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate) ?: return false
        return value in 2.0..1000.0
    }

    fun primaryFare(text: String): Double? {
        val lines = normalize(text).split('\n').map(String::trim).filter(String::isNotBlank)
        lines.firstOrNull(::isPrimaryFareLine)?.let { line ->
            return moneyRegex.find(line)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        }
        return null
    }

    fun fallbackDistancesAndMinutes(text: String): Pair<List<Double>, List<Int>> {
        val lines = normalize(text).split('\n').map(String::trim).filter(String::isNotBlank)
        val distances = mutableListOf<Double>()
        val minutes = mutableListOf<Int>()
        for (line in lines) {
            if (rangeMinutesRegex.containsMatchIn(line)) continue
            plainKmRegex.findAll(line)
                .mapNotNull { OfferParser.parseNumberCandidate(it.groupValues[1]) }
                .filter { it in 0.1..500.0 }
                .forEach(distances::add)
            UberDurationParser025.findAll(line)
                .map { it.minutes }
                .filter { it in 1..360 }
                .forEach(minutes::add)
        }
        return distances to minutes
    }

    fun geometryEvidence(rawText: String): GeometryEvidence {
        val text = normalize(rawText)
        val (distances, durations) = fallbackDistancesAndMinutes(text)
        return GeometryEvidence(
            pairedDurationDistanceCount = pairRegex.findAll(text).count(),
            distanceCount = distances.size,
            durationCount = durations.size,
            hasAdvertisedPerKm = advertisedRegex.containsMatchIn(text),
        )
    }

    private fun hasPlainGeometry(text: String): Boolean {
        val (km, min) = fallbackDistancesAndMinutes(text)
        return km.isNotEmpty() && min.isNotEmpty()
    }

    private fun normalize(raw: String) = BRUberLineSanitizer.sanitize(raw)
}
