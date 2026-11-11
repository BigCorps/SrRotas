package com.srrotas.app.r3lab

import com.srrotas.app.DriverSettings
import com.srrotas.app.FlexibleDriverOfferParser
import com.srrotas.app.MoneyRoleResolver030
import com.srrotas.app.OfferIntegrityGate027033
import com.srrotas.app.RideOffer
import com.srrotas.app.SpatialOcrLine
import com.srrotas.app.UberLegResolver106
import java.util.Locale

/** Perfis próprios e versionados do laboratório (não derivados de scripts de terceiros). */
object R3DefaultProfiles {
    /** Milhar com ponto ("R$ 1.234,56") ou valor simples; nunca corta o número no meio. */
    private const val FARE = """R\$\s*(\d{1,3}(?:\.\d{3})+,\d{2}|\d{1,4}[.,]\d{2})(?![\d.,])"""
    private const val PER_KM = """R\$\s*(\d{1,2}[.,]\d{2})\s*/\s*km"""
    /** "1 h 10 min • 30 km": horas obrigatórias (nunca ler só "10 min"). */
    private const val LEG_HM = """(\d{1,2})\s*h(?:rs?|oras?)?\s*(\d{1,2})\s*min\S*\s*[•·(\-|]?\s*\(?(\d{1,3}(?:[.,]\d{1,2})?)\s*km"""
    private const val RATING = """(\d[.,]\d{1,2})\s*\(\d{1,6}\)"""

    fun uber() = listOf(
        R3ExtractionRule("fare", FARE, listOf("fare")),
        R3ExtractionRule("leg_hm", LEG_HM, listOf("leg_h", "leg_min", "leg_km")),
        R3ExtractionRule("leg", """(\d{1,3})\s*(?:minutos|min)(?![a-zà-ú])[^0-9]{0,6}(\d{1,3}(?:[.,]\d{1,2})?)\s*km""", listOf("leg_min", "leg_km")),
        R3ExtractionRule("per_km", PER_KM, listOf("advertised_per_km")),
        R3ExtractionRule("service", """\b(UberX|Comfort|Black|Flash|Moto|Priority|Juntos|Bag|Envios)\b""", listOf("service_type")),
        R3ExtractionRule("action", """\b(Aceitar|Selecionar|Tenho interesse|Confirmar)\b""", listOf("action")),
        R3ExtractionRule("rating", RATING, listOf("rating")),
    )

    fun ninetyNine() = listOf(
        R3ExtractionRule("fare", FARE, listOf("fare")),
        R3ExtractionRule("leg_hm", LEG_HM, listOf("leg_h", "leg_min", "leg_km")),
        R3ExtractionRule("leg", """(\d{1,3})\s*min\S*\s*[•·(\-|]?\s*\(?(\d{1,3}(?:[.,]\d{1,2})?)\s*km""", listOf("leg_min", "leg_km")),
        R3ExtractionRule("per_km", PER_KM, listOf("advertised_per_km")),
        R3ExtractionRule("service", """\b(99Pop|Pop|Comfort|Black|Moto|Entrega|Plus)\b""", listOf("service_type")),
        R3ExtractionRule("action", """\b(Aceitar|Aceito|Pegar corrida)\b""", listOf("action")),
        R3ExtractionRule("rating", RATING, listOf("rating")),
    )

    val REQUIRED = setOf("fare", "leg_min", "leg_km")

    fun installDefaults(manager: R3ProfileManager) {
        if (manager.activeFor(R3AllowedPackages.UBER) == null) manager.install("uber-r3-1", R3AllowedPackages.UBER, REQUIRED, uber())
        if (manager.activeFor(R3AllowedPackages.NINETY_NINE) == null) manager.install("99-r3-1", R3AllowedPackages.NINETY_NINE, REQUIRED, ninetyNine())
    }
}

