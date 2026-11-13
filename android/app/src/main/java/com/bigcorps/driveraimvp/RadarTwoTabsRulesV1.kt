package com.srrotas.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/** Presentation rules only. Source of publication/approval remains the authenticated Radar API. */
internal object RadarTwoTabsRulesV1 {
    const val EVENT_TTL_MS = 5 * 60_000L
    const val DESTINATION_TTL_MS = 3 * 60_000L
    enum class Tab(val label: String) { DESTINATION("Próximas do destino"), EVENTS("Eventos em geral") }
    data class Card(
        val id: String, val title: String, val type: String, val distanceKm: Double,
        val reference: String, val description: String, val provenance: String?,
        val lat: Double?, val lng: Double?, val mapsLabel: String?, val facts: List<String> = emptyList(),
    )

    fun fresh(atMs: Long, nowMs: Long, ttlMs: Long): Boolean = atMs > 0 && nowMs - atMs in 0..ttlMs
    fun instant(value: String?): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    fun validDestination(spec: RadarDestinationSpecV1?, nowMs: Long): Boolean = spec != null &&
        spec.localOfferId.isNotBlank() && RadarExternalMapsV1.validCoordinate(spec.lat, spec.lng) &&
        DestinationRadarFreshnessV1.blockReason(spec.eta, nowMs) == null

    fun columns(widthDp: Float, fontScale: Float): Int =
        if (widthDp >= 2 * 240f * max(1f, fontScale) + 10f) 2 else 1

    fun events(result: EventRadarResult026, nowMs: Long): List<Card> = result.opportunities
        .filter { e ->
            EventRadarRules026.visible(e) && e.id.isNotBlank() &&
                (e.status == null || e.status == "active") &&
                (e.reviewStatus == null || e.reviewStatus == "approved") &&
                (instant(e.startsAt) ?: Long.MIN_VALUE) > nowMs &&
                (instant(e.egressEndAt) ?: Long.MIN_VALUE) > nowMs
        }.distinctBy { it.id }.sortedBy { it.distanceKm }.map { e ->
            val address = e.address?.takeIf(String::isNotBlank)
            val venue = e.venueName?.takeIf(String::isNotBlank)
            Card(e.id, e.name, e.type, e.distanceKm, "de você",
                listOfNotNull(venue, "${date(e.startsAt)} · ${time(e.startsAt)}").joinToString(" · "),
                "Fonte: ${e.source}", e.lat, e.lng,
                listOfNotNull(venue, address).joinToString(", ").takeIf(String::isNotBlank),
                listOfNotNull(address, "Saída: ${time(e.egressStartAt)}–${time(e.egressEndAt)}"))
        }

    fun matchesDestination(result: RadarContextualResultV1, spec: RadarDestinationSpecV1): Boolean =
        RadarExternalMapsV1.validCoordinate(result.destinationLat, result.destinationLng) &&
            abs(result.destinationLat - spec.lat) <= 0.000001 && abs(result.destinationLng - spec.lng) <= 0.000001 &&
            instant(result.destinationEta) != null && instant(result.destinationEta) == instant(spec.eta)

    fun destination(result: RadarContextualResultV1, spec: RadarDestinationSpecV1, nowMs: Long): List<Card> {
        if (!validDestination(spec, nowMs) || !matchesDestination(result, spec)) return emptyList()
        return result.opportunities.filter {
            it.id.isNotBlank() && it.title.isNotBlank() && RadarExternalMapsV1.validCoordinate(it.lat, it.lng)
        }.distinctBy { it.poiId.ifBlank { it.id } }.map { p ->
            val event = p.context?.takeIf {
                RadarContextualEventEligibilityV1.eligible(it.type, it.name, it.egressStartAt,
                    it.egressEndAt, it.confidence, spec.eta, nowMs)
            }
            Card(p.id, p.title, p.poiType, distance(spec.lat, spec.lng, p.lat, p.lng), "do destino",
                event?.let { "${it.name} · Saída ${time(it.egressStartAt)}–${time(it.egressEndAt)}" }
                    ?: EventRadarRules026.typeLabel(p.poiType),
                null, p.lat, p.lng, p.title,
                listOfNotNull(p.subtitle?.takeIf(String::isNotBlank),
                    event?.let { "Evento verificado: ${it.name}" }))
        }.sortedBy { it.distanceKm }
    }

    fun distance(lat: Double, lng: Double, otherLat: Double, otherLng: Double): Double {
        val a = Math.toRadians(lat); val b = Math.toRadians(otherLat)
        val h = sin((b - a) / 2).pow(2) + cos(a) * cos(b) * sin(Math.toRadians(otherLng - lng) / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun distanceLabel(card: Card) = String.format(Locale("pt", "BR"), "%.1f km %s", card.distanceKm, card.reference)
    fun time(value: String) = format(value, "HH:mm")
    private fun date(value: String) = format(value, "dd/MM")
    private fun format(value: String, pattern: String): String = runCatching {
        DateTimeFormatter.ofPattern(pattern, Locale("pt", "BR")).withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value))
    }.getOrDefault("Horário indisponível")
}
