package com.srrotas.app

import java.time.Instant
import kotlin.math.max

/** Uses only verified backend event context; never creates events or opportunities. */
internal object RadarContextualEventEligibilityV1 {
    fun eligible(type: String, name: String, start: String, end: String,
        confidence: Double, eta: String, nowMs: Long): Boolean {
        if (!type.contains("event", ignoreCase = true) || name.isBlank() ||
            !confidence.isFinite() || confidence <= 0.0) return false
        val begin = runCatching { Instant.parse(start).toEpochMilli() }.getOrNull() ?: return false
        val finish = runCatching { Instant.parse(end).toEpochMilli() }.getOrNull() ?: return false
        val arrival = runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull() ?: return false
        if (finish <= begin) return false
        val windowStart = max(nowMs, arrival)
        val windowEnd = arrival + 10 * 60_000L
        return windowStart < windowEnd && finish > windowStart && begin < windowEnd
    }
}
