package com.srrotas.app

import android.content.Context
import java.time.Instant

object UberJourneyLinker0262 {
    fun attach(context: Context, summary: UberSessionSummary026): UberSessionSummary026 {
        if (!summary.journeyId.isNullOrBlank()) return summary
        val start = summary.startedAt?.let(::instant) ?: return summary
        val end = summary.endedAt?.let(::instant) ?: return summary
        val store = LocalStore.get(context)
        val candidates = store.journeysInRange(
            start.minusSeconds(6 * 3600L).toString(),
            end.plusSeconds(6 * 3600L).toString(),
            30,
        ).map {
            UberJourneyLinkRules0262.Candidate(it.id, it.startedAt, it.endedAt)
        }
        val matched = UberJourneyLinkRules0262.match(summary.startedAt, summary.endedAt, candidates)
        return if (matched == null) summary else summary.copy(journeyId = matched)
    }

    private fun instant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
}
