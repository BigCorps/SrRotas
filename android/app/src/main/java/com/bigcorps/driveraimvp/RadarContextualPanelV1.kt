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
    private var requestGeneration = 0L
    private var map: RadarMiniMapViewV1? = null
    private val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var spec: RadarDestinationSpecV1? = null
    private var result: RadarContextualResultV1? = null
    private var selectedId: String? = null
    private var demoMode = false
    private var surfaceResumed = false
    private var renderPending = false
    private var pendingGeneration = 0L
    private var preferredOpportunityId:String? = null

    fun isDemo():Boolean = demoMode

    fun setSurfaceResumed(value:Boolean) {
        surfaceResumed=value
        if(!value) {
            renderPending=result!=null
            pendingGeneration=requestGeneration
            releaseMap("activity_paused")
        }
    }

    fun cancelSurface(reason:String) {
        invalidateRequests()
        renderPending=false
        demoMode=false
        result=null
        spec=null
        releaseMap(reason)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { renderPendingIfReady() }
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        post { renderPendingIfReady() }
    }

    fun renderPendingIfReady() {
        val value=result ?: return
        if(!renderPending || pendingGeneration!=requestGeneration) return
        if(!demoMode && RadarDestinationContextV1.current(context)?.localOfferId!=spec?.localOfferId) {
            renderPending=false
            FieldPipelineTraceV1.event(context,spec?.localOfferId,"RADAR_OPENED_BLOCKED","stale_render")
            RadarContextualDiagnosticV1.surfaceBlocked("stale_render")
            return
        }
        val stage=parent as? View
        if (!surfaceResumed || !isShown || !isAttachedToWindow || visibility!=View.VISIBLE || width<=0 || height<=0 ||
            stage?.isAttachedToWindow!=true || stage.width<=0 || stage.height<=0) {
            RadarContextualDiagnosticV1.surfaceRenderDeferred()
            return
        }
        renderPending=false
        RadarContextualDiagnosticV1.surfaceRenderStarted()
        renderResult(value)
        RadarContextualDiagnosticV1.surfaceRenderCompleted()
        if(!demoMode) FieldPipelineTraceV1.event(context,spec?.localOfferId,"RADAR_OPENED","surface_ready")
        if(demoMode) post {
            if(demoMode && isShown) {
                smoothScrollTo(0, 0)
                RadarContextualDiagnosticV1.demoPreviewRendered()
            }
        }
    }

    private fun renderOrDefer() {
        pendingGeneration=requestGeneration
        renderPending=true
        renderPendingIfReady()
    }

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
        renderIdle()
    }

    override fun onDetachedFromWindow() {
        invalidateRequests()
        renderPending=false
        releaseMap("detached")
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            renderPending=result!=null
            pendingGeneration=requestGeneration
            releaseMap("hidden")
        } else post { renderPendingIfReady() }
    }

    /** Entrada preferencial quando aberta durante uma corrida real. */
    fun openDestination(value: RadarDestinationSpecV1, force: Boolean = false, opportunityId:String? = null) {
        val generation = ++requestGeneration
        leaveDemo()
        releaseMap()
        result = null
        renderPending = false
        preferredOpportunityId=opportunityId
        selectedId = null
        spec = value
        if (!force) RadarContextualTelemetryV1.track(context, "radar_opened", value)
        val loading = SrUi023.body(context, "Analisando o destino e as oportunidades próximas…", 10.5f)
        body.removeAllViews()
        addHeader(value)
        body.addView(
            UiKit.margin(
                SrUi023.card(context, 12, 16).apply { addView(loading) },
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
            traceOfferId = value.localOfferId,
        ) { response ->
            // Sucessos e erros só pertencem à consulta ainda ativa nesta superfície.
            if (generation != requestGeneration || demoMode ||
                spec?.localOfferId != value.localOfferId ||
                RadarDestinationContextV1.current(context)?.localOfferId != value.localOfferId
            ) return@fetch
            response.onSuccess {
                result = it
                selectedId =
                    preferredOpportunityId?.takeIf { id -> it.opportunities.any { opportunity -> opportunity.id==id } }
                        ?: it.assistant.opportunityId
                        ?: it.opportunities.firstOrNull()?.id
                RadarContextualDiagnosticV1.acceptResult(it, "ui")
                renderOrDefer()
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
        invalidateRequests()
        demoMode = true
        spec = RadarContextualDemoV1.spec()
        result = RadarContextualDemoV1.result()
        selectedId = result?.opportunities?.firstOrNull()?.id
        renderOrDefer()
    }

    fun focusOpportunity(opportunityId: String?) {
        if (opportunityId.isNullOrBlank()) return
        val value = result ?: return
        if (value.opportunities.none { it.id == opportunityId }) return
        selectedId = opportunityId
        if (!demoMode) RadarContextualDiagnosticV1.userSelectedOpportunity(opportunityId)
        renderOrDefer()
    }

    private fun leaveDemo() {
        if (!demoMode) return
        demoMode = false
        spec = null
        result = null
        selectedId = null
    }

    private fun invalidateRequests() {
        requestGeneration++
    }

    private fun renderIdle() {
        invalidateRequests()
        renderPending=false
        releaseMap("idle")
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
        val mapView = ensureMap()
        detach(mapView)
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
                    mapView.render(
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
                    addView(UiKit.margin(mapView, top = 9))
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

        if (value.opportunities.isEmpty()) {
            body.addView(UiKit.margin(SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.body(context, "Nenhuma oportunidade mapeada nesta região no momento.", 10f))
                addView(SrUi023.body(context, "O Radar continuará atualizando enquanto a corrida estiver ativa.", 9.5f))
                addView(UiKit.secondaryButton(context, "Atualizar") { refresh() })
            }, top = 8))
        }

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
        renderOrDefer()
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
        releaseMap()
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

    private fun ensureMap(): RadarMiniMapViewV1 {
        map?.let { return it }
        return RadarMiniMapViewV1(context, demo = demoMode, traceOfferId = if(demoMode) null else spec?.localOfferId).also { created ->
            created.onMarkerSelected = { select(it) }
            map = created
        }
    }

    private fun releaseMap(reason:String="replaced") {
        val current = map ?: return
        detach(current)
        current.release(reason)
        map = null
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
