package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Existing authenticated readers only. Injection is for native UI tests, never a production feed. */
internal interface RadarTwoTabsDataV1 {
    fun destination(context: Context): RadarDestinationSpecV1?
    fun location(context: Context): RadarVisibleLocationV1.Position?
    fun destinationReason(context: Context): String
    fun events(context: Context, position: RadarVisibleLocationV1.Position, force: Boolean, done: (Result<EventRadarResult026>) -> Unit)
    fun places(context: Context, spec: RadarDestinationSpecV1, force: Boolean, done: (Result<RadarContextualResultV1>) -> Unit)
}

internal object RadarTwoTabsReadersV1 : RadarTwoTabsDataV1 {
    override fun destination(context: Context) = RadarDestinationContextV1.current(context)
    override fun location(context: Context) = RadarVisibleLocationV1.latest(context)
    override fun destinationReason(context: Context) = RadarRegion109.idleReason(context)
    override fun events(context: Context, position: RadarVisibleLocationV1.Position, force: Boolean, done: (Result<EventRadarResult026>) -> Unit) =
        EventRadarClient026.fetchAt(context, position.lat, position.lng, force = force, callback = done)
    override fun places(context: Context, spec: RadarDestinationSpecV1, force: Boolean, done: (Result<RadarContextualResultV1>) -> Unit) =
        RadarContextualClientV1.fetch(context, spec.lat, spec.lng, spec.eta, spec.label,
            force = force, source = "ui", traceOfferId = spec.localOfferId, callback = done)
}

