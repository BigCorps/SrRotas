package com.srrotas.app

import java.text.NumberFormat
import java.util.Locale

/** UI-only guidance, read from explicit evidence; never computes financial values. */
enum class PerformanceDataIssue(val explanation: String, val keys: Set<String>) {
    JOURNEY_DURATION("Há jornadas sem duração encerrada válida no período. Confira seus registros, sem encerrar uma jornada que ainda está em andamento.",
        setOf("closed_journey_hours", "realized_revenue_per_hour", "realized_revenue_per_km", "estimated_profit")),
    ODOMETER("Há jornadas sem odômetro completo no período. Confira a quilometragem registrada; dados antigos podem não ser recuperáveis.",
        setOf("actual_distance", "realized_revenue_per_km", "estimated_profit")),
}

enum class PerformanceDestination(val label: String) {
    ACCOUNT("Ver minha conta"), SETTINGS("Abrir configurações"), JOURNEYS("Ver jornadas"), RIDES("Ver corridas"),
}

data class PerformanceNotice(val title: String, val message: String, val banner: String?)

object PerformancePresentation {
    val titles = linkedMapOf(
        "realized_revenue" to "Tarifas de corridas concluídas",
        "estimated_profit" to "Resultado estimado",
        "completed_rides" to "Corridas confirmadas",
        "closed_journey_hours" to "Horas de jornadas encerradas",
        "actual_distance" to "Distância registrada",
        "realized_revenue_per_hour" to "Tarifas por hora",
        "realized_revenue_per_km" to "Tarifas por km",
    )

    fun warningDestination(key: String, problem: PerformanceProblem?, issues: Set<PerformanceDataIssue>): PerformanceDestination? {
        if (problem != null) return when (problem) {
            PerformanceProblem.NO_SESSION, PerformanceProblem.UNAUTHORIZED, PerformanceProblem.ACCESS_RESTRICTED,
            PerformanceProblem.FORBIDDEN -> PerformanceDestination.ACCOUNT
            PerformanceProblem.NO_CONFIGURATION -> PerformanceDestination.SETTINGS
            else -> null
        }
        return if (issues.any { key in it.keys }) PerformanceDestination.JOURNEYS else null
    }

    fun cardDestination(key: String): PerformanceDestination? = when (key) {
        "realized_revenue", "completed_rides" -> PerformanceDestination.RIDES
        "actual_distance", "closed_journey_hours", "realized_revenue_per_hour", "realized_revenue_per_km" -> PerformanceDestination.JOURNEYS
        else -> null // No native profit drill-down exists in stage 1.
    }

    /** Same policy drives the actual dialog button and unit tests of its callback. */
    fun warningAction(key: String, problem: PerformanceProblem?, issues: Set<PerformanceDataIssue>,
        navigate: (PerformanceDestination) -> Unit): (() -> Unit)? =
        warningDestination(key, problem, issues)?.let { destination -> { navigate(destination) } }

    fun notice(snapshot: PerformanceSnapshot?, problem: PerformanceProblem?): PerformanceNotice {
        if (problem == PerformanceProblem.LOADING) return PerformanceNotice("Consultando resumo…", "Aguarde a consulta dos indicadores deste período.", null)
        if (problem != null) return PerformanceNotice(
            if (problem == PerformanceProblem.ACCESS_RESTRICTED) "Acesso às estatísticas restrito" else "Resumo indisponível", problem.explanation, null)
        if (snapshot == null) return PerformanceNotice("Resumo indisponível", "Nenhum resumo foi recebido para este período.", null)
        // Count only explicit server states, not missing/null fields or HTTP errors.
        val unavailable = titles.keys.count { snapshot.metrics[it]?.status == "unavailable" }
        val partial = titles.keys.count { key ->
            snapshot.metrics[key]?.let { it.status == "available" && it.completeness == "partial" } == true
        }
        val banner = buildList {
            if (unavailable > 0) add(if (unavailable == 1) "1 indicador indisponível" else "$unavailable indicadores indisponíveis")
            if (partial > 0) add(if (partial == 1) "1 indicador com dados parciais" else "$partial indicadores com dados parciais")
        }.joinToString(" · ").takeIf { it.isNotEmpty() }
        val hasValue = titles.keys.any { key -> snapshot.metrics[key]?.display()?.let { it != "—" } == true }
        return PerformanceNotice(
            if (partial > 0) "Dados parciais" else if (!hasValue) "Sem dados suficientes" else "Dados do período",
            if (snapshot.issues.isNotEmpty()) snapshot.issues.joinToString("\n") { it.explanation }
            else if (!hasValue) "Não há indicadores com evidência suficiente neste resumo. Ausência de dados não significa zero."
            else if (partial > 0) "Alguns indicadores não abrangem todos os registros. Confira a cobertura antes de comparar."
            else "Confira a origem, a amostra e as limitações de cada indicador.", banner)
    }

    fun sourceLabel(source: String?): String = when (source) {
        "personal_canonical+outcomes+journey_metrics" -> "Seus registros de ofertas, corridas confirmadas e jornadas"
        "realized_revenue+actual_distance+cost_profile" -> "Tarifas de corridas concluídas, distância registrada e perfil de custo"
        null, "" -> "Origem não informada"
        else -> "Serviço de estatísticas da sua conta"
    }

    fun evidenceText(reading: PerformanceReading?): String {
        if (reading == null) return "Este indicador não tem uma leitura validada no período. Isso não significa zero."
        val coverage = reading.coverage?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let {
            NumberFormat.getPercentInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 2 }.format(it)
        } ?: "não informada"
        val completeness = when (reading.completeness) { "complete" -> "completa"; "partial" -> "parcial"; else -> "não informada" }
        return "${if (reading.display() == "—") "Indicador indisponível" else "Indicador disponível"}. Abrangência dos registros: $completeness.\n" +
            "Amostra da fonte: ${reading.sampleCount?.let { "$it registro(s)" } ?: "não informada"}. Cobertura informada pela fonte: $coverage."
    }
}

/** Statistics-only: split header rows only when logo/title/actions cannot share one line (vc105: phones 340dp+ keep one row). */
object StatisticsHeaderLayout {
    fun stacked(widthDp: Int, fontScale: Float): Boolean = widthDp < 340 || (fontScale >= 1.3f && widthDp / fontScale < 400f)
}
