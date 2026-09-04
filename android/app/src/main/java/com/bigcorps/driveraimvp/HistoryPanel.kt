package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryPanel(context: Context) : LinearLayout(context) {
    private val repo = SettingsRepository(context)
    private val journeyMetrics = JourneyMetricsStore026.get(context)
    private val status = UiKit.body(context, "Carregando estatísticas...", 12f)
    private val sectionHost = LinearLayout(context).apply { orientation = VERTICAL }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }

    // Os filtros continuam existindo, mas saem da abertura da página e ficam
    // exclusivamente em "Detalhes do período".
    private val periodSpinner = spinner(listOf("Hoje", "7 dias", "30 dias", "90 dias"))
    private val verdictSpinner = spinner(listOf("Todas", "Boas", "Atenção", "Abaixo"))
    private val serviceSpinner = spinner(listOf(
        "Todos serviços", "UberX", "Comfort", "Black", "Electric", "Priority", "Moto",
        "99Pop", "99Plus", "99Moto", "99Táxi", "99electric", "99Entrega", "inDrive", "Maxim", "Desconhecido",
    ))
    private val typeSpinner = spinner(listOf("Todos tipos", "Exclusivo", "Radar"))

    private val collectiveStatus = UiKit.body(context, "", 11f)
    private val collectiveCheck = CheckBox(context)
    private val collectiveCard: View by lazy { importAndPrivacyCard() }
    private val periodFiltersCard: View by lazy { filterCard() }
    private var activeSection = StatisticsSection026.DEFAULT
    private var currentData: HistoryAnalytics? = null
    private var lastFetchAt = 0L
    private var changingCollective = false
    private var collectiveFetched = false

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, UiKit.dp(context, 18))

        addView(
            UiKit.body(
                context,
                "Seu desempenho primeiro. Abra uma área para consultar corridas, comparativos, análises, categorias, período ou jornadas.",
                12f,
            ),
        )
        addView(UiKit.margin(sectionHost, top = 10))
        addView(UiKit.margin(status, top = 8))
        addView(UiKit.margin(content, top = 10))

        renderSectionNavigation()
        syncCollectivePreferenceOnce()
        refresh(true)
    }

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < 15_000) return
        lastFetchAt = now
        val days = listOf(1, 7, 30, 90)[periodSpinner.selectedItemPosition]
        val verdict = listOf(null, "boa", "regular", "ruim")[verdictSpinner.selectedItemPosition]
        val service = listOf(null, "uberx", "comfort", "black", "electric", "priority", "moto", "99pop", "99plus", "99moto", "99taxi", "99electric", "99entrega", "indrive", "maxim", "unknown")[serviceSpinner.selectedItemPosition]
        val type = listOf(null, "exclusive", "radar")[typeSpinner.selectedItemPosition]
        status.text = "Calculando..."
        val settings = repo.load()
        if (!ConnectivityState.isOnline(context) || settings.deviceToken.isBlank()) {
            val local = LocalAnalytics.build(context, days, verdict, service, type)
            render(local)
            status.text = if (settings.deviceToken.isBlank()) "Dados locais · aparelho sem sessão de nuvem" else "Dados locais · offline"
            return
        }
        BackendClient.fetchHistoryAnalytics(context, days, verdict, service, type) { result ->
            result.onSuccess {
                render(it)
                status.text = "Sincronizado · ${it.summary.offerCount} oferta(s) observada(s)"
                refreshJourneyMetrics(days)
            }.onFailure {
                val local = LocalAnalytics.build(context, days, verdict, service, type)
                render(local)
                status.text = "Nuvem indisponível · mostrando dados locais (${it.message})"
            }
        }
    }

    private fun importAndPrivacyCard(): View {
        val card = UiKit.card(context).apply {
            addView(UiKit.sectionTitle(context, "Inteligência Coletiva").apply { setTextColor(SrUi023.palette(context).purple) })
            addView(UiKit.body(context, "Sua base pessoal permanece privada. Se você optar por contribuir, somente dados agregados e anonimizados entram na base coletiva; screenshots, OCR bruto e endereços textuais não são compartilhados.", 13f))
            collectiveCheck.text = "Contribuir com estatísticas coletivas agregadas"
            collectiveCheck.setTextColor(UiKit.palette(context).ink)
            collectiveCheck.isChecked = repo.load().collectiveStatsOptIn
            collectiveCheck.setOnCheckedChangeListener { _, enabled ->
                if (changingCollective) return@setOnCheckedChangeListener
                val previous = repo.load().collectiveStatsOptIn
                repo.save(repo.load().copy(collectiveStatsOptIn = enabled))
                val current = repo.load()
                if (current.deviceToken.isBlank()) {
                    collectiveStatus.text = "Preferência salva localmente; será enviada quando houver sessão."
                    return@setOnCheckedChangeListener
                }
                collectiveCheck.isEnabled = false
                collectiveStatus.text = "Salvando preferência..."
                RegionalPreferenceSync.set(context, enabled) { result ->
                    collectiveCheck.isEnabled = true
                    result.onSuccess { saved ->
                        changingCollective = true; collectiveCheck.isChecked = saved; changingCollective = false
                        collectiveStatus.text = if (saved) "Ativo. Só células, tempos agregados e métricas estatísticas podem contribuir; sem OCR bruto, screenshot ou endereço textual." else "Desativado. Seus dados continuam na inteligência pessoal."
                        refresh(true)
                    }.onFailure {
                        changingCollective = true; collectiveCheck.isChecked = previous; changingCollective = false
                        repo.save(repo.load().copy(collectiveStatsOptIn = previous))
                        collectiveStatus.text = "Não foi possível salvar na nuvem: ${it.message}"
                    }
                }
            }
            addView(UiKit.margin(collectiveCheck, top = 12))
            addView(UiKit.margin(collectiveStatus, top = 4))
        }
        return CollectiveVisual0242.frame(context, card, borderDp = 1)
    }

    private fun syncCollectivePreferenceOnce() {
        if (collectiveFetched) return
        collectiveFetched = true
        val settings = repo.load()
        if (settings.deviceToken.isBlank() || !ConnectivityState.isOnline(context)) {
            collectiveStatus.text = if (settings.collectiveStatsOptIn) "Contribuição coletiva ativa localmente." else "Contribuição coletiva desativada."
            return
        }
        collectiveStatus.text = "Conferindo preferência coletiva..."
        RegionalPreferenceSync.fetch(context) { result ->
            result.onSuccess { enabled ->
                changingCollective = true; collectiveCheck.isChecked = enabled; changingCollective = false
                collectiveStatus.text = if (enabled) "Contribuição coletiva ativa." else "Contribuição coletiva desativada."
            }.onFailure { collectiveStatus.text = "Preferência local mantida (${it.message})." }
        }
    }

    private fun render(data: HistoryAnalytics) {
        currentData = data
        renderSectionNavigation()
        renderActiveSection(data)
    }

    private fun refreshJourneyMetrics(days: Int) {
        val settings = repo.load()
        if (!ConnectivityState.isOnline(context) || settings.deviceToken.isBlank()) return
        JourneyMetricsClient026.syncPending(context) {
            JourneyMetricsClient026.refreshDays(context, days) { result ->
                if (result.isSuccess && activeSection == StatisticsSection026.Section.JOURNEYS) {
                    currentData?.let(::renderActiveSection)
                }
            }
            JourneyRealizedClient0262.refreshDays(context, days) { result ->
                if (result.isSuccess && activeSection == StatisticsSection026.Section.JOURNEYS) {
                    currentData?.let(::renderActiveSection)
                }
            }
        }
    }

    private fun renderSectionNavigation() {
        sectionHost.removeAllViews()
        StatisticsSection026.items().chunked(2).forEachIndexed { rowIndex, entries ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            entries.forEachIndexed { columnIndex, item ->
                row.addView(
                    sectionTile(item, activeSection == item.section),
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (columnIndex > 0) marginStart = UiKit.dp(context, 7)
                    },
                )
            }
            sectionHost.addView(
                row,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = UiKit.dp(context, 7)
                },
            )
        }
    }

    private fun sectionTile(
        item: StatisticsSection026.Item,
        active: Boolean,
    ): View {
        val p = SrUi023.palette(context)
        val tone = when (item.section) {
            StatisticsSection026.Section.HISTORY -> p.teal
            StatisticsSection026.Section.COMPARISONS -> p.cyan
            StatisticsSection026.Section.ANALYSES -> p.blue
            StatisticsSection026.Section.CATEGORIES -> p.purple
            StatisticsSection026.Section.PERIOD -> p.orange
            StatisticsSection026.Section.JOURNEYS -> p.userGreen
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            minimumHeight = UiKit.dp(context, 72)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                UiKit.dp(context, 11),
                UiKit.dp(context, 10),
                UiKit.dp(context, 11),
                UiKit.dp(context, 10),
            )
            background = UiKit.rounded(
                context,
                if (active) tone else UiKit.palette(context).surface,
                14,
                tone,
                1,
            )
            isClickable = true
            isFocusable = true
            contentDescription = item.title
            setOnClickListener {
                if (activeSection != item.section) {
                    activeSection = item.section
                    renderSectionNavigation()
                    currentData?.let(::renderActiveSection)
                }
            }

            addView(
                UiKit.body(context, item.title, 12f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (active) android.graphics.Color.WHITE else UiKit.palette(context).ink)
                },
            )
            addView(
                UiKit.body(context, item.subtitle, 9.5f).apply {
                    setTextColor(
                        if (active) 0xFFF7FBFF.toInt()
                        else UiKit.palette(context).muted,
                    )
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = UiKit.dp(context, 2)
                },
            )
        }
    }

    private fun renderActiveSection(data: HistoryAnalytics) {
        content.removeAllViews()
        when (activeSection) {
            StatisticsSection026.Section.ANALYSES -> {
                content.addView(sectionHeading("Análises", "Indicadores principais e evolução do seu desempenho."))
                content.addView(UiKit.margin(UiKit.body(context, analyzedPeriod(data), 11f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(UiKit.palette(context).ink)
                }, top = 6))
                content.addView(UiKit.margin(summaryCard(data), top = 8))
                content.addView(UiKit.margin(
                    chartCard("R$/km por dia", data.daily.map { HistoryChartView.Bar(it.label, it.averagePerKm ?: 0.0) }, " /km"),
                    top = 10,
                ))
                content.addView(UiKit.margin(
                    chartCard("R$/hora por horário", data.hours.map { HistoryChartView.Bar(it.label, it.averagePerHour ?: 0.0) }, " /h"),
                    top = 10,
                ))
                content.addView(UiKit.margin(topOffersCard(data), top = 10))
                content.addView(UiKit.margin(collectiveCard, top = 10))
            }

            StatisticsSection026.Section.HISTORY -> {
                content.addView(sectionHeading("Histórico de corridas", "Ofertas recentes, digitalização da Uber e confirmação do que realmente foi realizado."))
                content.addView(UiKit.margin(uberDigitizationCard(), top = 8))
                content.addView(UiKit.margin(rideCorrectionsCard(), top = 10))
                content.addView(UiKit.margin(
                    UiKit.body(context, buildString {
                        append(data.note)
                        append("\nCorridas só entram como realizadas quando você as confirma no Sr. Rotas.")
                        if (data.truncated) {
                            append("\nO período ultrapassou o limite de linhas analisadas; use Detalhes do período para reduzir o filtro.")
                        }
                    }, 11f),
                    top = 8,
                ))
            }

            StatisticsSection026.Section.COMPARISONS -> {
                content.addView(sectionHeading("Comparativos", "Compare o período selecionado com a janela anterior equivalente."))
                content.addView(UiKit.margin(UiKit.body(context, analyzedPeriod(data), 11f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(UiKit.palette(context).ink)
                }, top = 6))
                content.addView(UiKit.margin(comparisonCard(data), top = 8))
                content.addView(UiKit.margin(comparisonEvolutionCard(data), top = 10))
            }

            StatisticsSection026.Section.CATEGORIES -> {
                content.addView(sectionHeading("Categorias", "Veja quais categorias aparecem mais e como elas performam."))
                content.addView(UiKit.margin(serviceCard(data), top = 8))
            }

            StatisticsSection026.Section.PERIOD -> {
                content.addView(sectionHeading("Detalhes do período", "Escolha período, classificação, categoria e tipo de oferta."))
                content.addView(UiKit.margin(periodFiltersCard, top = 8))
                content.addView(UiKit.margin(summaryCard(data), top = 10))
            }

            StatisticsSection026.Section.JOURNEYS -> {
                content.addView(sectionHeading("Jornadas", "Hodômetro, distância rodada e gastos reais de combustível/recarga."))
                content.addView(UiKit.margin(journeysCard(data), top = 8))
                content.addView(UiKit.margin(
                    UiKit.body(
                        context,
                        "Os dados abaixo são informados pelo motorista para a janela da jornada. Eles não alteram automaticamente o custo/km configurado nem o veredito das ofertas.",
                        11f,
                    ),
                    top = 8,
                ))
            }
        }
    }

    private fun sectionHeading(title: String, subtitle: String): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            addView(UiKit.title(context, title, 19f))
            addView(UiKit.body(context, subtitle, 11f))
        }

    private fun filterCard(): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Filtros do período"))
        addView(caption("Período")); addView(periodSpinner)
        addView(caption("Classificação")); addView(verdictSpinner)
        addView(caption("Categoria")); addView(serviceSpinner)
        addView(caption("Tipo de oferta")); addView(typeSpinner)
        addView(
            UiKit.margin(
                UiKit.primaryButton(context, "Atualizar período") { refresh(true) },
                top = 10,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Os filtros só afetam as estatísticas consultadas; nenhum histórico é apagado.",
                    10.5f,
                ),
                top = 6,
            ),
        )
    }



    private fun rideCorrectionsCard(): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Corridas realizadas"))
        addView(UiKit.body(context, "Confirme ou corrija as ofertas recentes. Isso separa oferta recebida de corrida realmente feita."))
        val store = LocalStore.get(context)
        val offers = store.recentOffers(40).filterNot { it.captureMethod.startsWith("historical-import/") }.take(10)
        if (offers.isEmpty()) {
            addView(UiKit.margin(UiKit.body(context, "Nenhuma oferta recente neste aparelho."), top = 8))
            return@apply
        }
        offers.forEach { offer ->
            val outcome = store.rideOutcomeForOffer(offer.localId)
            val currentStatus = outcome?.status ?: RideOperationalStatus.OFFERED
            addView(UiKit.margin(rideCard(offer, currentStatus), top = 8, bottom = 10))
        }
    }

    private fun rideCard(offer: RideOffer, currentStatus: RideOperationalStatus): View = LinearLayout(context).apply {
        orientation = VERTICAL
        background = UiKit.rounded(context, UiKit.palette(context).surface, 14, UiKit.palette(context).line, 1)
        setPadding(UiKit.dp(context, 12), UiKit.dp(context, 11), UiKit.dp(context, 12), UiKit.dp(context, 11))

        val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(UiKit.title(context, money(offer.fare), 20f).apply { setTextColor(SrUi023.palette(context).blue) })
        top.addView(UiKit.margin(UiKit.pill(context, serviceLabel(offer.serviceType), "primary"), start = 7))
        top.addView(View(context), LayoutParams(0, 1, 1f))
        top.addView(UiKit.pill(context, rideStatusLabel(currentStatus), rideStatusTone(currentStatus)))
        addView(top)

        val secondary = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        secondary.addView(UiKit.body(context, "${platformLabel(offer.platform)} · ${dateTime(offer.observedAt)}", 9.5f), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (ReportSelection0211.isSelected(context, offer)) secondary.addView(UiKit.pill(context, "✓ RELATÓRIO", "primary"))
        addView(UiKit.margin(secondary, top = 3))

        addView(UiKit.margin(LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(UiKit.body(context, "Quilometragem", 10f).apply { setTypeface(typeface, Typeface.BOLD) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(UiKit.title(context, offer.totalKm?.let { "${fmt(it)} km" } ?: "— km", 17f).apply { setTextColor(SrUi023.palette(context).teal) })
        }, top = 8))

        val pickup = OfferContextQuality0242.confirmedDisplayLabel(offer.context, pickup = true)
        val destination = OfferContextQuality0242.confirmedDisplayLabel(offer.context, pickup = false)
        addView(UiKit.margin(addressBlock("Embarque", pickup), top = 8))
        addView(UiKit.margin(addressBlock("Destino", destination), top = 6))

        addView(UiKit.margin(UiKit.body(context, "${moneyMetric(offer.perKm)}/km", 10.5f), top = 6))

        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        if (currentStatus != RideOperationalStatus.COMPLETED) {
            actions.addView(compactAction("Fiz essa corrida") {
                JourneyCoordinator.correctRide(context, offer.localId, RideOperationalStatus.COMPLETED); refresh(true)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        } else {
            actions.addView(compactAction("Desmarcar realizada") {
                JourneyCoordinator.correctRide(context, offer.localId, RideOperationalStatus.NOT_COMPLETED); refresh(true)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        if (currentStatus != RideOperationalStatus.NOT_COMPLETED && currentStatus != RideOperationalStatus.CANCELLED) {
            actions.addView(compactAction("Não realizei") {
                JourneyCoordinator.correctRide(context, offer.localId, RideOperationalStatus.NOT_COMPLETED); refresh(true)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 6) })
        }
        addView(UiKit.margin(actions, top = 8))
    }

    private fun addressBlock(label: String, value: String?): View = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(UiKit.body(context, label, 11f).apply { setTypeface(typeface, Typeface.BOLD); setTextColor(UiKit.palette(context).ink) })
        addView(UiKit.body(context, value?.take(110) ?: "Não identificado", 11f))
    }

    private fun compactAction(label: String, action: () -> Unit): TextView =
        UiKit.secondaryButton(context, label, action).apply {
            textSize = 10.5f
            setSingleLine(true)
            minHeight = UiKit.dp(context, 38)
            setPadding(UiKit.dp(context, 7), UiKit.dp(context, 8), UiKit.dp(context, 7), UiKit.dp(context, 8))
        }

    private fun rideStatusLabel(status: RideOperationalStatus) = when (status) {
        RideOperationalStatus.OFFERED -> "OFERTA"
        RideOperationalStatus.DOING_RIDE -> "EM CORRIDA"
        RideOperationalStatus.COMPLETED -> "REALIZADA"
        RideOperationalStatus.NOT_COMPLETED -> "NÃO REALIZADA"
        RideOperationalStatus.CANCELLED -> "CANCELADA"
    }
    private fun rideStatusTone(status: RideOperationalStatus) = when (status) {
        RideOperationalStatus.COMPLETED -> "good"
        RideOperationalStatus.DOING_RIDE -> "primary"
        RideOperationalStatus.CANCELLED, RideOperationalStatus.NOT_COMPLETED -> "bad"
        RideOperationalStatus.OFFERED -> "warn"
    }

    private fun summaryCard(data: HistoryAnalytics): View {
        val s = data.summary
        return UiKit.card(context).apply {
            val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(UiKit.sectionTitle(context, "Visão do período"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            top.addView(UiKit.pill(context, if (data.source == "local") "LOCAL" else "NUVEM", if (data.source == "local") "warn" else "good"))
            addView(top)
            addView(UiKit.title(context, "${s.offerCount} ofertas observadas", 22f))
            addView(UiKit.body(context, "Boas ${s.goodCount} · Atenção ${s.regularCount} · Abaixo ${s.badCount}", 13f))
            addView(metricGrid(listOf(
                "R$/km médio" to moneyMetric(s.averagePerKm), "R$/h médio" to moneyMetric(s.averagePerHour),
                "R$/min médio" to moneyMetric(s.averagePerMinute), "Oferta média" to money(s.averageFare),
                "Valor observado*" to money(s.totalOfferedFare), "Lucro est. observado*" to money(s.estimatedTotalProfit),
            )))
            addView(UiKit.margin(UiKit.body(context, "*Somatório das ofertas exibidas pelos aplicativos de motorista. Não representa faturamento nem corridas realizadas.", 11f), top = 8))
        }
    }

    private fun comparisonCard(data: HistoryAnalytics): View =
        UiKit.card(context).apply {
            addView(UiKit.sectionTitle(context, "Comparação com período anterior"))
            val c = data.comparison
            if (c == null) {
                addView(UiKit.body(context, "Sem período anterior suficiente para comparação."))
                return@apply
            }
            addView(
                metricGrid(
                    listOf(
                        "Ofertas" to delta(c.offerCountPct),
                        "R$/km" to delta(c.averagePerKmPct),
                        "R$/hora" to delta(c.averagePerHourPct),
                        "R$/min" to delta(c.averagePerMinutePct),
                        "Lucro médio*" to delta(c.averageProfitPct),
                    ),
                ),
            )
            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "Variação das ofertas observadas no mesmo tamanho de janela anterior.",
                        11f,
                    ),
                    top = 6,
                ),
            )
        }

    private fun chartCard(title: String, bars: List<HistoryChartView.Bar>, suffix: String): View = collapsibleAnalyticsCard(title) { body ->
        body.addView(HistoryChartView(context).apply { setBars(bars.filter { it.value > 0.0 }, suffix) })
    }

    private fun collapsibleAnalyticsCard(title: String, builder: (LinearLayout) -> Unit): View = UiKit.card(context).apply {
        val body = LinearLayout(context).apply { orientation = VERTICAL; visibility = View.GONE }
        val arrow = UiKit.body(context, "⌄", 19f).apply { setTypeface(typeface, Typeface.BOLD) }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(UiKit.sectionTitle(context, title), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); addView(arrow)
            setPadding(0, UiKit.dp(context, 2), 0, UiKit.dp(context, 2)); isClickable = true; isFocusable = true
            contentDescription = "$title · expandir ou recolher"
            setOnClickListener { val open = body.visibility != View.VISIBLE; body.visibility = if (open) View.VISIBLE else View.GONE; arrow.text = if (open) "⌃" else "⌄" }
        }
        addView(header); builder(body); addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 7) })
    }

    private fun serviceCard(data: HistoryAnalytics): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Categorias"))
        if (data.services.isEmpty()) addView(UiKit.body(context, "Nenhuma categoria no período."))
        data.services.take(12).forEachIndexed { index, row ->
            if (index > 0) {
                addView(View(context).apply { setBackgroundColor(UiKit.palette(context).line) },
                    LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 1)).apply {
                        topMargin = UiKit.dp(context, 3)
                        bottomMargin = UiKit.dp(context, 3)
                    })
            }
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 8))
                addView(UiKit.body(context, serviceLabel(row.serviceType), 14f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(UiKit.palette(context).ink)
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(UiKit.body(
                    context,
                    "${row.offerCount} · ${moneyMetric(row.averagePerKm)}/km · ${moneyMetric(row.averagePerHour)}/h",
                    12f,
                ).apply { gravity = Gravity.END })
            })
        }
    }

    private fun comparisonEvolutionCard(data: HistoryAnalytics): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Evolução no período"))
        addView(UiKit.body(context, "Linha diária de R$/km para visualizar crescimento ou redução ao longo do intervalo.", 11f))
        val points = data.daily.mapNotNull { row ->
            row.averagePerKm?.takeIf { it.isFinite() && it > 0.0 }?.let {
                HistoryLineChartView0262.Point(row.label, it)
            }
        }
        addView(HistoryLineChartView0262(context).apply { setPoints(points, " /km") },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 7) })
    }

    private fun topOffersCard(data: HistoryAnalytics): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Destaques do período"))
        if (data.topOffers.isEmpty()) addView(UiKit.body(context, "Nenhuma oferta para destacar."))
        data.topOffers.take(6).forEach { o ->
            val tone = when (o.verdict) { "boa" -> "good"; "ruim" -> "bad"; else -> "warn" }
            addView(UiKit.margin(LinearLayout(context).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(context, UiKit.palette(context).surfaceAlt, 13, UiKit.palette(context).line, 1)
                setPadding(UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9), UiKit.dp(context, 8))
                addView(UiKit.pill(context, serviceLabel(o.serviceType), tone))
                addView(LinearLayout(context).apply {
                    orientation = VERTICAL; setPadding(UiKit.dp(context, 9), 0, 0, 0)
                    addView(UiKit.title(context, money(o.fare), 16f)); addView(UiKit.body(context, "${moneyMetric(o.perMinute)}/min · ${moneyMetric(o.perKm)}/km · ${moneyMetric(o.perHour)}/h", 11f))
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }, bottom = 10))
        }
    }

    private fun journeysCard(data: HistoryAnalytics): View = UiKit.card(context).apply {
        addView(UiKit.sectionTitle(context, "Jornadas"))
        if (data.journeys.isEmpty()) addView(UiKit.body(context, "Nenhuma jornada no período."))
        data.journeys.take(12).forEach { j ->
            val snapshot = journeyMetrics.snapshot(j.id)
            val metric = snapshot.metric
            val realized = JourneyRealizedClient0262.snapshot(context, j.id)
            val localUberSession = UberDigitizationStore026.get(context).sessionForJourney(j.id)
            val sessionEarnings = realized?.sessionEarnings ?: localUberSession?.earnings
            val sessionCompleted = realized?.sessionCompletedTrips ?: localUberSession?.completedTrips
            val sessionOffered = realized?.sessionOfferedTrips ?: localUberSession?.offeredTrips
            val sessionConfidence = realized?.sessionConfidence ?: localUberSession?.confidence
            val displayCompleted = sessionCompleted ?: realized?.completedTrips
            val displayRevenue = sessionEarnings ?: realized?.realizedRevenue
            val fuel = snapshot.energyEntries.filter { it.kind == JourneyMetricsRules026.KIND_FUEL }
            val electric = snapshot.energyEntries.filter { it.kind == JourneyMetricsRules026.KIND_ELECTRIC }
            val informedSpend = snapshot.energyEntries.mapNotNull { it.amountPaid }.sum().takeIf { it > 0.0 }

            addView(UiKit.margin(LinearLayout(context).apply {
                orientation = VERTICAL
                background = UiKit.rounded(context, UiKit.palette(context).surfaceAlt, 13, UiKit.palette(context).line, 1)
                setPadding(UiKit.dp(context, 10), UiKit.dp(context, 9), UiKit.dp(context, 10), UiKit.dp(context, 9))
                addView(UiKit.body(context, "${dateTime(j.startedAt)}", 14f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(UiKit.palette(context).ink)
                })
                val duration = j.durationMinutes?.let { formatDuration(it) } ?: "em andamento"
                addView(UiKit.body(context, "Duração $duration · ${j.offerCount} ofertas observadas", 11.5f))
                addView(UiKit.margin(metricGrid(listOf(
                    "Viagens realizadas" to (displayCompleted?.toString() ?: "—"),
                    "Faturamento" to (displayRevenue?.let(::money) ?: "—"),
                    "Distância rodada" to (metric?.distanceKm?.let(::km) ?: "—"),
                    "Gastos informados" to (informedSpend?.let(::money) ?: "—"),
                )), top = 6))
                if (sessionEarnings != null || sessionCompleted != null || sessionOffered != null) {
                    addView(UiKit.margin(UiKit.body(context, buildString {
                        append("Resumo Uber digitalizado")
                        sessionOffered?.let { append(" · $it viagens oferecidas") }
                        sessionConfidence?.let { append(" · ${(it * 100).toInt()}% confiança") }
                    }, 10.5f).apply { setTextColor(UiKit.palette(context).primary) }, top = 4))
                } else if (realized != null && !realized.revenueComplete && realized.completedTrips > 0) {
                    addView(UiKit.body(context, "Faturamento parcial: ${realized.fareMatchedTrips} de ${realized.completedTrips} corridas têm tarifa associada.", 10f))
                }
                addView(UiKit.body(context, "Médias das ofertas: ${moneyMetric(j.averagePerKm)}/km · ${moneyMetric(j.averagePerHour)}/h", 11f))

                addView(
                    UiKit.margin(
                        UiKit.body(context, "Quilometragem", 11f).apply { setTypeface(typeface, Typeface.BOLD) },
                        top = 8,
                    ),
                )
                if (metric == null) {
                    addView(UiKit.body(context, "Km inicial/final ainda não informados.", 11f))
                } else {
                    addView(UiKit.body(context, buildString {
                        append("Inicial ${km(metric.odometerStartKm)} · Final ${km(metric.odometerEndKm)}")
                        metric.distanceKm?.let { append("\nDistância da jornada ${km(it)}") }
                    }, 11.5f))
                }

                if (fuel.isNotEmpty() || electric.isNotEmpty()) {
                    val liters = fuel.mapNotNull { it.quantity }.sum().takeIf { it > 0.0 }
                    val kwh = electric.mapNotNull { it.quantity }.sum().takeIf { it > 0.0 }
                    addView(UiKit.margin(UiKit.body(context, buildString {
                        append("Consumo informado: ")
                        val parts = mutableListOf<String>()
                        liters?.let { parts += "${qty(it)} L" }
                        kwh?.let { parts += "${qty(it)} kWh" }
                        append(parts.ifEmpty { listOf("quantidade não informada") }.joinToString(" · "))
                    }, 11f), top = 6))
                    addView(
                        UiKit.margin(
                            UiKit.body(context, "Abastecimento / recarga", 11f).apply { setTypeface(typeface, Typeface.BOLD) },
                            top = 7,
                        ),
                    )
                    if (fuel.isNotEmpty()) {
                        val amount = fuel.mapNotNull { it.amountPaid }.sum().takeIf { it > 0.0 }
                        val liters = fuel.mapNotNull { it.quantity }.sum().takeIf { it > 0.0 }
                        val types = fuel.mapNotNull { it.fuelType }.distinct()
                        addView(UiKit.body(context, buildString {
                            append("Combustível")
                            if (types.size == 1) append(" (${types.first()})")
                            amount?.let { append(" · ${money(it)}") }
                            liters?.let { append(" · ${qty(it)} L") }
                            if (fuel.size > 1) append(" · ${fuel.size} lançamentos")
                        }, 11.5f))
                    }
                    if (electric.isNotEmpty()) {
                        val amount = electric.mapNotNull { it.amountPaid }.sum().takeIf { it > 0.0 }
                        val kwh = electric.mapNotNull { it.quantity }.sum().takeIf { it > 0.0 }
                        addView(UiKit.body(context, buildString {
                            append("Recarga")
                            amount?.let { append(" · ${money(it)}") }
                            kwh?.let { append(" · ${qty(it)} kWh") }
                            if (electric.size > 1) append(" · ${electric.size} lançamentos")
                        }, 11.5f))
                    }
                } else {
                    addView(UiKit.margin(UiKit.body(context, "Nenhum abastecimento ou recarga registrado.", 11f), top = 6))
                }

                addView(
                    UiKit.margin(
                        JourneyFlow026.editorButton(context, j.id) {
                            refresh(true)
                        },
                        top = 9,
                    ),
                )
            }, bottom = 10))
        }
    }

    private fun uberDigitizationCard(): View = UiKit.card(context).apply {
        val summary = UberDigitizationStore026.get(context).summary()
        addView(UiKit.sectionTitle(context, "Digitalizar Uber"))
        addView(
            UiKit.body(
                context,
                "Leia uma tela da Uber e confirme antes de salvar. Resumos e corridas concluídas ficam separados das ofertas observadas.",
                11f,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Resumos salvos ${summary.first} · Corridas importadas ${summary.second}" +
                        if (summary.third > 0.0) " · Ganhos em resumos ${money(summary.third)}" else "",
                    11.5f,
                ),
                top = 7,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.primaryButton(context, "Digitalizar jornada") {
                    UberDigitizationActivity026.open(context, UberDigitizationParser026.MODE_SESSION)
                },
                top = 9,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.secondaryButton(context, "Digitalizar histórico") {
                    UberDigitizationActivity026.open(context, UberDigitizationParser026.MODE_HISTORY)
                },
                top = 7,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Uma corrida importada só é associada a uma oferta antiga quando há uma única correspondência segura de valor, categoria e horário.",
                    10f,
                ),
                top = 7,
            ),
        )
    }

    private fun metricGrid(items: List<Pair<String, String>>): View {
        val holder = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, UiKit.dp(context, 8), 0, 0) }
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            pair.forEach { item -> row.addView(LinearLayout(context).apply {
                orientation = VERTICAL; setPadding(0, UiKit.dp(context, 6), UiKit.dp(context, 8), UiKit.dp(context, 6))
                addView(UiKit.body(context, item.first, 11f)); addView(UiKit.title(context, item.second, 17f))
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
            if (pair.size == 1) row.addView(View(context), LayoutParams(0, 1, 1f)); holder.addView(row)
        }
        return holder
    }

    private fun caption(text: String) = UiKit.body(context, text, 10f).apply { setPadding(0, UiKit.dp(context, 5), 0, UiKit.dp(context, 2)) }
    private fun spinner(items: List<String>) = Spinner(context).apply {
        val palette = UiKit.palette(context)
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).also { view ->
                    (view as? TextView)?.apply {
                        setTextColor(palette.ink)
                        textSize = 13f
                        setPadding(UiKit.dp(context, 10), UiKit.dp(context, 9), UiKit.dp(context, 10), UiKit.dp(context, 9))
                    }
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getDropDownView(position, convertView, parent).also { view ->
                    (view as? TextView)?.apply {
                        setTextColor(palette.ink)
                        setBackgroundColor(palette.surface)
                        textSize = 13f
                        setPadding(UiKit.dp(context, 12), UiKit.dp(context, 11), UiKit.dp(context, 12), UiKit.dp(context, 11))
                    }
                }
        }
        background = UiKit.rounded(context, palette.surfaceAlt, 12, palette.line, if (Appearance021.isDark(context)) 2 else 1)
    }
    private fun money(v: Double?) = if (v == null) "—" else "R$ ${fmt(v)}"
    private fun moneyMetric(v: Double?) = if (v == null) "—" else "R$ ${fmt(v)}"
    private fun delta(v: Double?) = if (v == null) "—" else "${if (v > 0) "+" else ""}${fmt(v)}%"
    private fun fmt(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun km(v: Double?) = if (v == null) "—" else String.format(java.util.Locale("pt", "BR"), "%,.1f km", v)
    private fun qty(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun platformLabel(v: String) = when (v.lowercase()) { "uber" -> "Uber"; "99" -> "99"; "indrive" -> "inDrive"; "maxim" -> "Maxim"; "multi" -> "Multiplataforma"; else -> "Outro app" }
    private fun serviceLabel(v: String) = when (v.lowercase()) {
        "uberx" -> "UberX"; "comfort" -> "Comfort"; "black" -> "Black"; "electric" -> "Electric"; "priority" -> "Priority"; "moto" -> "Moto";
        "99" -> "99"; "99pop" -> "99Pop"; "99plus" -> "99Plus"; "99moto" -> "99Moto"; "99taxi" -> "99Táxi"; "99electric" -> "99electric"; "99entrega" -> "99Entrega";
        "indrive" -> "inDrive"; "maxim" -> "Maxim"; else -> "Outro"
    }
    private fun dateTime(value: String): String = runCatching { DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value)) }.getOrDefault(value.take(16))
    private fun analyzedPeriod(data: HistoryAnalytics): String =
        "Período analisado: ${dateOnly(data.from)} a ${dateOnly(data.to)}"

    private fun dateOnly(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value))
    }.getOrDefault(value.take(10))

    private fun formatDuration(minutes: Int) = if (minutes < 60) "${minutes}min" else "${minutes / 60}h ${minutes % 60}min"
}
