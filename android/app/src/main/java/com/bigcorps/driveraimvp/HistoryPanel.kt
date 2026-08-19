package com.srrotas.app

import android.content.Context
import android.content.Intent
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
import kotlin.math.abs

class HistoryPanel(context: Context) : LinearLayout(context) {
    private val repo = SettingsRepository(context)
    private val status =
        UiKit.body(context, "Carregando histórico...", 13f)
    private val content =
        LinearLayout(context).apply { orientation = VERTICAL }
    private val periodSpinner =
        spinner(listOf("Hoje", "7 dias", "30 dias", "90 dias"))
    private val verdictSpinner =
        spinner(listOf("Todas", "Boas", "Atenção", "Abaixo"))
    private val serviceSpinner =
        spinner(
            listOf(
                "Todos serviços",
                "UberX",
                "Comfort",
                "Black",
                "Electric",
                "Priority",
                "Moto",
                "Desconhecido",
            ),
        )
    private val typeSpinner =
        spinner(listOf("Todos tipos", "Exclusivo", "Radar"))

    private val importSummary = UiKit.body(context, "", 11f)
    private val collectiveStatus = UiKit.body(context, "", 11f)
    private val collectiveCheck = CheckBox(context)

    private var lastFetchAt = 0L
    private var changingCollective = false
    private var collectiveFetched = false

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, UiKit.dp(context, 18))

        addView(
            UiKit.card(context).apply {
                addView(UiKit.sectionTitle(context, "Filtros"))
                addView(caption("Período"))
                addView(periodSpinner)
                addView(caption("Classificação"))
                addView(verdictSpinner)
                addView(caption("Categoria"))
                addView(serviceSpinner)
                addView(caption("Tipo de oferta"))
                addView(typeSpinner)
                addView(
                    UiKit.margin(
                        UiKit.primaryButton(
                            context,
                            "Atualizar analytics",
                        ) { refresh(true) },
                        top = 10,
                    ),
                )
                addView(UiKit.margin(status, top = 8))
            },
        )

        addView(
            UiKit.margin(
                importAndPrivacyCard(),
                top = 12,
            ),
        )
        addView(UiKit.margin(content, top = 12))

        syncCollectivePreferenceOnce()
        refreshImportSummary()
        refresh(true)
    }

    fun refresh(force: Boolean = false) {
        refreshImportSummary()

        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < 15_000) return
        lastFetchAt = now

        val days =
            listOf(1, 7, 30, 90)[periodSpinner.selectedItemPosition]
        val verdict =
            listOf(null, "boa", "regular", "ruim")[
                verdictSpinner.selectedItemPosition
            ]
        val service =
            listOf(
                null,
                "uberx",
                "comfort",
                "black",
                "electric",
                "priority",
                "moto",
                "unknown",
            )[serviceSpinner.selectedItemPosition]
        val type =
            listOf(null, "exclusive", "radar")[
                typeSpinner.selectedItemPosition
            ]

        status.text = "Calculando..."
        val s = repo.load()

        if (
            !ConnectivityState.isOnline(context) ||
            s.deviceToken.isBlank()
        ) {
            val local =
                LocalAnalytics.build(
                    context,
                    days,
                    verdict,
                    service,
                    type,
                )
            render(local)
            status.text =
                if (s.deviceToken.isBlank()) {
                    "Dados locais · aparelho sem sessão de nuvem"
                } else {
                    "Dados locais · offline"
                }
            return
        }

        BackendClient.fetchHistoryAnalytics(
            context,
            days,
            verdict,
            service,
            type,
        ) { result ->
            result
                .onSuccess {
                    render(it)
                    status.text =
                        "Sincronizado · ${it.summary.offerCount} oferta(s) observada(s)"
                }
                .onFailure {
                    val local =
                        LocalAnalytics.build(
                            context,
                            days,
                            verdict,
                            service,
                            type,
                        )
                    render(local)
                    status.text =
                        "Nuvem indisponível · mostrando dados locais (${it.message})"
                }
        }
    }

    private fun importAndPrivacyCard(): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Histórico e inteligência",
                ),
            )

            addView(
                UiKit.body(
                    context,
                    "Importe screenshots antigos para aumentar o histórico positivo de ofertas e contexto. " +
                        "As imagens ficam no aparelho e não entram no denominador de probabilidade.",
                    13f,
                ),
            )

            addView(
                UiKit.margin(
                    UiKit.secondaryButton(
                        context,
                        "Importar screenshots antigos",
                    ) {
                        context.startActivity(
                            Intent(
                                context,
                                HistoricalImportActivity::class.java,
                            ),
                        )
                    },
                    top = 9,
                ),
            )
            addView(UiKit.margin(importSummary, top = 7))

            collectiveCheck.text =
                "Contribuir com estatísticas coletivas agregadas"
            collectiveCheck.setTextColor(UiKit.palette(context).ink)
            collectiveCheck.isChecked =
                repo.load().collectiveStatsOptIn

            collectiveCheck.setOnCheckedChangeListener { _, enabled ->
                if (changingCollective) {
                    return@setOnCheckedChangeListener
                }

                val previous = repo.load().collectiveStatsOptIn
                repo.save(
                    repo.load().copy(
                        collectiveStatsOptIn = enabled,
                    ),
                )

                val current = repo.load()
                if (current.deviceToken.isBlank()) {
                    collectiveStatus.text =
                        "Preferência salva localmente; será enviada quando houver sessão."
                    return@setOnCheckedChangeListener
                }

                collectiveCheck.isEnabled = false
                collectiveStatus.text = "Salvando preferência..."

                RegionalPreferenceSync.set(
                    context,
                    enabled,
                ) { result ->
                    collectiveCheck.isEnabled = true
                    result
                        .onSuccess { saved ->
                            changingCollective = true
                            collectiveCheck.isChecked = saved
                            changingCollective = false
                            collectiveStatus.text =
                                if (saved) {
                                    "Ativo. Só células, tempos agregados e métricas estatísticas podem contribuir; sem OCR bruto, screenshot ou endereço textual."
                                } else {
                                    "Desativado. Seus dados continuam na inteligência pessoal."
                                }
                            refresh(true)
                        }
                        .onFailure {
                            changingCollective = true
                            collectiveCheck.isChecked = previous
                            changingCollective = false
                            repo.save(
                                repo.load().copy(
                                    collectiveStatsOptIn = previous,
                                ),
                            )
                            collectiveStatus.text =
                                "Não foi possível salvar na nuvem: ${it.message}"
                        }
                }
            }

            addView(UiKit.margin(collectiveCheck, top = 12))
            addView(UiKit.margin(collectiveStatus, top = 4))
        }

    private fun syncCollectivePreferenceOnce() {
        if (collectiveFetched) return
        collectiveFetched = true

        val settings = repo.load()
        if (
            settings.deviceToken.isBlank() ||
            !ConnectivityState.isOnline(context)
        ) {
            collectiveStatus.text =
                if (settings.collectiveStatsOptIn) {
                    "Contribuição coletiva ativa localmente."
                } else {
                    "Contribuição coletiva desativada."
                }
            return
        }

        collectiveStatus.text =
            "Conferindo preferência coletiva..."

        RegionalPreferenceSync.fetch(context) { result ->
            result
                .onSuccess { enabled ->
                    changingCollective = true
                    collectiveCheck.isChecked = enabled
                    changingCollective = false
                    collectiveStatus.text =
                        if (enabled) {
                            "Contribuição coletiva ativa."
                        } else {
                            "Contribuição coletiva desativada."
                        }
                }
                .onFailure {
                    collectiveStatus.text =
                        "Preferência local mantida (${it.message})."
                }
        }
    }

    private fun refreshImportSummary() {
        val s =
            HistoricalImportStore.get(context).summary()
        importSummary.text =
            "Importadas ${s.importedOffers} oferta(s) · " +
                "duplicadas evitadas ${s.duplicateOffers} · " +
                "arquivos processados ${s.processedFiles}"
    }

    private fun render(data: HistoryAnalytics) {
        content.removeAllViews()
        content.addView(rideCorrectionsCard())
        content.addView(
            UiKit.margin(summaryCard(data), top = 10),
        )
        content.addView(
            UiKit.margin(
                regionalCard(data.regionalIntelligence),
                top = 10,
            ),
        )
        content.addView(
            UiKit.margin(comparisonCard(data), top = 10),
        )
        content.addView(
            UiKit.margin(
                chartCard(
                    "R$/km por dia",
                    data.daily.map {
                        HistoryChartView.Bar(
                            it.label,
                            it.averagePerKm ?: 0.0,
                        )
                    },
                    " /km",
                ),
                top = 10,
            ),
        )
        content.addView(
            UiKit.margin(
                chartCard(
                    "R$/hora por horário",
                    data.hours.map {
                        HistoryChartView.Bar(
                            it.label,
                            it.averagePerHour ?: 0.0,
                        )
                    },
                    " /h",
                ),
                top = 10,
            ),
        )
        content.addView(
            UiKit.margin(serviceCard(data), top = 10),
        )
        content.addView(
            UiKit.margin(topOffersCard(data), top = 10),
        )
        content.addView(
            UiKit.margin(journeysCard(data), top = 10),
        )
        content.addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    buildString {
                        append(data.note)
                        append(
                            "\nCorridas realizadas só entram como realizadas quando você as confirma no Sr. Rotas.",
                        )
                        if (data.truncated) {
                            append(
                                "\nO período ultrapassou o limite de linhas analisadas; reduza o filtro para precisão total.",
                            )
                        }
                    },
                    12f,
                ),
                top = 10,
            ),
        )
    }

    private fun regionalCard(
        regional: RegionalIntelligenceAnalytics?,
    ): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Inteligência regional",
                ),
            )

            if (regional == null) {
                addView(
                    UiKit.body(
                        context,
                        "A inteligência regional completa precisa das exposições sincronizadas. Offline, o histórico financeiro continua disponível, mas nenhuma probabilidade é inventada.",
                        13f,
                    ),
                )
                return@apply
            }

            val q = regional.dataQuality
            addView(
                UiKit.body(
                    context,
                    "Base pessoal · ${q.exposureCount} exposições estatísticas · " +
                        "${q.cellsWithExposure} região(ões) · " +
                        "${q.offersWithDestinationCell} oferta(s) com destino geográfico.",
                    13f,
                ),
            )

            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "Rajadas coalescidas: ${q.burstIntervalsCollapsed} de ${q.rawExposureCount} intervalo(s) brutos. " +
                            "Histórico importado: ${q.historicalPositiveOffers} evento(s) positivo(s); " +
                            "${q.importedUnknownTime} com horário incerto. " +
                            "Regiões prontas para probabilidade: ${q.probabilityReadyCells}.",
                        11f,
                    ),
                    top = 5,
                ),
            )

            if (regional.topRegions.isEmpty()) {
                addView(
                    UiKit.margin(
                        UiKit.body(
                            context,
                            "Ainda não há exposição regional suficiente. Continue usando a jornada com localização aproximada ativada.",
                            13f,
                        ),
                        top = 9,
                    ),
                )
            } else {
                regional.topRegions
                    .take(6)
                    .forEach { row ->
                        addView(
                            UiKit.margin(
                                LinearLayout(context).apply {
                                    orientation = VERTICAL
                                    setPadding(
                                        0,
                                        UiKit.dp(context, 7),
                                        0,
                                        UiKit.dp(context, 7),
                                    )

                                    val line =
                                        LinearLayout(context).apply {
                                            orientation =
                                                HORIZONTAL
                                            gravity =
                                                Gravity.CENTER_VERTICAL
                                        }
                                    line.addView(
                                        UiKit.title(
                                            context,
                                            row.cell,
                                            15f,
                                        ),
                                        LayoutParams(
                                            0,
                                            LayoutParams.WRAP_CONTENT,
                                            1f,
                                        ),
                                    )
                                    line.addView(
                                        UiKit.pill(
                                            context,
                                            probabilityLabel(
                                                row.p10,
                                            ),
                                            probabilityTone(
                                                row.p10,
                                            ),
                                        ),
                                    )
                                    addView(line)

                                    addView(
                                        UiKit.body(
                                            context,
                                            buildString {
                                                append(
                                                    "${row.exposureCount} exposições · ${fmt(row.availableMinutes)} min disponíveis",
                                                )
                                                append(
                                                    " · ${row.offerHits} oferta(s)",
                                                )
                                                row
                                                    .medianTimeToOfferMinutes
                                                    ?.let {
                                                        append(
                                                            "\nMediana até oferta ${fmt(it)} min",
                                                        )
                                                    }
                                                if (
                                                    row.destinationOfferCount >
                                                    0
                                                ) {
                                                    append(
                                                        " · ${row.destinationOfferCount} chegada(s) históricas",
                                                    )
                                                }
                                                row.serviceDistribution.maxByOrNull { it.value }?.let { service ->
                                                    append(" · ${serviceLabel(service.key)} ${service.value}x")
                                                }
                                                row.averagePerKm?.let {
                                                    append(
                                                        "\nMédia vinculada R$ ${fmt(it)}/km",
                                                    )
                                                }
                                                row.averagePerMinute
                                                    ?.let {
                                                        append(
                                                            " · R$ ${fmt(it)}/min",
                                                        )
                                                    }
                                            },
                                            11f,
                                        ),
                                    )
                                },
                                top = 4,
                            ),
                        )
                    }
            }

            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "P(10 min) só aparece com pelo menos ${regional.minimumProbabilitySamples} intervalos elegíveis. " +
                            "Intervalos curtos encerrados por pausa/mudança de célula são censurados, não tratados como falhas.",
                        11f,
                    ),
                    top = 8,
                ),
            )

            if (regional.collectiveAvailableRegions > 0) {
                addView(
                    UiKit.margin(
                        UiKit.body(
                            context,
                            "Base coletiva disponível em ${regional.collectiveAvailableRegions} recorte(s) com pelo menos 3 contribuidores.",
                            11f,
                        ),
                        top = 4,
                    ),
                )
            }
        }

    private fun probabilityLabel(
        h: ProbabilityHorizonAnalytics,
    ): String =
        h.probabilityPct?.let {
            "P10 ${fmt(it)}%"
        } ?: "DADOS INSUF."

    private fun probabilityTone(
        h: ProbabilityHorizonAnalytics,
    ): String =
        when (h.reliability) {
            "high" -> "good"
            "medium" -> "primary"
            "low" -> "warn"
            else -> "neutral"
        }

    private fun rideCorrectionsCard(): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Corridas realizadas",
                ),
            )
            addView(
                UiKit.body(
                    context,
                    "Confirme ou corrija as ofertas recentes. Isso separa oferta recebida de corrida realmente feita.",
                ),
            )

            val store = LocalStore.get(context)
            val offers = store.recentOffers(40)
                .filterNot { it.captureMethod.startsWith("historical-import/") }
                .take(10)

            if (offers.isEmpty()) {
                addView(
                    UiKit.margin(
                        UiKit.body(
                            context,
                            "Nenhuma oferta recente neste aparelho.",
                        ),
                        top = 8,
                    ),
                )
                return@apply
            }

            offers.forEach { offer ->
                val outcome =
                    store.rideOutcomeForOffer(offer.localId)
                val currentStatus =
                    outcome?.status ?: RideOperationalStatus.OFFERED

                addView(
                    UiKit.margin(
                        LinearLayout(context).apply {
                            orientation = VERTICAL
                            setPadding(
                                0,
                                UiKit.dp(context, 7),
                                0,
                                UiKit.dp(context, 7),
                            )

                            val top =
                                LinearLayout(context).apply {
                                    orientation = HORIZONTAL
                                    gravity =
                                        Gravity.CENTER_VERTICAL
                                }
                            top.addView(
                                UiKit.title(
                                    context,
                                    "${dateTime(offer.observedAt)} · ${money(offer.fare)}",
                                    15f,
                                ),
                                LayoutParams(
                                    0,
                                    LayoutParams.WRAP_CONTENT,
                                    1f,
                                ),
                            )
                            top.addView(
                                UiKit.pill(
                                    context,
                                    rideStatusLabel(
                                        currentStatus,
                                    ),
                                    rideStatusTone(
                                        currentStatus,
                                    ),
                                ),
                            )
                            addView(top)

                            addView(
                                UiKit.body(
                                    context,
                                    buildString {
                                        append(
                                            "${serviceLabel(offer.serviceType)} · ${moneyMetric(offer.perKm)}/km",
                                        )
                                        offer.context
                                            ?.destinationLabel
                                            ?.takeIf {
                                                it.isNotBlank()
                                            }
                                            ?.let {
                                                append(
                                                    "\n→ ${it.take(90)}",
                                                )
                                            }
                                    },
                                    11f,
                                ),
                            )

                            val actions =
                                LinearLayout(context).apply {
                                    orientation = HORIZONTAL
                                }

                            if (
                                currentStatus !=
                                RideOperationalStatus.COMPLETED
                            ) {
                                actions.addView(
                                    UiKit.secondaryButton(
                                        context,
                                        "Fiz esta corrida",
                                    ) {
                                        JourneyCoordinator.correctRide(
                                            context,
                                            offer.localId,
                                            RideOperationalStatus.COMPLETED,
                                        )
                                        refresh(true)
                                    },
                                    LayoutParams(
                                        0,
                                        LayoutParams.WRAP_CONTENT,
                                        1f,
                                    ),
                                )
                            } else {
                                actions.addView(
                                    UiKit.secondaryButton(
                                        context,
                                        "Desmarcar realizada",
                                    ) {
                                        JourneyCoordinator.correctRide(
                                            context,
                                            offer.localId,
                                            RideOperationalStatus.NOT_COMPLETED,
                                        )
                                        refresh(true)
                                    },
                                    LayoutParams(
                                        0,
                                        LayoutParams.WRAP_CONTENT,
                                        1f,
                                    ),
                                )
                            }

                            if (
                                currentStatus !=
                                RideOperationalStatus.NOT_COMPLETED &&
                                currentStatus !=
                                RideOperationalStatus.CANCELLED
                            ) {
                                actions.addView(
                                    UiKit.secondaryButton(
                                        context,
                                        "Não realizei",
                                    ) {
                                        JourneyCoordinator.correctRide(
                                            context,
                                            offer.localId,
                                            RideOperationalStatus.NOT_COMPLETED,
                                        )
                                        refresh(true)
                                    },
                                    LayoutParams(
                                        0,
                                        LayoutParams.WRAP_CONTENT,
                                        1f,
                                    ).apply {
                                        marginStart =
                                            UiKit.dp(
                                                context,
                                                6,
                                            )
                                    },
                                )
                            }

                            addView(
                                UiKit.margin(
                                    actions,
                                    top = 6,
                                ),
                            )
                        },
                        top = 6,
                    ),
                )
            }
        }

    private fun rideStatusLabel(
        status: RideOperationalStatus,
    ) = when (status) {
        RideOperationalStatus.OFFERED -> "OFERTA"
        RideOperationalStatus.DOING_RIDE -> "EM CORRIDA"
        RideOperationalStatus.COMPLETED -> "REALIZADA"
        RideOperationalStatus.NOT_COMPLETED -> "NÃO REALIZADA"
        RideOperationalStatus.CANCELLED -> "CANCELADA"
    }

    private fun rideStatusTone(
        status: RideOperationalStatus,
    ) = when (status) {
        RideOperationalStatus.COMPLETED -> "good"
        RideOperationalStatus.DOING_RIDE -> "primary"
        RideOperationalStatus.CANCELLED,
        RideOperationalStatus.NOT_COMPLETED -> "bad"
        RideOperationalStatus.OFFERED -> "warn"
    }

    private fun summaryCard(
        data: HistoryAnalytics,
    ): View {
        val s = data.summary
        return UiKit.card(context).apply {
            val top =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            top.addView(
                UiKit.sectionTitle(
                    context,
                    "Visão do período",
                ),
                LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            top.addView(
                UiKit.pill(
                    context,
                    if (data.source == "local") "LOCAL" else "NUVEM",
                    if (data.source == "local") "warn" else "good",
                ),
            )
            addView(top)

            addView(
                UiKit.title(
                    context,
                    "${s.offerCount} ofertas observadas",
                    22f,
                ),
            )
            addView(
                UiKit.body(
                    context,
                    "Boas ${s.goodCount} · Atenção ${s.regularCount} · Abaixo ${s.badCount}",
                    13f,
                ),
            )
            addView(
                metricGrid(
                    listOf(
                        "R$/km médio" to
                            moneyMetric(s.averagePerKm),
                        "R$/h médio" to
                            moneyMetric(s.averagePerHour),
                        "R$/min médio" to
                            moneyMetric(s.averagePerMinute),
                        "Oferta média" to money(s.averageFare),
                        "Valor observado*" to
                            money(s.totalOfferedFare),
                        "Lucro est. observado*" to
                            money(s.estimatedTotalProfit),
                    ),
                ),
            )
            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "*Somatório das ofertas exibidas pelo Uber. Não representa faturamento nem corridas realizadas.",
                        11f,
                    ),
                    top = 8,
                ),
            )
        }
    }

    private fun comparisonCard(
        data: HistoryAnalytics,
    ): View {
        val c = data.comparison
        return UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Comparação com período anterior",
                ),
            )

            if (c == null) {
                addView(
                    UiKit.body(
                        context,
                        "Sem período anterior suficiente para comparação.",
                    ),
                )
                return@apply
            }

            addView(
                metricGrid(
                    listOf(
                        "Ofertas" to delta(c.offerCountPct),
                        "R$/km" to delta(c.averagePerKmPct),
                        "R$/hora" to
                            delta(c.averagePerHourPct),
                        "R$/min" to
                            delta(c.averagePerMinutePct),
                        "Lucro médio*" to
                            delta(c.averageProfitPct),
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
    }

    private fun chartCard(
        title: String,
        bars: List<HistoryChartView.Bar>,
        suffix: String,
    ): View =
        UiKit.card(context).apply {
            addView(UiKit.sectionTitle(context, title))
            addView(
                HistoryChartView(context).apply {
                    setBars(
                        bars.filter {
                            it.value > 0.0
                        },
                        suffix,
                    )
                },
            )
        }

    private fun serviceCard(
        data: HistoryAnalytics,
    ): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Categorias",
                ),
            )

            if (data.services.isEmpty()) {
                addView(
                    UiKit.body(
                        context,
                        "Nenhuma categoria no período.",
                    ),
                )
            }

            data.services.take(8).forEach { row ->
                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            0,
                            UiKit.dp(context, 6),
                            0,
                            UiKit.dp(context, 6),
                        )
                        addView(
                            UiKit.body(
                                context,
                                serviceLabel(
                                    row.serviceType,
                                ),
                                14f,
                            ).apply {
                                setTypeface(
                                    typeface,
                                    Typeface.BOLD,
                                )
                            },
                            LayoutParams(
                                0,
                                LayoutParams.WRAP_CONTENT,
                                1f,
                            ),
                        )
                        addView(
                            UiKit.body(
                                context,
                                "${row.offerCount} · ${moneyMetric(row.averagePerKm)}/km · ${moneyMetric(row.averagePerHour)}/h",
                                12f,
                            ),
                        )
                    },
                )
            }
        }

    private fun topOffersCard(
        data: HistoryAnalytics,
    ): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Destaques do período",
                ),
            )
            if (data.topOffers.isEmpty()) {
                addView(
                    UiKit.body(
                        context,
                        "Nenhuma oferta para destacar.",
                    ),
                )
            }

            data.topOffers.take(6).forEach { o ->
                val tone =
                    when (o.verdict) {
                        "boa" -> "good"
                        "ruim" -> "bad"
                        else -> "warn"
                    }

                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            0,
                            UiKit.dp(context, 7),
                            0,
                            UiKit.dp(context, 7),
                        )
                        addView(
                            UiKit.pill(
                                context,
                                serviceLabel(
                                    o.serviceType,
                                ),
                                tone,
                            ),
                        )
                        addView(
                            LinearLayout(
                                context,
                            ).apply {
                                orientation = VERTICAL
                                setPadding(
                                    UiKit.dp(
                                        context,
                                        9,
                                    ),
                                    0,
                                    0,
                                    0,
                                )
                                addView(
                                    UiKit.title(
                                        context,
                                        money(o.fare),
                                        16f,
                                    ),
                                )
                                addView(
                                    UiKit.body(
                                        context,
                                        "${moneyMetric(o.perMinute)}/min · ${moneyMetric(o.perKm)}/km · ${moneyMetric(o.perHour)}/h",
                                        11f,
                                    ),
                                )
                            },
                            LayoutParams(
                                0,
                                LayoutParams.WRAP_CONTENT,
                                1f,
                            ),
                        )
                    },
                )
            }
        }

    private fun journeysCard(
        data: HistoryAnalytics,
    ): View =
        UiKit.card(context).apply {
            addView(
                UiKit.sectionTitle(
                    context,
                    "Jornadas",
                ),
            )
            if (data.journeys.isEmpty()) {
                addView(
                    UiKit.body(
                        context,
                        "Nenhuma jornada no período.",
                    ),
                )
            }

            data.journeys.take(12).forEach { j ->
                addView(
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        setPadding(
                            0,
                            UiKit.dp(context, 7),
                            0,
                            UiKit.dp(context, 7),
                        )
                        addView(
                            UiKit.body(
                                context,
                                "${dateTime(j.startedAt)} · ${j.offerCount} ofertas",
                                14f,
                            ).apply {
                                setTypeface(
                                    typeface,
                                    Typeface.BOLD,
                                )
                            },
                        )
                        val duration =
                            j.durationMinutes?.let {
                                " · ${formatDuration(it)}"
                            } ?: " · em andamento"
                        addView(
                            UiKit.body(
                                context,
                                "Boas ${j.goodCount} · Atenção ${j.regularCount} · Abaixo ${j.badCount}$duration",
                                12f,
                            ),
                        )
                        addView(
                            UiKit.body(
                                context,
                                "Médias: ${moneyMetric(j.averagePerKm)}/km · ${moneyMetric(j.averagePerHour)}/h",
                                12f,
                            ),
                        )
                    },
                )
            }
        }

    private fun metricGrid(
        items: List<Pair<String, String>>,
    ): View {
        val holder =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(
                    0,
                    UiKit.dp(context, 8),
                    0,
                    0,
                )
            }

        items.chunked(2).forEach { pair ->
            val row =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                }

            pair.forEach { item ->
                row.addView(
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        setPadding(
                            0,
                            UiKit.dp(context, 6),
                            UiKit.dp(context, 8),
                            UiKit.dp(context, 6),
                        )
                        addView(
                            UiKit.body(
                                context,
                                item.first,
                                11f,
                            ),
                        )
                        addView(
                            UiKit.title(
                                context,
                                item.second,
                                17f,
                            ),
                        )
                    },
                    LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            }

            if (pair.size == 1) {
                row.addView(
                    View(context),
                    LayoutParams(0, 1, 1f),
                )
            }
            holder.addView(row)
        }

        return holder
    }

    private fun caption(text: String) =
        UiKit.body(context, text, 12f).apply {
            setPadding(
                0,
                UiKit.dp(context, 8),
                0,
                UiKit.dp(context, 3),
            )
        }

    private fun spinner(items: List<String>) =
        Spinner(context).apply {
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    items,
                )
        }

    private fun money(v: Double?) =
        if (v == null) "—" else "R$ ${fmt(v)}"

    private fun moneyMetric(v: Double?) =
        if (v == null) "—" else "R$ ${fmt(v)}"

    private fun delta(v: Double?): String =
        if (v == null) {
            "—"
        } else {
            "${if (v > 0) "+" else ""}${fmt(v)}%"
        }

    private fun fmt(v: Double) =
        String.format(
            java.util.Locale("pt", "BR"),
            "%.2f",
            v,
        )

    private fun serviceLabel(v: String) =
        when (v) {
            "uberx" -> "UberX"
            "comfort" -> "Comfort"
            "black" -> "Black"
            "electric" -> "Electric"
            "priority" -> "Priority"
            "moto" -> "Moto"
            else -> "Outro"
        }

    private fun dateTime(value: String): String =
        runCatching {
            DateTimeFormatter
                .ofPattern("dd/MM HH:mm")
                .withZone(
                    ZoneId.of(
                        "America/Sao_Paulo",
                    ),
                )
                .format(
                    Instant.parse(value),
                )
        }.getOrDefault(
            value.take(16),
        )

    private fun formatDuration(minutes: Int) =
        if (minutes < 60) {
            "${minutes}min"
        } else {
            "${minutes / 60}h ${minutes % 60}min"
        }
}
