package com.srrotas.app

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * OCR da digitalização manual da Uber.
 *
 * 0.26.2:
 * - entende datas pt-BR do "Resumo da sessão";
 * - reconhece "Viagens oferecidas" e o valor grande sem rótulo "Ganhos";
 * - separa a tarifa principal de preço dinâmico/valor extra no Histórico;
 * - lê duração, distância, situação e endereços do cartão;
 * - produz chave semântica estável entre capturas sobrepostas da rolagem.
 */
object UberDigitizationParser026 {
    const val MODE_SESSION = "session"
    const val MODE_HISTORY = "history"
    private val ZONE = ZoneId.of("America/Sao_Paulo")

    fun parse(
        mode: String,
        rawText: String,
        capturedAt: Instant = Instant.now(),
    ): UberDigitizationResult026 {
        val normalized = normalize(rawText)
        require(normalized.length >= 12) { "Texto insuficiente para digitalização." }
        return when (mode) {
            MODE_SESSION -> UberDigitizationResult026.Session(parseSession(normalized, capturedAt))
            MODE_HISTORY -> UberDigitizationResult026.Rides(parseRides(normalized, capturedAt))
            else -> error("Modo de digitalização inválido.")
        }
    }

    fun parseSession(
        rawText: String,
        capturedAt: Instant = Instant.now(),
    ): UberSessionSummary026 {
        val text = normalize(rawText)
        val lower = text.lowercase(Locale.ROOT)
        require(
            listOf("resumo da sessão", "resumo da sessao", "viagens conclu", "viagens oferec", "ganh", "atividade")
                .count { lower.contains(it) } >= 2,
        ) { "A tela não parece ser um resumo de sessão da Uber." }

        val earnings =
            findMoneyNear(text, listOf("ganhos", "ganho", "receita", "total"))
                ?: MONEY_REGEX.findAll(text).mapNotNull(::moneyFrom).maxOrNull()
        val completed = findCount(
            text,
            listOf(
                "viagens concluídas", "viagens concluidas",
                "corridas concluídas", "corridas concluidas",
            ),
        )
        val offered = findCount(
            text,
            listOf(
                "viagens oferecidas", "corridas oferecidas", "ofertas",
                "solicitações", "solicitacoes", "chamadas recebidas", "pedidos recebidos",
            ),
        )
        val pair = sessionDateTimes(text, capturedAt)
        val observation = sessionObservation(text)

        var confidence = 0.20
        if (earnings != null) confidence += 0.25
        if (completed != null) confidence += 0.20
        if (offered != null) confidence += 0.15
        if (pair.first != null && pair.second != null) confidence += 0.20
        require(confidence >= 0.55) { "Não foi possível confirmar os campos principais do resumo." }

        val capturedIso = capturedAt.toString()
        val semantic = listOf(pair.first, pair.second, earnings, completed, offered).joinToString("|")
        return UberSessionSummary026(
            sourceKey = "uber-session-${sha256(semantic)}",
            capturedAt = capturedIso,
            startedAt = pair.first,
            endedAt = pair.second,
            earnings = earnings,
            completedTrips = completed,
            offeredTrips = offered,
            confidence = confidence.coerceAtMost(1.0),
            observation = observation,
        )
    }

    fun parseRides(
        rawText: String,
        capturedAt: Instant = Instant.now(),
    ): List<UberCompletedRide026> = parseRidesFrame(rawText, capturedAt, relaxed = false)

