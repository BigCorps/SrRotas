package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant

/**
 * vc114 (issue #11) — tela operacional do Radar: cabeçalho canônico, seletor de DUAS abas e cards
 * compactos (duas colunas quando cabem). Rodapé e Diagnóstico continuam no shell. Sem mapa interno:
 * "Ver no Maps" abre o Google Maps externo por toque explícito.
 */
class RadarPanelV114(context: Context) : ScrollView(context) {
    enum class Tab { DESTINATION, EVENTS }

    var tab = Tab.DESTINATION; private set
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var generation = 0L
    private var lastWidthPx = 0

    // Estado da aba de destino.
    private var spec: RadarDestinationSpecV1? = null
    private var destResult: RadarContextualResultV1? = null
    private var destError: String? = null
    private var destLoading = false
    private var destFetchedAt = 0L

    // Estado da aba de eventos.
    private var reference = RadarV114.Reference.NONE
    private var events: EventRadarResult026? = null
    private var eventsError: String? = null
    private var eventsLoading = false
    private var eventsFetchedAt = 0L

    /** Testes/QA: renderiza dados fornecidos sem rede. */
    internal var offline = false

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(SrAppHeader023(context, "Radar", ""))
        body.setPadding(dp(12), dp(6), dp(12), dp(18))
        root.addView(body, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        contentDescription = "sr114_radar_two_tabs"
        render()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != lastWidthPx) { lastWidthPx = w; post { render() } }
    }

    /** Atualiza a aba visível (chamado ao abrir o Radar e no "Atualizar"). */
    fun refresh(force: Boolean = false) {
        if (offline) { render(); return }
        when (tab) {
            Tab.DESTINATION -> loadDestination(force)
            Tab.EVENTS -> loadEvents(force)
        }
    }

    fun selectTab(next: Tab) {
        if (tab == next) return
        tab = next
        render()
        refresh(false)
    }

    // ------------------------------------------------------------------ dados
    private fun loadDestination(force: Boolean) {
        val current = runCatching { RadarDestinationContextV1.current(context) }.getOrNull()
        val changed = current != spec
        spec = current
        if (current == null) { destResult = null; destError = null; destLoading = false; render(); return }
        val stale = System.currentTimeMillis() - destFetchedAt > STALE_MS
        if (!force && !changed && destResult != null && !stale) { render(); return }
        if (changed) destResult = null
        val g = ++generation
        destLoading = true; destError = null; render()
        RadarContextualClientV1.fetch(context, current.lat, current.lng, current.eta, current.label, force = force, source = "radar_v114", traceOfferId = current.localOfferId) { r ->
            if (g != generation || spec != current) return@fetch
            destLoading = false
            r.onSuccess { destResult = it; destFetchedAt = System.currentTimeMillis() }.onFailure { destError = RadarRegion109.failure(it.message) }
            render()
        }
    }

    private fun loadEvents(force: Boolean) {
        val position = RadarVisibleLocationV1.latest(context)
        val dest = runCatching { RadarDestinationContextV1.current(context) }.getOrNull()
        val nextRef = RadarV114.reference(position != null, dest != null)
        val refChanged = nextRef != reference
        reference = nextRef
        val point = when (nextRef) {
            RadarV114.Reference.YOU -> position!!.lat to position.lng
            RadarV114.Reference.DESTINATION -> dest!!.lat to dest.lng
            RadarV114.Reference.NONE -> null
        }
        if (point == null) { events = null; eventsError = null; eventsLoading = false; render(); return }
        val stale = System.currentTimeMillis() - eventsFetchedAt > STALE_MS
        if (!force && !refChanged && events != null && !stale) { render(); return }
        val g = ++generation
        eventsLoading = true; eventsError = null; render()
        EventRadarClient026.fetchAt(context, point.first, point.second, force = force) { r ->
            if (g != generation) return@fetchAt
            eventsLoading = false
            r.onSuccess { events = it; eventsFetchedAt = System.currentTimeMillis() }.onFailure { eventsError = RadarRegion109.failure(it.message) }
            render()
        }
    }

    /** QA/testes: injeta o estado sem rede. */
    internal fun showDestinationForTest(value: RadarDestinationSpecV1?, result: RadarContextualResultV1?, error: String? = null, loading: Boolean = false) {
        offline = true; tab = Tab.DESTINATION; spec = value; destResult = result; destError = error; destLoading = loading
        destFetchedAt = System.currentTimeMillis(); render()
    }

    internal fun showEventsForTest(ref: RadarV114.Reference, result: EventRadarResult026?, error: String? = null, loading: Boolean = false) {
        offline = true; tab = Tab.EVENTS; reference = ref; events = result; eventsError = error; eventsLoading = loading
        eventsFetchedAt = System.currentTimeMillis(); render()
    }

    // ------------------------------------------------------------------ renderização
    private fun render() {
        body.removeAllViews()
        body.addView(tabSelector())
        when (tab) {
            Tab.DESTINATION -> renderDestination()
            Tab.EVENTS -> renderEvents()
        }
    }

    private fun renderDestination() {
        val value = spec
        if (value == null) {
            val doingRide = runCatching { JourneyCoordinator.snapshot(context).isDoingRide }.getOrDefault(false)
            // Com corrida marcada mas destino incompleto, o motivo exato (sem endereço, sem geocodificação, sem ETA).
            body.addView(contextCard(if (doingRide) "Destino indisponível" else "Sem corrida em andamento",
                if (doingRide) RadarRegion109.idleReason(context)
                else "Nenhuma corrida confirmada agora. Com uma corrida em andamento e destino lido, as oportunidades perto do destino aparecem aqui. Enquanto isso, veja Eventos em geral.", null))
            return
        }
        val eta = RadarV114.hm(value.eta)
        val etaValid = DestinationRadarFreshnessV1.blockReason(value.eta, System.currentTimeMillis()) == null && eta != null
        val minutes = runCatching { ((Instant.parse(value.eta).toEpochMilli() - System.currentTimeMillis()) / 60_000L) }.getOrNull()
        body.addView(contextCard(
            value.label?.takeIf(String::isNotBlank) ?: "Destino da corrida",
            if (etaValid) "Chegada estimada: $eta${minutes?.takeIf { it >= 0 }?.let { " ($it min)" } ?: ""}"
            else "Previsão de chegada indisponível ou vencida.",
            "Corrida em andamento", eyebrow = "Destino da corrida (lido da oferta)",
        ))
        body.addView(sectionTitle("Oportunidades próximas do destino", "Locais verificados perto do destino, mais próximos primeiro."))
        val result = destResult
        when {
            destLoading && result == null -> body.addView(stateCard("Consultando informações verificadas para este destino…", false))
            destError != null && result == null -> body.addView(stateCard(destError!!, true))
            result == null -> body.addView(stateCard("Sem dados do destino ainda.", true))
            result.opportunities.isEmpty() -> body.addView(stateCard("Nenhuma oportunidade verificada perto do destino agora. Isso não é um erro: o catálogo de lugares verificados ainda é pequeno.", false))
            else -> {
                val now = System.currentTimeMillis()
                val items = result.opportunities.sortedBy { it.distanceKm }.take(12).map { o ->
                    val ctx = o.context
                    val eligible = ctx != null && RadarContextualEventEligibilityV1.eligible(ctx.type, ctx.name, ctx.egressStartAt, ctx.egressEndAt, ctx.confidence, result.destinationEta, now)
                    CardData(RadarV114.placeCategory(o.poiType, eligible), o.title, RadarV114.distance(o.distanceKm, true),
                        RadarV114.opportunityReason(o, eligible), o.lat, o.lng, o.title)
                }
                body.addView(grid(items))
                if (destLoading) body.addView(stateCard("Atualizando…", false))
            }
        }
        body.addView(refreshLink())
    }

    private fun renderEvents() {
        val subtitle = when (reference) {
            RadarV114.Reference.YOU -> "Eventos verificados, ordenados pela distância de você."
            RadarV114.Reference.DESTINATION -> "Sem localização recente: distâncias medidas a partir do destino da corrida."
            RadarV114.Reference.NONE -> "Sem localização recente nem destino de corrida: não há referência para medir distâncias."
        }
        body.addView(contextCard("Eventos em geral", subtitle, if (reference == RadarV114.Reference.NONE) null else "Por proximidade"))
        if (reference == RadarV114.Reference.NONE) {
            body.addView(stateCard("Ative a localização (ou inicie uma corrida com destino) e toque em Atualizar. O Radar não simula sua posição.", true))
            body.addView(refreshLink()); return
        }
        body.addView(sectionTitle("Eventos próximos", "Toque em um card para abrir o local no Maps."))
        val value = events
        when {
            eventsLoading && value == null -> body.addView(stateCard("Consultando eventos verificados…", false))
            eventsError != null && value == null -> body.addView(stateCard(eventsError!!, true))
            value == null -> body.addView(stateCard("Sem dados de eventos ainda.", true))
            else -> {
                val list = RadarV114.visibleEvents(value.opportunities)
                if (RadarV114.staleSource(value.refreshedAt)) body.addView(stateCard("Fonte de eventos desatualizada (última atualização ${RadarV114.dayTime(value.refreshedAt) ?: "—"}).", true))
                if (list.isEmpty()) body.addView(stateCard(RadarRegion109.emptyEvents(value.sourceStatus) + " Isso não é um erro de conexão.", false))
                else body.addView(grid(list.take(16).map { e ->
                    val label = listOfNotNull(e.venueName, e.address).joinToString(", ").ifBlank { e.name }
                    CardData(RadarV114.eventCategory(e.type), e.name, RadarV114.distance(e.distanceKm, reference == RadarV114.Reference.DESTINATION),
                        RadarV114.dayTime(e.startsAt), null, null, label)
                }))
                if (eventsLoading) body.addView(stateCard("Atualizando…", false))
            }
        }
        body.addView(refreshLink())
    }

    private data class CardData(val category: RadarV114.Category, val name: String, val distance: String?, val detail: String?,
                                val lat: Double?, val lng: Double?, val mapsLabel: String?)

    // ------------------------------------------------------------------ componentes
    private fun tabSelector(): View {
        val p = SrUi023.palette(context)
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = SrUi023.rounded(p.surfaceMuted, 16, p.outline, 1, context)
            contentDescription = "sr114_radar_tabs"
        }
        for ((t, label) in listOf(Tab.DESTINATION to "Próximas do destino", Tab.EVENTS to "Eventos em geral")) {
            val active = t == tab
            bar.addView(TextView(context).apply {
                text = label; gravity = Gravity.CENTER; textSize = if (narrow()) 12.5f else 14f; maxLines = 2
                minHeight = dp(46); setPadding(dp(6), dp(4), dp(6), dp(4))
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) (if (Appearance021.isDark(context)) p.ink else p.navy) else p.muted)
                background = if (active) SrUi023.rounded(p.infoSoft, 13, p.blue, 2, context) else null
                isSelected = active
                contentDescription = "$label${if (active) " (selecionada)" else ""}"
                setOnClickListener { selectTab(t) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (t == Tab.EVENTS) marginStart = dp(4) })
        }
        return bar
    }

    private fun contextCard(title: String, subtitle: String, pill: String?, eyebrow: String? = null): View {
        val p = SrUi023.palette(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = SrUi023.rounded(p.surface, 16, p.outline, 1, context)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            eyebrow?.let { addView(text(it, 12f, p.muted)) }
            // Janela estreita: o selo vai para baixo do título (o destino nunca é cortado).
            if (narrow()) {
                addView(text(title, 16f, p.ink, bold = true))
                pill?.let { addView(statusPill(it), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }) }
            } else {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(text(title, 17f, p.ink, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                pill?.let { row.addView(statusPill(it), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }) }
                addView(row)
            }
            addView(text(subtitle, 13f, p.muted).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun statusPill(label: String): View {
        val p = SrUi023.palette(context)
        return TextView(context).apply {
            text = "● $label"; textSize = 12f; setTextColor(p.tealDark)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = SrUi023.rounded(p.successSoft, 14, null, 0, context)
        }
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        val p = SrUi023.palette(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16); bottomMargin = dp(6) }
            addView(text(title, 18f, p.ink, bold = true))
            addView(text(subtitle, 13f, p.muted))
        }
    }

    private fun stateCard(message: String, warn: Boolean): View {
        val p = SrUi023.palette(context)
        return text(message, 13f, if (warn) p.ink else p.muted).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = SrUi023.rounded(if (warn) p.warningSoft else p.surface, 14, p.outline, 1, context)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
    }

    private fun refreshLink(): View = text("Atualizar", 13f, SrUi023.palette(context).blue).apply {
        gravity = Gravity.CENTER; minHeight = dp(44)
        setOnClickListener { refresh(true) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
    }

    private fun grid(items: List<CardData>): View {
        val widthPx = (if (width > 0) width else resources.displayMetrics.widthPixels) - dp(24)
        val density = resources.displayMetrics.density
        val cols = RadarV114.columns(widthPx / density, resources.configuration.fontScale)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "sr114_grid_cols_$cols"
            items.chunked(cols).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEachIndexed { i, item ->
                    row.addView(card(item), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(10) })
                }
                if (rowItems.size < cols) row.addView(View(context), LinearLayout.LayoutParams(0, 1, (cols - rowItems.size).toFloat()).apply { marginStart = dp(10) })
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            }
        }
    }

    private fun card(item: CardData): View {
        val p = SrUi023.palette(context)
        val c = item.category
        val mapsOk = RadarExternalMapsV1.available(item.lat, item.lng, item.mapsLabel)
        val openMaps = { RadarExternalMapsV1.open(context, item.lat, item.lng, item.mapsLabel); Unit }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(6))
            background = SrUi023.rounded(p.surface, 16, p.outline, 1, context)
            contentDescription = listOfNotNull(c.label, item.name, item.distance, item.detail).joinToString(", ")
            val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(FrameLayout(context).apply {
                background = SrUi023.rounded(c.color, 12, null, 0, context)
                addView(ImageView(context).apply { setImageResource(c.icon.drawable); imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt()) },
                    FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            col.addView(TextView(context).apply {
                text = c.label; textSize = 11.5f; setTextColor(c.color); setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(1), dp(8), dp(1))
                background = SrUi023.rounded((c.color and 0x00FFFFFF) or 0x24000000, 10, null, 0, context)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            col.addView(text(item.name, 14.5f, p.ink, bold = true).apply { setPadding(0, dp(3), 0, 0) })
            item.distance?.let { d ->
                col.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(context).apply { setImageResource(R.drawable.sr114_ic_place); imageTintList = ColorStateList.valueOf(p.muted) }, LinearLayout.LayoutParams(dp(15), dp(15)))
                    addView(text(d, 13f, p.ink).apply { setPadding(dp(3), 0, 0, 0) })
                })
            }
            top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(top)
            item.detail?.let { addView(text(it, 12.5f, p.muted).apply { setPadding(0, dp(6), 0, 0) }) }
            addView(View(context).apply { setBackgroundColor(p.outline) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) })
            if (mapsOk) {
                addView(text("Ver no Maps", 13f, p.blue).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL; minHeight = dp(36)
                    contentDescription = "Ver ${item.name} no Google Maps (abre fora do Sr. Rotas)"
                    setOnClickListener { openMaps() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                isClickable = true
                setOnClickListener { openMaps() }
            } else addView(text("Local sem endereço para o Maps", 11.5f, p.muted).apply { gravity = Gravity.END; minHeight = dp(30) })
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = UiKit.dp(context, v)

    /** Largura útil < 380dp (celular estreito, tela dividida, janela popup). */
    private fun narrow(): Boolean {
        val wPx = if (width > 0) width else resources.displayMetrics.widthPixels
        return wPx / resources.displayMetrics.density < 380f
    }

    private companion object { const val STALE_MS = 10 * 60_000L }
}
