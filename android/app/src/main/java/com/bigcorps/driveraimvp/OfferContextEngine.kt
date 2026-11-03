package com.srrotas.app

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.floor

/**
 * Context Engine v1.
 *
 * Não calcula tarifa, km, tempo ou verdict. Recebe uma oferta já aprovada pelo
 * Offer Engine e tenta associar somente contexto espacial do mesmo card OCR.
 */
data class ContextOcrLine(
    val text: String,
    val top: Int,
    val left: Int = 0,
    val bottom: Int = top,
    val right: Int = left,
)

object OfferContextEngine {
    const val VERSION = "sr-context-v0.22.0"

    /** Uber e formatos genéricos/99, inclusive metros e parêntese antes do tempo. */
    private val geometry = Regex(
        """(?:\(\s*)?[0-9OSoIlL]{1,3}\s*(?:min|minuto|minutos)\s*(?:[·•\-–—]?\s*)?(?:\(\s*)?[0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?\s*(?:km|m)\s*\)?""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitPickup = Regex(
        """^\s*(?:retirada|embarque|buscar|origem|ponto\s+de\s+partida|local\s+de\s+embarque)(?=[\s:\-–]|$)\s*[:\-–]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitDestination = Regex(
        """^\s*(?:destino|chegada|para|vai\s+para|local\s+de\s+destino)(?=[\s:\-–]|$)\s*[:\-–]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val addressToken = Regex(
        """\b(?:rua|r\.|avenida|av\.|alameda|al\.|estrada|est\.|rodovia|rod\.|travessa|tv\.|praça|praca|largo|marginal|via|bairro)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val utility = Regex(
        """(?:r\$\s*\d|/\s*km\b|^\s*(?:de\s+)?dist[âa]ncia\b|(?<![\d.,])[0-5][,.]\d{2}(?![\d])(?:\s*\(\d+\))?|verificad[oa]|\b\d+\s*(?:min|minutos?)\b|\b\d+[,.]?\d*\s*(?:km|m)\b|radar\s+de\s+viagens|selecionar|aceitar|escolher|recusar|ver\s+rota|viagem\s+de|oferta|solicita[cç][aã]o|uberx|comfort|priority|black|electric|moto|plus\s+nova|99pop|99plus|99moto|99t[aá]xi|99electric|perfil\s+essencial|\bcorridas?\b|avaliação|avaliacao|★|ganhos?|taxa|promoção|promocao|você\s+está\s+online|voce\s+esta\s+online|isso\s+é\s+tudo\s+por\s+enquanto|isso\s+e\s+tudo\s+por\s+enquanto|para\s+onde\??|como\s+foi\s+a\s+viagem|ajude\s+a\s+melhorar|faça\s+planos\s+como\s+recurso|faca\s+planos\s+como\s+recurso|[áa]rea\s+de\s+risco|pol[ií]cia\s+em)""",
        RegexOption.IGNORE_CASE,
    )

    internal fun attach(offer: RideOffer, spatial: List<SpatialOcrLine>): RideOffer {
        val lines = spatial.map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) }
        return offer.copy(context = extract(lines, offer.observedAt, offer.totalMinutes))
    }

    fun extract(lines: List<ContextOcrLine>, observedAt: String, totalMinutes: Int?): OfferContext? {
        if (lines.isEmpty()) return etaOnly(observedAt, totalMinutes)

        val sorted = lines
            .map { it.copy(text = clean(it.text)) }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<ContextOcrLine> { it.top }.thenBy { it.left })
        val ordered = sorted

        var pickup: String? = null
        var destination: String? = null
        var pickupMethod = 0
        var destinationMethod = 0

