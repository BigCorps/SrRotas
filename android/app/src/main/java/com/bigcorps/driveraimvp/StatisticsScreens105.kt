package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.srrotas.app.StatsUi105.dp
import com.srrotas.app.StatsUi105.lp

/** Everything the four vc105 screens may show; built only from canonical readings. */
data class StatsState105(
    val periodLabel: String,
    val days: Int,
    val snapshot: PerformanceSnapshot?,
    val snapshotProblem: PerformanceProblem?,
    val series: StatisticsSeries105?,
    val seriesProblem: PerformanceProblem?,
    val offers: HistoryAnalytics?,
    val offersLoading: Boolean,
    val offersCloud: Boolean,
)

enum class StatsScreen105 { OVERVIEW, REVENUE, LIBRARY, CROSS, HOURS, OFFERS }

enum class CrossMetric105(val label: String, val axis: String, val legend: String, val icon: Int, val tone: String, val seriesKey: String?) {
    REVENUE("Tarifas (R$)", "Tarifas (R$)", "Tarifas (R$)", R.drawable.sr105_ic_money, "green", "realized_revenue"),
    HOURS("Horas de jornadas", "Horas (h)", "Horas (h)", R.drawable.sr105_ic_clock, "blue", "closed_journey_hours"),
    DISTANCE("Quilômetros", "Distância (km)", "Km", R.drawable.sr105_ic_road, "blue", "actual_distance"),
    RIDES("Corridas concluídas", "Corridas", "Corridas", R.drawable.sr105_ic_car, "teal", "completed_rides"),
    OFFERS("Ofertas observadas", "Ofertas", "Ofertas", R.drawable.sr105_ic_eye, "blue", "observed_offers"),
    QUALITY("Qualidade das ofertas", "R$/h médio (ofertas)", "R$/h médio", R.drawable.sr105_ic_star, "orange", null),
    HOUR("Horário", "Ofertas", "Ofertas", R.drawable.sr105_ic_clock, "blue", null);

    fun format(value: Double): String = when (this) {
        REVENUE, QUALITY -> StatsFormat105.moneyShort(value)
        HOURS, DISTANCE -> StatsFormat105.number(value, 0)
        else -> StatsFormat105.number(value, 0)
    }
    fun formatFull(value: Double): String = when (this) {
        REVENUE, QUALITY -> StatsFormat105.money(value)
        HOURS -> StatsFormat105.hours(value)
        DISTANCE -> StatsFormat105.km(value)
        else -> StatsFormat105.number(value, 0)
    }
    companion object { val daily = listOf(REVENUE, HOURS, DISTANCE, RIDES, OFFERS) }
}

interface StatsActions105 {
    fun openPeriod()
    fun openScreen(screen: StatsScreen105)
    fun openSection(section: StatisticsSection026.Section)
    fun openOffers()
    fun back()
    fun cardClicked(key: String, title: String, reading: PerformanceReading?)
    fun warning(key: String, title: String, reading: PerformanceReading?)
    fun sources()
    fun pendings()
    fun seriesInfo(title: String, series: DailySeries105?)
    fun info(title: String, message: String)
    fun table(title: String, subtitle: String, rows: List<Pair<String, String>>, note: String)
    fun choose(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit)
    fun openCross(a: CrossMetric105, b: CrossMetric105)
    fun retry()
}

object StatisticsScreens105 {
    // ---------------------------------------------------------------- shared

    private fun periodChips(context: Context, s: StatsState105, a: StatsActions105, current: StatsScreen105, withBack: Boolean): List<StatsUi105.Chip> = buildList {
        if (withBack) add(StatsUi105.Chip("‹ Resumo", description = "Voltar ao resumo") { a.back() })
        add(StatsUi105.Chip("${s.periodLabel} ▾", filled = true, description = "Período: ${s.periodLabel}. Alterar") { a.openPeriod() })
        if (!withBack) add(StatsUi105.Chip("Resumo", selected = current == StatsScreen105.OVERVIEW) { a.openScreen(StatsScreen105.OVERVIEW) })
        if (current == StatsScreen105.REVENUE) add(StatsUi105.Chip("Tarifas", selected = true) { })
        add(StatsUi105.Chip("Produt.", selected = current == StatsScreen105.LIBRARY, description = "Produtividade e relatórios") { a.openScreen(StatsScreen105.LIBRARY) })
        add(StatsUi105.Chip("Horários", selected = current == StatsScreen105.HOURS) { a.openScreen(StatsScreen105.HOURS) })
        add(StatsUi105.Chip("Comparativos") { a.openSection(StatisticsSection026.Section.COMPARISONS) })
        add(StatsUi105.Chip("Jornadas") { a.openSection(StatisticsSection026.Section.JOURNEYS) })
        add(StatsUi105.Chip("Corridas") { a.openSection(StatisticsSection026.Section.HISTORY) })
        add(StatsUi105.Chip("Categorias") { a.openSection(StatisticsSection026.Section.CATEGORIES) })
    }

    fun chips(context: Context, s: StatsState105, a: StatsActions105, current: StatsScreen105): View =
        StatsUi105.chipStrip(context, periodChips(context, s, a, current, withBack = current != StatsScreen105.OVERVIEW),
            more = if (current == StatsScreen105.OVERVIEW) ({ a.openScreen(StatsScreen105.LIBRARY) }) else null)

    private fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun loading(problem: PerformanceProblem?) = problem == PerformanceProblem.LOADING

    // ------------------------------------------------------------- overview

    data class Kpi(val key: String, val title: String, val icon: Int, val tone: String)