    /** Usado somente após o motorista iniciar manualmente a varredura do Histórico. */
    fun parseRidesFrame(
        rawText: String,
        capturedAt: Instant = Instant.now(),
        relaxed: Boolean = true,
    ): List<UberCompletedRide026> {
        val text = normalize(rawText)
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val lower = text.lowercase(Locale.ROOT)
        if (!relaxed) {
            require(
                listOf("histórico", "historico", "viagens", "corridas", "ganhos", "recurso").any(lower::contains),
            ) { "A tela não parece ser o histórico de corridas da Uber." }
        } else {
            val hasCardSignal = lines.any(::isPrimaryHistoryFareLine) &&
                (DURATION_REGEX.containsMatchIn(text) || DISTANCE_REGEX.containsMatchIn(text) || serviceType(text) != "unknown")
            require(hasCardSignal || listOf("ganhos", "histórico", "historico", "viagens").any(lower::contains)) {
                "Quadro sem cartões reconhecíveis do Histórico da Uber."
            }
        }

        val primary = lines.indices.filter { isPrimaryHistoryFareLine(lines[it]) }
        val cancellationOnly = lines.indices.filter { index ->
            isCancellationLine(lines[index]) && primary.none { abs(it - index) <= 4 }
        }
        val anchors = (primary + cancellationOnly).distinct().sorted()
        val capturedIso = capturedAt.toString()
        val rows = mutableListOf<UberCompletedRide026>()

        anchors.forEachIndexed { position, index ->
            val next = anchors.getOrNull(position + 1) ?: lines.size
            val start = if (index in cancellationOnly) maxOf(0, index - 3) else index
            val windowLines = lines.subList(start, next.coerceAtMost(lines.size))
            val window = windowLines.joinToString("\n")
            val windowLower = window.lowercase(Locale.ROOT)
            if (listOf("ganhos totais", "total de ganhos", "saldo", "promoção", "promocao").any(windowLower::contains)) {
                return@forEachIndexed
            }

            val status = if (windowLines.any(::isCancellationLine)) {
                UberCompletedRide026.STATUS_CANCELLED
            } else {
                UberCompletedRide026.STATUS_COMPLETED
            }
            val fare = windowLines.firstNotNullOfOrNull { line ->
                if (isPrimaryHistoryFareLine(line)) MONEY_REGEX.find(line)?.let(::moneyFrom) else null
            } ?: 0.0
            if (fare > 5000.0 || (status == UberCompletedRide026.STATUS_COMPLETED && fare <= 0.0)) {
                return@forEachIndexed
            }

            val service = serviceType(window)
            val date = dateForAnchor(lines, index, capturedAt)
            val time = TIME_REGEX.find(window)?.let { toTime(it.groupValues[1], it.groupValues[2]) }
            val occurredAt = if (date != null && time != null) {
                date.atTime(time).atZone(ZONE).toInstant().toString()
            } else {
                parseOccurredAt(windowLines, capturedAt)
            }
            val durationSeconds = durationSeconds(window)
            val distanceKm = DISTANCE_REGEX.find(window)?.groupValues?.getOrNull(1)?.let(::decimal)
            val surge = moneyOnLabeledLine(windowLines, listOf("preço dinâmico", "preco dinamico", "dinâmico", "dinamico"))
            val extra = moneyOnLabeledLine(windowLines, listOf("valor extra", "valor a mais", "gorjeta"))
            val route = routeLabels(windowLines)

            var confidence = 0.28
            if (fare > 0.0) confidence += 0.20 else if (status == UberCompletedRide026.STATUS_CANCELLED) confidence += 0.10
            if (service != "unknown") confidence += 0.13
            if (occurredAt != null) confidence += 0.18
            if (durationSeconds != null) confidence += 0.08
            if (distanceKm != null) confidence += 0.08
            if (route.first != null || route.second != null) confidence += 0.10
            if (status == UberCompletedRide026.STATUS_CANCELLED) confidence += 0.05
            if (confidence < 0.55) return@forEachIndexed

            val dayKey = occurredAt ?: date?.toString() ?: capturedAt.atZone(ZONE).toLocalDate().toString()
            val keySeed = buildList {
                add(dayKey)
                add("%.2f".format(Locale.US, fare))
                add(service)
                add(status)
                add(durationSeconds?.toString().orEmpty())
                add(distanceKm?.let { "%.2f".format(Locale.US, it) }.orEmpty())
                if (occurredAt == null) {
                    add(route.first.orEmpty())
                    add(route.second.orEmpty())
                }
            }.joinToString("|")

            rows += UberCompletedRide026(
                sourceKey = "uber-ride-${sha256(keySeed)}",
                capturedAt = capturedIso,
                occurredAt = occurredAt,
                fare = fare,
                serviceType = service,
                pickupLabel = route.first,
                destinationLabel = route.second,
                confidence = confidence.coerceAtMost(1.0),
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                surgeAmount = surge,
                extraAmount = extra,
                rideStatus = status,
            )
        }

        val unique = rows
            .groupBy { it.sourceKey }
            .map { (_, group) -> group.maxBy(::rideRichness) }
            .sortedByDescending { it.occurredAt.orEmpty() }
        if (!relaxed) require(unique.isNotEmpty()) {
            "Nenhuma corrida pôde ser identificada com segurança nesta tela."
        }
        return unique.take(40)
    }

