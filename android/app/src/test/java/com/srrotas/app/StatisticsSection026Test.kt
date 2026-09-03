package com.srrotas.app

object StatisticsSection026Test {
    @JvmStatic
    fun main(args: Array<String>) {
        check(StatisticsSection026.DEFAULT == StatisticsSection026.Section.ANALYSES)
        check(StatisticsSection026.mainRouteLabel() == "Estatísticas")
        check(StatisticsSection026.headerTitle("Histórico") == "Estatísticas")
        check(StatisticsSection026.headerTitle("Agora") == "Agora")

        val items = StatisticsSection026.items()
        check(items.size == 6)
        check(items.map { it.section } == listOf(
            StatisticsSection026.Section.HISTORY,
            StatisticsSection026.Section.COMPARISONS,
            StatisticsSection026.Section.ANALYSES,
            StatisticsSection026.Section.CATEGORIES,
            StatisticsSection026.Section.PERIOD,
            StatisticsSection026.Section.JOURNEYS,
        ))
        check(items.first().title == "Histórico de corridas")
        check(items[4].title == "Detalhes do período")
    }
}