/** Candidato de UM quadro (observação). `offer` só existe se o parser oficial aceitou os campos. */
data class R3Candidate(
    val platform: String,
    val fields: Map<String, R3FieldEvidence>,
    val offer: RideOffer?,
    val rejection: String?,
    val confidence: Double,
) {
    val accepted get() = offer != null && rejection == null
    /** Identidade semântica do card para o estabilizador (nunca para vincular imagem). */
    fun identity(windowKey: String): String {
        fun f(n: String) = fields[n]?.value.orEmpty()
        // Sem trechos (card rejeitado), a identidade usa TODOS os campos lidos: dois cards rejeitados
        // diferentes com a mesma tarifa/motivo não se fundem nem trocam campos entre si.
        if (!fields.containsKey("pickup_min")) return (listOf(windowKey, platform, rejection.orEmpty()) +
            fields.toSortedMap().map { (k, v) -> "$k=${v.value}" }).joinToString("|")
        return listOf(windowKey, platform, f("fare"), f("pickup_min"), f("pickup_km"), f("trip_min"), f("trip_km")).joinToString("|")
    }
}

/**
 * Modo D — extração por perfil: papéis por geometria relativa (tarifa principal → trechos → endereços),
 * sem coordenadas absolutas. O resultado passa pelo parser oficial (`FlexibleDriverOfferParser`) e
 * pelo `OfferIntegrityGate027033`: os cálculos financeiros são os do Sr. Rotas, sem bypass.
 */
internal object R3ProfileExtractor {
    private val HEADER = Regex("""(?i)\best\.?\s*de\b|/\s*km""")
    private val NOISE = Regex("""(?i)^(aceitar|selecionar|tenho interesse|confirmar|verificado|exclusivo|recusar)$""")

    fun extract(profile: R3ProfileBundle, lines: List<SpatialOcrLine>, frameId: String, observationId: String,
                settings: DriverSettings, frameWidth: Int): List<R3Candidate> {
        val sorted = lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        val fareLines = fareLines(profile, sorted)
        if (fareLines.isEmpty()) return emptyList()
        return fareLines.map { fare ->
            // Coluna do card: alinhamento à esquerda com a tarifa ou sobreposição majoritária com a faixa
            // do card (linhas longas de um card vizinho em tela dividida não entram).
            val colStart = fare.box.left - frameWidth * 0.05; val colEnd = fare.box.left + frameWidth * 0.45
            val sameColumn = { l: SpatialOcrLine ->
                val overlap = (minOf(l.box.right.toDouble(), colEnd) - maxOf(l.box.left.toDouble(), colStart)).coerceAtLeast(0.0)
                kotlin.math.abs(l.box.left - fare.box.left) < frameWidth * 0.25 || overlap >= l.box.width() * 0.5
            }
            val nextFareTop = fareLines.filter { it !== fare && it.box.top > fare.box.top && sameColumn(it) }.minOfOrNull { it.box.top } ?: Int.MAX_VALUE
            val region = sorted.filter { it.box.top >= fare.box.top - fare.box.height() && it.box.top < nextFareTop && sameColumn(it) }
            card(profile, fare, region, frameId, observationId, settings)
        }
    }

    private fun fareLines(profile: R3ProfileBundle, sorted: List<SpatialOcrLine>): List<SpatialOcrLine> {
        val matching = sorted.filter { profile.match("fare", it.text) != null && profile.match("per_km", it.text) == null }
        return if (profile.platform == "uber") {
            val primary = runCatching { MoneyRoleResolver030.primarySpatialFareLines(sorted) }.getOrDefault(emptyList())
            matching.filter { m -> primary.any { it === m || (it.text == m.text && it.box == m.box) } }
        } else matching.filterNot { Regex("""(?i)\+\s*R\$|inclu[ií]d|promo|b[oô]nus|gorjeta""").containsMatchIn(it.text) }
    }

