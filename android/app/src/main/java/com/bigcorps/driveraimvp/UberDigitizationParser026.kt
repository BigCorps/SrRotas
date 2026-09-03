package com.srrotas.app

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object UberDigitizationParser026 {
    const val MODE_SESSION = "session"
    const val MODE_HISTORY = "history"

    fun parse(mode: String, rawText: String, capturedAt: Instant = Instant.now()): UberDigitizationResult026 {
        val normalized = normalize(rawText)
        require(normalized.length >= 12) { "Texto insuficiente para digitalização." }
        return when (mode) {
            MODE_SESSION -> UberDigitizationResult026.Session(parseSession(normalized, capturedAt))
            MODE_HISTORY -> UberDigitizationResult026.Rides(parseRides(normalized, capturedAt))
            else -> error("Modo de digitalização inválido.")
        }
    }

    fun parseSession(rawText: String, capturedAt: Instant = Instant.now()): UberSessionSummary026 {
        val text = normalize(rawText)
        val lower = text.lowercase(Locale.ROOT)
        require(listOf("ganh", "viagen", "corrida", "online", "atividade", "resumo").count { lower.contains(it) } >= 2) {
            "A tela não parece ser um resumo de sessão da Uber."
        }

        val earnings = findMoneyNear(text, listOf("ganhos", "ganho", "receita", "total"))
        val completed = findCount(text, listOf("viagens concluídas", "viagens concluidas", "corridas concluídas", "corridas concluidas", "viagens", "corridas"))
        val offered = findCount(text, listOf("ofertas", "solicitações", "solicitacoes", "chamadas recebidas", "pedidos recebidos"))
        val times = TIME_REGEX.findAll(text).mapNotNull { toTime(it.groupValues[1], it.groupValues[2]) }.toList()
        val zone = ZoneId.of("America/Sao_Paulo")
        val captureDate = capturedAt.atZone(zone).toLocalDate()
        val pair = deriveSessionTimes(times, captureDate, zone)

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
        )
    }

    fun parseRides(rawText: String, capturedAt: Instant = Instant.now()): List<UberCompletedRide026> {
        val text = normalize(rawText)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lower = text.lowercase(Locale.ROOT)
        require(listOf("atividade", "histórico", "historico", "viagens", "corridas", "ganhos").any { lower.contains(it) }) {
            "A tela não parece ser o histórico de corridas da Uber."
        }

        val capturedIso = capturedAt.toString()
        val rows = mutableListOf<UberCompletedRide026>()
        val moneyIndexes = lines.indices.filter { MONEY_REGEX.containsMatchIn(lines[it]) }
        moneyIndexes.forEachIndexed { moneyPosition, index ->
            val line = lines[index]
            val money = MONEY_REGEX.find(line) ?: return@forEachIndexed
            val fare = money.groupValues[1].replace(".", "").replace(',', '.').toDoubleOrNull() ?: return@forEachIndexed
            if (fare <= 0.0 || fare > 5000.0) return@forEachIndexed

            val previousMoney = moneyIndexes.getOrNull(moneyPosition - 1)
            val nextMoney = moneyIndexes.getOrNull(moneyPosition + 1)
            val from = previousMoney?.let { ((it + index) / 2) + 1 } ?: maxOf(0, index - 3)
            val to = nextMoney?.let { (index + it) / 2 } ?: minOf(lines.lastIndex, index + 3)
            val windowLines = lines.subList(from, to + 1)
            val window = windowLines.joinToString("\n")
            val windowLower = window.lowercase(Locale.ROOT)
            if (listOf("ganhos totais", "total de ganhos", "saldo", "promoção", "promocao").any { windowLower.contains(it) }) return@forEachIndexed

            val service = serviceType(window)
            val timeFrom = maxOf(0, index - 3)
            val occurredAt = parseOccurredAt(lines.subList(timeFrom, index + 1), capturedAt)
            val route = routeLabels(windowLines)
            var confidence = 0.45
            if (service != "unknown") confidence += 0.15
            if (occurredAt != null) confidence += 0.20
            if (route.first != null || route.second != null) confidence += 0.10
            if (windowLower.contains("conclu") || lower.contains("atividade")) confidence += 0.10
            if (confidence < 0.60) return@forEachIndexed

            val keySeed = listOf(
                occurredAt ?: capturedIso.take(10),
                "%.2f".format(Locale.US, fare),
                service,
                route.first,
                route.second,
                sha256(window.lowercase(Locale.ROOT)),
            ).joinToString("|")
            rows += UberCompletedRide026(
                sourceKey = "uber-ride-${sha256(keySeed)}",
                capturedAt = capturedIso,
                occurredAt = occurredAt,
                fare = fare,
                serviceType = service,
                pickupLabel = route.first,
                destinationLabel = route.second,
                confidence = confidence.coerceAtMost(1.0),
            )
        }
        val unique = rows.distinctBy { it.sourceKey }
        require(unique.isNotEmpty()) { "Nenhuma corrida concluída pôde ser identificada com segurança nesta tela." }
        return unique.take(20)
    }

    private fun normalize(value: String): String = value
        .replace('\u00A0', ' ')
        .replace("R $", "R$")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun findMoneyNear(text: String, labels: List<String>): Double? {
        val lines = text.lines()
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (labels.none { lower.contains(it) }) continue
            val joined = lines.subList(i, (i + 3).coerceAtMost(lines.size)).joinToString(" ")
            val match = MONEY_REGEX.find(joined) ?: continue
            return match.groupValues[1].replace(".", "").replace(',', '.').toDoubleOrNull()
        }
        return null
    }

    private fun findCount(text: String, labels: List<String>): Int? {
        val lines = text.lines()
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            val label = labels.firstOrNull { lower.contains(it) } ?: continue
            val joined = lines.subList(i, (i + 2).coerceAtMost(lines.size)).joinToString(" ")
            val after = Regex("(?i)${Regex.escape(label)}[^0-9]{0,18}(\\d{1,3})").find(joined)?.groupValues?.get(1)?.toIntOrNull()
            if (after != null) return after
            val before = Regex("(?i)(\\d{1,3})[^0-9]{0,8}${Regex.escape(label)}").find(joined)?.groupValues?.get(1)?.toIntOrNull()
            if (before != null) return before
        }
        return null
    }

    private fun deriveSessionTimes(times: List<LocalTime>, date: LocalDate, zone: ZoneId): Pair<String?, String?> {
        if (times.size < 2) return null to null
        val start = times.first()
        val end = times.last()
        val endDate = date
        val startDate = if (start.isAfter(end)) date.minusDays(1) else date
        return startDate.atTime(start).atZone(zone).toInstant().toString() to
            endDate.atTime(end).atZone(zone).toInstant().toString()
    }

    private fun parseOccurredAt(lines: List<String>, capturedAt: Instant): String? {
        val zone = ZoneId.of("America/Sao_Paulo")
        val captureDate = capturedAt.atZone(zone).toLocalDate()
        val joined = lines.joinToString(" ")
        val dateMatch = DATE_REGEX.find(joined)
        val timeMatch = TIME_REGEX.find(joined)
        val time = timeMatch?.let { toTime(it.groupValues[1], it.groupValues[2]) } ?: return null
        val date = if (dateMatch != null) {
            val d = dateMatch.groupValues[1].toIntOrNull() ?: return null
            val m = dateMatch.groupValues[2].toIntOrNull() ?: return null
            val yRaw = dateMatch.groupValues[3].toIntOrNull()
            val y = when {
                yRaw == null -> captureDate.year
                yRaw < 100 -> 2000 + yRaw
                else -> yRaw
            }
            runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
        } else captureDate
        return date.atTime(time).atZone(zone).toInstant().toString()
    }

    private fun routeLabels(lines: List<String>): Pair<String?, String?> {
        val arrow = lines.firstOrNull { it.contains("→") || it.contains("➜") }
        if (arrow != null) {
            val parts = arrow.split(Regex("[→➜]"), limit = 2).map { cleanPlace(it) }
            if (parts.size == 2) return parts[0].takeIf(String::isNotBlank) to parts[1].takeIf(String::isNotBlank)
        }
        var pickup: String? = null
        var destination: String? = null
        lines.forEachIndexed { i, line ->
            val low = line.lowercase(Locale.ROOT)
            if ((low.startsWith("embarque") || low.startsWith("de:")) && i + 1 < lines.size) pickup = cleanPlace(lines[i + 1])
            if ((low.startsWith("destino") || low.startsWith("para:")) && i + 1 < lines.size) destination = cleanPlace(lines[i + 1])
        }
        return pickup?.takeIf(String::isNotBlank) to destination?.takeIf(String::isNotBlank)
    }

    private fun cleanPlace(value: String): String = value
        .replace(MONEY_REGEX, "")
        .replace(TIME_REGEX, "")
        .trim(' ', '-', '·', ':')
        .take(180)

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

    private fun toTime(hour: String, minute: String): LocalTime? = runCatching {
        LocalTime.of(hour.toInt(), minute.toInt())
    }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

    private val MONEY_REGEX = Regex("(?i)R\\$\\s*([0-9]{1,4}(?:\\.[0-9]{3})*(?:,[0-9]{2})|[0-9]{1,4}(?:[.,][0-9]{2}))")
    private val TIME_REGEX = Regex("\\b([01]?\\d|2[0-3])\\s*[:hH]\\s*([0-5]\\d)\\b")
    private val DATE_REGEX = Regex("\\b([0-3]?\\d)[/.-]([01]?\\d)(?:[/.-](\\d{2,4}))?\\b")
}
