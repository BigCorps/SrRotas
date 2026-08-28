package com.srrotas.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/** Nova apresentação da tela Agora. Usa o RegionalClient existente sem mudar dados/cálculos. */
class NowPanel023(context: Context) : ScrollView(context) {
    private var mode = "now"
    private var source = "personal"
    private val root = LinearLayout(context)
    private val headerHost = LinearLayout(context)
    private val modeBox = LinearLayout(context)
    private val sourceBox = LinearLayout(context)
    private val results = LinearLayout(context)
    private val status = SrUi023.body(context, "", 11f)
    private val region = EditText(context).apply { hint = "Bairro ou região"; setSingleLine(true) }
    private val profile = Spinner(context)

    init {
        isFillViewport = true
        setBackgroundColor(SrUi023.palette(context).background)
        root.orientation = LinearLayout.VERTICAL
        addView(root)
        root.addView(headerHost)
        refreshHeader()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 16), SrUi023.dp(context, 12), SrUi023.dp(context, 16), SrUi023.dp(context, 28))
        }
        root.addView(content)

        modeBox.orientation = LinearLayout.HORIZONTAL
        modeBox.background = SrUi023.rounded(SrUi023.palette(context).surface, 13, SrUi023.palette(context).outline, 1, context)
        content.addView(modeBox)
        sourceBox.orientation = LinearLayout.HORIZONTAL
        sourceBox.background = SrUi023.rounded(SrUi023.palette(context).surface, 13, SrUi023.palette(context).outline, 1, context)
        content.addView(sourceBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
        renderSegments()

        profile.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Todas", "Popular", "Conforto", "Premium"))
        profile.setSelection(when (Strategy021Store.load(context).strategyPreset) { "popular" -> 1; "comfort" -> 2; "premium" -> 3; else -> 0 })

        val search = SrUi023.card(context, 14, 17).apply {
            addView(SrUi023.title(context, "Pesquisa", 14f))
            addView(region.apply {
                setTextColor(SrUi023.palette(context).ink)
                setHintTextColor(SrUi023.palette(context).muted)
                background = SrUi023.rounded(SrUi023.palette(context).surfaceMuted, 12, SrUi023.palette(context).outline, 1, context)
                setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 10), SrUi023.dp(context, 12), SrUi023.dp(context, 10))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
            addView(profile, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 7) })
            addView(SrUi023.primaryButton(context, "⌕  Consultar") { refresh() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
        }
        content.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 11) })
        content.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 10) })
        results.orientation = LinearLayout.VERTICAL
        content.addView(results)
        refresh()
    }

    fun refreshJourneyState() {
        refreshHeader()
    }

    fun refresh() {
        refreshHeader()
        status.text = "Consultando inteligência regional…"
        val pk = when (profile.selectedItemPosition) { 1 -> "popular"; 2 -> "comfort"; 3 -> "premium"; else -> "all" }
        RegionalClient.fetch(context, mode, source, region.text.toString(), pk) {
            it.onSuccess(::render).onFailure { e ->
                status.text = "Não foi possível consultar: ${e.message}"
                results.removeAllViews()
            }
        }
    }

    private fun refreshHeader() {
        headerHost.removeAllViews()
        val repo = SettingsRepository(context)
        val s = repo.load()
        val active = repo.currentJourneyId().isNotBlank()
        val overlayOk = Settings.canDrawOverlays(context)
        val locationOk = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val captureOk = !active || repo.isProjectionActive()
        val allOk = overlayOk && locationOk && captureOk && s.ocrEnabled && s.onboardingCompleted
        val pending = listOf(overlayOk, locationOk, captureOk, s.ocrEnabled, s.onboardingCompleted).count { !it }

        val header = SrUi023.curvedHeader(context).apply {
            val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(SrUi023.title(context, if (allOk) "Tudo pronto" else "Ação necessária", 22f, true))
                addView(SrUi023.body(context, buildString {
                    append("HUD ${if (overlayOk) "autorizado" else "pendente"}  ·  ")
                    append("Localização ${if (locationOk) "autorizada" else "pendente"}  ·  ")
                    append("Captura/OCR ${if (captureOk && s.ocrEnabled) "ativos" else "pendentes"}")
                }, 10.5f, true))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(SrUi023.pill(context, if (allOk) "OK" else "$pending PEND.", if (allOk) "good" else "warn"))
            addView(top)
            addView(TextView(context).apply {
                text = if (active) "☑  Encerrar jornada" else "▶  Iniciar jornada"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, SrUi023.dp(context, 12), 0, SrUi023.dp(context, 12))
                background = SrUi023.rounded(android.graphics.Color.TRANSPARENT, 13, 0xFF3E6281.toInt(), 1, context)
                setOnClickListener { (context as? MainActivity)?.toggleJourneyFromNow() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
        }
        headerHost.addView(header)
    }

    private fun renderSegments() {
        modeBox.removeAllViews()
        listOf("now" to "Momento", "today" to "Hoje", "week" to "Semana", "search" to "Pesquisa").forEach { (key, label) ->
            modeBox.addView(SrUi023.segment(context, label, mode == key) { mode = key; renderSegments(); refresh() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        sourceBox.removeAllViews()
        listOf("personal" to "Base pessoal", "collective" to "Base coletiva").forEach { (key, label) ->
            sourceBox.addView(SrUi023.segment(context, label, source == key) { source = key; renderSegments(); refresh() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun render(found: RegionalClient.Result) {
        results.removeAllViews()
        status.text = when {
            source == "collective" && !found.collectiveOptIn -> "Base coletiva: participe para acessar a comunidade. A Base Sr. Rotas continua disponível."
            found.tips.isEmpty() -> "Dados insuficientes para esta combinação."
            else -> "▣  ${found.tips.size} regiões em destaque  ·  ${sourceLabel(found.preferred)}"
        }
        found.tips.take(12).forEach { tip ->
            results.addView(regionCard(tip), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 10) })
        }
        if (found.tips.isNotEmpty()) results.addView(SrUi023.softCard(context, "neutral", 12).apply {
            addView(SrUi023.body(context, "ⓘ  ${found.note}", 10.5f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 10) })
    }

    private fun regionCard(t: RegionalClient.Tip): View = SrUi023.card(context, 14, 18).apply {
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(context).apply { text = "📍"; textSize = 18f })
        top.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(SrUi023.title(context, t.region, 16f))
            addView(SrUi023.body(context, "${profileName(t.profile)} · ${t.samples} amostras", 9.5f))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 7) })
        t.distanceKm?.let { top.addView(SrUi023.pill(context, "${fmt(it)} km", "good")) }
        addView(top)
        addView(SrUi023.body(context, t.wording, 10.5f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
        val metrics = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        metrics.addView(metric("R$/km", t.medianPerKm?.let(::fmt) ?: "—", range(t.p25PerKm, t.p75PerKm), "good"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        metrics.addView(metric("R$/h", t.medianPerHour?.let(::fmt) ?: "—", range(t.p25PerHour, t.p75PerHour), "warn"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 6) })
        metrics.addView(metric("Busca", t.pickupMinutes?.let { "${fmt(it)} min" } ?: "—", t.pickupKm?.let { "${fmt(it)} km" } ?: "histórico", "bad"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 6) })
        addView(metrics, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 9) })
        addView(SrUi023.body(context, "▣  Base Sr. Rotas: ${t.samples} rotas  ·  ◷  ${TimeWindow0212.label(t.hourBucket)}", 9f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
    }

    private fun metric(label: String, value: String, help: String, tone: String) = SrUi023.softCard(context, tone, 9).apply {
        addView(SrUi023.body(context, label, 9f))
        addView(SrUi023.title(context, value, 17f))
        addView(SrUi023.body(context, help, 8f))
    }
    private fun range(a: Double?, b: Double?) = if (a != null && b != null) "${fmt(a)}–${fmt(b)}" else "mediana"
    private fun fmt(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
    private fun profileName(v: String) = when (v) { "popular" -> "Popular"; "comfort" -> "Conforto"; "premium" -> "Premium"; else -> "Todas" }
    private fun sourceLabel(v: String) = when (v) { "personal" -> "sua base"; "collective" -> "comunidade"; else -> "Base Sr. Rotas" }
}
