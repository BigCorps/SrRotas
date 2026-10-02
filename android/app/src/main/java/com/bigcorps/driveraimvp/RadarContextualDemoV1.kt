package com.srrotas.app

import java.time.Instant

/** Dados 100% fictícios apenas para regressão visual das quatro facetas. */
object RadarContextualDemoV1 {
    fun spec() = RadarDestinationSpecV1(
        lat = -23.5505,
        lng = -46.6333,
        eta = Instant.now().plusSeconds(12 * 60).toString(),
        label = "DEMO · destino da corrida",
        localOfferId = "demo-offer",
    )

    fun result(): RadarContextualResultV1 {
        val eta = spec().eta
        val opportunities = listOf(
            opportunity("demo:1", "Terminal Central", -23.5489, -46.6310, .4, 78.0, 62.0, 84.0, "high", "Fluxo de saída coincide com sua chegada", eta),
            opportunity("demo:2", "Centro de Eventos", -23.5552, -46.6380, 1.0, 66.0, 62.0, 62.0, "medium", "Continuidade observada nas proximidades", eta),
            opportunity("demo:3", "Shopping próximo", -23.5600, -46.6270, 1.3, null, 62.0, 31.0, "insufficient", "Contexto próximo, ainda sem amostra suficiente", eta),
        )
        return RadarContextualResultV1(
            generatedAt = Instant.now().toString(),
            destinationLat = spec().lat,
            destinationLng = spec().lng,
            destinationEta = eta,
            destinationLabel = spec().label,
            destinationCell = "g2:-2356:-4664",
            baseline = RadarContextualBaselineV1(62.0, 84, "medium", "collective"),
            opportunities = opportunities,
            assistant = RadarContextualAssistantV1(
                eligible = true,
                reason = "demo",
                headline = "Boa chance de continuidade no destino.",
                actionLabel = "Ver",
                opportunityId = "demo:1",
                minEtaMinutes = 4,
                maxEtaMinutes = 18,
            ),
        )
    }

    private fun opportunity(
        id: String,
        title: String,
        lat: Double,
        lng: Double,
        distance: Double,
        probability: Double?,
        baseline: Double?,
        score: Double,
        potential: String,
        reason: String,
        eta: String,
    ) = RadarContextualOpportunityV1(
        id = id,
        poiId = id,
        title = title,
        subtitle = if (id == "demo:1") "Saída prevista próxima da chegada" else null,
        poiType = "demo",
        lat = lat,
        lng = lng,
        distanceKm = distance,
        eta = eta,
        continuityProbabilityPct = probability,
        baselineProbabilityPct = baseline,
        deltaProbabilityPct = if (probability != null && baseline != null) probability - baseline else null,
        confidence = if (probability == null) .45 else .82,
        rankingScore = score,
        potential = potential,
        reasonHeadline = reason,
        windowStart = null,
        windowEnd = null,
        context = null,
        evidence = listOf(
            RadarContextualEvidenceV1("baseline", "Permanecer no destino: ${baseline?.toInt() ?: "—"}% em até 10 min", baseline, 84, "medium"),
            RadarContextualEvidenceV1("distance", "$distance km do destino", null, null, null),
            RadarContextualEvidenceV1("quality", "DEMO visual · não representa previsão real", null, null, null),
        ),
    )
}
