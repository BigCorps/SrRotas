package com.srrotas.app

/**
 * Estrutura de navegação da página Estatísticas 0.26.
 *
 * A rota Android permanece HISTORY para preservar intents/atalhos antigos,
 * mas a nomenclatura visível passa a ser Estatísticas.
 */
object StatisticsSection026 {
    enum class Section {
        HISTORY,
        COMPARISONS,
        ANALYSES,
        CATEGORIES,
        PERIOD,
        JOURNEYS,
    }

    data class Item(
        val section: Section,
        val title: String,
        val subtitle: String,
    )

    val DEFAULT: Section = Section.ANALYSES

    fun mainRouteLabel(): String = "Estatísticas"

    fun headerTitle(original: String): String =
        if (original == "Histórico") "Estatísticas" else original

    fun headerSubtitle(originalTitle: String, originalSubtitle: String): String =
        if (originalTitle == "Histórico") {
            "Desempenho, corridas, comparativos e jornadas."
        } else {
            originalSubtitle
        }

    fun items(): List<Item> = listOf(
        Item(Section.HISTORY, "Histórico de corridas", "Ofertas e confirmações"),
        Item(Section.COMPARISONS, "Comparativos", "Período x anterior"),
        Item(Section.ANALYSES, "Análises", "Desempenho e evolução"),
        Item(Section.CATEGORIES, "Categorias", "Resultado por serviço"),
        Item(Section.PERIOD, "Detalhes do período", "Filtros e visão detalhada"),
        Item(Section.JOURNEYS, "Jornadas", "Sessões registradas"),
    )
}
