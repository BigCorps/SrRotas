package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RadarContextualPanelV1(context: Context) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = SrUi023.body(context, "", 10.5f)
    private val map = RadarMiniMapViewV1(context)
    private val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var spec: RadarDestinationSpecV1? = null
    private var result: RadarContextualResultV1? = null
    private var selectedId: String? = null
    private var demoMode = false

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(
            SrAppHeader023(
                context,
                "Radar",
                "Continuidade e oportunidades no destino.",
            ),
        )
        body.setPadding(
            UiKit.dp(context, 14), UiKit.dp(context, 10),
            UiKit.dp(context, 14), UiKit.dp(context, 26),
        )
        root.addView(
            body,
            LinearLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        map.onMarkerSelected = { select(it) }
        renderIdle()
    }

    override fun onDetachedFromWindow() {
        map.release()
        super.onDetachedFromWindow()
    }

    /** Entrada preferencial quando aberta durante uma corrida real. */
    fun openDestination(value: RadarDestinationSpecV1, force: Boolean = false) {
        leaveDemo()
        spec = value
        if (!force) RadarContextualTelemetryV1.track(context, "radar_opened", value)
        status.text = "Analisando o destino e as oportunidades próximas…"
        body.removeAllViews()
        addHeader(value)
        body.addView(
            UiKit.margin(
                SrUi023.card(context, 12, 16).apply { addView(status) },
                top = 8,
            ),
        )
        RadarContextualClientV1.fetch(
            context = context,
            destinationLat = value.lat,
            destinationLng = value.lng,
            eta = value.eta,
            destinationLabel = value.label,
            force = force,
            source = "ui",
        ) { response ->
            response.onSuccess {
                // Se a corrida mudou enquanto a UI consultava, não pinta a
                // resposta antiga como se fosse a corrida atual.
                val live = RadarDestinationContextV1.current(context)
                if (live?.localOfferId != value.localOfferId) {
                    refresh()
                    return@onSuccess
                }
                result = it
                selectedId =
                    it.assistant.opportunityId
                        ?: it.opportunities.firstOrNull()?.id
                RadarContextualDiagnosticV1.acceptResult(it, "ui")
                renderResult(it)
            }.onFailure {
                RadarContextualDiagnosticV1.failure(it)
                renderError(it.message ?: "Radar indisponível agora.")
            }
        }
    }

    /**
     * Refresh é sempre operacional.
     * Se a tela estava em Prévia DEMO, qualquer refresh abandona explicitamente
     * os dados fictícios e volta a resolver a corrida real.
     */
    fun refresh() {
        leaveDemo()
        val active = RadarDestinationContextV1.current(context)
        if (active != null) openDestination(active, true) else renderIdle()
    }

    /** Preview local, explicitamente marcado DEMO e sem telemetria. */
    fun openDemo() {
        demoMode = true
        spec = RadarContextualDemoV1.spec()
        result = RadarContextualDemoV1.result()
        selectedId = result?.opportunities?.firstOrNull()?.id
        result?.let(::renderResult)
    }

    fun focusOpportunity(opportunityId: String?) {
        if (opportunityId.isNullOrBlank()) return
        val value = result ?: return
        if (value.opportunities.none { it.id == opportunityId }) return
        selectedId = opportunityId
        if (!demoMode) RadarContextualDiagnosticV1.userSelectedOpportunity(opportunityId)
        renderResult(value)
    }

    private fun leaveDemo() {
        if (!demoMode) return
        demoMode = false
        spec = null
        result = null
        selectedId = null
    }

    private fun renderIdle() {
        demoMode = false
        spec = null
        result = null
        selectedId = null
        body.removeAllViews()
        val active = RadarDestinationContextV1.current(context)
        body.addView(
            SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.title(context, "Continuidade no destino", 14f))
                if (active == null) {
                    addView(
                        SrUi023.body(
                            context,
                            "Quando houver uma corrida ativa com destino e ETA conhecidos, o Radar poderá analisar oportunidades ao redor do desembarque.",
                            10f,
                        ),
                    )
                } else {
                    addView(SrUi023.body(context, active.label ?: "Destino da corrida atual", 10f))
                    addView(
                        UiKit.margin(
                            UiKit.primaryButton(context, "Ver oportunidades no destino") {
                                openDestination(active)
                            },
                            top = 8,
                        ),
                    )
                }
            },
        )
    }

    private fun addHeader(value: RadarDestinationSpecV1) {
        body.addView(
            SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.title(context, value.label ?: "Destino da corrida", 14f))
                if (demoMode) addView(SrUi023.pill(context, "DEMO · sem dados reais", "warn"))
                addView(SrUi023.body(context, "Chegada estimada ${formatTime(value.eta)}", 10f))
            },
        )
    }

    private fun renderResult(value: RadarContextualResultV1) {
        body.removeAllViews()
        detach(map)
        detach(cards)
        detach(detail)
        addHeader(
            spec ?: RadarDestinationSpecV1(
                value.destinationLat,
                value.destinationLng,
                value.destinationEta,
                value.destinationLabel,
                "",
            ),
        )

        val screen = RadarContextualPresenterV1.map(value)
        body.addView(
            UiKit.margin(
                SrUi023.card(context, 12, 16).apply {
                    addView(SrUi023.title(context, "Mapa — Continuidade no Destino", 14f))
                    addView(SrUi023.body(context, screen.baselineLabel, 9.5f))
                    map.render(
                        RadarMiniMapViewV1.State(
                            centerLat = screen.destinationLat,
                            centerLng = screen.destinationLng,
                            radiusKm = 4.0,
                            markers = value.opportunities.take(12).map {
                                RadarMiniMapViewV1.Marker(
                                    it.id, it.lat, it.lng, it.title, it.poiType, it.potential,
                                )
                            },
                            selectedId = selectedId,
                        ),
                    )
                    addView(UiKit.margin(map, top = 9))
                    addView(
                        UiKit.margin(
                            SrUi023.body(
                                context,
                                "Mapa por MapLibre + OpenFreeMap · toque em um marcador para ver detalhes.",
                                8.2f,
                            ),
                            top = 5,
                        ),
                    )
                },
                top = 8,
            ),
        )

        cards.removeAllViews()
        for (card in screen.cards.take(5)) {
            val selected = card.id == selectedId
            cards.addView(
                UiKit.margin(
                    SrUi023.softCard(context, if (selected) "info" else "neutral", 10).apply {
                        addView(SrUi023.title(context, card.title, 12.5f))
                        addView(SrUi023.body(context, card.meta, 9.5f))
                        addView(
                            SrUi023.pill(
                                context,
                                card.potentialLabel,
                                when {
                                    card.potentialLabel.contains("ALTO") -> "good"
                                    card.potentialLabel.contains("MÉDIO") -> "warn"
                                    card.potentialLabel.contains("BAIXO") -> "bad"
                                    else -> "purple"
                                },
                            ),
                        )
                        addView(SrUi023.body(context, card.reason, 10f))
                        setOnClickListener { select(card.id) }
                    },
                    top = 6,
                ),
            )
        }
        body.addView(UiKit.margin(cards, top = 8))
        renderDetail()
    }

    private fun select(id: String) {
        selectedId = id
        val value = result ?: return
        if (!demoMode) {
            RadarContextualDiagnosticV1.userSelectedOpportunity(id)
            spec?.let { RadarContextualTelemetryV1.track(context, "opportunity_viewed", it, value, id) }
        }
        renderResult(value)
    }

    private fun renderDetail() {
        val selected = result?.opportunities?.firstOrNull { it.id == selectedId } ?: return
        detail.removeAllViews()
        detail.addView(SrUi023.title(context, selected.title, 14f))
        val meta = buildString {
            append(String.format(Locale("pt", "BR"), "%.1f km", selected.distanceKm))
            selected.subtitle?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        }
        detail.addView(SrUi023.body(context, meta, 9.5f))
        detail.addView(SrUi023.body(context, selected.reasonHeadline, 10f))

        val potentialLabel = when (selected.potential) {
            "high" -> "Potencial alto"
            "medium" -> "Potencial médio"
            "low" -> "Potencial baixo"
            else -> "Amostra em formação"
        }
        detail.addView(
            UiKit.margin(
                SrUi023.pill(
                    context,
                    "$potentialLabel · ${(selected.confidence * 100).toInt()}% confiança",
                    when (selected.potential) {
                        "high" -> "good"
                        "medium" -> "warn"
                        "low" -> "bad"
                        else -> "purple"
                    },
                ),
                top = 6,
            ),
        )

        if (!selected.windowStart.isNullOrBlank() || !selected.windowEnd.isNullOrBlank()) {
            detail.addView(
                UiKit.margin(
                    SrUi023.body(
                        context,
                        "Janela provável: ${formatTime(selected.windowStart ?: "")} – ${formatTime(selected.windowEnd ?: "")}",
                        9.5f,
                    ),
                    top = 5,
                ),
            )
        }

        detail.addView(UiKit.margin(SrUi023.title(context, "Por que está aqui?", 11.5f), top = 8))
        selected.evidence.take(5).forEach {
            detail.addView(SrUi023.body(context, "✓ ${it.label}", 9.5f))
        }

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            smallButton("Google Maps") {
                trackNavigation(selected)
                openGoogleMaps(selected.lat, selected.lng)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            smallButton("Waze") {
                trackNavigation(selected)
                openWaze(selected.lat, selected.lng)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(context, 5)
            },
        )
        row.addView(
            smallButton("Atualizar") { refresh() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(context, 5)
            },
        )
        detail.addView(UiKit.margin(row, top = 9))

        body.addView(
            UiKit.margin(
                SrUi023.card(context, 12, 16).apply { addView(detail) },
                top = 8,
            ),
        )
    }

    private fun trackNavigation(selected: RadarContextualOpportunityV1) {
        if (demoMode) return
        spec?.let {
            RadarContextualTelemetryV1.track(
                context,
                "navigation_opened",
                it,
                result,
                selected.id,
            )
        }
    }

    private fun renderError(message: String) {
        body.removeAllViews()
        spec?.let(::addHeader)
        body.addView(
            UiKit.margin(
                SrUi023.card(context, 12, 16).apply {
                    addView(SrUi023.title(context, "Radar indisponível", 13f))
                    addView(SrUi023.body(context, message, 10f))
                    addView(
                        UiKit.margin(
                            UiKit.secondaryButton(context, "Tentar novamente") { refresh() },
                            top = 8,
                        ),
                    )
                },
                top = 8,
            ),
        )
    }

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun smallButton(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 9.2f
        gravity = Gravity.CENTER
        minHeight = UiKit.dp(context, 38)
        setTextColor(SrUi023.palette(context).blue)
        background = SrUi023.rounded(
            android.graphics.Color.TRANSPARENT,
            10,
            SrUi023.palette(context).blue,
            1,
            context,
        )
        setOnClickListener { action() }
    }

    private fun openGoogleMaps(lat: Double, lng: Double) {
        val native = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lng"),
        ).setPackage("com.google.android.apps.maps")

        runCatching {
            context.startActivity(native.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        }
    }

    private fun openWaze(lat: Double, lng: Double) {
        openUri("https://www.waze.com/ul?ll=$lat,$lng&navigate=yes")
    }

    private fun openUri(value: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(value))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun formatTime(value: String) = runCatching {
        DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value))
    }.getOrDefault("—")
}