    private fun sessionDateTimes(text: String, capturedAt: Instant): Pair<String?, String?> {
        val captureDate = capturedAt.atZone(ZONE).toLocalDate()
        val explicit = PT_DATE_TIME_REGEX.findAll(text).mapNotNull { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = monthNumber(match.groupValues[2]) ?: return@mapNotNull null
            val time = toTime(match.groupValues[3], match.groupValues[4]) ?: return@mapNotNull null
            val date = closestYearDate(day, month, captureDate) ?: return@mapNotNull null
            date.atTime(time).atZone(ZONE).toInstant()
        }.toList()
        if (explicit.size >= 2) return explicit.first().toString() to explicit.last().toString()

        val numeric = NUMERIC_DATE_TIME_REGEX.findAll(text).mapNotNull { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val time = toTime(match.groupValues[3], match.groupValues[4]) ?: return@mapNotNull null
            val date = closestYearDate(day, month, captureDate) ?: return@mapNotNull null
            date.atTime(time).atZone(ZONE).toInstant()
        }.toList()
        if (numeric.size >= 2) return numeric.first().toString() to numeric.last().toString()

        val times = TIME_REGEX.findAll(text).mapNotNull { toTime(it.groupValues[1], it.groupValues[2]) }.toList()
        if (times.size < 2) return null to null
        val start = times.first()
        val end = times.last()
        val startDate = if (start.isAfter(end)) captureDate.minusDays(1) else captureDate
        return startDate.atTime(start).atZone(ZONE).toInstant().toString() to
            captureDate.atTime(end).atZone(ZONE).toInstant().toString()
    }

    private fun dateForAnchor(lines: List<String>, index: Int, capturedAt: Instant): LocalDate? {
        val captureDate = capturedAt.atZone(ZONE).toLocalDate()
        for (i in index downTo 0) {
            parsePtDate(lines[i], captureDate)?.let { return it }
            parseNumericDate(lines[i], captureDate)?.let { return it }
        }
        return captureDate
    }

    private fun parseOccurredAt(lines: List<String>, capturedAt: Instant): String? {
        val captureDate = capturedAt.atZone(ZONE).toLocalDate()
        val joined = lines.joinToString(" ")
        val timeMatch = TIME_REGEX.find(joined) ?: return null
        val time = toTime(timeMatch.groupValues[1], timeMatch.groupValues[2]) ?: return null
        val date = parsePtDate(joined, captureDate) ?: parseNumericDate(joined, captureDate) ?: captureDate
        return date.atTime(time).atZone(ZONE).toInstant().toString()
    }

    private fun parsePtDate(text: String, captureDate: LocalDate): LocalDate? {
        val match = PT_DATE_REGEX.find(text) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = monthNumber(match.groupValues[2]) ?: return null
        return closestYearDate(day, month, captureDate)
    }