        // 1) Marcadores explícitos têm prioridade máxima.
        ordered.forEachIndexed { index, line ->
            explicitPickup.matchEntire(line.text)?.let { match ->
                val inline = clean(match.groupValues.getOrNull(1).orEmpty())
                pickup = inline.takeIf(::looksLikePlace)
                    ?: nextPlace(ordered, index + 1)
                if (pickup != null) pickupMethod = 3
            }
            explicitDestination.matchEntire(line.text)?.let { match ->
                val inline = clean(match.groupValues.getOrNull(1).orEmpty())
                destination = inline.takeIf(::looksLikePlace)
                    ?: nextPlace(ordered, index + 1)
                if (destination != null) destinationMethod = 3
            }
        }

        // 2) vc109: associação por PAPEL de cada linha de geometria (total × busca × viagem),
        // nunca pela posição. Sem papel comprovado o endereço fica indisponível; o texto do
        // mapa (acima do card) e "primeira/última linha" não são mais usados como busca/destino.
        val roles = routeRoles(ordered)
        if (pickup == null) roles.pickup?.let { index ->
            pickup = placeAfterAnchor(ordered, index)
            if (pickup != null) pickupMethod = 2
        }
        if (destination == null) roles.trip?.let { index ->
            destination = placeAfterAnchor(ordered, index)
            if (destination != null) destinationMethod = 2
        }

        if (pickup != null && destination != null && normalize(pickup!!) == normalize(destination!!)) {
            destination = null
            destinationMethod = 0
        }

        val eta = estimatedArrivalAt(observedAt, totalMinutes)
        if (pickup == null && destination == null && eta == null) return null

        val confidence = when {
            pickupMethod == 3 && destinationMethod == 3 -> 0.96
            pickupMethod == 2 && destinationMethod == 2 -> 0.95
            pickupMethod >= 2 && destinationMethod >= 2 -> 0.86
            pickup != null && destination != null -> 0.70
            pickupMethod == 3 || destinationMethod == 3 -> 0.72
            pickupMethod == 2 || destinationMethod == 2 -> 0.80
            pickup != null || destination != null -> 0.52
            else -> 0.0
        }

