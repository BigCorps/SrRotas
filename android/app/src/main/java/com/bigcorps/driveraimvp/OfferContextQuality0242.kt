package com.srrotas.app

import java.text.Normalizer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Barreira de qualidade para contexto textual/geográfico.
 * Não altera OCR financeiro; impede texto de interface e geocodes absurdos de
 * virarem endereço confirmado ou célula para inteligência regional.
 */
object OfferContextQuality0242 {
    private val street = Regex(
        """\b(?:rua|r\.?|avenida|av\.?|alameda|estrada|rodovia|travessa|praca|praça|largo|marginal|via)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val negative = listOf(
        "caixa de entrada",
        "fique online",
        "whatsapp",
        "informacao do carro",
        "informações do carro",
        "selecione alguns albuns",
        "selecione alguns álbuns",
        "combinacao em andamento",
        "combinação em andamento",
        "mostrar todos eles",
        "imagens",
    )
    private val timeOnly = Regex("""^\s*\d{1,2}:\d{2}\b.*$""")
    private val metricOnly = Regex(
        """^\s*(?:\d+[\.,]?\d*\s*)?(?:min|mins|minutos|km|quilometros|quilômetros)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    fun canGeocode(label: String?): Boolean {
        val value = label?.trim()?.takeIf { it.length in 4..180 } ?: return false
        val folded = fold(value)
        if (negative.any(folded::contains)) return false
        if (timeOnly.matches(value)) return false
        if (metricOnly.matches(value)) return false
        val letters = value.count(Char::isLetter)
        if (letters < 4) return false
        if (!value.any { it == ' ' || it == ',' } && !street.containsMatchIn(value)) return false
        return true
    }

    /** 0..6: maior = endereço mais autossuficiente para geocode distante. */
    fun specificity(label: String?): Int {
        if (!canGeocode(label)) return 0
        val value = label!!.trim()
        var score = 0
        if (street.containsMatchIn(value)) score += 1
        if (value.any(Char::isDigit)) score += 1
        if (value.split(',').count { it.isNotBlank() } >= 3) score += 2
        val f = fold(value)
        if (
            f.contains("sao paulo") ||
            f.contains("guarulhos") ||
            f.contains("campinas") ||
            f.contains("santo andre") ||
            f.contains("sao bernardo") ||
            f.contains("osasco")
        ) score += 2
        return score.coerceAtMost(6)
    }

    fun distanceKm(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Double {
        val r = 6371.0088
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(p1) * cos(p2) *
                sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Se dois resultados ficam muito distantes, exige endereço textual mais
     * específico. Preferimos não confirmar uma célula a contaminar Histórico/
     * Base Coletiva com um geocode de outro estado.
     */
    fun keepPairSide(
        ownLabel: String?,
        otherLabel: String?,
        distanceKm: Double,
    ): Boolean {
        if (distanceKm <= 80.0) return canGeocode(ownLabel)
        val own = specificity(ownLabel)
        val other = specificity(otherLabel)
        if (distanceKm > 350.0 && own < 4) return false
        if (own <= 2 && other >= 3) return false
        if (own <= 1 && distanceKm > 120.0) return false
        return own >= 2
    }

    fun confirmedDisplayLabel(
        context: OfferContext?,
        pickup: Boolean,
    ): String? {
        context ?: return null
        val label = if (pickup) context.pickupLabel else context.destinationLabel
        if (!canGeocode(label)) return null
        val lat = if (pickup) context.pickupLat else context.destinationLat
        val lng = if (pickup) context.pickupLng else context.destinationLng
        if (lat == null || lng == null) return null
        return label?.trim()?.take(120)
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
