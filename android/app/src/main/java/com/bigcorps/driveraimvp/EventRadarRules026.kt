package com.srrotas.app

import java.time.Duration
import java.time.Instant

object EventRadarRules026 {
    fun typeLabel(type: String): String = when (type.lowercase()) {
        "music", "show" -> "Show"
        "sports" -> "Esporte"
        "theatre", "arts_theatre" -> "Teatro / cultura"
        "fair_convention" -> "Feira / convenção"
        "family" -> "Família"
        "airport" -> "Aeroporto"
        "bus_terminal" -> "Rodoviária"
        "mall" -> "Shopping"
        else -> "Evento"
    }

    fun confidenceLabel(value: Double): String = when {
        value >= 0.85 -> "alta"
        value >= 0.68 -> "média"
        else -> "baixa"
    }

    fun urgencyLabel(
        now: Instant,
        opportunity: EventRadarOpportunity026,
    ): String {
        val egress = runCatching { Instant.parse(opportunity.egressStartAt) }.getOrNull()
            ?: return "horário estimado"
        val minutes = Duration.between(now, egress).toMinutes()
        return when {
            minutes < -30 -> "saída já passou"
            minutes <= 15 -> "saída começando agora"
            minutes <= 60 -> "saída em ${minutes.coerceAtLeast(0)} min"
            else -> "saída em ${minutes / 60}h${(minutes % 60).takeIf { it > 0 }?.let { "${it}min" } ?: ""}"
        }
    }

    fun visible(opportunity: EventRadarOpportunity026): Boolean =
        opportunity.distanceKm in 0.0..30.0 &&
            opportunity.confidence >= 0.50 &&
            opportunity.name.isNotBlank()
}
