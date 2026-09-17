package com.srrotas.app

/** Estrutura da página Estatísticas. RC3.5 remove Histórico da grade interna. */
object StatisticsSection026 {
    enum class Section { HISTORY, COMPARISONS, ANALYSES, CATEGORIES, PERIOD, JOURNEYS }
    data class Item(val section: Section, val title: String, val subtitle: String)
    val DEFAULT: Section = Section.ANALYSES
    fun mainRouteLabel(): String = "Estatísticas"
    fun headerTitle(original: String): String = if (original == "Histórico") "Estatísticas" else original
    fun headerSubtitle(originalTitle: String, originalSubtitle: String): String =
        if (originalTitle == "Histórico") "Desempenho, comparativos, categorias e jornadas." else originalSubtitle
    fun items(): List<Item> = listOf(
        Item(Section.COMPARISONS, "Comparativos", "Período x anterior"),
        Item(Section.ANALYSES, "Análises", "Desempenho e evolução"),
        Item(Section.CATEGORIES, "Categorias", "Resultado por serviço"),
        Item(Section.PERIOD, "Detalhes do período", "Filtros e visão detalhada"),
        Item(Section.JOURNEYS, "Jornadas", "Sessões registradas"),
    )
}