    private fun parseNumericDate(text: String, captureDate: LocalDate): LocalDate? {
        val match = DATE_REGEX.find(text) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val rawYear = match.groupValues[3].toIntOrNull()
        if (rawYear != null) {
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return closestYearDate(day, month, captureDate)
    }

    private fun closestYearDate(day: Int, month: Int, captureDate: LocalDate): LocalDate? =
        listOf(captureDate.year - 1, captureDate.year, captureDate.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minByOrNull { candidate -> abs(candidate.toEpochDay() - captureDate.toEpochDay()) }

    private fun monthNumber(raw: String): Int? {
        val value = raw.lowercase(Locale.ROOT).replace(".", "")
        return when {
            value.startsWith("jan") -> 1
            value.startsWith("fev") -> 2
            value.startsWith("mar") -> 3
            value.startsWith("abr") -> 4
            value.startsWith("mai") -> 5
            value.startsWith("jun") -> 6
            value.startsWith("jul") -> 7
            value.startsWith("ago") -> 8
            value.startsWith("set") -> 9
            value.startsWith("out") -> 10
            value.startsWith("nov") -> 11
            value.startsWith("dez") -> 12
            else -> null
        }
    }

    private fun routeLabels(lines: List<String>): Pair<String?, String?> {
        val arrow = lines.firstOrNull { it.contains("→") || it.contains("➜") }
        if (arrow != null) {
            val parts = arrow.split(Regex("[→➜]"), limit = 2).map(::cleanPlace)
            if (parts.size == 2) return parts[0].takeIf(String::isNotBlank) to parts[1].takeIf(String::isNotBlank)
        }

        var pickup: String? = null
        var destination: String? = null
        lines.forEachIndexed { i, line ->
            val low = line.lowercase(Locale.ROOT)
            if ((low.startsWith("embarque") || low.startsWith("de:") || low.startsWith("origem")) && i + 1 < lines.size) {
                pickup = cleanPlace(lines[i + 1])
            }
            if ((low.startsWith("destino") || low.startsWith("para:")) && i + 1 < lines.size) {
                destination = cleanPlace(lines[i + 1])
            }
        }
        if (!pickup.isNullOrBlank() || !destination.isNullOrBlank()) {
            return pickup?.takeIf(String::isNotBlank) to destination?.takeIf(String::isNotBlank)
        }

        val addresses = mutableListOf<String>()
        var i = 0
        while (i < lines.size && addresses.size < 2) {
            val line = lines[i]
            if (!looksAddressStart(line)) {
                i++
                continue
            }
            var value = cleanPlace(line)
            var j = i + 1
            while (j < lines.size && j <= i + 2 && looksAddressContinuation(lines[j]) && !looksAddressStart(lines[j])) {
                value = cleanPlace("$value ${lines[j]}")
                j++
            }
            value.takeIf { it.length >= 6 }?.let(addresses::add)
            i = j
        }
        return addresses.getOrNull(0) to addresses.getOrNull(1)
    }

    private fun looksAddressStart(value: String): Boolean {
        val v = value.lowercase(Locale.ROOT)
        if (isNoiseLine(v)) return false
        return ADDRESS_START_REGEX.containsMatchIn(v)
    }

    private fun looksAddressContinuation(value: String): Boolean {
        val v = value.lowercase(Locale.ROOT)
        if (isNoiseLine(v)) return false
        return v.length in 3..150 && (
            v.contains(" - sp") || v.contains("são paulo") || v.contains("sao paulo") ||
                v.contains("guarulhos") || v.contains("br") || POSTAL_REGEX.containsMatchIn(v) ||
                v.startsWith("sp,") || v.startsWith("-")
            )
    }

    private fun isNoiseLine(value: String): Boolean =
        MONEY_REGEX.containsMatchIn(value) ||
            DURATION_REGEX.containsMatchIn(value) ||
            DISTANCE_REGEX.containsMatchIn(value) ||
            listOf("preço dinâmico", "preco dinamico", "valor extra", "ganhos", "página inicial", "pagina inicial", "descubra").any(value::contains)

    private fun durationSeconds(value: String): Int? {
        HOUR_DURATION_REGEX.find(value)?.let { m ->
            val hours = m.groupValues[1].toIntOrNull() ?: return@let
            val minutes = m.groupValues[2].toIntOrNull() ?: 0
            val seconds = m.groupValues[3].toIntOrNull() ?: 0
            return hours * 3600 + minutes * 60 + seconds
        }
        MINUTE_DURATION_REGEX.find(value)?.let { m ->
            val minutes = m.groupValues[1].toIntOrNull() ?: return@let
            val seconds = m.groupValues[2].toIntOrNull() ?: 0
            return minutes * 60 + seconds
        }
        return null
    }

    private fun moneyOnLabeledLine(lines: List<String>, labels: List<String>): Double? {
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (labels.none(lower::contains)) continue
            return MONEY_REGEX.find(line)?.let(::moneyFrom)
        }
        return null
    }

    private fun findMoneyNear(text: String, labels: List<String>): Double? {
        val lines = text.lines()
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (labels.none(lower::contains)) continue
            val joined = lines.subList(i, (i + 3).coerceAtMost(lines.size)).joinToString(" ")
            MONEY_REGEX.find(joined)?.let { return moneyFrom(it) }
        }
        return null
    }

    private fun findCount(text: String, labels: List<String>): Int? {
        val lines = text.lines()
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            val label = labels.firstOrNull(lower::contains) ?: continue
            val joined = lines.subList(i, (i + 3).coerceAtMost(lines.size)).joinToString(" ")
            Regex("(?i)${Regex.escape(label)}[^0-9]{0,28}(\\d{1,3})").find(joined)
                ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            Regex("(?i)(\\d{1,3})[^0-9]{0,12}${Regex.escape(label)}").find(joined)
                ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun sessionObservation(text: String): String? = text.lines()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filter { line ->
            val v = line.lowercase(Locale.ROOT)
            listOf("você", "voce", "bom demais", "muito bem", "cancelamento", "seguidas", "sequidas").any(v::contains)
        }
        .distinct()
        .joinToString(" ")
        .take(500)
        .takeIf(String::isNotBlank)

    private fun isPrimaryHistoryFareLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        if (!MONEY_REGEX.containsMatchIn(line)) return false
        if (listOf("preço dinâmico", "preco dinamico", "valor extra", "valor a mais", "gorjeta", "saldo", "promoção", "promocao").any(lower::contains)) return false
        val match = MONEY_REGEX.find(line) ?: return false
        val fare = moneyFrom(match) ?: return false
        return fare in 0.0..5000.0
    }

