package com.srrotas.app

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Associação conservadora entre o Resumo da sessão Uber e uma jornada Sr. Rotas. */
object UberJourneyLinkRules0262 {
    data class Candidate(
        val id: String,
        val startedAt: String,
        val endedAt: String?,
    )

    fun match(
        sessionStartedAt: String?,
        sessionEndedAt: String?,
        candidates: List<Candidate>,
    ): String? {
        val sessionStart = sessionStartedAt?.let(::instant) ?: return null
        val sessionEnd = sessionEndedAt?.let(::instant) ?: return null
        if (sessionEnd.isBefore(sessionStart)) return null

        val scored = candidates.mapNotNull { candidate ->
            val start = instant(candidate.startedAt) ?: return@mapNotNull null
            val end = candidate.endedAt?.let(::instant)
            val startDiff = abs(Duration.between(sessionStart, start).toMinutes())
            if (startDiff > 240) return@mapNotNull null
            val endDiff = if (end != null) abs(Duration.between(sessionEnd, end).toMinutes()) else 60L
            if (end != null && endDiff > 240) return@mapNotNull null

            val effectiveEnd = end ?: sessionEnd
            val overlapStart = if (start.isAfter(sessionStart)) start else sessionStart
            val overlapEnd = if (effectiveEnd.isBefore(sessionEnd)) effectiveEnd else sessionEnd
            val overlap = Duration.between(overlapStart, overlapEnd).toMinutes().coerceAtLeast(0)
            val sessionMinutes = Duration.between(sessionStart, sessionEnd).toMinutes().coerceAtLeast(1)
            if (overlap < minOf(15L, sessionMinutes / 3)) return@mapNotNull null

            val overlapBonus = minOf(60L, overlap) / 3.0
            candidate.id to (startDiff + endDiff - overlapBonus)
        }.sortedBy { it.second }

        val best = scored.firstOrNull() ?: return null
        if (best.second > 300.0) return null
        val second = scored.getOrNull(1)
        if (second != null && second.second - best.second < 20.0) return null
        return best.first
    }

    private fun instant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
}
