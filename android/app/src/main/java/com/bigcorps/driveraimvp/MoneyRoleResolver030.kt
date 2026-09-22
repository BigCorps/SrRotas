package com.srrotas.app

import kotlin.math.abs
import kotlin.math.max

/**
 * 0.30 Field2 — classificação semântica de valores monetários dentro do card.
 *
 * Nem todo R$ é tarifa. Este resolver separa tarifa principal, R$/km anunciado,
 * promoção/bônus e outros valores secundários antes de o valor virar âncora de
 * card. Nenhum número é "corrigido" por heurística.
 */
internal object MoneyRoleResolver030 {
    enum class Role {
        PRIMARY_FARE,
        ADVERTISED_PER_KM,
        PROMOTION_BONUS,
        SECONDARY_MONEY,
        UNKNOWN_MONEY,
    }

    data class Candidate(
        val lineIndex: Int,
        val value: Double,
        val role: Role,
        val reason: String,
        val rawLine: String,
    )

    private val moneyRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val advertisedRegex = Regex(
        "(?:R\\$|\\$)\\s*[0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?\\s*/\\s*km",
        RegexOption.IGNORE_CASE,
    )
    private val promotionAnchors = listOf(
        "turbo mais", "turbo+", "turbo +", "promoção", "promocao",
        "bônus", "bonus", "incentivo", "ganho extra", "valor extra",
    )
    private val secondaryAnchors = listOf(
        "incluído", "incluido", "pedágio", "pedagio", "gorjeta", "taxa",
        "ganhos", "reembolso",
    )
    private val uberAnchors = listOf(
        "aceitar", "selecionar", "exclusivo", "radar de viagens", "uberx",
        "comfort", "priority", "uber moto", "ubermoto", "black", "electric",
    )

    /** Inspeção textual independente da geometria. */
    fun inspectText(rawText: String): List<Candidate> {
        val lines = normalize(rawText).lines().map(String::trim).filter(String::isNotBlank)
        if (lines.isEmpty()) return emptyList()

        val provisional = lines.mapIndexedNotNull { index, line ->
            val match = moneyRegex.find(line) ?: return@mapIndexedNotNull null
            val value = parseNumber(match.groupValues[1]) ?: return@mapIndexedNotNull null
            if (value !in 2.0..1000.0) return@mapIndexedNotNull null

            // Rótulos de bônus normalmente antecedem o valor. Não usamos a linha
            // seguinte para não transformar a tarifa principal em promoção caso
            // um novo layout coloque "Turbo Mais" imediatamente abaixo dela.
            val context = listOfNotNull(lines.getOrNull(index - 1), line)
                .joinToString(" ")
                .lowercase()
            val classified = classifyLineAndContext(line, context)
            Candidate(index, value, classified.first, classified.second, line)
        }
        if (provisional.isEmpty()) return emptyList()

        var primaryAssigned = false
        return provisional.map { candidate ->
            if (candidate.role != Role.UNKNOWN_MONEY) return@map candidate
            if (!primaryAssigned) {
                primaryAssigned = true
                candidate.copy(role = Role.PRIMARY_FARE, reason = "first_plain_money_in_card")
            } else {
                candidate
            }
        }
    }

    /** Seleção textual oficial dentro de um cluster já isolado. */
    fun primaryFare(rawText: String): Double? =
        inspectText(rawText).firstOrNull { it.role == Role.PRIMARY_FARE }?.value

    /**
     * Segunda seleção de tarifa para o shadow. Usa pontuação própria e nunca
     * recebe a decisão M1 como entrada.
     */
    fun shadowPrimaryFare(rawText: String): Double? {
        val normalized = normalize(rawText)
        val lines = normalized.lines().map(String::trim).filter(String::isNotBlank)
        val candidates = inspectText(normalized)
            .filter { it.role == Role.PRIMARY_FARE || it.role == Role.UNKNOWN_MONEY }
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { candidate ->
            var score = 0.0
            if (candidate.role == Role.PRIMARY_FARE) score += 4.0
            score += max(0.0, 3.0 - candidate.lineIndex * 0.12)
            val before = lines.take(candidate.lineIndex + 1).joinToString(" ").lowercase()
            val after = lines.drop(candidate.lineIndex).take(6).joinToString(" ").lowercase()
            if (uberAnchors.any(before::contains) || uberAnchors.any(after::contains)) score += 1.0
            if (after.contains("/km") || after.contains("aprox")) score += 1.2
            if (after.contains("verificado") || Regex("\\b[45][.,][0-9]{1,2}\\b").containsMatchIn(after)) score += 0.8
            if (promotionAnchors.any(after::contains)) score += 0.4
            score
        }?.value
    }

    /** Compatibilidade para pontos legados que só recebem uma linha. */
    fun isPrimaryFareLine(rawLine: String): Boolean {
        val line = normalize(rawLine).trim()
        val match = moneyRegex.find(line) ?: return false
        val value = parseNumber(match.groupValues[1]) ?: return false
        if (value !in 2.0..1000.0) return false
        val role = classifyLineAndContext(line, line.lowercase()).first
        return role == Role.UNKNOWN_MONEY || role == Role.PRIMARY_FARE
    }

