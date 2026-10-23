package com.srrotas.app

import java.time.Instant

/** Pure delivery gate; ETA is advisory for a ride, never an automatic ride-completion signal. */
internal object DestinationRadarFreshnessV1 {
    fun blockReason(eta: String, nowMs: Long): String? {
        val arrivalMs = runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull()
            ?: return "eta_invalid"
        return if (arrivalMs <= nowMs) "eta_expired" else null
    }

    /** A real banner cannot remain on screen after the active ride's ETA. */
    fun bannerDurationMs(eta: String, nowMs: Long, preferredMs: Long): Long {
        val arrivalMs = runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull() ?: return 0L
        return minOf(preferredMs.coerceAtLeast(0L), (arrivalMs - nowMs).coerceAtLeast(0L))
    }
}
