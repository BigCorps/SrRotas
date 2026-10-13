package com.srrotas.app

import java.time.Instant

/** Pure delivery gate; ETA is advisory for a ride, never an automatic ride-completion signal. */
internal object DestinationRadarFreshnessV1 {
    fun blockReason(eta: String, nowMs: Long): String? {
        val arrivalMs = runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull()
            ?: return "eta_invalid"
        return if (arrivalMs <= nowMs) "eta_expired" else null
    }
}