    private fun card(profile: R3ProfileBundle, fareLine: SpatialOcrLine, region: List<SpatialOcrLine>, frameId: String,
                     observationId: String, settings: DriverSettings): R3Candidate {
        val fields = LinkedHashMap<String, R3FieldEvidence>()
        fun put(name: String, value: String?, line: SpatialOcrLine?, confidence: Double) {
            if (value.isNullOrBlank()) return
            fields[name] = R3FieldEvidence(name, frameId, observationId, value.trim(), R3FieldOrigin.OBSERVED, confidence,
                line?.box?.let { intArrayOf(it.left, it.top, it.right, it.bottom) })
        }
        put("fare", profile.match("fare", fareLine.text)?.get("fare"), fareLine, 0.95)
        region.firstNotNullOfOrNull { l -> profile.match("service", l.text)?.get("service_type")?.let { it to l } }?.let { put("service_type", it.first, it.second, 0.8) }
        region.firstNotNullOfOrNull { l -> profile.match("per_km", l.text)?.get("advertised_per_km")?.let { it to l } }?.let { put("advertised_per_km", it.first, it.second, 0.8) }
        region.firstNotNullOfOrNull { l -> profile.match("rating", l.text)?.get("rating")?.let { it to l } }?.let { put("rating", it.first, it.second, 0.7) }

        data class Leg(val min: Int, val km: Double, val line: SpatialOcrLine, val header: Boolean)
        val legs = region.mapNotNull { l ->
            val hm = profile.match("leg_hm", l.text)
            val m = hm ?: profile.match("leg", l.text)?.takeUnless { Regex("""(?i)\d\s*h(?:rs?|oras?)?\b""").containsMatchIn(l.text) } ?: return@mapNotNull null
            val min = (m["leg_min"]?.toIntOrNull() ?: return@mapNotNull null) + (hm?.get("leg_h")?.toIntOrNull()?.times(60) ?: 0)
            val km = m["leg_km"]?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            if (min !in 1..360 || km !in 0.05..500.0) null else Leg(min, km, l, HEADER.containsMatchIn(l.text))
        }
        val header = legs.filter { it.header }
        val route = legs.filterNot { it.header }
        fun reject(reason: String) = R3Candidate(profile.platform, fields, null, reason, 0.4)
        if (header.size > 1) return reject("multiple_total_headers")
        val (pickup, trip) = when {
            route.size == 2 -> route[0] to route[1]
            route.size == 3 && header.isEmpty() -> {
                val r = UberLegResolver106.resolve(route.mapIndexed { i, l -> UberLegResolver106.Leg(l.min, l.km, i, false) })
                if (r !is UberLegResolver106.Resolution.Resolved) return reject("three_legs_without_total")
                route.first { it.min == r.pickup.minutes && it.km == r.pickup.km } to route.last { it.min == r.trip.minutes && it.km == r.trip.km }
            }
            route.isEmpty() -> return reject(if (header.isEmpty()) "no_legs" else "total_without_both_legs")
            route.size == 1 -> return reject("single_leg")
            else -> return reject("too_many_legs")
        }
        header.singleOrNull()?.let { h ->
            val sums = UberLegResolver106.sums(UberLegResolver106.Leg(h.min, h.km, 0, true), UberLegResolver106.Leg(pickup.min, pickup.km, 1, false), UberLegResolver106.Leg(trip.min, trip.km, 2, false))
            if (!sums) return reject("legs_disagree_with_total")
            put("total_min", h.min.toString(), h.line, 0.9); put("total_km", fmt(h.km), h.line, 0.9)
        }
        put("pickup_min", pickup.min.toString(), pickup.line, 0.9); put("pickup_km", fmt(pickup.km), pickup.line, 0.9)
        put("trip_min", trip.min.toString(), trip.line, 0.9); put("trip_km", fmt(trip.km), trip.line, 0.9)
        address(region, pickup.line, trip.line, profile)?.let { put("pickup_address", it.text, it, 0.7) }
        address(region, trip.line, null, profile)?.let { put("destination_address", it.text, it, 0.7) }

        val canonical = buildList {
            fields["service_type"]?.value?.let(::add)
            add("R$ " + fields.getValue("fare").value)
            fields["advertised_per_km"]?.value?.let { add("R$ $it/km") }
            add("${pickup.min} min • ${fmt(pickup.km)} km")
            fields["pickup_address"]?.value?.let(::add)
            add("${trip.min} min • ${fmt(trip.km)} km")
            fields["destination_address"]?.value?.let(::add)
            add("Aceitar")
        }.joinToString("\n")
        val offer = runCatching {
            FlexibleDriverOfferParser.parseText(canonical, profile.platform, profile.targetPackage, "r3lab-profile-${profile.id}", settings)
        }.getOrNull() ?: return R3Candidate(profile.platform, fields, null, "official_parser_rejected", 0.5)
        val integrity = OfferIntegrityGate027033.assess(offer)
        if (!integrity.ready) return R3Candidate(profile.platform, fields, null, "integrity:" + integrity.flags.take(3).joinToString("+"), 0.5)
        return R3Candidate(profile.platform, fields, offer, null, 0.9)
    }

