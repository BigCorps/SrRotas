package com.srrotas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Agora consolidado: uma pesquisa, um controle de jornada e nenhuma mutação
 * visual periódica. 0.28 restaura a pesquisa de região colapsável no próprio
 * componente canônico. Atualizações de estado alteram somente Views existentes.
 *
 * 0.31.1 acrescenta apenas o estado explícito de retomada de captura: a jornada
 * permanece aberta e o usuário pode reautorizar MediaProjection sem encerrá-la.
 */
class NowPanel027037(context: Context) : ScrollView(context) {
    private var mode = "now"
    private var source = if (SettingsRepository(context).load().collectiveStatsOptIn) "collective" else "personal"
    private var profileKey = when (Strategy021Store.load(context).strategyPreset) {
        "popular" -> "popular"
        "comfort" -> "comfort"
        "premium" -> "premium"
        else -> "all"
    }

    private val region: EditText = UiKit.input(context, "Pesquisar região")
    private val results = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val queryStatus = SrUi023.body(context, "", 10.5f)
    private val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val sourceRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val profileRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private var searchExpanded = false
    private val searchBody = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        contentDescription = "sr028_region_search_body"
    }
    private val searchToggle = TextView(context).apply {
        contentDescription = "sr028_region_search_toggle"
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(SrUi023.palette(context).navy)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = SrUi023.dp(context, 46)
        setPadding(SrUi023.dp(context, 4), 0, SrUi023.dp(context, 4), 0)
        setOnClickListener { setSearchExpanded(!searchExpanded) }
    }

    private val startButton = journeyButton("▶  Iniciar", 0xFF0CBF78.toInt()) { (context as? MainActivity)?.toggleJourneyFromNow() }
    private val endButton = journeyButton("■  Encerrar", 0xFFE5484D.toInt()) { (context as? MainActivity)?.toggleJourneyFromNow() }
    private val resumeButton = journeyButton("▶  Retomar captura", 0xFF1479FF.toInt()) {
        CaptureRecoveryActivity0270.open(context)
    }.apply {
        visibility = View.GONE
        contentDescription = "sr0311_resume_capture"
    }
    private val journeyState = SrUi023.title(context, "OK", 12.5f).apply { gravity = Gravity.CENTER }
    private val journeyDetail = SrUi023.body(context, "", 9f).apply { gravity = Gravity.CENTER }
    private val preflight = buildPreflight()

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(SrAppHeader023(context, "Agora", "Inteligência de região e controle da jornada."))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 8), SrUi023.dp(context, 12), SrUi023.dp(context, 28))
        }
        root.addView(body, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        body.addView(buildJourneyCard())
        body.addView(UiKit.margin(buildSearchCard(), top = 10))
        body.addView(UiKit.margin(queryStatus, top = 8))
        body.addView(results)

        renderSearchControls()
        refreshJourneyState()
        refresh()
    }

    /** Usado pelo Assistente Ativo sem reflection nem busca por TextView. */
    fun showMoment() {
        mode = "now"
        region.setText("")
        setSearchExpanded(false)
        renderSearchControls()
        refresh()
    }

    fun refreshJourneyState() {
        val repo = SettingsRepository(context)
        val settings = repo.load()
        val active = repo.currentJourneyId().isNotBlank()
        val m2Only = ReaderLab027036.mode(context) == ReaderLab027036.MODE_M2
        val overlayOk = Settings.canDrawOverlays(context)
        val locationOk = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val projectionActive = repo.isProjectionActive()
        val recoveryNeeded = active && !m2Only && CaptureResilience0311.needsRecovery(context)
        val readerOk = if (m2Only) {
            ReaderLab027036.disclosureAccepted(context) && ReaderLab027036.isAccessibilityEnabled(context)
        } else {
            !active || projectionActive
        }
        val allOk = overlayOk && locationOk && readerOk && settings.ocrEnabled && settings.onboardingCompleted

        startButton.isEnabled = !active
        startButton.alpha = if (active) .35f else 1f
        endButton.isEnabled = active
        endButton.alpha = if (active) 1f else .35f
        resumeButton.visibility = if (recoveryNeeded) View.VISIBLE else View.GONE
        resumeButton.isEnabled = recoveryNeeded

        journeyState.text = when {
            active && allOk -> "● Jornada ativa"
            active -> "⚠ Jornada ativa"
            allOk -> "✓ OK"
            else -> "⚠ Ajustes"
        }
        journeyState.setTextColor(if (allOk) SrUi023.palette(context).teal else SrUi023.palette(context).orange)
        journeyDetail.text = when {
            recoveryNeeded -> "Captura interrompida · jornada preservada"
            active -> ReaderLab027036.modeLabel(ReaderLab027036.mode(context))
            else -> JourneyPreflight027037.summary(context)
        }
        preflight.visibility = if (active) View.GONE else View.VISIBLE
    }

    fun refresh() {
        queryStatus.text = "Consultando inteligência regional…"
        RegionalClient.fetch(context, mode, source, region.text?.toString().orEmpty(), profileKey) { result ->
            result.onSuccess(::render).onFailure { error ->
                queryStatus.text = "Não foi possível consultar: ${error.message ?: "tente novamente"}"
                results.removeAllViews()
            }
        }
    }

    private fun buildJourneyCard(): View = SrUi023.card(context, 14, 12).apply {
        contentDescription = "sr37_journey_control"
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(startButton, LinearLayout.LayoutParams(0, SrUi023.dp(context, 48), .38f))
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(journeyState, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(journeyDetail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .24f).apply {
            marginStart = SrUi023.dp(context, 5)
            marginEnd = SrUi023.dp(context, 5)
        })
        row.addView(endButton, LinearLayout.LayoutParams(0, SrUi023.dp(context, 48), .38f))
        addView(row)
        addView(UiKit.margin(resumeButton, top = 7), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, SrUi023.dp(context, 44)))
        addView(UiKit.margin(preflight, top = 9))
    }

    private fun buildPreflight(): View = SrUi023.softCard(context, "good", 10).apply {
        contentDescription = "sr37_preflight"
        addView(SrUi023.title(context, "Antes de iniciar", 12.5f))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(preflightButton("◉  Odômetro inicial (km)") {
            JourneyPreflight027037.openOdometer(context) { refreshJourneyState() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(preflightButton("⛽  Abastecimento / recarga") {
            JourneyPreflight027037.openEnergy(context) { refreshJourneyState() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 6) })
        addView(UiKit.margin(row, top = 7))
    }

    private fun buildSearchCard(): View = SrUi023.card(context, 14, 14).apply {
        contentDescription = "sr37_region_search"
        addView(searchToggle)
        searchBody.addView(UiKit.margin(region, top = 7))
        searchBody.addView(UiKit.margin(modeRow, top = 7))
        searchBody.addView(UiKit.margin(sourceRow, top = 6))
        searchBody.addView(UiKit.margin(profileRow, top = 6))
        searchBody.addView(UiKit.margin(SrUi023.primaryButton(context, "Consultar", R.drawable.sr23_ic_search) {
            if (region.text?.toString().orEmpty().isNotBlank()) mode = "search"
            renderSearchControls()
            refresh()
        }, top = 7))
        addView(searchBody)
        setSearchExpanded(false)
    }

    private fun setSearchExpanded(expanded: Boolean) {
        searchExpanded = expanded
        searchBody.visibility = if (expanded) View.VISIBLE else View.GONE
        searchToggle.text = if (expanded) "Pesquisar região   ▴" else "Pesquisar região   ▾"
        searchToggle.isSelected = expanded
    }

    private fun renderSearchControls() {
        modeRow.removeAllViews()
        listOf("now" to "Momento", "today" to "Hoje", "week" to "Semana", "search" to "Pesquisa").forEach { (key, label) ->
            modeRow.addView(SrUi023.segment(context, label, mode == key) {
                mode = key
                renderSearchControls()
                refresh()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        sourceRow.removeAllViews()
        listOf("collective" to "Base Coletiva", "personal" to "Base Pessoal").forEachIndexed { index, (key, label) ->
            sourceRow.addView(SrUi023.segment(context, label, source == key) {
                source = key
                renderSearchControls()
                refresh()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = SrUi023.dp(context, 5)
            })
        }

        profileRow.removeAllViews()
        listOf("all" to "Todas", "popular" to "Popular", "comfort" to "Conforto", "premium" to "Premium").forEachIndexed { index, (key, label) ->
            profileRow.addView(SrUi023.segment(context, label, profileKey == key) {
                profileKey = key
                renderSearchControls()
                refresh()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = SrUi023.dp(context, 4)
            })
        }
    }

    private fun render(found: RegionalClient.Result) {
        results.removeAllViews()
        val locked = source == "collective" && (!found.collectiveOptIn || found.resolvedSource == "collective_locked_preview")
        queryStatus.text = when {
            locked -> "Prévia da Base Coletiva · participe para liberar os dados completos."
            found.tips.isEmpty() -> "Dados insuficientes para esta combinação."
            source == "collective" -> "Base Coletiva"
            else -> "Base Pessoal"
        }
        found.tips.take(if (locked) 3 else 12).forEachIndexed { index, tip ->
            results.addView(UiKit.margin(regionCard(tip, locked), top = if (index == 0) 8 else 7))
        }
        if (found.tips.isNotEmpty() && !locked && found.note.isNotBlank()) {
            results.addView(UiKit.margin(SrUi023.body(context, found.note, 9.5f), top = 8))
        }
    }

    private fun regionCard(t: RegionalClient.Tip, locked: Boolean): View = SrUi023.card(context, 14, 16).apply {
        val p = SrUi023.palette(context)
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(SrUi023.iconBox(context, R.drawable.sr23_ic_location, if (source == "collective") p.purple else p.teal, 42))
        head.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(SrUi023.title(context, t.region, 15.5f))
            addView(SrUi023.body(context, if (locked) "${profileName(t.profile)} · prévia" else "${profileName(t.profile)} · ${t.samples} amostras", 9.5f))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 8) })
        addView(head)
        if (!locked && t.wording.isNotBlank()) addView(UiKit.margin(SrUi023.body(context, t.wording, 10f), top = 6))

        val metrics = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val perMin = t.medianPerHour?.div(60.0)
        listOf(
            Triple("R$/km", if (locked) "•••" else t.medianPerKm?.let(::fmt) ?: "—", p.teal),
            Triple("R$/min", if (locked) "•••" else perMin?.let(::fmt) ?: "—", p.blue),
            Triple("Distância", if (locked) "•••" else t.distanceKm?.let { "${fmt1(it)} km" } ?: "—", p.purple),
        ).forEachIndexed { index, (label, value, tone) ->
            metrics.addView(metric(label, value, tone), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = SrUi023.dp(context, 5)
            })
        }
        addView(UiKit.margin(metrics, top = 8))
    }

    private fun metric(label: String, value: String, tone: Int) = SrUi023.softCard(context, "neutral", 9).apply {
        addView(SrUi023.body(context, label, 8.5f).apply { setTextColor(tone); gravity = Gravity.CENTER })
        addView(SrUi023.title(context, value, 14.5f).apply { gravity = Gravity.CENTER })
    }

    private fun journeyButton(label: String, tone: Int, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 10.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = SrUi023.rounded(tone, 999, tone, 1, context)
        setOnClickListener { action() }
    }

    private fun preflightButton(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 9.5f
        gravity = Gravity.CENTER
        minHeight = SrUi023.dp(context, 46)
        setTextColor(SrUi023.palette(context).navy)
        background = SrUi023.rounded(SrUi023.palette(context).surface, 10, SrUi023.palette(context).outline, 1, context)
        setOnClickListener { action() }
    }

    private fun fmt(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
    private fun fmt1(v: Double) = String.format(Locale("pt", "BR"), "%.1f", v)
    private fun profileName(v: String) = when (v) {
        "popular" -> "Popular"
        "comfort" -> "Conforto"
        "premium" -> "Premium"
        else -> "Todas"
    }
}
