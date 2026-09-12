package com.srrotas.app

import kotlin.math.abs

/**
 * RC3.2 — resolve somente colisões Uber/99 produzidas pelo MESMO frame.
 *
 * A plataforma continua fazendo parte do dedupe normal da jornada. Aqui tratamos
 * um caso anterior ao dedupe: dois parsers emitiram o mesmo card financeiro com
 * rótulos diferentes. Duas ofertas simultâneas e distintas continuam intactas.
 */
object CrossPlatformOfferArbitrator0270 {
    data class Result(
        val ninetyNine: List<RideOffer>,
        val uber: List<RideOffer>,
        val resolvedCollisions: Int,
    )

    fun resolve(
        ninetyNine: List<RideOffer>,
        uber: List<RideOffer>,
    ): Result {
        val keep99 = ninetyNine.toMutableList()
        val keepUber = uber.toMutableList()
        var resolved = 0

        ninetyNine.forEach { n ->
            val u = keepUber.firstOrNull { equivalent(n, it) } ?: return@forEach
            val nText = DriverOcrNormalizer.sanitize(n.rawText).lowercase()
            val uText = DriverOcrNormalizer.sanitize(u.rawText).lowercase()
            val nScore = ninetyNineEvidenceScore(nText)
            val uScore = uberEvidenceScore(uText)

            // Se ambos os clusters têm identidade inequívoca e são diferentes,
            // preserve ambos: podem ser duas ofertas reais coincidentemente iguais.
            val sameCluster =
                nText == uText ||
                    nText.contains(uText) ||
                    uText.contains(nText)
            if (!sameCluster && nScore >= 4 && uScore >= 4) return@forEach

            when {
                nScore > uScore -> keepUber.remove(u)
                uScore > nScore -> keep99.remove(n)
                n.confidence > u.confidence + 0.02 -> keepUber.remove(u)
                u.confidence > n.confidence + 0.02 -> keep99.remove(n)
                nScore > 0 -> keepUber.remove(u)
                else -> keep99.remove(n)
            }
            resolved++
        }

        return Result(keep99, keepUber, resolved)
    }

    internal fun equivalent(a: RideOffer, b: RideOffer): Boolean {
        if (abs(a.fare - b.fare) > 0.01) return false
        if (!sameNullableMetric(a.pickupKm, b.pickupKm, 0.10)) return false
        if (!sameNullableMetric(a.tripKm, b.tripKm, 0.10)) return false
        if (!sameNullableMetric(a.totalKm, b.totalKm, 0.15)) return false
        val am = a.totalMinutes
        val bm = b.totalMinutes
        if (am != null && bm != null && abs(am - bm) > 1) return false
        return true
    }

    private fun sameNullableMetric(a: Double?, b: Double?, tolerance: Double): Boolean =
        when {
            a == null && b == null -> true
            a == null || b == null -> false
            else -> abs(a - b) <= tolerance
        }

    private fun ninetyNineEvidenceScore(lower: String): Int {
        var score = 0
        if (lower.contains("escolher")) score += 4
        if (listOf(
                "perfil essencial", "plus nova", "99pop", "99 pop", "99plus", "99 plus",
                "99moto", "99 moto", "99táxi", "99taxi", "99electric", "99 entrega",
            ).any(lower::contains)
        ) score += 4
        if (lower.contains("solicitações") || lower.contains("solicitacoes")) score += 1
        if (explicitUberOfferAction(lower)) score -= 5
        return score
    }

    private fun uberEvidenceScore(lower: String): Int {
        var score = 0
        if (explicitUberOfferAction(lower)) score += 5
        if (listOf(
                "uberx", "comfort", "priority", "uber moto", "ubermoto",
                "black", "electric",
            ).any(lower::contains)
        ) score += 2
        if (lower.contains("escolher") || hasStrong99Identity(lower)) score -= 5
        return score
    }

    private fun hasStrong99Identity(lower: String): Boolean = listOf(
        "perfil essencial", "plus nova", "99pop", "99 pop", "99plus", "99 plus",
        "99moto", "99 moto", "99táxi", "99taxi", "99electric", "99 entrega",
    ).any(lower::contains)

    private fun explicitUberOfferAction(lower: String): Boolean =
        listOf("aceitar", "selecionar", "exclusivo", "radar de viagens")
            .any(lower::contains)
}
