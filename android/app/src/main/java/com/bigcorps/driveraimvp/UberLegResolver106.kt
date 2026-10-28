package com.srrotas.app

import kotlin.math.abs
import kotlin.math.max

/**
 * vc106 — distingue TOTAL, BUSCA e VIAGEM nos cards Uber `min • km`.
 *
 * Causa confirmada em replay de OCR real (vc105): o par só era reconhecido entre
 * parênteses; nos cards atuais o fallback por ordem do texto tomava o total do
 * cabeçalho (`20 min • 3.5 km • Est. de R$5,14/km`) como busca e a busca como
 * viagem, omitindo a viagem real. A distinção aqui é estrutural e verificável:
 *  - o cabeçalho de total traz o marcador `Est. de R$x/km` na MESMA linha;
 *  - sem esse marcador, um dos três pares precisa ser a soma exata dos outros dois;
 *  - busca/viagem seguem a ordem do card somente depois de excluído o total.
 * Qualquer outra combinação é ambígua e o chamador não publica valores derivados.
 */
object UberLegResolver106 {
    data class Leg(val minutes: Int, val km: Double, val line: Int, val header: Boolean)

    sealed class Resolution {
        /** Nenhum par `min • km` na mesma linha: layout legado segue seu caminho. */
        object None : Resolution()
        data class Resolved(val pickup: UberOfferDetector.TimeDistance, val trip: UberOfferDetector.TimeDistance,
            val total: UberOfferDetector.TimeDistance?, val evidence: String) : Resolution()
        data class Ambiguous(val reason: String) : Resolution()
    }

    private val separator = "[\\s·•*\\-–—:;«»º°|~.,]{0,4}"
    private val pairRegex = Regex(
        "(${UberDurationParser026.durationPattern})$separator([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*km\\b",
        RegexOption.IGNORE_CASE,
    )
    private val headerMarker = Regex(
        "(?:R\\$|\\$)\\s*[0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?\\s*/\\s*km|\\best\\.?\\s*de\\b",
        RegexOption.IGNORE_CASE,
    )
    private val rangeMinutes = Regex("\\b[0-9]{1,2}\\s*[-–—]\\s*[0-9]{1,2}\\s*min", RegexOption.IGNORE_CASE)

    fun legs(text: String): List<Leg> = text.lines().flatMapIndexed { index, rawLine ->
        val line = UberDurationParser026.normalizeOcrText(rawLine)
        // Map ETA bands such as "1-3 min" never belong to the card geometry.
        if (rangeMinutes.containsMatchIn(line)) return@flatMapIndexed emptyList()
        val header = headerMarker.containsMatchIn(line)
        pairRegex.findAll(line).mapNotNull { match ->
            val minutes = UberDurationParser026.parseCandidate(match.groupValues[1].trim()) ?: return@mapNotNull null
            val km = OfferParser.parseNumberCandidate(match.groupValues[2]) ?: return@mapNotNull null
            if (minutes !in 1..360 || km !in 0.05..500.0) null else Leg(minutes, km, index, header)
        }.toList()
    }

    fun resolve(text: String): Resolution = resolve(legs(text))

    fun resolve(all: List<Leg>): Resolution {
        if (all.isEmpty()) return Resolution.None
        val headers = all.filter { it.header }
        if (headers.size > 1) return Resolution.Ambiguous("multiple_total_headers")
        val header = headers.singleOrNull()
        val route = all.filterNot { it.header }.sortedBy { it.line }
        return when {
            route.size == 2 -> {
                val (pickup, trip) = route
                if (header != null && !sums(header, pickup, trip)) Resolution.Ambiguous("legs_disagree_with_total")
                else Resolution.Resolved(td(pickup), td(trip), (header ?: synthetic(pickup, trip)).let(::td),
                    if (header != null) "header_total+two_legs" else "two_legs")
            }
            route.size == 3 && header == null -> when {
                sums(route[0], route[1], route[2]) -> Resolution.Resolved(td(route[1]), td(route[2]), td(route[0]), "sum_total_first")
                sums(route[2], route[0], route[1]) -> Resolution.Resolved(td(route[0]), td(route[1]), td(route[2]), "sum_total_last")
                else -> Resolution.Ambiguous("three_legs_without_total")
            }
            route.size > 2 -> Resolution.Ambiguous("too_many_legs")
            header != null -> Resolution.Ambiguous("total_without_both_legs")
            else -> Resolution.Ambiguous("single_leg")
        }
    }

    private fun synthetic(a: Leg, b: Leg) = Leg(a.minutes + b.minutes, a.km + b.km, -1, false)
    private fun td(leg: Leg) = UberOfferDetector.TimeDistance(leg.minutes, leg.km)

    /** total ≈ a + b, with the same tolerances as OfferIntegrityGate027033. */
    fun sums(total: Leg, a: Leg, b: Leg): Boolean {
        val km = a.km + b.km
        return abs(total.minutes - (a.minutes + b.minutes)) <= 1 && abs(total.km - km) <= max(0.20, km * 0.04)
    }
}
