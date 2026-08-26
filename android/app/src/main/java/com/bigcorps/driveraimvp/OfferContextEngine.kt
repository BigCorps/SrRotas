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
        """^\s*(?:retirada|embarque|buscar|origem|ponto\s+de\s+partida|local\s+de\s+embarque)\s*[:\-–]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitDestination = Regex(
        """^\s*(?:destino|chegada|para|vai\s+para|local\s+de\s+destino)\s*[:\-–]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val addressToken = Regex(
        """\b(?:rua|r\.|avenida|av\.|alameda|al\.|estrada|est\.|rodovia|rod\.|travessa|tv\.|praça|praca|largo|marginal|via|bairro)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val utility = Regex(
        """(?:r\$\s*\d|/\s*km\b|\b\d+\s*(?:min|minutos?)\b|\b\d+[,.]?\d*\s*(?:km|m)\b|radar\s+de\s+viagens|selecionar|aceitar|escolher|recusar|ver\s+rota|viagem\s+de|oferta|solicita[cç][aã]o|uberx|comfort|priority|black|electric|moto|plus\s+nova|99pop|99plus|99moto|99t[aá]xi|99electric|perfil\s+essencial|\bcorridas?\b|avaliação|avaliacao|★|ganhos?|taxa|promoção|promocao|você\s+está\s+online|voce\s+esta\s+online|isso\s+é\s+tudo\s+por\s+enquanto|isso\s+e\s+tudo\s+por\s+enquanto|para\s+onde\??|como\s+foi\s+a\s+viagem|ajude\s+a\s+melhorar|faça\s+planos\s+como\s+recurso|faca\s+planos\s+como\s+recurso|[áa]rea\s+de\s+risco|pol[ií]cia\s+em)""",
        RegexOption.IGNORE_CASE,
    )

    internal fun attach(offer: RideOffer, spatial: List<SpatialOcrLine>): RideOffer {
        val lines = spatial.map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) }
        return offer.copy(context = extract(lines, offer.observedAt, offer.totalMinutes))
    }

    fun extract(lines: List<ContextOcrLine>, observedAt: String, totalMinutes: Int?): OfferContext? {
        if (lines.isEmpty()) return etaOnly(observedAt, totalMinutes)

        val ordered = lines
            .map { it.copy(text = clean(it.text)) }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<ContextOcrLine> { it.top }.thenBy { it.left })

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

        val geometryIndexes = ordered.indices.filter { geometry.containsMatchIn(ordered[it].text) }
        val placeIndexes = ordered.indices.filter { looksLikePlace(ordered[it].text) }

        // 2) Formatos como "(8 min 591 m) Rua ..." podem vir numa única linha do OCR.
        // O endereço residual da própria linha tem preferência sobre a linha seguinte.
        if (pickup == null && geometryIndexes.isNotEmpty()) {
            val firstAnchor = geometryIndexes.first()
            val upper = geometryIndexes.getOrNull(1) ?: ordered.size
            pickup = inlinePlaceAt(ordered, firstAnchor, upper)
                ?: firstPlaceBetween(ordered, placeIndexes, firstAnchor + 1, upper)
            if (pickup != null) pickupMethod = 2
        }
        if (destination == null && geometryIndexes.size >= 2) {
            val secondAnchor = geometryIndexes[1]
            val nextAnchor = geometryIndexes.getOrNull(2) ?: ordered.size
            destination = inlinePlaceAt(ordered, secondAnchor, nextAnchor)
                ?: firstPlaceBetween(ordered, placeIndexes, secondAnchor + 1, nextAnchor)
                ?: firstPlaceAfter(ordered, placeIndexes, secondAnchor)
            if (destination != null) destinationMethod = 2
        }

        // 3) Fallback conservador: primeiro/último texto com aparência de lugar.
        val inlineCandidates = geometryIndexes.mapNotNull { inlinePlaceAfterGeometry(ordered[it].text) }
        val regularCandidates = placeIndexes.map { ordered[it].text }
        val candidates = (inlineCandidates + regularCandidates).distinctBy { normalize(it) }
        if (pickup == null && candidates.isNotEmpty()) {
            pickup = candidates.first()
            pickupMethod = 1
        }
        if (destination == null && candidates.size >= 2) {
            destination = candidates.last()
            destinationMethod = 1
        }

        if (pickup != null && destination != null && normalize(pickup!!) == normalize(destination!!)) {
            destination = null
            destinationMethod = 0
        }

        val eta = estimatedArrivalAt(observedAt, totalMinutes)
        if (pickup == null && destination == null && eta == null) return null

        val confidence = when {
            pickupMethod == 3 && destinationMethod == 3 -> 0.96
            pickupMethod >= 2 && destinationMethod >= 2 -> 0.86
            pickup != null && destination != null -> 0.70
            pickupMethod == 3 || destinationMethod == 3 -> 0.72
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

    private fun inlinePlaceAfterGeometry(raw: String): String? {
        val match = geometry.find(raw) ?: return null
        val residual = clean(raw.removeRange(match.range))
            .trim(':', '-', '–', '—', '•', '·')
            .trim()
        return residual.takeIf(::looksLikePlace)
    }

    private fun inlinePlaceAt(lines: List<ContextOcrLine>, index: Int, until: Int): String? {
        val inline = inlinePlaceAfterGeometry(lines[index].text) ?: return null
        val nextIndex = index + 1
        if (nextIndex >= minOf(lines.size, until)) return inline
        val next = lines[nextIndex].text
        return if (looksLikePlace(next) && shouldJoinPlaceLines(inline, next)) {
            clean("$inline $next")
        } else inline
    }

    private fun firstPlaceBetween(
        lines: List<ContextOcrLine>,
        indexes: List<Int>,
        from: Int,
        until: Int,
    ): String? {
        val index = indexes.firstOrNull { it in from until until } ?: return null
        return composePlace(lines, index, until)
    }

    private fun firstPlaceAfter(lines: List<ContextOcrLine>, indexes: List<Int>, after: Int): String? {
        val index = indexes.firstOrNull { it > after } ?: return null
        return composePlace(lines, index, minOf(lines.size, index + 3))
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
            if (!looksLikePlace(next) || !shouldJoinPlaceLines(value, next)) break
            value = clean("$value $next")
            cursor++
        }
        return value
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