    /** Endereço = primeira linha textual após o trecho e antes do próximo trecho/ação; nunca nota/valor. */
    private fun address(region: List<SpatialOcrLine>, after: SpatialOcrLine, before: SpatialOcrLine?, profile: R3ProfileBundle): SpatialOcrLine? =
        region.filter { it.box.top > after.box.top && (before == null || it.box.top < before.box.top) }.firstOrNull { l ->
            val t = l.text.trim()
            t.length >= 6 && !NOISE.matches(t) && profile.match("fare", t) == null && profile.match("leg", t) == null && profile.match("leg_hm", t) == null &&
                profile.match("rating", t) == null && profile.match("action", t) == null && t.any(Char::isLetter)
        }

    private fun fmt(v: Double): String {
        val one = Math.round(v * 10) / 10.0
        return String.format(Locale.US, if (kotlin.math.abs(v - one) < 1e-9) "%.1f" else "%.2f", v)
    }
}

/** Modo C — o mesmo quadro R3 interpretado pelo roteador oficial (Uber+99 no mesmo frame). */
object R3OfficialInterpretation {
    fun fromOffers(offers: List<RideOffer>, platform: String, frameId: String, observationId: String): List<R3Candidate> =
        offers.filter { it.platform.equals(platform, true) }.map { offer ->
            fun ev(n: String, v: Any?) = v?.toString()?.let { n to R3FieldEvidence(n, frameId, observationId, it, R3FieldOrigin.OBSERVED, offer.confidence.coerceIn(0.0, 1.0)) }
            val fields = listOfNotNull(
                // Totais NÃO entram: o parser oficial os calcula (busca + viagem); não são valores lidos.
                ev("fare", String.format(Locale.US, "%.2f", offer.fare)), ev("pickup_min", offer.pickupMinutes), ev("pickup_km", offer.pickupKm),
                ev("trip_min", offer.tripMinutes), ev("trip_km", offer.tripKm),
                ev("service_type", offer.serviceType.takeIf { it != "unknown" }), ev("advertised_per_km", offer.advertisedPerKm),
                ev("pickup_address", offer.context?.pickupLabel), ev("destination_address", offer.context?.destinationLabel),
            ).toMap()
            val integrity = OfferIntegrityGate027033.assess(offer)
            if (integrity.ready) R3Candidate(platform, fields, offer, null, offer.confidence.coerceIn(0.0, 1.0))
            else R3Candidate(platform, fields, null, "integrity:" + integrity.flags.take(3).joinToString("+"), 0.4)
        }
}

/**
 * Reconstrução de linhas visuais: o ML Kit às vezes separa "8 min" e "• 2.4 km" (ou "R$ 37," e "27")
 * em linhas diferentes na mesma altura. Junta, da esquerda para a direita, peças com sobreposição
 * vertical ≥ 50% e espaço horizontal curto. Não altera texto, só concatena peças vizinhas.
 */
internal object R3RowAssembler {
    fun rows(lines: List<SpatialOcrLine>): List<SpatialOcrLine> {
        val sorted = lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        val used = BooleanArray(sorted.size)
        val out = mutableListOf<SpatialOcrLine>()
        for (i in sorted.indices) {
            if (used[i]) continue
            used[i] = true
            var row = sorted[i]
            while (true) {
                val next = sorted.indices.filter { !used[it] }.map { it to sorted[it] }.filter { (_, l) ->
                    val overlap = minOf(row.box.bottom, l.box.bottom) - maxOf(row.box.top, l.box.top)
                    val h = minOf(row.box.height(), l.box.height()).coerceAtLeast(1)
                    val gap = l.box.left - row.box.right
                    overlap >= h * 0.5 && gap in -h..(h * 3)
                }.minByOrNull { it.second.box.left } ?: break
                used[next.first] = true
                val b = android.graphics.Rect(minOf(row.box.left, next.second.box.left), minOf(row.box.top, next.second.box.top),
                    maxOf(row.box.right, next.second.box.right), maxOf(row.box.bottom, next.second.box.bottom))
                row = SpatialOcrLine(row.text + " " + next.second.text, b)
            }
            out += row
        }
        return out
    }
}

/**
 * Interpretação do R3 como Reader principal (issue #7). Ordem de confiança:
 *  1. caminho OFICIAL (mesmo roteador/parsers do M1) sobre o OCR do R3 — fonte primária;
 *  2. perfil R3 (modo D) SOMENTE para cards em que o oficial não emitiu oferta, e apenas se passar no
 *     parser oficial + `OfferIntegrityGate027033`; divergência C × D no mesmo card = rejeição.
 * Nunca corrige números: "vírgula perdida" (ex.: "8min:24km" com total 12,9 km) é rejeitada com motivo
 * próprio. Uma observação de um trecho é incompleta, nunca oferta oficial.
 */