    private fun isCancellationLine(line: String): Boolean {
        val v = line.lowercase(Locale.ROOT)
        return "cancelad" in v || "cancelamento" in v
    }

    private fun serviceType(value: String): String {
        val v = value.lowercase(Locale.ROOT)
        return when {
            "comfort" in v -> "comfort"
            "black" in v -> "black"
            "electric" in v || "elétric" in v || "eletric" in v -> "electric"
            "priority" in v || "prioridade" in v -> "priority"
            Regex("\\bmoto\\b").containsMatchIn(v) -> "moto"
            "uberx" in v || Regex("\\buber x\\b").containsMatchIn(v) -> "uberx"
            else -> "unknown"
        }
    }

    private fun rideRichness(value: UberCompletedRide026): Double {
        var score = value.confidence * 10
        if (value.occurredAt != null) score += 3
        if (value.durationSeconds != null) score += 2
        if (value.distanceKm != null) score += 2
        if (value.pickupLabel != null) score += 2
        if (value.destinationLabel != null) score += 2
        if (value.surgeAmount != null) score += 1
        if (value.extraAmount != null) score += 1
        return score
    }

    private fun cleanPlace(value: String): String = value
        .replace(MONEY_REGEX, "")
        .replace(Regex("[ \\t]+"), " ")
        .trim(' ', '-', '·', ':', '•')
        .take(220)

    private fun moneyFrom(match: MatchResult): Double? = decimal(match.groupValues[1])

    private fun decimal(value: String): Double? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        return if (raw.contains(',')) {
            raw.replace(".", "").replace(',', '.').toDoubleOrNull()
        } else {
            raw.toDoubleOrNull()
        }
    }

    private fun toTime(hour: String, minute: String): LocalTime? = runCatching {
        LocalTime.of(hour.toInt(), minute.toInt())
    }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

    private fun normalize(value: String): String = value
        .replace('\u00A0', ' ')
        .replace("R $", "R$")
        .replace('–', '-')
        .replace('—', '-')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private val MONEY_REGEX = Regex(
        "(?i)R\\$\\s*([0-9]{1,5}(?:\\.[0-9]{3})*(?:,[0-9]{2})|[0-9]{1,5}(?:[.,][0-9]{2}))",
    )
    private val TIME_REGEX = Regex("\\b([01]?\\d|2[0-3])\\s*[:hH]\\s*([0-5]\\d)\\b")
    private val DATE_REGEX = Regex("\\b([0-3]?\\d)[/.-]([01]?\\d)(?:[/.-](\\d{2,4}))?\\b")
    private val PT_DATE_REGEX = Regex(
        "(?i)\\b([0-3]?\\d)\\s+de\\s+(jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)\\.?\\b",
    )
    private val PT_DATE_TIME_REGEX = Regex(
        "(?i)\\b([0-3]?\\d)\\s+de\\s+(jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)\\.?\\s*,?\\s*([01]?\\d|2[0-3])\\s*[:hH]\\s*([0-5]\\d)\\b",
    )
    private val NUMERIC_DATE_TIME_REGEX = Regex(
        "\\b([0-3]?\\d)[/.-]([01]?\\d)(?:[/.-]\\d{2,4})?\\s*,?\\s*([01]?\\d|2[0-3])\\s*[:hH]\\s*([0-5]\\d)\\b",
    )
    private val HOUR_DURATION_REGEX = Regex(
        "(?i)\\b(\\d{1,2})\\s*h(?:oras?)?(?:\\s*(\\d{1,2})\\s*min(?:utos?)?)?(?:\\s*(\\d{1,2})\\s*(?:s|seg|segundos?))?",
    )
    private val MINUTE_DURATION_REGEX = Regex(
        "(?i)\\b(\\d{1,3})\\s*min(?:utos?)?(?:\\s*(\\d{1,2})\\s*(?:s|seg|segundos?))?",
    )
    private val DURATION_REGEX = Regex("(?i)\\b(?:\\d{1,2}\\s*h|\\d{1,3}\\s*min)")
    private val DISTANCE_REGEX = Regex("(?i)\\b(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*km\\b")
    private val ADDRESS_START_REGEX = Regex(
        "(?i)^(?:r\\.?|rua|av\\.?|avenida|al\\.?|alameda|estrada|rodovia|travessa|praça|praca|largo|marginal|via|terminal|aeroporto)\\b",
    )
    private val POSTAL_REGEX = Regex("\\b\\d{5}-?\\d{3}\\b")
}
