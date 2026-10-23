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
    private var map: RadarMiniMapViewV1? = null
    // Memory-only map focus, never persisted or uploaded. Last real destination
    // remains available if the Android location provider has no fresh fix.
    private var lastMapFocus: Pair<Double,Double>? = null
    private val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private enum class Tab { MAP, OPPORTUNITIES, EVENTS }
    private var tab = Tab.MAP
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

    /** Restore the existing map after Activity/tab visibility transitions without
     * depending on onSizeChanged. Never starts an HTTP request by itself.
     */
    private fun recoverVisibleMapSurface() {
        if (!surfaceResumed || !isShown || !isAttachedToWindow ||
            tab != Tab.MAP || map != null) return
        val currentResult=result
        if(currentResult!=null) {
            renderPending=true
            pendingGeneration=requestGeneration
            renderPendingIfReady()
            return
        }
        val currentSpec=spec
        when {
            currentSpec==null -> renderIdle()
            errorMessage!=null -> renderError(errorMessage!!)
            else -> renderLoading(currentSpec)
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
            if (!surfaceResumed || !isShown || tab != Tab.MAP) return
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
            releaseMap("activity_paused")
            removeCallbacks(destinationTicker)
        } else post {
            refreshIfDestinationChanged()
            recoverVisibleMapSurface()
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
        releaseMap(reason)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            refreshIfDestinationChanged()
            recoverVisibleMapSurface()
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
        // The map may reach the content edges; the lower overlay owns its padding.
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
        releaseMap("detached")
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            renderPending=result!=null
            pendingGeneration=requestGeneration
            releaseMap("hidden")
            removeCallbacks(destinationTicker)
        } else post {
            refreshIfDestinationChanged()
            recoverVisibleMapSurface()
            scheduleDestinationTicker()
            renderPendingIfReady()
        }
    }

    /** Entrada preferencial quando aberta durante uma corrida real. */
    fun openDestination(value: RadarDestinationSpecV1, force: Boolean = false, opportunityId:String? = null) {
        if (spec?.localOfferId != value.localOfferId) tab = Tab.MAP
        val generation = ++requestGeneration
        // A manual refresh of an identical destination must not rebuild MapLibre.
        // Changing the ride/destination or leaving DEMO still releases the old map.
        val reuseVisibleMap = !demoMode && spec == value && tab == Tab.MAP && map != null
        lastMapFocus=value.lat to value.lng
        leaveDemo()
        if (!reuseVisibleMap) releaseMap("destination_changed")
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
        tab = Tab.MAP
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
        if (tab != Tab.MAP) releaseMap("tab_hidden")
        demoMode = false
        spec = null
        result = null
        errorMessage = null
        selectedId = null
        stopArrivalTicker()
        body.removeAllViews()
        body.addView(tabRow())
        val active = RadarDestinationContextV1.current(context)
        if (tab == Tab.MAP) {
            val position = RadarVisibleLocationV1.latest(context)
            if (position != null) {
                appendMapStage(position.lat, position.lng,
                    listOf(RadarVisibleLocationV1.marker(position)))
            } else {
                val previous=lastMapFocus
                if(previous!=null) appendMapStage(previous.first,previous.second,emptyList())
                else {
                    releaseMap("location_unavailable")
                    appendUnavailableMap()
                }
            }
        }
        body.addView(
            SrUi023.card(context, 12, 16).apply {
                when (tab) {
                    Tab.MAP -> {
                        addView(SrUi023.title(context, "Mapa", 14f))
                        addView(SrUi023.body(context,
                            if (RadarVisibleLocationV1.latest(context)!=null)
                                "Mapa da região com sua posição aproximada."
                            else if(lastMapFocus!=null)
                                "Última região consultada. Localização atual indisponível."
                            else "Para visualizar sua região, permita acesso à localização.",10f))
                        addView(UiKit.secondaryButton(context,"Atualizar localização") { renderIdle() })
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
        if (tab == Tab.MAP) {
            val position = RadarVisibleLocationV1.latest(context)
            appendMapStage(value.lat,value.lng,
                position?.let { listOf(RadarVisibleLocationV1.marker(it)) }.orEmpty())
        }
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
        map?.let(::detach)
        detach(cards)
        detach(detail)
        body.addView(tabRow())

        val screen = RadarContextualPresenterV1.map(value)
        if (tab == Tab.MAP) {
            val position=RadarVisibleLocationV1.latest(context)
            // Keep the established destination/POI camera framing; a driver far
            // away would otherwise zoom the whole map out beyond usefulness.
            val nearbyPosition=position?.takeIf {
                val latKm=(it.lat-screen.destinationLat)*111.0
                val lngKm=(it.lng-screen.destinationLng)*111.0 *
                    kotlin.math.cos(Math.toRadians(screen.destinationLat))
                latKm*latKm+lngKm*lngKm <= 15.0*15.0
            }
            val userMarker=nearbyPosition?.let {
                listOf(RadarVisibleLocationV1.marker(it))
            }.orEmpty()
            val mapView = ensureMap()
            mapView.render(
                RadarMiniMapViewV1.State(
                    centerLat = screen.destinationLat,
                    centerLng = screen.destinationLng,
                    radiusKm = 4.0,
                    markers = value.opportunities.take(if(userMarker.isEmpty()) 12 else 11).map {
                        RadarMiniMapViewV1.Marker(
                            it.id, it.lat, it.lng, it.title, it.poiType, it.potential,
                        )
                    } + userMarker,
                    selectedId = selectedId,
                ),
            )
            val stage = FrameLayout(context)
            stage.addView(mapView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 490),
            ))
            val summary = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = UiKit.rounded(context, SrUi023.palette(context).surface, 12,
                    SrUi023.palette(context).outline, 1)
                setPadding(UiKit.dp(context, 10), UiKit.dp(context, 8),
                    UiKit.dp(context, 10), UiKit.dp(context, 8))
            }
            val destination = TextView(context).apply {
                text = RadarCompactOverlayV1.address(spec?.label ?: value.destinationLabel)
                textSize = 11.5f
                setTextColor(SrUi023.palette(context).navy)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
            summary.addView(destination)
            val etaLabel = SrUi023.body(context,
                arrivalWithAssistantStatus(value.destinationEta, System.currentTimeMillis()), 10f)
            summary.addView(etaLabel)
            arrivalText = etaLabel
            arrivalEta = value.destinationEta
            if (surfaceResumed && isShown) postDelayed(arrivalTicker, 30_000L)
            RadarCompactOverlayV1.probability(
                value.baseline.probabilityPct, value.baseline.samples,
            )?.let { summary.addView(SrUi023.title(context, it, 11f)) }
            stage.addView(summary, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply {
                setMargins(UiKit.dp(context, 10), 0, UiKit.dp(context, 10), UiKit.dp(context, 12))
            })
            if(nearbyPosition!=null) {
                stage.addView(userMarkerLegend(),FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START).apply {
                    leftMargin=UiKit.dp(context,10);topMargin=UiKit.dp(context,12)
                })
            } else if(position!=null) {
                stage.addView(TextView(context).apply {
                    text="Sua posição fica fora deste recorte"
                    textSize=10f
                    setTextColor(SrUi023.palette(context).navy)
                    setPadding(UiKit.dp(context,9),UiKit.dp(context,6),
                        UiKit.dp(context,9),UiKit.dp(context,6))
                    background=SrUi023.rounded(SrUi023.palette(context).surface,10,
                        SrUi023.palette(context).outline,1,context)
                },FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START).apply {
                    leftMargin=UiKit.dp(context,10);topMargin=UiKit.dp(context,12)
                })
            }
            body.addView(stage, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 490)))
            // Marker selection (or assistant deep link) must expose its selected opportunity.
            value.opportunities.firstOrNull { it.id == selectedId }?.let { selected ->
                body.addView(UiKit.margin(SrUi023.softCard(context, "info", 10).apply {
                    addView(SrUi023.title(context, selected.title, 12f))
                    addView(SrUi023.body(context, selected.reasonHeadline, 10f))
                    addView(UiKit.secondaryButton(context, "Ver detalhes") {
                        tab = Tab.OPPORTUNITIES
                        renderOrDefer()
                    })
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
            Tab.MAP to "Mapa",
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
        errorMessage = message
        if (tab != Tab.MAP) releaseMap("tab_hidden")
        body.removeAllViews()
        body.addView(tabRow())
        spec?.let(::addHeader)
        if (tab == Tab.MAP) {
            spec?.let { current ->
                val position=RadarVisibleLocationV1.latest(context)
                appendMapStage(current.lat,current.lng,
                    position?.let { listOf(RadarVisibleLocationV1.marker(it)) }.orEmpty())
            } ?: RadarVisibleLocationV1.latest(context)?.let {
                appendMapStage(it.lat,it.lng,listOf(RadarVisibleLocationV1.marker(it)))
            } ?: appendUnavailableMap()
        }
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

    /** Draws the same 490dp MapLibre component, even if there is no POI response.
     * Instantiated only while the actual Radar surface is visible.
     */
    private fun appendMapStage(
        centerLat:Double,centerLng:Double,
        markers:List<RadarMiniMapViewV1.Marker>,
    ) {
        if (tab != Tab.MAP || !surfaceResumed || !isShown || !isAttachedToWindow) {
            appendUnavailableMap("Abra o Radar para visualizar o mapa da região.")
            return
        }
        val mapView=ensureMap()
        mapView.render(RadarMiniMapViewV1.State(
            centerLat=centerLat,centerLng=centerLng,radiusKm=4.0,
            markers=markers,selectedId=null,
        ))
        detach(mapView)
        val stage=FrameLayout(context)
        stage.addView(mapView,FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,UiKit.dp(context,490)))
        if (markers.any { it.id == RadarVisibleLocationV1.MARKER_ID }) {
            stage.addView(userMarkerLegend(),FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START).apply {
                leftMargin=UiKit.dp(context,10);topMargin=UiKit.dp(context,12)
            })
        }
        body.addView(stage,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,UiKit.dp(context,490)))
    }

    private fun userMarkerLegend() = TextView(context).apply {
        text="● Sua posição aproximada"
        textSize=10.5f
        setTextColor(SrUi023.palette(context).navy)
        setPadding(UiKit.dp(context,9),UiKit.dp(context,6),
            UiKit.dp(context,9),UiKit.dp(context,6))
        background=SrUi023.rounded(SrUi023.palette(context).surface,10,
            SrUi023.palette(context).outline,1,context)
    }

    private fun appendUnavailableMap(message:String="Localização recente indisponível. Ative a localização e atualize.") {
        body.addView(FrameLayout(context).apply {
            setBackgroundColor(SrUi023.palette(context).surface)
            addView(SrUi023.body(context,message,12f),FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER).apply {
                leftMargin=UiKit.dp(context,24);rightMargin=UiKit.dp(context,24)
            })
        },LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,UiKit.dp(context,490)))
    }

    private fun ensureMap(): RadarMiniMapViewV1 {
        map?.let { return it }
        return RadarMiniMapViewV1(context, demo = demoMode, traceOfferId = if(demoMode) null else spec?.localOfferId).also { created ->
            created.onMarkerSelected = {
                if (it != RadarVisibleLocationV1.MARKER_ID) select(it)
            }
            map = created
        }
    }

    private fun releaseMap(reason:String="replaced") {
        stopArrivalTicker()
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
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault("—")
}