/** Two visual tabs; no change to runtime/assistant flags, accepted rides, Reader or backend. */
internal class RadarTwoTabsPanelV1(
    context: Context,
    private val data: RadarTwoTabsDataV1 = RadarTwoTabsReadersV1,
    private val now: () -> Long = System::currentTimeMillis,
) : LinearLayout(context) {
    private var tab = RadarTwoTabsRulesV1.Tab.DESTINATION
    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val scroll = ScrollView(context).apply { isFillViewport = true }
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private var spec: RadarDestinationSpecV1? = null
    private var result: RadarContextualResultV1? = null
    private var events: EventRadarResult026? = null
    private var reference: RadarVisibleLocationV1.Position? = null
    private var destinationError: String? = null
    private var eventsError: String? = null
    private var destinationLoading = false
    private var eventsLoading = false
    private var destinationGeneration = 0L
    private var eventGeneration = 0L
    private var resumed = false
    private var demoMode = false
    private var selectedId: String? = null
    private var renderedGates: List<Boolean>? = null
    private fun temporalGates() = listOf(
        RadarTwoTabsRulesV1.validDestination(spec, now()),
        result?.let { freshDestination(it) } == true,
        events?.let { RadarTwoTabsRulesV1.fresh(it.receivedAtMs, now(), RadarTwoTabsRulesV1.EVENT_TTL_MS) } == true,
    )
    private val tick = object : Runnable {
        override fun run() {
            if (!active()) return
            if (!refreshIfDestinationChanged() && temporalGates() != renderedGates) render()
            scheduleTick()
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(SrUi023.palette(context).background)
        addView(SrAppHeader023(context, "Radar", ""))
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.setPadding(dp(10), dp(10), dp(10), dp(20))
        scroll.addView(body, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        render()
    }

    fun isDemo(): Boolean = demoMode
    private fun active() = resumed && isShown && isAttachedToWindow
    fun setSurfaceResumed(value: Boolean) {
        resumed = value
        if (value) post { if (active()) { ensureCurrent(); scheduleTick() } }
        else suspendRequests()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { if (active()) { ensureCurrent(); scheduleTick() } }
    }
    override fun onDetachedFromWindow() { suspendRequests(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Android invokes this before field initialization from the View constructor.
        if (visibility != View.VISIBLE) { if (childCount > 0) suspendRequests() }
        else post { if (active()) { ensureCurrent(); scheduleTick() } }
    }
    private fun scheduleTick() { removeCallbacks(tick); if (active()) postDelayed(tick, 20_000L) }
    private fun suspendRequests() {
        removeCallbacks(tick)
        destinationGeneration++; eventGeneration++
        destinationLoading = false; eventsLoading = false
    }
    fun cancelSurface(@Suppress("UNUSED_PARAMETER") reason: String) {
        suspendRequests(); demoMode = false; spec = null; result = null; events = null; reference = null
        selectedId = null; destinationError = null; eventsError = null
        render()
    }
    fun renderPendingIfReady() { if (active()) { refreshIfDestinationChanged(); render() } }
    fun refresh() {
        if (demoMode) { demoMode = false; spec = null; result = null; selectedId = null }
        ensureCurrent(force = true)
    }
    fun refreshIfDestinationChanged(): Boolean {
        if (!active() || demoMode) return false
        val current = data.destination(context)
        val position = data.location(context)
        val changed = current != spec || (tab == RadarTwoTabsRulesV1.Tab.EVENTS && !samePosition(position, reference))
        if (changed) { ensureCurrent(); return true }
        return false
    }
    fun openDestination(value: RadarDestinationSpecV1, force: Boolean = false, opportunityId: String? = null) {
        demoMode = false
        tab = RadarTwoTabsRulesV1.Tab.DESTINATION
        selectedId = opportunityId
        // A deep link or a delayed callback is never evidence of an accepted ride.
        val current = data.destination(context)
        if (current != value) { destinationGeneration++; spec = current; result = null; destinationLoading = false; render(); return }
        ensureCurrent(force)
    }
    fun openDemo() {
        suspendRequests(); demoMode = true; tab = RadarTwoTabsRulesV1.Tab.DESTINATION
        val preview = RadarContextualDemoV1.result()
        spec = RadarDestinationSpecV1(preview.destinationLat, preview.destinationLng, preview.destinationEta,
            preview.destinationLabel, "demo-offer")
        result = preview; selectedId = null
        render()
    }
    fun focusOpportunity(opportunityId: String?) {
        if (!demoMode && refreshIfDestinationChanged()) return
        selectedId = opportunityId?.takeIf { id -> result?.opportunities?.any { it.id == id } == true }
        render()
    }

    private fun ensureCurrent(force: Boolean = false) {
        if (demoMode) { render(); return }
        val current = data.destination(context)
        if (current != spec) {
            destinationGeneration++; destinationLoading = false; result = null; destinationError = null; selectedId = null; spec = current
        }
        if (!active()) { render(); return }
        if (tab == RadarTwoTabsRulesV1.Tab.DESTINATION) {
            if (!RadarTwoTabsRulesV1.validDestination(current, now())) { render(); return }
            if (destinationLoading && !force) return
            val previous = result
            if (!force && previous != null && freshDestination(previous)) { render(); return }
            val requested = requireNotNull(current)
            val generation = ++destinationGeneration
            destinationLoading = true; destinationError = null
            render()
            data.places(context, requested, force) { response ->
                if (!active() || generation != destinationGeneration || demoMode) return@places
                if (data.destination(context) != requested) { ensureCurrent(); return@places }
                destinationLoading = false
                response.onSuccess {
                    if (RadarTwoTabsRulesV1.matchesDestination(it, requested)) {
                        result = it
                        RadarContextualDiagnosticV1.acceptResult(it, "ui")
                    } else {
                        result = null
                        destinationError = "A resposta não corresponde ao destino e à ETA atuais. Atualize para consultar novamente."
                    }
                }.onFailure { destinationError = RadarRegion109.failure(it.message); result = null }
                render()
            }
        } else {
            val position = data.location(context)
            if (!samePosition(position, reference)) {
                eventGeneration++; events = null; eventsError = null; eventsLoading = false; reference = position
            }
            if (position == null) { render(); return }
            if (eventsLoading && !force) return
            if (!force && events?.let { RadarTwoTabsRulesV1.fresh(it.receivedAtMs, now(), RadarTwoTabsRulesV1.EVENT_TTL_MS) } == true) { render(); return }
            val generation = ++eventGeneration
            eventsLoading = true; eventsError = null
            render()
            data.events(context, position, force) { response ->
                if (!active() || generation != eventGeneration || demoMode) return@events
                if (!samePosition(data.location(context), position)) { ensureCurrent(); return@events }
                eventsLoading = false
                response.onSuccess { events = it }.onFailure { eventsError = RadarRegion109.failure(it.message); events = null }
                render()
            }
        }
    }
    private fun freshDestination(value: RadarContextualResultV1) = RadarTwoTabsRulesV1.fresh(
        RadarTwoTabsRulesV1.instant(value.generatedAt) ?: 0, now(), RadarTwoTabsRulesV1.DESTINATION_TTL_MS)
    private fun samePosition(a: RadarVisibleLocationV1.Position?, b: RadarVisibleLocationV1.Position?) =
        if (a == null || b == null) a == b else a.lat == b.lat && a.lng == b.lng

    private fun render() {
        renderedGates = temporalGates()
        renderTabs()
        body.removeAllViews()
        if (demoMode) status("DEMO · dados fictícios, somente prévia local")
        if (tab == RadarTwoTabsRulesV1.Tab.DESTINATION) renderDestination() else renderEvents()
    }
    private fun renderTabs() {
        tabs.removeAllViews()
        val p = SrUi023.palette(context)
        tabs.setPadding(dp(5), dp(4), dp(5), dp(4))
        tabs.background = SrUi023.rounded(p.surface, 22, p.blue, 1, context)
        for (value in RadarTwoTabsRulesV1.Tab.values()) tabs.addView(TextView(context).apply {
            text = value.label; textSize = 13f; gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8)); minHeight = dp(48)
            isSelected = tab == value; isClickable = true; isFocusable = true
            setTextColor(if (isSelected) Color.WHITE else if (Appearance021.isDark(context)) p.blueBright else p.navy)
            background = SrUi023.rounded(if (isSelected) p.navyDeep else Color.TRANSPARENT, 19, Color.TRANSPARENT, 0, context)
            contentDescription = value.label
            if (android.os.Build.VERSION.SDK_INT >= 30)
                stateDescription = if (isSelected) "Aba selecionada" else "Aba não selecionada"
            setOnClickListener { if (tab != value) { tab = value; selectedId = null; ensureCurrent(); scroll.scrollTo(0, 0) } }
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun renderDestination() {
        val current = if (demoMode) spec else data.destination(context)
        if (current == null) {
            status("Sem destino de corrida ativa", data.destinationReason(context))
            actions(); return
        }
        status(current.label?.takeIf(String::isNotBlank) ?: "Destino da corrida ativa",
            "Referência: destino confirmado · Chegada ${RadarTwoTabsRulesV1.time(current.eta)}")
        if (!RadarTwoTabsRulesV1.validDestination(current, now())) {
            status("ETA vencida ou destino indisponível", "Atualize o destino na corrida para consultar com uma ETA real. Nenhuma corrida foi encerrada.")
            actions(); return
        }
        val value = result
        when {
            destinationLoading -> status("Carregando locais próximos do destino…")
            destinationError != null -> status("Radar indisponível", destinationError)
            current != spec -> status("Destino alterado", "Atualize para consultar a corrida atual.")
            value == null -> status("Locais ainda não consultados", "Atualize para consultar os locais verificados.")
            !demoMode && !freshDestination(value) -> status("Dados expirados", "Atualize para conferir os locais próximos do destino.")
            else -> {
                val items = RadarTwoTabsRulesV1.destination(value, current, now())
                if (items.isEmpty()) status("Sem locais verificados próximos do destino", "Nenhum local elegível no raio consultado agora.")
                else showCards(items)
            }
        }
        actions()
    }
    private fun renderEvents() {
        if (demoMode) { status("Eventos em geral", "Saia da prévia para consultar eventos reais da sua localização."); actions(); return }
        val position = data.location(context)
        status("Eventos em geral", "Referência: sua localização recente · até 15 km · próximas 8 h")
        if (position == null) {
            status("Sem localização recente", "Ative a localização e atualize. Nenhuma distância é estimada sem uma referência válida.")
            actions(); return
        }
        val value = events
        when {
            !samePosition(position, reference) -> status("Localização alterada", "Atualize para recalcular as distâncias da sua localização atual.")
            eventsLoading -> status("Carregando eventos…")
            eventsError != null -> status("Radar indisponível", eventsError)
            value == null -> status("Eventos ainda não consultados", "Atualize para consultar eventos verificados.")
            !RadarTwoTabsRulesV1.fresh(value.receivedAtMs, now(), RadarTwoTabsRulesV1.EVENT_TTL_MS) ->
                status("Dados expirados", "Atualize para consultar eventos futuros da sua localização atual.")
            else -> {
                val items = RadarTwoTabsRulesV1.events(value, now())
                if (items.isEmpty()) status("Sem eventos futuros verificados", RadarRegion109.emptyEvents(value.sourceStatus))
                else showCards(items)
            }
        }
        actions()
    }
    private fun showCards(items: List<RadarTwoTabsRulesV1.Card>) {
        body.addView(RadarTwoTabsCardsV1(context).apply {
            submit(items, selectedId, demoMode) { id -> selectedId = if (selectedId == id) null else id; render() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private fun status(title: String, message: String? = null) {
        body.addView(SrUi023.card(context, 12, 14).apply {
            addView(SrUi023.title(context, title, 14f))
            message?.let { addView(UiKit.margin(SrUi023.body(context, it, 12f), top = 5)) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }
    private fun actions() {
        body.addView(UiKit.secondaryButton(context, if (destinationLoading || eventsLoading) "Atualizando…" else "Atualizar") { refresh() }.apply {
            isEnabled = !(destinationLoading || eventsLoading)
        })
        body.addView(TextView(context).apply {
            text = "Meus lugares marcados"; textSize = 12f; setTextColor(SrUi023.palette(context).blue)
            gravity = Gravity.CENTER; minHeight = dp(48); isClickable = true; isFocusable = true
            setOnClickListener { RadarPlacesDialog109.show(context) }
        })
    }
    private fun dp(value: Int) = UiKit.dp(context, value)
}