    fun looksUberContext(lines: List<SpatialOcrLine>): Boolean {
        val lower = lines.joinToString("\n") { it.text }.lowercase()
        return uberAnchors.any(lower::contains)
    }

    /**
     * Retorna somente linhas que podem realmente iniciar um card Uber.
     *
     * Proteção estrutural: se um segundo R$ aparece na mesma coluna antes de
     * qualquer geometria/ação que delimite outro card, ele não vira nova âncora.
     * Assim o sistema não depende apenas da string "Turbo Mais".
     */
    fun primarySpatialFareLines(lines: List<SpatialOcrLine>): List<SpatialOcrLine> {
        if (lines.isEmpty()) return emptyList()
        val ordered = lines.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })
        val eligible = ordered.filter { line ->
            val match = moneyRegex.find(normalize(line.text)) ?: return@filter false
            val value = parseNumber(match.groupValues[1]) ?: return@filter false
            value in 2.0..1000.0 && !isExplicitSecondarySpatialMoney(ordered, line)
        }
        if (eligible.isEmpty()) return emptyList()

        val accepted = mutableListOf<SpatialOcrLine>()
        for (candidate in eligible) {
            val previous = accepted.lastOrNull { sameColumn(it, candidate) }
            if (previous == null) {
                accepted += candidate
                continue
            }
            val top = previous.box.centerY()
            val bottom = candidate.box.centerY()
            val between = ordered.filter { line ->
                val y = line.box.centerY()
                y > top && y < bottom && sameColumn(previous, line)
            }
            if (between.any(::isRouteOrCardBoundary)) {
                accepted += candidate
            }
        }
        return accepted
    }

    fun isExplicitSecondarySpatialMoney(
        lines: List<SpatialOcrLine>,
        target: SpatialOcrLine,
    ): Boolean {
        val line = normalize(target.text)
        if (!moneyRegex.containsMatchIn(line)) return false
        val ordered = lines.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })
        val preceding = ordered
            .filter { it !== target && it.box.centerY() < target.box.centerY() && sameColumn(it, target) }
            .sortedByDescending { it.box.centerY() }
            .take(2)
        val context = (preceding.map { it.text } + target.text).joinToString(" ").lowercase()
        val role = classifyLineAndContext(line, context).first
        return role == Role.ADVERTISED_PER_KM ||
            role == Role.PROMOTION_BONUS ||
            role == Role.SECONDARY_MONEY
    }

    private fun classifyLineAndContext(lineRaw: String, contextRaw: String): Pair<Role, String> {
        val line = normalize(lineRaw).lowercase()
        val context = normalize(contextRaw).lowercase()
        if (advertisedRegex.containsMatchIn(line) || line.contains("/km") || line.contains("aprox")) {
            return Role.ADVERTISED_PER_KM to "advertised_per_km"
        }
        if (Regex("\\+\\s*(?:R\\$|\\$)", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
            return Role.SECONDARY_MONEY to "plus_money"
        }
        if (promotionAnchors.any(context::contains)) {
            return Role.PROMOTION_BONUS to "promotion_context"
        }
        if (secondaryAnchors.any(context::contains)) {
            return Role.SECONDARY_MONEY to "secondary_context"
        }
        return Role.UNKNOWN_MONEY to "plain_money"
    }

    private fun isRouteOrCardBoundary(line: SpatialOcrLine): Boolean {
        val text = normalize(line.text).lowercase()
        if (FlexibleDriverOfferParser.geometryRegex.containsMatchIn(text)) return true
        if (Regex("\\b[0-9OSoIlL]{1,3}\\s*(?:min|minuto|minutos)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        if (!moneyRegex.containsMatchIn(text) && Regex("\\b[0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?\\s*(?:km|m)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        return listOf("aceitar", "selecionar", "exclusivo", "radar de viagens").any(text::contains)
    }

    private fun sameColumn(a: SpatialOcrLine, b: SpatialOcrLine): Boolean {
        val overlap = minOf(a.box.right, b.box.right) - maxOf(a.box.left, b.box.left)
        if (overlap > 0) return true
        val width = max(a.box.width(), b.box.width()).coerceAtLeast(1)
        return abs(a.box.centerX() - b.box.centerX()) <= width * 1.35
    }

    private fun parseNumber(raw: String): Double? {
        val candidate = BRUberLineSanitizer.normalizeNumericToken(raw).trim()
        if (!candidate.any(Char::isDigit)) return null
        val cleaned = if (candidate.contains(',') && candidate.contains('.')) {
            candidate.replace(".", "").replace(',', '.')
        } else {
            candidate.replace(',', '.')
        }
        return cleaned.toDoubleOrNull()
    }

    private fun normalize(raw: String): String =
        UberDurationParser026.normalizeOcrText(BRUberLineSanitizer.sanitize(raw))
}
