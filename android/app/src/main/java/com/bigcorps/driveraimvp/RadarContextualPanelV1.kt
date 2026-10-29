package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.text.TextUtils
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RadarContextualPanelV1(context: Context) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var requestGeneration = 0L
    // vc107: no embedded cartography (no MapView/MapLibre/snapshots). Locations are
    // handed to the external Google Maps only when the driver taps "Ver no Google Maps".
    private val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private enum class Tab { SUMMARY, OPPORTUNITIES, EVENTS }
    private var tab = Tab.SUMMARY
    private var spec: RadarDestinationSpecV1? = null
    private var result: RadarContextualResultV1? = null
    private var errorMessage: String? = null
    private var selectedId: String? = null
    private var demoMode = false
    private var surfaceResumed = false
    private var renderPending = false
    private var pendingGeneration = 0L
    private var preferredOpportunityId:String? = null
    private var arrivalText: TextView? = null
    private var arrivalEta: String? = null
    // Low-frequency fallback for destination revisions that arrive without a capture broadcast.
    // Runs only while the real Radar surface is visible, including loading/Events.
    private val destinationTicker = object : Runnable {
        override fun run() {
            if (!surfaceResumed || !isShown || demoMode || spec == null) return
            refreshIfDestinationChanged()
            scheduleDestinationTicker()
        }
    }
    private fun scheduleDestinationTicker() {
        removeCallbacks(destinationTicker)
        if (surfaceResumed && isShown && !demoMode && spec != null) {
            postDelayed(destinationTicker, 20_000L)
        }
    }

    /** Re-query only on actual changes; an unrelated ride is never opened automatically. */
    fun refreshIfDestinationChanged(): Boolean {
        val previous = spec ?: return false
        if (demoMode || !surfaceResumed || !isShown) return false
        val current = RadarDestinationContextV1.current(context)
        if (previous == current) return false
        FieldPipelineTraceV1.event(context, previous.localOfferId,
            "RADAR_OPENED_BLOCKED", "stale_destination_recovery")
        if (current != null && current.localOfferId == previous.localOfferId) {
            openDestination(current, force = true)
        } else {
            renderIdle()
        }
        return true
    }

    private val arrivalTicker = object : Runnable {
        override fun run() {
            val label = arrivalText ?: return
            val eta = arrivalEta ?: return
            if (!surfaceResumed || !isShown || tab != Tab.SUMMARY) return
            if (refreshIfDestinationChanged()) return
            label.text = arrivalWithAssistantStatus(eta, System.currentTimeMillis())
            postDelayed(this, 30_000L)
        }
    }
    private fun arrivalWithAssistantStatus(eta: String, nowMs: Long): String {
        val base = RadarCompactOverlayV1.arrival(eta, nowMs)
        return if (DestinationRadarFreshnessV1.blockReason(eta, nowMs) != null)
            "$base · Assistente pausado até uma nova ETA válida"
        else base
    }
    private fun stopArrivalTicker() {
        removeCallbacks(arrivalTicker)
        arrivalText = null
        arrivalEta = null
    }

    fun isDemo():Boolean = demoMode

    fun setSurfaceResumed(value:Boolean) {
        surfaceResumed=value
        if(!value) {
            renderPending=result!=null
            pendingGeneration=requestGeneration
            releaseSurface("activity_paused")
            removeCallbacks(destinationTicker)
        } else post {
            refreshIfDestinationChanged()
            renderPendingIfReady()
            scheduleDestinationTicker()
        }
    }

    fun cancelSurface(reason:String) {
        removeCallbacks(destinationTicker)
        invalidateRequests()
        renderPending=false
        demoMode=false
        result=null
        spec=null
        releaseSurface(reason)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            refreshIfDestinationChanged()
            scheduleDestinationTicker()
            renderPendingIfReady()
        }
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        post { renderPendingIfReady() }
    }

    fun renderPendingIfReady() {
        val value=result ?: return
        if(!renderPending || pendingGeneration!=requestGeneration) return
        if(!demoMode && RadarDestinationContextV1.current(context)!=spec) {
            renderPending=false
            FieldPipelineTraceV1.event(context,spec?.localOfferId,"RADAR_OPENED_BLOCKED","stale_render")
            RadarContextualDiagnosticV1.surfaceBlocked("stale_render")
            refreshIfDestinationChanged()
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
        root.addView(SrAppHeader023(context, "Radar", ""))
        // vc107: no map stage; contextual cards keep their own padding.
        body.setPadding(0, UiKit.dp(context, 4), 0, UiKit.dp(context, 20))
        root.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        renderIdle()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(destinationTicker)
        invalidateRequests()
        renderPending=false
        releaseSurface("detached")
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            renderPending=result!=null
            pendingGeneration=requestGeneration
            releaseSurface("hidden")
            removeCallbacks(destinationTicker)
        } else post {
            refreshIfDestinationChanged()
            scheduleDestinationTicker()
            renderPendingIfReady()
        }
    }

    /** Entrada preferencial quando aberta durante uma corrida real. */
    fun openDestination(value: RadarDestinationSpecV1, force: Boolean = false, opportunityId:String? = null) {
        if (spec?.localOfferId != value.localOfferId) tab = Tab.SUMMARY
        val generation = ++requestGeneration
        leaveDemo()
        releaseSurface("destination_changed")
        result = null
        errorMessage = null
        renderPending = false
        preferredOpportunityId=opportunityId
        selectedId = null
        spec = value
        scheduleDestinationTicker()
        if (!force) RadarContextualTelemetryV1.track(context, "radar_opened", value)
        renderLoading(value)
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
            if (generation != requestGeneration || demoMode || spec != value) return@fetch
            if (RadarDestinationContextV1.current(context) != value) {
                refreshIfDestinationChanged()
                return@fetch
            }
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
        removeCallbacks(destinationTicker)
        tab = Tab.SUMMARY
        invalidateRequests()
        demoMode = true
        spec = RadarContextualDemoV1.spec()
        result = RadarContextualDemoV1.result()
        selectedId = result?.opportunities?.firstOrNull()?.id
        renderOrDefer()
    }

    fun focusOpportunity(opportunityId: String?) {
        if (!demoMode && refreshIfDestinationChanged()) return
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
        removeCallbacks(destinationTicker)
        invalidateRequests()
        renderPending=false
        demoMode = false
        spec = null
        result = null
        errorMessage = null
        selectedId = null
        stopArrivalTicker()
        body.removeAllViews()
        body.addView(tabRow())
        val active = RadarDestinationContextV1.current(context)
        body.addView(
            SrUi023.card(context, 12, 16).apply {
                when (tab) {
                    Tab.SUMMARY -> {
                        val position = RadarVisibleLocationV1.latest(context)
                        addView(SrUi023.title(context, "Sua região", 14f))
                        addView(SrUi023.body(context,
                            if (position != null) "Localização aproximada disponível. O Radar mostra oportunidades quando há um destino de corrida."
                            else "Localização recente indisponível. Ative a localização para abrir sua região no Google Maps.", 10f))
                        if (position != null) addView(UiKit.margin(mapsButton(position.lat, position.lng, null), top = 8))
                        addView(UiKit.margin(UiKit.secondaryButton(context,"Atualizar localização") { renderIdle() }, top = 6))
                    }
                    Tab.OPPORTUNITIES -> {
                        addView(SrUi023.title(context, "Oportunidades no destino",14f))
                        addView(SrUi023.body(context,
                            "As oportunidades verificadas aparecerão aqui quando um destino de corrida estiver disponível.",10f))
                    }
                    Tab.EVENTS -> {
                        addView(SrUi023.title(context, "Áreas de interesse",14f))
                        addView(SrUi023.body(context,
                            "Eventos e áreas de interesse aparecerão aqui quando houver informações verificadas para a região.",10f))
                    }
                }
                if (active != null) {
                    addView(UiKit.margin(
                        UiKit.primaryButton(context, "Consultar destino atual") {
                            openDestination(RadarDestinationContextV1.current(context) ?: return@primaryButton)
                        }, top = 8))
                }
            },
        )
    }

    private fun renderLoading(value: RadarDestinationSpecV1) {
        stopArrivalTicker()
        body.removeAllViews()
        body.addView(tabRow())
        addHeader(value)
        body.addView(UiKit.margin(SrUi023.card(context, 12, 16).apply {
            addView(SrUi023.body(context, "Consultando informações verificadas para este destino…", 10.5f))
            addView(UiKit.secondaryButton(context, "Atualizar") { refresh() })
        }, top = 8))
    }

    private fun addHeader(value: RadarDestinationSpecV1) {
        body.addView(
            SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.title(context, value.label ?: "Destino da corrida", 14f))
                if (demoMode) addView(SrUi023.pill(context, "DEMO · sem dados reais", "warn"))
                addView(SrUi023.body(context, "Chegada estimada ${formatTime(value.eta)}", 10f))
                if (!demoMode && RadarExternalMapsV1.available(value.lat, value.lng, value.label)) {
                    addView(UiKit.margin(mapsButton(value.lat, value.lng, value.label), top = 8))
                }
            if (DestinationRadarFreshnessV1.blockReason(value.eta, System.currentTimeMillis()) != null) {
                addView(SrUi023.body(context,
                    "ETA vencida ou inválida: o Assistente não envia sugestões antigas. Atualize o destino na corrida para obter uma nova ETA real.", 10f))
            }
            },
        )
    }

    private fun renderResult(value: RadarContextualResultV1) {
        errorMessage = null
        stopArrivalTicker()
        body.removeAllViews()
        detach(cards)
        detach(detail)
        body.addView(tabRow())

        val screen = RadarContextualPresenterV1.map(value)
        if (tab == Tab.SUMMARY) {
            // Light contextual summary instead of the old 490dp MapLibre stage.
            val summary = SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.title(context, RadarCompactOverlayV1.address(spec?.label ?: value.destinationLabel), 14f).apply {
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                })
                if (demoMode) addView(SrUi023.pill(context, "DEMO · sem dados reais", "warn"))
                val etaLabel = SrUi023.body(context,
                    arrivalWithAssistantStatus(value.destinationEta, System.currentTimeMillis()), 10.5f)
                addView(etaLabel)
                arrivalText = etaLabel
                arrivalEta = value.destinationEta
                if (surfaceResumed && isShown) postDelayed(arrivalTicker, 30_000L)
                // Shown only when the backend baseline has a sample behind it.
                RadarCompactOverlayV1.probability(
                    value.baseline.probabilityPct, value.baseline.samples,
                )?.let { addView(UiKit.margin(SrUi023.title(context, it, 12f), top = 4)) }
                addView(SrUi023.body(context, when (value.opportunities.size) {
                    0 -> "Sem oportunidades verificadas perto do destino agora."
                    1 -> "1 oportunidade verificada perto do destino."
                    else -> "${value.opportunities.size} oportunidades verificadas perto do destino."
                } + if (eventContexts(value).isNotEmpty()) " Há eventos elegíveis na aba Áreas." else "", 10f))
                val destLat = spec?.lat ?: screen.destinationLat
                val destLng = spec?.lng ?: screen.destinationLng
                if (!demoMode && RadarExternalMapsV1.available(destLat, destLng, spec?.label ?: value.destinationLabel)) {
                    addView(UiKit.margin(mapsButton(destLat, destLng, spec?.label ?: value.destinationLabel), top = 8))
                }
            }
            body.addView(summary)
            screen.cards.take(3).forEach { card ->
                body.addView(UiKit.margin(SrUi023.softCard(context, if (card.id == selectedId) "info" else "neutral", 10).apply {
                    addView(SrUi023.title(context, card.title, 12.5f))
                    addView(SrUi023.body(context, card.meta, 9.5f))
                    addView(SrUi023.pill(context, card.potentialLabel, when {
                        card.potentialLabel.contains("ALTO") -> "good"
                        card.potentialLabel.contains("MÉDIO") -> "warn"
                        card.potentialLabel.contains("BAIXO") -> "bad"
                        else -> "purple"
                    }))
                    isClickable = true
                    setOnClickListener { select(card.id) }
                }, top = 6))
            }
            // Selection (or assistant deep link) exposes the selected opportunity.
            value.opportunities.firstOrNull { it.id == selectedId }?.let { selected ->
                body.addView(UiKit.margin(SrUi023.softCard(context, "info", 10).apply {
                    addView(SrUi023.title(context, selected.title, 12f))
                    addView(SrUi023.body(context, selected.reasonHeadline, 10f))
                    addView(UiKit.secondaryButton(context, "Ver detalhes") {
                        tab = Tab.OPPORTUNITIES
                        renderOrDefer()
                    })
                    if (!demoMode && RadarExternalMapsV1.available(selected.lat, selected.lng, selected.title)) {
                        addView(UiKit.margin(mapsButton(selected.lat, selected.lng, selected.title) { trackNavigation(selected) }, top = 6))
                    }
                }, top = 6))
            }
            body.addView(UiKit.margin(UiKit.secondaryButton(context, "Atualizar") { refresh() }, top = 6))
            return
        }

        if (tab == Tab.EVENTS) {
            // There is no separate verified event feed in the Radar contract.
            // Show the existing opportunity evidence only; never synthesize events.
            val withContext = eventContexts(value)
            body.addView(SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.title(context, "Áreas de interesse", 14f))
                if (withContext.isEmpty()) {
                    addView(SrUi023.body(context, "Sem eventos ou áreas de interesse verificados para este destino.", 11f))
                } else {
                    withContext.take(8).forEach { opportunity ->
                        val eventContext = opportunity.context ?: return@forEach
                        addView(SrUi023.body(context, eventContext.name, 11f))
                        addView(SrUi023.body(context,
                            "Saída prevista: ${formatTime(eventContext.egressStartAt)} – ${formatTime(eventContext.egressEndAt)}", 10f))
                        addView(SrUi023.body(context, opportunity.reasonHeadline, 10f))
                    }
                }
            })
            return
        }

        if (value.opportunities.isEmpty()) {
            body.addView(SrUi023.card(context, 12, 16).apply {
                addView(SrUi023.body(context, "Sem oportunidades mapeadas agora.", 11f))
                addView(UiKit.secondaryButton(context, "Atualizar") { refresh() })
            })
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

    // The existing backend must supply a real contextual event and its temporal window.
    private fun eventContexts(value: RadarContextualResultV1) = value.opportunities.filter { opportunity ->
        val event = opportunity.context ?: return@filter false
        RadarContextualEventEligibilityV1.eligible(event.type, event.name,
            event.egressStartAt, event.egressEndAt, event.confidence,
            value.destinationEta, System.currentTimeMillis())
    }

    /** Three always-visible folder tabs, even with no active ride or backend events. */
    private fun tabRow(): View {
        val colors=SrUi023.palette(context)
        val bar = LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(UiKit.dp(context, 4), UiKit.dp(context, 4),
                UiKit.dp(context, 4), UiKit.dp(context, 6))
        }
        val availableTabs=listOf(
            Tab.SUMMARY to "Resumo",
            Tab.OPPORTUNITIES to "Oportunidades",
            Tab.EVENTS to "Áreas",
        )
        for ((next,label) in availableTabs) {
            val active=tab==next
            bar.addView(TextView(context).apply {
                text=label
                textSize=if(label.length>15) 9f else 10.5f
                gravity=Gravity.CENTER
                minHeight=UiKit.dp(context, 42)
                setPadding(UiKit.dp(context, 3), 0, UiKit.dp(context, 3), 0)
                setTextColor(if(active) colors.navy else colors.muted)
                background=SrUi023.rounded(
                    if(active) colors.surface else android.graphics.Color.TRANSPARENT,
                    12, if(active) colors.blue else colors.outline,
                    if(active) 2 else 1, context)
                setOnClickListener {
                    if (tab != next) {
                        if (!demoMode && refreshIfDestinationChanged()) return@setOnClickListener
                        tab=next
                        result?.let(::renderResult)
                            ?: spec?.let { current ->
                                errorMessage?.let(::renderError) ?: renderLoading(current)
                            }
                            ?: renderIdle()
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart=UiKit.dp(context, 2)
            })
        }
        return bar
    }

    private fun select(id: String) {
        if (!demoMode && refreshIfDestinationChanged()) return
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
            smallButton("Ver no Google Maps") {
                trackNavigation(selected)
                RadarExternalMapsV1.open(context, selected.lat, selected.lng, selected.title)
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
        errorMessage = message
        stopArrivalTicker()
        body.removeAllViews()
        body.addView(tabRow())
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

    /** Former map lifecycle hook: only the ETA ticker remains to stop. */
    private fun releaseSurface(@Suppress("UNUSED_PARAMETER") reason:String="replaced") {
        stopArrivalTicker()
    }

    /** Explicit tap only; opens the external Google Maps at this location. */
    private fun mapsButton(lat: Double?, lng: Double?, label: String?, before: () -> Unit = {}): View =
        UiKit.secondaryButton(context, "Ver no Google Maps") {
            before()
            RadarExternalMapsV1.open(context, lat, lng, label)
        }.apply { contentDescription = "Ver no Google Maps (abre fora do Sr. Rotas)" }

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
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault("—")
}
