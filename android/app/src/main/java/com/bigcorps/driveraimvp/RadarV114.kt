package com.srrotas.app

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * vc114 (issue #11) — Radar com exatamente duas abas: "Próximas do destino" e "Eventos em geral".
 *
 * Só reapresenta dados existentes (Radar contextual e `/radar/events`): nunca inventa evento,
 * incidência, distância, horário ou endereço. Funções puras para teste.
 */
object RadarV114 {
    private const val PREFS = "sr_radar_v114"
    private const val ROLLBACK = "rollback_previous_radar"

    /** Nova tela é a padrão; o rollback (Diagnóstico › Radar anterior) volta à superfície anterior. */
    fun enabled(context: Context): Boolean = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ROLLBACK, false)
    fun setRollback(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ROLLBACK, value).apply()
    }

    enum class Icon(val drawable: Int) {
        MUSIC(R.drawable.sr114_ic_music), SPORTS(R.drawable.sr114_ic_trophy), GROUP(R.drawable.sr114_ic_groups),
        STORE(R.drawable.sr114_ic_store), TICKET(R.drawable.sr114_ic_ticket), FOOD(R.drawable.sr114_ic_food),
        BAG(R.drawable.sr114_ic_bag), CROSS(R.drawable.sr114_ic_cross), BED(R.drawable.sr114_ic_bed),
        PLANE(R.drawable.sr114_ic_plane), BUS(R.drawable.sr114_ic_bus), SCHOOL(R.drawable.sr114_ic_school),
        PLACE(R.drawable.sr114_ic_place),
    }

    data class Category(val label: String, val icon: Icon, val color: Int)

    private const val PURPLE = 0xFF7C3AED.toInt()
    private const val PINK = 0xFFE11D74.toInt()
    private const val RED = 0xFFE5484D.toInt()
    private const val BLUE = 0xFF1479FF.toInt()
    private const val GREEN = 0xFF16A34A.toInt()
    private const val ORANGE = 0xFFF0631E.toInt()
    private const val AMBER = 0xFFF5A00F.toInt()
    private const val TEAL = 0xFF0E9F9A.toInt()
    private const val SLATE = 0xFF5B6B82.toInt()

    /** Categoria curta de um evento (`event_type` do backend). */
    fun eventCategory(type: String): Category = when (type.lowercase(Locale.ROOT)) {
        "music", "show", "concert" -> Category("Show", Icon.MUSIC, PURPLE)
        "sports", "sport" -> Category("Esporte", Icon.SPORTS, GREEN)
        "theatre", "arts_theatre", "theater" -> Category("Teatro", Icon.TICKET, PINK)
        "fair_convention", "fair", "convention" -> Category("Feira", Icon.STORE, ORANGE)
        "festival", "food" -> Category("Festival", Icon.FOOD, AMBER)
        "family" -> Category("Família", Icon.GROUP, PURPLE)
        else -> Category("Evento", Icon.GROUP, PURPLE)
    }

    /** Categoria curta de um lugar (`poi_type` do backend). */
    fun placeCategory(poiType: String, hasEvent: Boolean): Category {
        if (hasEvent) return Category("Evento", Icon.GROUP, PURPLE)
        val t = poiType.lowercase(Locale.ROOT)
        return when {
            "mall" in t || "shopping" in t -> Category("Shopping", Icon.BAG, PINK)
            "hospital" in t || "clinic" in t || "health" in t -> Category("Hospital", Icon.CROSS, RED)
            "hotel" in t || "lodging" in t -> Category("Hotel", Icon.BED, BLUE)
            "airport" in t -> Category("Aeroporto", Icon.PLANE, TEAL)
            "bus" in t || "terminal" in t || "station" in t -> Category("Rodoviária", Icon.BUS, TEAL)
            "university" in t || "school" in t || "college" in t -> Category("Universidade", Icon.SCHOOL, BLUE)
            // Sem evento verificado, um estádio/arena é só um LOCAL de eventos (nunca "há evento").
            "stadium" in t || "arena" in t || "venue" in t || "event" in t || "convention" in t -> Category("Local de eventos", Icon.GROUP, PURPLE)
            "restaurant" in t || "food" in t || "bar" in t -> Category("Gastronomia", Icon.FOOD, AMBER)
            else -> Category("Local", Icon.PLACE, SLATE)
        }
    }

    /**
     * Frase curta e verificável do motivo (semântica distinta, issue #11):
     * evento com janela verificada ≠ alta incidência observada (só com potencial "high", que o backend
     * só atribui com probabilidade e ≥ 20 amostras) ≠ simples proximidade de um tipo de lugar.
     */
    fun opportunityReason(o: RadarContextualOpportunityV1, eventEligible: Boolean, zone: ZoneId = ZoneId.of("America/Sao_Paulo")): String {
        val ctx = o.context
        if (eventEligible && ctx != null) {
            val start = hm(ctx.egressStartAt, zone); val end = hm(ctx.egressEndAt, zone)
            return if (start != null && end != null) "Evento registrado · saída $start–$end" else "Evento registrado próximo"
        }
        if (o.potential == "high") return "Alta incidência observada"
        val cat = placeCategory(o.poiType, false)
        return if (cat.label == "Local") "Local verificado próximo" else "${cat.label} próximo"
    }

    fun distance(km: Double, fromDestination: Boolean): String? {
        if (!km.isFinite() || km < 0) return null
        return String.format(Locale("pt", "BR"), "%.1f km", km) + if (fromDestination) " do destino" else " de você"
    }

    /** "Hoje · 20h00", "Amanhã · 09h00", "Sáb · 18h00"; nulo se o horário não for válido. */
    fun dayTime(iso: String?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("America/Sao_Paulo")): String? {
        val at = runCatching { Instant.parse(iso ?: return null) }.getOrNull() ?: return null
        val day = at.atZone(zone).toLocalDate()
        val today = LocalDate.ofInstant(now, zone)
        val label = when (day) {
            today -> "Hoje"
            today.plusDays(1) -> "Amanhã"
            else -> DateTimeFormatter.ofPattern("EEE", Locale("pt", "BR")).format(at.atZone(zone))
                .replace(".", "").replaceFirstChar { it.titlecase(Locale("pt", "BR")) }
        }
        return label + " · " + DateTimeFormatter.ofPattern("HH'h'mm", Locale("pt", "BR")).format(at.atZone(zone))
    }

    fun hm(iso: String?, zone: ZoneId = ZoneId.of("America/Sao_Paulo")): String? =
        runCatching { DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR")).withZone(zone).format(Instant.parse(iso ?: return null)) }.getOrNull()

    /** Eventos ativos e futuros (nada encerrado), mais próximos primeiro. */
    fun visibleEvents(list: List<EventRadarOpportunity026>, now: Instant = Instant.now()): List<EventRadarOpportunity026> =
        list.filter { e ->
            val end = runCatching { Instant.parse(e.expectedEndAt) }.getOrNull()
                ?: runCatching { Instant.parse(e.egressEndAt) }.getOrNull()
            val start = runCatching { Instant.parse(e.startsAt) }.getOrNull()
            // Sem horário de término válido, só eventos que ainda não começaram (nunca um evento passado).
            EventRadarRules026.visible(e) && e.distanceKm.isFinite() &&
                (if (end != null) end.isAfter(now) else start != null && start.isAfter(now))
        }.sortedBy { it.distanceKm }

    /** Duas colunas só quando cabem sem cortar texto crítico (fonte do sistema considerada). */
    fun columns(availableDp: Float, fontScale: Float): Int =
        if (availableDp - 10f >= 2 * 168f * fontScale.coerceAtLeast(1f)) 2 else 1

    /** Referência de distância dos eventos: localização recente; senão destino da corrida; senão nenhuma. */
    enum class Reference { YOU, DESTINATION, NONE }

    fun reference(hasFreshLocation: Boolean, hasDestination: Boolean) = when {
        hasFreshLocation -> Reference.YOU
        hasDestination -> Reference.DESTINATION
        else -> Reference.NONE
    }

    /** Fonte de eventos desatualizada (> 24 h) é sinalizada, nunca apresentada como recente. */
    fun staleSource(refreshedAt: String?, now: Instant = Instant.now()): Boolean =
        runCatching { Instant.parse(refreshedAt ?: return false) }.getOrNull()?.let { now.toEpochMilli() - it.toEpochMilli() > 24 * 3_600_000L } ?: false
}