internal object R3PrimaryInterpreter {
    data class Result(val candidates: List<R3Candidate>, val source: String)

    fun interpret(profile: R3ProfileBundle, lines: List<SpatialOcrLine>, official: List<RideOffer>, settings: DriverSettings,
                  frameWidth: Int, frameId: String, observationId: String): Result {
        val c = R3OfficialInterpretation.fromOffers(official, profile.platform, frameId, observationId)
        val acceptedC = c.filter { it.accepted }
        val d = R3ProfileExtractor.extract(profile, R3RowAssembler.rows(lines), frameId, observationId, settings, frameWidth)
        val out = acceptedC.toMutableList()
        var usedProfile = false
        for (cand in d) {
            val fare = cand.fields["fare"]?.value?.let(::money) ?: continue
            val sameCardC = acceptedC.firstOrNull { kotlin.math.abs(it.offer!!.fare - fare) < 0.005 }
            when {
                sameCardC != null -> if (cand.accepted && !sameLegs(sameCardC.offer!!, cand.offer!!)) {
                    // Mesmo card, leituras diferentes dos trechos: não escolhe às cegas.
                    out.remove(sameCardC); out += cand.copy(offer = null, rejection = "official_profile_conflict")
                }
                cand.accepted -> { out += cand; usedProfile = true }
                else -> out += cand.copy(rejection = decimalLost(cand, lines, profile) ?: cand.rejection)
            }
        }
        if (out.none { it.accepted }) c.filter { !it.accepted }.forEach { out += it }
        // Mesmo guard físico do dispatcher (ex.: 24 km em 8 min = 180 km/h) já na interpretação, com motivo.
        val guarded = out.map { cand ->
            val o = cand.offer ?: return@map cand
            val inspection = com.srrotas.app.OfferIntegrityGuard028.inspect(o)
            if (inspection.accepted) cand else cand.copy(offer = null, rejection = "guard028:${inspection.reason}")
        }
        out.clear(); out += guarded
        return Result(out, when { usedProfile && acceptedC.isNotEmpty() -> "official+profile"; usedProfile -> "profile"; acceptedC.isNotEmpty() -> "official"; else -> "none" })
    }

    private fun money(v: String) = v.replace(".", "").replace(',', '.').toDoubleOrNull()

    private fun sameLegs(a: RideOffer, b: RideOffer) = a.pickupMinutes == b.pickupMinutes && a.tripMinutes == b.tripMinutes &&
        close(a.pickupKm, b.pickupKm) && close(a.tripKm, b.tripKm)

    private fun close(x: Double?, y: Double?) = x == null || y == null || kotlin.math.abs(x - y) <= 0.15

    /**
     * Diagnóstico (não correção): total lido fecha exatamente se um km de trecho sem separador decimal
     * for dividido por 10 ⇒ "legs_decimal_lost". A oferta continua rejeitada.
     */
    private fun decimalLost(cand: R3Candidate, lines: List<SpatialOcrLine>, profile: R3ProfileBundle): String? {
        if (cand.rejection != "legs_disagree_with_total") return null
        val legs = R3RowAssembler.rows(lines).mapNotNull { profile.match("leg", it.text)?.let { m -> it.text to m } }
        if (legs.size != 3) return null
        val header = legs.firstOrNull { Regex("""(?i)\best\.?\s*de\b|/\s*km""").containsMatchIn(it.first) } ?: return null
        val route = legs.filter { it !== header }
        fun km(s: String?) = s?.replace(',', '.')?.toDoubleOrNull()
        val total = km(header.second["leg_km"]) ?: return null
        val values = route.map { km(it.second["leg_km"]) ?: return null }
        val raw = route.map { it.second["leg_km"].orEmpty() }
        for (i in route.indices) {
            if ('.' in raw[i] || ',' in raw[i]) continue
            val repaired = values.toMutableList().also { it[i] = it[i] / 10.0 }
            if (kotlin.math.abs(repaired.sum() - total) <= maxOf(0.2, total * 0.04)) return "legs_decimal_lost"
        }
        return null
    }
}