    /** Short labels of the mock; full titles stay in PerformancePresentation for dialogs. */
    val kpis = listOf(
        Kpi("realized_revenue", "Tarifas concluídas", R.drawable.sr105_ic_money, "green"),
        Kpi("estimated_profit", "Resultado estimado", R.drawable.sr105_ic_bars, "orange"),
        Kpi("completed_rides", "Corridas", R.drawable.sr105_ic_car, "teal"),
        Kpi("closed_journey_hours", "Horas", R.drawable.sr105_ic_clock, "purple"),
        Kpi("actual_distance", "Distância", R.drawable.sr105_ic_pin, "blue"),
        Kpi("realized_revenue_per_hour", "R$/h", R.drawable.sr105_ic_gauge, "blue"),
        Kpi("realized_revenue_per_km", "R$/km", R.drawable.sr105_ic_road, "blue"),
    )

    /** Pendências: each indicator counted once (unavailable OR partial). */
    fun pendingCount(snapshot: PerformanceSnapshot?): Int = PerformancePresentation.titles.keys.count { key ->
        val reading = snapshot?.metrics?.get(key)
        reading?.status == "unavailable" || (reading?.status == "available" && reading.completeness == "partial")
    }

    fun overview(context: Context, s: StatsState105, a: StatsActions105): View = column(context).apply {
        addView(chips(context, s, a, StatsScreen105.OVERVIEW))
        addView(StatsUi105.sectionTitle(context, "Visão rápida do período", 20f), lp(context, top = 12, bottom = 8))
        val cfg = context.resources.configuration
        val cols = StatsUi105.columns(cfg.screenWidthDp, cfg.fontScale)
        val isLoading = loading(s.snapshotProblem)
        val cells = kpis.map { kpi ->
            val reading = s.snapshot?.metrics?.get(kpi.key)
            val value = reading?.display() ?: "—"
            val notice = !isLoading && (value == "—" || reading?.completeness == "partial")
            // The card label already says R$/h or R$/km; the server suffix is dropped only for display.
            val shown = if (value == "—") "—" else StatsFormat105.unbreakable(value.removeSuffix("/h").removeSuffix("/km"))
            kpiCard(context, kpi, shown, isLoading, notice,
                onClick = { a.cardClicked(kpi.key, PerformancePresentation.titles[kpi.key] ?: kpi.title, reading) },
                onWarning = { a.warning(kpi.key, PerformancePresentation.titles[kpi.key] ?: kpi.title, reading) })
        } + completenessCard(context, s, a)
        addView(StatsUi105.grid(context, cells, cols))
        pendingBanner(context, s, a)?.let { addView(it, lp(context, top = 10)) }
        addView(StatsUi105.sectionTitle(context, "Ofertas e atividade", 19f), lp(context, top = 16, bottom = 8))
        addView(StatsUi105.grid(context, offerCards(context, s, a), cols))
        addView(rowLink(context, R.drawable.sr105_ic_doc, "Fonte dos dados e limitações") { a.sources() }, lp(context, top = 10))
        addView(StatsUi105.sectionTitle(context, "Explorar", 19f), lp(context, top = 16, bottom = 8))
        addView(StatsUi105.grid(context, listOf(
            exploreCard(context, R.drawable.sr105_ic_bars, "Cruzar\nmétricas") { a.openScreen(StatsScreen105.CROSS) },
            exploreCard(context, R.drawable.sr105_ic_calendar, "Comparar\nperíodos") { a.openSection(StatisticsSection026.Section.COMPARISONS) },
            exploreCard(context, R.drawable.sr105_ic_doc, "Todos os\nrelatórios") { a.openScreen(StatsScreen105.LIBRARY) },
        ), if (cfg.screenWidthDp < 330) 1 else 3, gapDp = 8))
    }

