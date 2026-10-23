package com.srrotas.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Compact presentation only. Does not change Radar evidence, probabilities or ETA calculation. */
internal object RadarCompactOverlayV1 {
    fun address(label: String?): String {
        val source = label?.trim()?.takeIf { it.isNotEmpty() } ?: return "Destino da corrida"
        return if (source.length <= 55) source else source.take(52).trimEnd() + "…"
    }

    fun arrival(eta: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val arrival = runCatching { Instant.parse(eta) }.getOrNull()
            ?: return "Chegada indisponível"
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))
            .withZone(zone).format(arrival)
        val minutes = ceil((arrival.toEpochMilli() - nowMs) / 60_000.0).toInt()
        return if (minutes > 0) "ETA ${minutes} min · Chegada ${time}"
            else "ETA vencida · Chegada ${time}"
    }

    fun probability(value: Double?, samples: Int): String? {
        if (samples <= 0 || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return "Prob. ${value.roundToInt()}% · até 10 min"
    }
}
