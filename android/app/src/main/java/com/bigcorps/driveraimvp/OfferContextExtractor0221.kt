package com.srrotas.app

import kotlin.math.abs

/**
 * Contexto espacial por card.
 *
 * 0.26.2 troca limites fixos em pixels por limites derivados da própria altura
 * das linhas OCR. Isso evita perder ou trocar origem/destino em tablets e em
 * resoluções com densidade maior, sem recorrer a texto de outro card.
 */
internal object OfferContextExtractor0221 {
    private val geometry = FlexibleDriverOfferParser.geometryRegex

    fun attach(offer: RideOffer, spatial: List<SpatialOcrLine>): RideOffer {
        val context = extract(spatial, offer.observedAt, offer.totalMinutes)
        return offer.copy(context = context)
    }

    fun extract(
        spatial: List<SpatialOcrLine>,
        observedAt: String,
        totalMinutes: Int?,
    ): OfferContext? {
        if (spatial.isEmpty()) {
            return OfferContextEngine.extract(emptyList(), observedAt, totalMinutes)
        }
        val ordered = spatial.sortedWith(
            compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left },
        )
        val anchors = ordered.filter { geometry.containsMatchIn(it.text) }
        if (anchors.size < 2) {
            // Sem duas geometrias independentes não inventamos uma associação
            // espacial. O motor legado ainda pode recuperar um endereço, mas a
            // qualidade final decide se ele pode ser mostrado/geocodificado.
            return OfferContextEngine.extract(
                ordered.map {
                    ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right)
                },
                observedAt,
                totalMinutes,
            )
        }

        val pickup = addressForAnchor(ordered, anchors[0], anchors.getOrNull(1))
        var destination = addressForAnchor(ordered, anchors[1], anchors.getOrNull(2))
        val fallback = OfferContextEngine.extract(
            ordered.map {
                ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right)
            },
            observedAt,
            totalMinutes,
        )

        val finalPickup = pickup ?: fallback?.pickupLabel?.takeIf(OfferContextQuality0242::canGeocode)
        if (
            destination != null &&
            finalPickup != null &&
            normalize(destination) == normalize(finalPickup)
        ) {
            // Endereços idênticos vindos de duas âncoras diferentes costumam ser
            // uma linha repetida do OCR, não um destino confiável.
            destination = null
        }
        val finalDestination = destination
            ?: fallback?.destinationLabel
                ?.takeIf { (fallback.contextConfidence >= 0.90) && OfferContextQuality0242.canGeocode(it) }
                ?.takeUnless { finalPickup != null && normalize(it) == normalize(finalPickup) }
        val eta = OfferContextEngine.estimatedArrivalAt(observedAt, totalMinutes)

        if (finalPickup == null && finalDestination == null && eta == null) return null
        return OfferContext(
            pickupLabel = finalPickup,
            destinationLabel = finalDestination,
            estimatedArrivalAt = eta,
            contextConfidence = when {
                pickup != null && destination != null -> 0.95
                pickup != null || destination != null -> 0.80
                else -> fallback?.contextConfidence ?: 0.0
            },
            geocodeStatus = if (finalPickup != null || finalDestination != null) "pending" else "unresolved",
            geocodeSource = "spatial_ocr_0262",
            contextVersion = "sr-context-v0.26.2",
        )
    }

    private fun addressForAnchor(
        lines: List<SpatialOcrLine>,
        anchor: SpatialOcrLine,
        nextAnchor: SpatialOcrLine?,
    ): String? {
        inlineAfterGeometry(anchor.text)?.let { return it }

        val typicalHeight = lines.map { it.box.height().coerceAtLeast(1) }
            .sorted()
            .let { heights -> heights.getOrNull(heights.size / 2) ?: anchor.box.height().coerceAtLeast(24) }
        val adaptiveReach = (typicalHeight * 14).coerceIn(360, 1200)
        val verticalLimit = nextAnchor?.box?.top ?: (anchor.box.bottom + adaptiveReach)
        val topTolerance = (typicalHeight * 0.45).toInt().coerceAtLeast(8)

        val candidates = lines.filter { line ->
            if (line === anchor) return@filter false
            if (line.box.top < anchor.box.top - topTolerance || line.box.top >= verticalLimit) {
                return@filter false
            }
            if (!OfferContextEngine.looksLikePlace(line.text)) return@filter false
            horizontalAffinity(anchor, line)
        }.sortedBy { it.box.top }

        val first = candidates.firstOrNull() ?: return null
        var value = clean(first.text)
        val continuation = candidates.drop(1).firstOrNull { next ->
            val maxGap = (maxOf(first.box.height(), next.box.height()) * 2.2).toInt().coerceAtLeast(90)
            val closeY = next.box.top - first.box.bottom in -12..maxGap
            val aligned = horizontalAffinity(first, next)
            closeY && aligned && shouldJoin(value, next.text)
        }
        if (continuation != null) value = clean("$value ${continuation.text}")
        return value.take(180)
    }

    private fun inlineAfterGeometry(raw: String): String? {
        val match = geometry.find(raw) ?: return null
        val residual = clean(raw.removeRange(match.range))
            .trim(':', '-', '–', '—', '•', '·')
            .trim()
        return residual.takeIf(OfferContextEngine::looksLikePlace)?.take(180)
    }

    private fun horizontalAffinity(a: SpatialOcrLine, b: SpatialOcrLine): Boolean {
        val overlap = minOf(a.box.right, b.box.right) - maxOf(a.box.left, b.box.left)
        if (overlap > 0) return true
        val width = maxOf(a.box.width(), b.box.width()).coerceAtLeast(1)
        return abs(a.box.centerX() - b.box.centerX()) <= width * 1.35
    }

    private fun shouldJoin(first: String, next: String): Boolean {
        val a = clean(first)
        val b = clean(next)
        if (!OfferContextEngine.looksLikePlace(b)) return false
        val street = Regex(
            """^(?:rua|r\.|avenida|av\.|alameda|estrada|rodovia|travessa|praça|praca|largo|marginal|via)\b""",
            RegexOption.IGNORE_CASE,
        )
        if (street.containsMatchIn(b)) return false
        return a.endsWith(",") || a.endsWith("-") || b.length <= 70
    }

    private fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun normalize(value: String): String =
        clean(value).lowercase().replace(Regex("[^a-z0-9à-ÿ ]"), "")
}