    fun kpiCard(context: Context, kpi: Kpi, value: String, loading: Boolean, notice: Boolean, onClick: () -> Unit, onWarning: () -> Unit): View =
        StatsUi105.card(context, padH = 9, padV = 9).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 62)
            isClickable = true; isFocusable = true
            contentDescription = "${kpi.title}: ${if (loading) "consultando" else if (value == "—") "indisponível" else value}"
            setOnClickListener { onClick() }
            addView(StatsUi105.iconCircle(context, kpi.icon, StatsUi105.tone(context, kpi.tone), 38, 21))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // The "!" sits on the label line so the value keeps the full width.
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(StatsUi105.text(context, kpi.title, 12f, StatsUi105.ink(context)).apply { alpha = .82f },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (notice) addView(StatsUi105.warnBadge(context, "Saiba por que ${kpi.title} está incompleto") { onWarning() },
                        LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)).apply { marginStart = dp(context, 4) })
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 20)))
                if (loading) addView(StatsUi105.skeleton(context, 72, 14), lp(context, top = 5, width = dp(context, 72)))
                else addView(StatsUi105.value(context, value, if (value == "—") 18 else 19, 9), lp(context, top = 2))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 9) })
        }

    private fun completenessCard(context: Context, s: StatsState105, a: StatsActions105): View {
        val notice = PerformancePresentation.notice(s.snapshot, s.snapshotProblem)
        val complete = PerformancePresentation.titles.keys.count { key ->
            s.snapshot?.metrics?.get(key)?.let { it.display() != "—" && it.completeness == "complete" } == true
        }
        val isLoading = loading(s.snapshotProblem)
        return StatsUi105.card(context, padH = 9, padV = 9).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 62)
            isClickable = true; isFocusable = true
            contentDescription = "${notice.title}. Ver fonte e limitações"
            setOnClickListener { a.sources() }
            addView(StatsUi105.iconCircle(context, R.drawable.sr105_ic_doc, StatsUi105.tone(context, "slate"), 38, 20))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(StatsUi105.text(context, notice.title, 12f, StatsUi105.ink(context)).apply { alpha = .82f })
                if (isLoading) {
                    addView(StatsUi105.skeleton(context, 84, 7), lp(context, top = 7, width = dp(context, 84)))
                    addView(StatsUi105.skeleton(context, 56, 7), lp(context, top = 5, width = dp(context, 56)))
                } else addView(StatsUi105.text(context,
                    if (s.snapshot != null) "$complete de ${PerformancePresentation.titles.size} completos" else "Toque para detalhes",
                    12.5f, StatsUi105.ink(context), bold = true), lp(context, top = 5))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 9) })
        }
    }

    private fun pendingBanner(context: Context, s: StatsState105, a: StatsActions105): View? {
        val problem = s.snapshotProblem
        val count = pendingCount(s.snapshot)
        val label = when {
            problem == PerformanceProblem.LOADING -> return null
            problem != null -> PerformancePresentation.notice(s.snapshot, problem).title
            count == 0 -> return null
            count == 1 -> "1 pendência nos dados"
            else -> "$count pendências nos dados"
        }
        val warn = StatsUi105.tone(context, "amber")
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = StatsUi105.rounded(warn.soft, 12f, context, warn.fg, 1.2f)
            minimumHeight = dp(context, 44)
            setPadding(dp(context, 11), dp(context, 8), dp(context, 10), dp(context, 8))
            isClickable = true; isFocusable = true
            contentDescription = "$label. Ver detalhes"
            setOnClickListener { a.pendings() }
            addView(StatsUi105.warnBadge(context, label, 24) { a.pendings() }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            addView(StatsUi105.text(context, label, 14f, StatsUi105.ink(context), bold = false, lines = 2),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 11) })
            addView(StatsUi105.chevron(context))
        }
    }

    private fun offerCards(context: Context, s: StatsState105, a: StatsActions105): List<View> {
        val summary = s.offers?.summary
        val enough = summary != null && summary.offerCount >= 20
        val goodRate = if (enough) summary!!.goodCount * 100.0 / summary.offerCount else null
        fun card(icon: Int, tone: String, title: String, value: String?, valueColor: Int? = null) = kpiCardSimple(context, icon, tone, title, value, valueColor) { a.openOffers() }
        return listOf(
            card(R.drawable.sr105_ic_eye, "blue", "Ofertas observadas", summary?.offerCount?.toString()),
            card(R.drawable.sr105_ic_trend, "green", "R$/km médio", summary?.let { StatsFormat105.money(it.averagePerKm) }),
            card(R.drawable.sr105_ic_clock, "blue", "R$/h médio", summary?.let { StatsFormat105.money(it.averagePerHour) }),
            card(R.drawable.sr105_ic_star, "orange", "Qualidade", if (summary == null) null else goodRate?.let { "${StatsFormat105.number(it, 0)}% boas" } ?: "—",
                if (goodRate != null) StatsUi105.tone(context, "green").fg else null),
        )
    }

    fun kpiCardSimple(context: Context, icon: Int, tone: String, title: String, value: String?, valueColor: Int? = null, onClick: () -> Unit): View =
        StatsUi105.card(context, padH = 9, padV = 9).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 58)
            isClickable = true; isFocusable = true
            contentDescription = "$title: ${value ?: "consultando"}"
            setOnClickListener { onClick() }
            addView(StatsUi105.iconCircle(context, icon, StatsUi105.tone(context, tone), 36, 20))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(StatsUi105.text(context, title, 12f, StatsUi105.ink(context)).apply { alpha = .82f })
                if (value == null) addView(StatsUi105.skeleton(context, 60, 13), lp(context, top = 7, width = dp(context, 60)))
                else addView(StatsUi105.value(context, value, 18, 11, valueColor ?: StatsUi105.ink(context)), lp(context, top = 4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 9) })
        }

    fun rowLink(context: Context, icon: Int, label: String, trailing: View? = null, onClick: () -> Unit): View =
        StatsUi105.card(context, padH = 12, padV = 10).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 44)
            isClickable = true; isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(StatsUi105.icon(context, icon, StatsUi105.ink(context), 20))
            addView(StatsUi105.text(context, label, 14f, StatsUi105.ink(context), lines = 2),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 10) })
            trailing?.let { addView(it) }
            addView(StatsUi105.chevron(context))
        }

    private fun exploreCard(context: Context, icon: Int, label: String, onClick: () -> Unit): View =
        StatsUi105.card(context, padH = 8, padV = 8).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 52)
            isClickable = true; isFocusable = true
            contentDescription = label.replace("\n", " ")
            setOnClickListener { onClick() }
            addView(StatsUi105.icon(context, icon, StatsUi105.blue(context), 22))
            addView(StatsUi105.text(context, label, 12.5f, StatsUi105.ink(context), lines = 2).apply { setLineSpacing(0f, 1.05f) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 7) })
            addView(StatsUi105.chevron(context, sizeDp = 16))
        }

    // --------------------------------------------------- revenue detail (2)

    fun revenue(context: Context, s: StatsState105, a: StatsActions105): View = column(context).apply {
        addView(chips(context, s, a, StatsScreen105.REVENUE))
        val series = s.series?.get("realized_revenue")
        val seriesLoading = loading(s.seriesProblem) || loading(s.snapshotProblem)
        val kpi = s.snapshot?.metrics?.get("realized_revenue")?.display()?.takeIf { it != "—" }
        addView(StatsUi105.card(context, padH = 12, padV = 12, radius = 16f).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(StatsUi105.iconCircle(context, R.drawable.sr105_ic_money, StatsUi105.tone(context, "green"), 44, 26))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(StatsUi105.text(context, "Tarifas de corridas concluídas", 16f, StatsUi105.ink(context), bold = true, lines = 2))
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        if (seriesLoading && kpi == null) addView(StatsUi105.skeleton(context, 130, 24), lp(context, top = 6, width = dp(context, 130)))
                        else addView(StatsUi105.value(context, kpi?.let(StatsFormat105::unbreakable) ?: "—", 30, 16),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { topMargin = dp(context, 4) })
                        val change = s.series?.comparison?.takeIf { it.eligible && it.changePct != null }
                        if (change != null && kpi != null) addView(changePill(context, change.changePct!!), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 8) })
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 10) })
            })
            val change = s.series?.comparison?.takeIf { it.eligible && it.changePct != null }
            if (change != null && kpi != null) addView(StatsUi105.text(context, "em relação aos ${s.days} dias anteriores", 11f, StatsUi105.muted(context)).apply { gravity = Gravity.END }, lp(context, top = 2))
            else if (kpi != null && s.series?.comparison?.eligible == false) addView(StatsUi105.text(context, "Comparação indisponível: exige tarifas conciliadas completas nos dois períodos.", 11f, StatsUi105.muted(context), lines = 2), lp(context, top = 4))
            addView(revenueChart(context, s, series, seriesLoading), lp(context, top = 10))
            if (series != null && series.status == "partial") addView(StatsUi105.text(context, "Colunas claras indicam dias com tarifas ainda não conciliadas; traço indica dia sem dado.", 11f, StatsUi105.muted(context), lines = 3), lp(context, top = 4))
        }, lp(context, top = 12))

        val rides = s.series?.rides
        val summaryRows = listOf(
            "Corridas confirmadas" to (rides?.completed?.takeIf { rides.status != "unavailable" || it > 0 }?.toString()
                ?: s.snapshot?.metrics?.get("completed_rides")?.display()?.takeIf { it != "—" } ?: "—"),
            "Tarifa média" to StatsFormat105.money(rides?.averageFare),
            "Maior valor" to StatsFormat105.money(rides?.maxFare),
            "Menor valor" to StatsFormat105.money(rides?.minFare),
        )
        addView(tableCard(context, "Resumo", summaryRows, seriesLoading && rides == null), lp(context, top = 12))
        if (rides != null && rides.withKnownFare < rides.completed) addView(StatsUi105.text(context,
            "Tarifa média considera ${rides.withKnownFare} de ${rides.completed} corridas com tarifa conciliada.", 11f, StatsUi105.muted(context), lines = 2), lp(context, top = 4))

        addView(detailCard(context, s, a), lp(context, top = 12))
        addView(insightCard(context, s, seriesLoading), lp(context, top = 12))
        addView(StatsUi105.text(context, "Tarifas ofertadas vinculadas a corridas concluídas; não comprovam o recebimento final. Ofertas observadas não entram nesta soma.", 11f, StatsUi105.muted(context), lines = 3), lp(context, top = 8))
    }

    private fun changePill(context: Context, pct: Double): View = StatsUi105.text(context,
        "${if (pct >= 0) "↗" else "↘"} ${StatsFormat105.pct(pct)}", 14f,
        if (pct >= 0) StatsUi105.tone(context, "green").fg else SrUi023.palette(context).red, bold = true).apply {
        background = StatsUi105.rounded(if (pct >= 0) StatsUi105.tone(context, "green").soft else SrUi023.palette(context).dangerSoft, 8f, context)
        setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5))
        contentDescription = "Variação ${StatsFormat105.pct(pct)} em relação ao período anterior"
    }

    private fun revenueChart(context: Context, s: StatsState105, series: DailySeries105?, isLoading: Boolean): View = StatisticsChartView105(context).apply {
        val labels = series?.buckets?.map { it.label } ?: weekLabels(s.days)
        when {
            series != null -> setData(series.buckets.map { StatisticsChartView105.Point(it.label, if (it.drawable) it.value else null, partial = it.status == "partial") },
                barName = "Tarifas (R$)", barFormat = { StatsFormat105.moneyShort(it) },
                emptyMessage = "Sem tarifas de corridas concluídas conciliadas neste período. Ausência de dado não significa zero.")
            isLoading -> setLoading(labels, false)
            else -> setMessage(labels, (s.seriesProblem ?: PerformanceProblem.SERVER).explanation)
        }
    }

    private fun weekLabels(days: Int): List<String> = if (days <= 7) listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom") else (1..8).map { "" }

    fun tableCard(context: Context, title: String, rows: List<Pair<String, String>>, isLoading: Boolean): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = SrUi023.palette(context)
            background = StatsUi105.rounded(p.surface, 14f, context, p.outline, 1f)
            elevation = dp(context, 1.5f).toFloat()
            clipToOutline = true
            addView(header(context, title))
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) addView(StatsUi105.divider(context).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(context, 14), 0, dp(context, 14), 0) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(context, 40)
                    setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6))
                    addView(StatsUi105.text(context, label, 14f, StatsUi105.ink(context)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (isLoading) addView(StatsUi105.skeleton(context, 54, 12))
                    else addView(StatsUi105.value(context, value, 14, 10))
                    contentDescription = "$label: ${if (isLoading) "consultando" else value}"
                })
            }
        }

    private fun header(context: Context, title: String): View = StatsUi105.text(context, title, 16f, StatsUi105.tone(context, "blue").fg.let {
        if (Appearance021.isDark(context)) it else 0xFF1E3A8A.toInt()
    }, bold = true).apply {
        background = StatsUi105.rounded(StatsUi105.tone(context, "blue").soft, 0f, context)
        setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9))
    }

    private fun detailCard(context: Context, s: StatsState105, a: StatsActions105): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = SrUi023.palette(context)
        background = StatsUi105.rounded(p.surface, 14f, context, p.outline, 1f)
        elevation = dp(context, 1.5f).toFloat()
        clipToOutline = true
        addView(header(context, "Detalhamento"))
        val series = s.series
        val revenue = series?.get("realized_revenue")
        val noRegion = "As corridas concluídas ainda não têm região conciliada; este detalhamento não está disponível."
        val rows = listOf(
            Triple(R.drawable.sr105_ic_calendar, "Por dia", revenue?.takeIf { it.hasValues }?.let { {
                a.table("Por dia", "Tarifas de corridas concluídas por dia de início da jornada", it.buckets.map { b ->
                    "${b.weekday} ${b.date}${if (b.partialWindow) "*" else ""}" to (if (b.drawable) StatsFormat105.money(b.value) else "sem dado") + if (b.status == "partial") " (parcial)" else ""
                }, "* dia incompleto no início ou no fim do período.")
            } }),
            Triple(R.drawable.sr105_ic_bars, "Por modalidade", series?.byService?.let { rows -> {
                a.table("Por modalidade", "Corridas concluídas e tarifas conciliadas", rows.map { g -> "${g.label} · ${g.rides} corrida(s)" to StatsFormat105.money(g.revenue) },
                    "Modalidade registrada na oferta vinculada à corrida.")
            } }),
            Triple(R.drawable.sr105_ic_timeline, "Por faixa de distância", series?.byDistance?.let { rows -> {
                a.table("Por faixa de distância", "Distância total anunciada na oferta vinculada", rows.map { g -> "${g.label} · ${g.rides} corrida(s)" to StatsFormat105.money(g.revenue) },
                    "A distância é a anunciada pela plataforma, não o odômetro.")
            } }),
            Triple(R.drawable.sr105_ic_pin, "Por região de origem", null),
            Triple(R.drawable.sr105_ic_pin, "Por região de destino", null),
        )
        rows.forEachIndexed { i, (icon, label, action) ->
            if (i > 0) addView(StatsUi105.divider(context).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(context, 14), 0, dp(context, 14), 0) })
            val enabled = action != null
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, 44)
                setPadding(dp(context, 14), dp(context, 6), dp(context, 12), dp(context, 6))
                isClickable = true; isFocusable = true
                val reason = if (label.startsWith("Por região")) noRegion else "Sem corridas concluídas conciliadas no período para detalhar."
                contentDescription = if (enabled) label else "$label: indisponível"
                setOnClickListener { if (action != null) action() else a.info(label, reason) }
                addView(StatsUi105.icon(context, icon, StatsUi105.blue(context), 21).apply { alpha = if (enabled) 1f else .45f })
                addView(StatsUi105.text(context, label, 14.5f, StatsUi105.ink(context)).apply { alpha = if (enabled) 1f else .5f },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 14) })
                if (!enabled && !loading(s.seriesProblem)) addView(StatsUi105.text(context, "indisponível", 11f, StatsUi105.muted(context)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 6) })
                addView(StatsUi105.chevron(context).apply { alpha = if (enabled) 1f else .45f })
            })
        }
    }

    private fun insightCard(context: Context, s: StatsState105, isLoading: Boolean): View {
        val blue = StatsUi105.tone(context, "blue")
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = StatsUi105.rounded(blue.soft, 14f, context, blend(blue.fg), 1f)
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            addView(StatsUi105.iconCircle(context, R.drawable.sr105_ic_bulb, StatsUi105.Tone(blue.fg, SrUi023.palette(context).surface), 48, 26))
            val text = s.series?.insight
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(StatsUi105.text(context, "Insight", 16f, blue.fg, bold = true))
                if (isLoading && s.series == null) addView(StatsUi105.skeleton(context, 180, 10), lp(context, top = 8, width = dp(context, 180)))
                else addView(StatsUi105.text(context, text ?: "Ainda não há uma leitura sustentada pelos dados deste período.", 14f, StatsUi105.ink(context), lines = 4).apply { setLineSpacing(0f, 1.12f) }, lp(context, top = 4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        }
    }

    private fun blend(color: Int): Int = (color and 0x00FFFFFF) or 0x66000000

    // ----------------------------------------------------------- library (3)

    data class Entry(val icon: Int, val title: String, val subtitle: String, val warning: Boolean, val disabledReason: String?, val action: (() -> Unit)?)

    fun library(context: Context, s: StatsState105, a: StatsActions105): View = column(context).apply {
        addView(chips(context, s, a, StatsScreen105.LIBRARY))
        val revenue = s.series?.get("realized_revenue")
        val hours = s.series?.get("closed_journey_hours")
        val distance = s.series?.get("actual_distance")
        val seriesWarn = !loading(s.seriesProblem) && s.series == null
        val offersEmpty = !s.offersLoading && (s.offers == null || s.offers.summary.offerCount == 0 || !s.offersCloud)
        val profit = s.snapshot?.metrics?.get("estimated_profit")
        val productivity = listOf(
            Entry(R.drawable.sr105_ic_bars, "Tarifas por hora", "Evolução diária e horas de jornada", seriesWarn || hours?.status == "partial" || hours?.status == "unavailable", null) { a.openCross(CrossMetric105.REVENUE, CrossMetric105.HOURS) },
            Entry(R.drawable.sr105_ic_road, "Tarifas por km", "Tarifas e distância registrada por dia", seriesWarn || distance?.status != "complete" && distance != null, null) { a.openCross(CrossMetric105.REVENUE, CrossMetric105.DISTANCE) },
            Entry(R.drawable.sr105_ic_calendar_days, "Melhores e piores dias", "Ranking do período", seriesWarn || revenue?.hasValues == false, null) { bestWorstDays(a, revenue) },
            Entry(R.drawable.sr105_ic_clock, "Jornadas", "Duração, início e fim", false, null) { a.openSection(StatisticsSection026.Section.JOURNEYS) },
            Entry(R.drawable.sr105_ic_cup, "Tempo ocioso", "Intervalos entre corridas", false, "Intervalos entre corridas ainda não são medidos pelo Sr. Rotas.", null),
        )
        val others = listOf(
            Entry(R.drawable.sr105_ic_clock, "Horários", "Desempenho por faixa horária", offersEmpty, null) { a.openScreen(StatsScreen105.HOURS) },
            Entry(R.drawable.sr105_ic_pin, "Regiões", "Melhores áreas e destinos", false, "Regiões de corridas concluídas ainda não têm relatório conciliado.", null),
            Entry(R.drawable.sr105_ic_car, "Corridas", "Modalidades, distâncias e valores", seriesWarn, null) { a.openScreen(StatsScreen105.REVENUE) },
            Entry(R.drawable.sr105_ic_megaphone, "Ofertas", "Quantidade, qualidade e conversão", offersEmpty, null) { a.openOffers() },
            Entry(R.drawable.sr105_ic_wallet, "Custos", "Gastos e resultado estimado", profit != null && profit.display() == "—", null) {
                a.warning("estimated_profit", PerformancePresentation.titles["estimated_profit"] ?: "Resultado estimado", profit)
            },
            Entry(R.drawable.sr105_ic_bars, "Comparativos", "Hoje x ontem, semana x anterior", false, null) { a.openSection(StatisticsSection026.Section.COMPARISONS) },
            Entry(R.drawable.sr105_ic_chart_rise, "Tendências", "Padrões e evolução diária", seriesWarn, null) { a.openScreen(StatsScreen105.REVENUE) },
        )
        addView(groupTitle(context, "Produtividade"), lp(context, top = 14, bottom = 8))
        productivity.forEachIndexed { i, e -> addView(entryRow(context, e, a), lp(context, top = if (i > 0) 7 else 0)) }
        addView(StatsUi105.divider(context), lp(context, top = 14))
        addView(groupTitle(context, "Outras categorias"), lp(context, top = 12, bottom = 8))
        others.forEachIndexed { i, e -> addView(entryRow(context, e, a), lp(context, top = if (i > 0) 7 else 0)) }
    }

    private fun bestWorstDays(a: StatsActions105, revenue: DailySeries105?) {
        val valued = revenue?.buckets?.filter { it.status == "complete" && it.value != null && !it.partialWindow }.orEmpty()
        if (revenue == null || valued.size < 2) {
            a.info("Melhores e piores dias", "O ranking precisa de pelo menos dois dias completos com tarifas de corridas concluídas conciliadas. Dias parciais ou sem dado não entram.")
            return
        }
        a.table("Melhores e piores dias", "Somente dias completos, do maior para o menor valor",
            valued.sortedByDescending { it.value }.map { "${it.weekday} ${it.date}" to StatsFormat105.money(it.value) },
            "Tarifas de corridas concluídas pelo dia de início da jornada.")
    }

    private fun groupTitle(context: Context, title: String) = StatsUi105.text(context, title, 22f, StatsUi105.ink(context), bold = true)

    private fun entryRow(context: Context, e: Entry, a: StatsActions105): View = StatsUi105.card(context, padH = 12, padV = 7).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 56)
        isClickable = true; isFocusable = true
        val enabled = e.action != null
        contentDescription = "${e.title}: ${e.disabledReason ?: e.subtitle}${if (e.warning) ". Dados incompletos" else ""}"
        setOnClickListener { if (e.action != null) e.action.invoke() else a.info(e.title, e.disabledReason ?: "") }
        addView(StatsUi105.iconCircle(context, e.icon, StatsUi105.tone(context, "blue"), 42, 24, square = true).apply { alpha = if (enabled) 1f else .5f })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(StatsUi105.text(context, e.title, 15.5f, StatsUi105.ink(context), bold = true))
            addView(StatsUi105.text(context, e.disabledReason ?: e.subtitle, 12.5f, StatsUi105.muted(context), lines = 2), lp(context, top = 3))
            alpha = if (enabled) 1f else .6f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        if (e.warning && enabled) addView(StatsUi105.warnBadge(context, "Dados incompletos em ${e.title}", 24) { e.action?.invoke() },
            LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply { marginEnd = dp(context, 8) })
        addView(StatsUi105.chevron(context).apply { alpha = if (enabled) 1f else .4f })
    }

    // ---------------------------------------------------- cross metrics (4)

    fun cross(context: Context, s: StatsState105, a: StatsActions105, first: CrossMetric105, second: CrossMetric105, onPick: (CrossMetric105, CrossMetric105) -> Unit): View = column(context).apply {
        val hourly = first == CrossMetric105.QUALITY
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(StatsUi105.card(context, padH = 14, padV = 8, radius = 10f).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, 46)
                isClickable = true; isFocusable = true
                contentDescription = "Período: ${s.periodLabel}. Alterar"
                setOnClickListener { a.openPeriod() }
                addView(StatsUi105.text(context, s.periodLabel, 16f, StatsUi105.ink(context)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(StatsUi105.icon(context, R.drawable.sr23_ic_chevron_down, StatsUi105.ink(context), 18))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val blue = StatsUi105.tone(context, "blue")
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = dp(context, 46)
                background = StatsUi105.rounded(blue.soft, 10f, context, blend(blue.fg), 1f)
                setPadding(dp(context, 10), 0, dp(context, 10), 0)
                contentDescription = "Cruzar métricas, tela atual"
                addView(StatsUi105.icon(context, R.drawable.sr105_ic_bars, blue.fg, 22))
                addView(StatsUi105.text(context, "Cruzar métricas", 15f, blue.fg), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 8) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(StatsUi105.icon(context, R.drawable.sr105_ic_bars, StatsUi105.blue(context), 30))
            addView(StatsUi105.text(context, "Cruzar métricas", 23f, StatsUi105.ink(context), bold = true), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 10) })
        }, lp(context, top = 16))
        addView(StatsUi105.text(context, "Escolha duas métricas para comparar", 14.5f, StatsUi105.muted(context), lines = 2).apply { setPadding(dp(context, 40), 0, 0, 0) }, lp(context, top = 2))

        val seriesA = first.seriesKey?.let { s.series?.get(it) }
        val seriesB = second.seriesKey?.let { s.series?.get(it) }
        fun incomplete(series: DailySeries105?) = !hourly && !loading(s.seriesProblem) && (series == null || series.status != "complete")
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selector(context, first, incomplete(seriesA), { a.seriesInfo(first.label, seriesA) }) {
                if (hourly) a.info("Qualidade das ofertas × Horário", "Este cruzamento usa somente ofertas observadas por faixa horária; escolha outra combinação abaixo para métricas diárias.")
                else pick(a, "Primeira métrica", second) { onPick(it, second) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(StatsUi105.icon(context, R.drawable.sr105_ic_swap, StatsUi105.ink(context), 22), LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply { marginStart = dp(context, 6); marginEnd = dp(context, 6) })
            addView(selector(context, second, incomplete(seriesB), { a.seriesInfo(second.label, seriesB) }) {
                if (hourly) a.info("Horário", "As faixas horárias vêm da mesma consulta de ofertas observadas do período.")
                else pick(a, "Segunda métrica", first) { onPick(first, it) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, lp(context, top = 14))

        val points: List<CrossSummary105.Point>
        val chart = StatisticsChartView105(context)
        if (hourly) {
            val hours = s.offers?.hours.orEmpty()
            points = hours.map { CrossSummary105.Point(it.label, it.label, it.averagePerHour, it.offerCount.toDouble()) }
            when {
                s.offersLoading -> chart.setLoading(listOf("06h", "09h", "12h", "15h", "18h", "21h"), true, first.axis, second.axis)
                hours.isEmpty() -> chart.setMessage(listOf("06h", "09h", "12h", "15h", "18h", "21h"), "Sem ofertas observadas com faixa horária neste período.", true, first.axis, second.axis)
                else -> chart.setData(hours.map { StatisticsChartView105.Point(it.label, it.averagePerHour?.takeIf { v -> v.isFinite() && v >= 0 }, it.offerCount.toDouble()) },
                    barName = first.legend, lineName = second.legend, barAxis = first.axis, lineAxis = second.axis,
                    barFormat = first::format, lineFormat = second::format)
            }
        } else {
            val bucketsA = seriesA?.buckets.orEmpty()
            val bucketsB = seriesB?.buckets.orEmpty()
            val aligned = bucketsA.size == bucketsB.size && bucketsA.indices.all { bucketsA[it].key == bucketsB[it].key }
            points = if (aligned) bucketsA.indices.map { i ->
                CrossSummary105.Point(bucketsA[i].label, CrossSummary105.weekdayName(bucketsA[i].weekday),
                    bucketsA[i].value.takeIf { bucketsA[i].drawable }, bucketsB[i].value.takeIf { bucketsB[i].drawable })
            } else emptyList()
            when {
                loading(s.seriesProblem) -> chart.setLoading(weekLabels(s.days), true, first.axis, second.axis)
                s.series == null -> chart.setMessage(weekLabels(s.days), (s.seriesProblem ?: PerformanceProblem.SERVER).explanation, true, first.axis, second.axis)
                !aligned -> chart.setMessage(weekLabels(s.days), "As duas séries não estão no mesmo intervalo; o cruzamento foi omitido.", true, first.axis, second.axis)
                else -> chart.setData(bucketsA.indices.map { i ->
                    StatisticsChartView105.Point(bucketsA[i].label, bucketsA[i].value.takeIf { bucketsA[i].drawable }, bucketsB[i].value.takeIf { bucketsB[i].drawable },
                        partial = bucketsA[i].status == "partial")
                }, barName = first.legend, lineName = second.legend, barAxis = first.axis, lineAxis = second.axis,
                    barFormat = first::format, lineFormat = second::format,
                    emptyMessage = "Sem dados de ${first.label.lowercase()} e ${second.label.lowercase()} neste período. Ausência de dado não significa zero.")
            }
        }
        addView(StatsUi105.card(context, padH = 12, padV = 12, radius = 14f).apply {
            orientation = LinearLayout.VERTICAL
            addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, lp(context, top = 12))

        val facts = if (hourly) {
            val best = s.offers?.hours?.filter { it.offerCount >= 5 && it.averagePerHour != null }?.maxByOrNull { it.averagePerHour!! }
            best?.let { "Faixa com maior R$/h médio das ofertas observadas: ${it.label} (${StatsFormat105.money(it.averagePerHour)}, ${it.offerCount} ofertas). Ofertas não são ganhos." }
        } else CrossSummary105.facts(points, first.label.removeSuffix(" (R$)").lowercase(), second.label.removeSuffix(" (R$)").lowercase(), first::formatFull, second::formatFull)
        addView(StatsUi105.card(context, padH = 12, padV = 12, radius = 14f).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(StatsUi105.iconCircle(context, R.drawable.sr105_ic_bars, StatsUi105.tone(context, "blue"), 48, 26))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(StatsUi105.text(context, "Resumo da comparação", 16f, StatsUi105.ink(context), bold = true))
                addView(StatsUi105.text(context, when {
                    (hourly && s.offersLoading) || (!hourly && loading(s.seriesProblem)) -> "Consultando as duas séries…"
                    facts != null -> facts
                    else -> "Ainda não há dias suficientes com as duas medidas para uma leitura."
                }, 14f, StatsUi105.muted(context), lines = 5).apply { setLineSpacing(0f, 1.12f) }, lp(context, top = 4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        }, lp(context, top = 12))

        addView(suggestions(context, a, first, second), lp(context, top = 12))
    }

    private fun pick(a: StatsActions105, title: String, other: CrossMetric105, onPick: (CrossMetric105) -> Unit) {
        val options = CrossMetric105.daily.filter { it != other }
        a.choose(title, options.map { it.label to true }) { onPick(options[it]) }
    }

    private fun selector(context: Context, metric: CrossMetric105, warn: Boolean, onWarn: () -> Unit, onClick: () -> Unit): View =
        android.widget.FrameLayout(context).apply {
            addView(StatsUi105.card(context, padH = 10, padV = 10, radius = 12f).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, 66)
                isClickable = true; isFocusable = true
                contentDescription = "${metric.label}. Trocar métrica"
                setOnClickListener { onClick() }
                addView(StatsUi105.iconCircle(context, metric.icon, StatsUi105.tone(context, metric.tone), 40, 23))
                addView(StatsUi105.text(context, metric.label, 14.5f, StatsUi105.ink(context), bold = true, lines = 2),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 8) })
                addView(StatsUi105.icon(context, R.drawable.sr23_ic_chevron_down, StatsUi105.ink(context), 16))
            }, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 8); marginEnd = dp(context, 4) })
            if (warn) addView(StatsUi105.warnBadge(context, "${metric.label}: fonte de dados incompleta", 22) { onWarn() },
                android.widget.FrameLayout.LayoutParams(dp(context, 22), dp(context, 22), Gravity.END or Gravity.TOP))
        }

    private fun suggestions(context: Context, a: StatsActions105, first: CrossMetric105, second: CrossMetric105): View = StatsUi105.card(context, padH = 10, padV = 10, radius = 14f).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(StatsUi105.iconCircle(context, R.drawable.sr105_ic_bulb, StatsUi105.tone(context, "blue"), 38, 21))
            addView(StatsUi105.text(context, "Outras combinações sugeridas", 16f, StatsUi105.ink(context), bold = true, lines = 2),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        })
        val combos = listOf(
            Triple("Tarifas × Horas de jornadas", CrossMetric105.REVENUE to CrossMetric105.HOURS, null),
            Triple("Tarifas × Quilômetros", CrossMetric105.REVENUE to CrossMetric105.DISTANCE, null),
            Triple("R$/km × Região", null, "Regiões de corridas concluídas ainda não têm relatório conciliado; o cruzamento não está disponível."),
            Triple("Qualidade das ofertas × Horário", CrossMetric105.QUALITY to CrossMetric105.HOUR, null),
            Triple("Corridas × Ofertas observadas", CrossMetric105.RIDES to CrossMetric105.OFFERS, null),
        ).filter { it.second != (first to second) }
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = SrUi023.palette(context)
            background = StatsUi105.rounded(p.surface, 10f, context, p.outline, 1f)
        }
        combos.forEachIndexed { i, (label, pair, reason) ->
            if (i > 0) rows.addView(StatsUi105.divider(context))
            rows.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, 44)
                setPadding(dp(context, 12), dp(context, 6), dp(context, 10), dp(context, 6))
                isClickable = true; isFocusable = true
                contentDescription = if (reason == null) label else "$label: indisponível"
                setOnClickListener { if (pair != null) a.openCross(pair.first, pair.second) else a.info(label, reason ?: "") }
                addView(StatsUi105.icon(context, R.drawable.sr105_ic_bars, StatsUi105.blue(context), 20).apply { alpha = if (pair != null) 1f else .45f })
                addView(StatsUi105.text(context, label, 14.5f, StatsUi105.ink(context), lines = 2).apply { alpha = if (pair != null) 1f else .5f },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 14) })
                if (pair == null) addView(StatsUi105.text(context, "indisponível", 11f, StatsUi105.muted(context)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 6) })
                addView(StatsUi105.chevron(context).apply { alpha = if (pair != null) 1f else .4f })
            })
        }
        addView(rows, lp(context, top = 10))
    }

    // -------------------------------------------------- hours (offers only)

    fun hours(context: Context, s: StatsState105, a: StatsActions105): View = column(context).apply {
        addView(chips(context, s, a, StatsScreen105.HOURS))
        addView(StatsUi105.sectionTitle(context, "Desempenho por faixa horária", 20f), lp(context, top = 12))
        addView(StatsUi105.text(context, "Ofertas observadas no período. Não comprovam corridas realizadas nem ganhos.", 13f, StatsUi105.muted(context), lines = 3), lp(context, top = 2))
        val hours = s.offers?.hours.orEmpty()
        val chart = StatisticsChartView105(context)
        when {
            s.offersLoading -> chart.setLoading(listOf("06h", "09h", "12h", "15h", "18h", "21h"), true, "R$/h médio", "Ofertas")
            hours.isEmpty() -> chart.setMessage(listOf("06h", "09h", "12h", "15h", "18h", "21h"), "Sem ofertas observadas com faixa horária neste período.", true, "R$/h médio", "Ofertas")
            else -> chart.setData(hours.map { StatisticsChartView105.Point(it.label, it.averagePerHour?.takeIf { v -> v.isFinite() && v >= 0 }, it.offerCount.toDouble()) },
                barName = "R$/h médio", lineName = "Ofertas", barAxis = "R$/h médio (ofertas)", lineAxis = "Ofertas",
                barFormat = { StatsFormat105.moneyShort(it) }, lineFormat = { StatsFormat105.number(it, 0) })
        }
        addView(StatsUi105.card(context, padH = 12, padV = 12).apply { orientation = LinearLayout.VERTICAL; addView(chart) }, lp(context, top = 12))
        val top = hours.filter { it.offerCount > 0 }.sortedByDescending { it.offerCount }.take(5)
        addView(tableCard(context, "Faixas com mais ofertas", if (top.isEmpty()) listOf("Sem faixas" to "—") else top.map {
            "${it.label} · ${it.offerCount} ofertas" to "${StatsFormat105.money(it.averagePerHour)}/h"
        }, s.offersLoading), lp(context, top = 12))
    }
}

/** Used by tests/QA: the visible text of a rendered tree. */
fun View.visibleTexts105(): List<String> = when (this) {
    is TextView -> listOf(text.toString())
    is android.view.ViewGroup -> (0 until childCount).flatMap { getChildAt(it).visibleTexts105() }
    else -> emptyList()
}