        return OfferContext(
            pickupLabel = pickup,
            destinationLabel = destination,
            estimatedArrivalAt = eta,
            contextConfidence = confidence,
            geocodeStatus = if (pickup != null || destination != null) "pending" else "unresolved",
            geocodeSource = "spatial_ocr",
            contextVersion = VERSION,
        )
    }

    fun estimatedArrivalAt(observedAt: String, totalMinutes: Int?): String? {
        val minutes = totalMinutes?.takeIf { it in 1..600 } ?: return null
        return try {
            Instant.parse(observedAt).plusSeconds(minutes * 60L).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun geoCell(lat: Double, lng: Double): String? {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        val latBucket = floor(lat * 100.0).toInt()
        val lngBucket = floor(lng * 100.0).toInt()
        return "g2:$latBucket:$lngBucket"
    }

    fun normalizePlaceForCache(value: String): String =
        normalize(clean(value)).take(220)

    private fun etaOnly(observedAt: String, totalMinutes: Int?): OfferContext? =
        estimatedArrivalAt(observedAt, totalMinutes)?.let {
            OfferContext(
                estimatedArrivalAt = it,
                contextConfidence = 0.0,
                geocodeStatus = "unresolved",
                geocodeSource = "eta_local",
                contextVersion = VERSION,
            )
        }

    internal data class Roles(val total: List<Int>, val pickup: Int?, val trip: Int?, val evidence: String)

    private val headerMarker = Regex("""\best\.?\s*de\b""", RegexOption.IGNORE_CASE)
    private val perKmMarker = Regex("""(?:R\$|\$)\s*[0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?\s*/\s*km""", RegexOption.IGNORE_CASE)
    private val tripCue = Regex("""viagem\s+de|\d\s*minutos?\b""", RegexOption.IGNORE_CASE)
    private val pickupCue = Regex("""dist[âa]ncia|\bat[ée]\s+(?:o\s+)?passageir""", RegexOption.IGNORE_CASE)
    private val shortMinutes = Regex("""\d\s*min(?!u)""", RegexOption.IGNORE_CASE)
    private val stopLine = Regex("""^\s*\W{0,3}\s*(?:aceitar|tenho\s+interesse|selecionar|recusar|escolher|confirmar)\b""", RegexOption.IGNORE_CASE)

    /** Linha de geometria: o padrão legado ou um par `min • km` tolerante a separadores do OCR real. */
    internal fun isGeometry(text: String): Boolean =
        geometry.containsMatchIn(text) || UberLegResolver106.legs(text).isNotEmpty()

    /**
     * vc109 — papel de cada linha de geometria, por evidência:
     *  - TOTAL: marcador `Est. de` na linha; `R$x/km` na linha com ≥ 3 geometrias; ou, com três
     *    pares sem marcador, o que for a soma exata dos outros dois (mesma regra do UberLegResolver106);
     *  - com dois trechos restantes: busca e viagem na ordem do card (salvo pistas explícitas invertidas);
     *  - com UM trecho: só por pista explícita (`minutos`/`Viagem de` = viagem; `de distância` = busca;
     *    `min` abreviado ao lado de um total Uber = busca). Sem pista, papel desconhecido.
     */
    internal fun routeRoles(ordered: List<ContextOcrLine>): Roles {
        val geo = ordered.indices.filter { isGeometry(ordered[it].text) }
        val total = mutableListOf<Int>()
        geo.forEach { i ->
            val text = ordered[i].text
            if (headerMarker.containsMatchIn(text) || (geo.size >= 3 && perKmMarker.containsMatchIn(text))) total += i
        }
        var route = geo - total.toSet()
        if (total.isEmpty() && route.size == 3) {
            val legs = route.map { UberLegResolver106.legs(ordered[it].text).firstOrNull() }
            if (legs.all { it != null }) {
                val (a, b, c) = legs.map { it!! }
                when {
                    UberLegResolver106.sums(a, b, c) -> { total += route[0]; route = route.drop(1) }
                    UberLegResolver106.sums(c, a, b) -> { total += route[2]; route = route.dropLast(1) }
                }
            }
        }
        fun cue(i: Int): String? {
            val text = ordered[i].text
            return when {
                pickupCue.containsMatchIn(text) -> "pickup"
                tripCue.containsMatchIn(text) -> "trip"
                total.isNotEmpty() && shortMinutes.containsMatchIn(text) -> "pickup"
                else -> null
            }
        }
        return when (route.size) {
            0 -> Roles(total, null, null, "no_route_geometry")
            1 -> when (cue(route[0])) {
                "pickup" -> Roles(total, route[0], null, "single_leg_pickup_cue")
                "trip" -> Roles(total, null, route[0], "single_leg_trip_cue")
                else -> Roles(total, null, null, "single_leg_without_role")
            }
            2 -> if (cue(route[0]) == "trip" && cue(route[1]) == "pickup") Roles(total, route[1], route[0], "two_legs_cues")
                else Roles(total, route[0], route[1], "two_legs_card_order")
            else -> Roles(total, null, null, "too_many_route_geometries")
        }
    }

    /** Endereço do trecho: resíduo na própria linha ou primeiro lugar até a próxima geometria/botão. */
    private fun placeAfterAnchor(lines: List<ContextOcrLine>, anchor: Int): String? {
        inlinePlaceAfterGeometry(lines[anchor].text)?.let { inline ->
            val next = lines.getOrNull(anchor + 1)?.text
            return if (next != null && !isGeometry(next) && looksLikePlace(next) && shouldJoinPlaceLines(inline, next)) clean("$inline $next") else inline
        }
        var until = anchor + 1
        while (until < lines.size && until <= anchor + 6 && !isGeometry(lines[until].text) && !stopLine.containsMatchIn(lines[until].text)) until++
        val index = (anchor + 1 until until).firstOrNull { looksLikePlace(lines[it].text) } ?: return null
        // Só a cidade (resto de um endereço quebrado cujo início o OCR perdeu) não é um destino.
        return composePlace(lines, index, until).takeUnless(::cityOnly)
    }

    private val cityNames = Regex(
        """^(?:sao paulo|guarulhos|campinas|santo andre|sao bernardo(?: do campo)?|osasco|rio de janeiro|brasil)(?: (?:sp|rj|brasil))*$""",
    )

    internal fun cityOnly(label: String): Boolean =
        cityNames.matches(java.text.Normalizer.normalize(label.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z ]+"), " ").replace(Regex("\\s+"), " ").trim())

    private fun inlinePlaceAfterGeometry(raw: String): String? {
        val match = geometry.find(raw) ?: return null
        val residual = clean(raw.removeRange(match.range))
            .trim(':', '-', '–', '—', '•', '·')
            .trim()
        return residual.takeIf(::looksLikePlace)
    }

    private fun nextPlace(lines: List<ContextOcrLine>, from: Int): String? {
        val upper = minOf(lines.size, from + 3)
        val index = (from until upper).firstOrNull { looksLikePlace(lines[it].text) } ?: return null
        return composePlace(lines, index, upper)
    }

    private fun composePlace(lines: List<ContextOcrLine>, index: Int, until: Int): String {
        var value = lines[index].text
        var cursor = index + 1
        while (cursor < minOf(lines.size, until) && cursor <= index + 2) {
            val next = lines[cursor].text
            if (!(looksLikePlace(next) && shouldJoinPlaceLines(value, next)) && !danglingTail(value, next)) break
            value = clean("$value $next")
            cursor++
        }
        return value
    }

    /** vc109: "…, Bairro, São" + "Paulo" — linha termina numa palavra pendente e a seguinte só completa o nome. */
    private val danglingWord = Regex("""\s(?:são|sao|santo|santa|vila|jardim|de|do|da|dos|das)$""", RegexOption.IGNORE_CASE)
    private fun danglingTail(first: String, next: String): Boolean {
        val b = clean(next)
        return danglingWord.containsMatchIn(clean(first)) && b.length in 3..30 && b.all { it.isLetter() || it == ' ' || it == '-' } &&
            !utility.containsMatchIn(b) && !stopLine.containsMatchIn(b)
    }

    private fun shouldJoinPlaceLines(first: String, next: String): Boolean {
        val a = clean(first)
        val b = clean(next)
        if (a.isBlank() || b.isBlank()) return false
        if (utility.containsMatchIn(b) || geometry.containsMatchIn(b)) return false
        if (explicitPickup.containsMatchIn(b) || explicitDestination.containsMatchIn(b)) return false
        if (a.endsWith(",") || a.endsWith("-") || a.endsWith("–")) return true
        if (addressToken.containsMatchIn(a)) {
            // Endereços do Uber frequentemente quebram após nome da via/número/bairro.
            // Não junta outra via completa para evitar misturar embarque e destino.
            val nextStartsAnotherStreet = addressToken.find(b)?.range?.first == 0
            return !nextStartsAnotherStreet && b.length <= 64
        }
        return false
    }

    internal fun looksLikePlace(raw: String): Boolean {
        val value = clean(raw)
        if (value.length !in 4..140) return false
        if (utility.containsMatchIn(value)) return false
        if (value.endsWith("?") && !addressToken.containsMatchIn(value)) return false
        if (!value.any(Char::isLetter)) return false
        if (value.count(Char::isLetter) < 4) return false
        if (value.matches(Regex("""^[\d\s.,:/()-]+$"""))) return false

        if (addressToken.containsMatchIn(value)) return true
        if (Regex("""\b\d{1,5}\b""").containsMatchIn(value) && value.any(Char::isLetter)) return true
        if (value.contains(',') && value.split(',').any { it.trim().length >= 3 }) return true

        val words = value.split(Regex("""\s+""")).filter { it.length >= 2 }
        return words.size >= 2 && value.length >= 8
    }

    private fun clean(value: String): String =
        value.replace(Regex("""\s+"""), " ")
            .trim()
            .trim('•', '·', '-', '–', '|')

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
}
