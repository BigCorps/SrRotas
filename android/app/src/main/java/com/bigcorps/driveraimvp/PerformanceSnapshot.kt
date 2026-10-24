package com.srrotas.app

import java.time.Instant

/** Read-only presentation contract. No financial arithmetic or local fallback. */
data class PerformanceReading(
    val key: String,
    val formatted: String,
    val value: Double?,
    val semantic: String?,
    val status: String,
    val source: String?,
    val sampleCount: Int?,
    val completeness: String?,
    val coverage: Double?,
) {
    fun display(): String {
        val expected = PerformanceSnapshot.semantics[key] ?: return "—"
        if (status != "available" || semantic != expected || value?.isFinite() != true ||
            formatted.isBlank() || formatted == "—" || source.isNullOrBlank() ||
            sampleCount == null || sampleCount < 0 || completeness !in setOf("complete", "partial") ||
            (coverage != null && (!coverage.isFinite() || coverage !in 0.0..1.0))) return "—"
        if (key != "estimated_profit" && value < 0.0) return "—"
        // These metrics are only eligible under complete evidence in the canonical contract.
        if (key in setOf("estimated_profit", "realized_revenue_per_hour", "realized_revenue_per_km") && completeness != "complete") return "—"
        if (key == "realized_revenue" && (coverage == null || coverage <= 0)) return "—"
        return formatted
    }
}

data class PerformanceSnapshot(
    val from: String,
    val to: String,
    val metrics: Map<String, PerformanceReading>,
    val limitations: List<String>,
    val issues: Set<PerformanceDataIssue> = emptySet(),
) {
    fun matches(from: String, to: String): Boolean = runCatching {
        Instant.parse(this.from) == Instant.parse(from) && Instant.parse(this.to) == Instant.parse(to)
    }.getOrDefault(false)

    companion object {
        const val SCHEMA = "srrotas-performance-snapshot-v1"
        val semantics = linkedMapOf(
            "realized_revenue" to "REALIZED_REVENUE",
            "estimated_profit" to "ESTIMATED_COST",
            "completed_rides" to "REALIZED_RIDE",
            "closed_journey_hours" to "MIXED",
            "actual_distance" to "ACTUAL_DISTANCE",
            "realized_revenue_per_hour" to "REALIZED_REVENUE",
            "realized_revenue_per_km" to "REALIZED_REVENUE",
        )
    }
}

enum class PerformanceProblem(val explanation: String) {
    LOADING("Consultando o resumo canônico…"),
    NO_SESSION("Conecte este aparelho à sua conta para consultar os indicadores."),
    NO_CONFIGURATION("O endereço do serviço não está configurado."),
    UNAUTHORIZED("Sua sessão não foi autorizada. Confira a conta nas configurações."),
    ACCESS_RESTRICTED("O acesso às estatísticas está restrito nesta conta. Confira a situação do Sr. Rotas Inteligência em Minha conta."),
    FORBIDDEN("Sua conta não possui acesso autorizado a estas estatísticas."),
    CONNECTION("Não foi possível comunicar com o serviço. Tente novamente."),
    INVALID_PAYLOAD("O resumo recebido não corresponde ao contrato esperado."),
    PERIOD_MISMATCH("O serviço devolveu outro intervalo. Os indicadores foram omitidos."),
    SERVER("O serviço de estatísticas está indisponível. Tente novamente."),
}

class PerformanceFailure(val problem: PerformanceProblem) : Exception(problem.name) {
    companion object {
        fun forStatus(status: Int): PerformanceProblem? = when (status) {
            200 -> null
            401 -> PerformanceProblem.UNAUTHORIZED
            402 -> PerformanceProblem.ACCESS_RESTRICTED
            403 -> PerformanceProblem.FORBIDDEN
            else -> PerformanceProblem.SERVER
        }
        fun classify(error: Throwable): PerformanceFailure = when (error) {
            is PerformanceFailure -> error
            is java.io.IOException -> PerformanceFailure(PerformanceProblem.CONNECTION)
            else -> PerformanceFailure(PerformanceProblem.INVALID_PAYLOAD)
        }
    }
}
