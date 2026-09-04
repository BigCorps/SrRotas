package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Acabamento do relatório de campo 0.26.3 sem duplicar a arquitetura das telas.
 *
 * Mantém os componentes validados e aplica somente apresentação/ordem:
 * marca oficial no Agora, cabeçalhos compactos, Radar ao final, estados da
 * jornada, textos enxutos, screenshots e ícones das Estatísticas.
 */
object FieldValidationPolish0263 {
    private val attached =
        WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = attach(activity)
                override fun onActivityPaused(activity: Activity) = detach(activity)
                override fun onActivityDestroyed(activity: Activity) = detach(activity)
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            },
        )
    }

    private fun attach(activity: Activity) {
        if (attached.containsKey(activity)) return
        val root = activity.window.decorView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching {
                if (activity is MainActivity) decorate(activity, root)
                else removeDescriptions(root)
            }
                .onFailure {
                    LocalLog.append(
                        activity,
                        "Polish 0.26.3 ignorou ajuste visual: ${it.message}",
                    )
                }
        }
        attached[activity] = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.post {
            runCatching {
                if (activity is MainActivity) decorate(activity, root)
                else removeDescriptions(root)
            }
        }
    }

    private fun detach(activity: Activity) {
        val listener = attached.remove(activity) ?: return
        val root = activity.window?.decorView ?: return
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    private fun decorate(activity: MainActivity, root: View) {
        compactHeaders(root)
        decorateNow(activity, root)
        decorateSettings(root)
        removeDescriptions(root)
        centerMetricSubcards(root)
        polishJourneyHistory(root)
        decorateStatistics(root)
    }

    private fun compactHeaders(root: View) {
        findText(root, setOf("Configurações", "Usuário")).forEach { title ->
            ancestor(title) { it is SrSectionHeader024 }?.visibility = View.GONE
        }

        findText(root, setOf("IA do Sr. Rotas", "IA")).forEach { title ->
            val header = ancestor(title) { it is SrSectionHeader024 } as? ViewGroup ?: return@forEach
            title.text = "IA"
            hideHeaderBody(header, title)
        }

        findText(root, setOf("Estatísticas")).forEach { title ->
            val header = ancestor(title) { it is SrSectionHeader024 } as? ViewGroup ?: return@forEach
            hideHeaderBody(header, title)
        }
    }

    private fun hideHeaderBody(header: ViewGroup, keep: TextView) {
        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i)
            if (child !== keep) child.visibility = View.GONE
        }
    }

    private fun decorateNow(activity: MainActivity, root: View) {
        val now = findFirst(root) { it is NowPanel023 } as? NowPanel023 ?: return
        val pageRoot = now.getChildAt(0) as? LinearLayout ?: return

        installOfficialBrand(activity, pageRoot)

        val content = pageRoot.getChildAt(2) as? LinearLayout ?: return
        val radarTitle = findText(now, setOf("Sr. Rotas Radar")).firstOrNull()
        val radarCard = radarTitle?.let {
            ancestor(it) { candidate -> candidate is SrSoftShadowCard023 }
        }

        if (radarCard != null && radarCard.parent !== content) {
            (radarCard.parent as? ViewGroup)?.removeView(radarCard)
            content.addView(
                radarCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(activity, 14)
                    bottomMargin = SrUi023.dp(activity, 8)
                },
            )
        }

        if (radarCard != null &&
            (0 until content.childCount).none {
                content.getChildAt(it).contentDescription == "sr0263_view_radar"
            }
        ) {
            val shortcut = TextView(activity).apply {
                contentDescription = "sr0263_view_radar"
                text = "Visualizar Radar  ↓"
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(SrUi023.palette(activity).blue)
                gravity = Gravity.CENTER
                minHeight = SrUi023.dp(activity, 38)
                background = SrUi023.rounded(
                    Color.TRANSPARENT,
                    12,
                    SrUi023.palette(activity).blue,
                    1,
                    activity,
                )
                setOnClickListener {
                    now.post {
                        now.smoothScrollTo(
                            0,
                            (content.top + radarCard.top - SrUi023.dp(activity, 8))
                                .coerceAtLeast(0),
                        )
                    }
                }
            }
            content.addView(
                shortcut,
                0,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(activity, 8) },
            )
        }

        decorateJourneyButton(activity, now)
        matchReadyIconToBorder(activity, now)
    }

    private fun installOfficialBrand(context: Context, pageRoot: LinearLayout) {
        if (pageRoot.childCount == 0) return
        if (pageRoot.getChildAt(0).contentDescription == "sr0263_official_brand") return

        val old = pageRoot.getChildAt(0)
        if (old is SrSectionHeader024) {
            pageRoot.removeViewAt(0)
        } else {
            // A tela ainda pode estar montando; não substituímos componente desconhecido.
            return
        }

        val holder = LinearLayout(context).apply {
            contentDescription = "sr0263_official_brand"
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 6),
            )
            setBackgroundColor(SrUi023.palette(context).background)
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.sr_rotas_brand_official_0263)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_START
                    contentDescription = "Senhor Rotas"
                },
                LinearLayout.LayoutParams(
                    SrUi023.dp(context, 286),
                    SrUi023.dp(context, 78),
                ),
            )
        }
        pageRoot.addView(
            holder,
            0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun decorateJourneyButton(activity: MainActivity, now: NowPanel023) {
        val repo = SettingsRepository(activity)
        val journeyId = repo.currentJourneyId().takeIf(String::isNotBlank)
        val healthy = CaptureHealthState0263.isHealthy(
            activity,
            journeyId,
            repo.isProjectionActive(),
        )
        val target = findText(
            now,
            setOf("Iniciar jornada", "Encerrar jornada", "Recuperar leitura"),
        ).firstOrNull() ?: return

        val palette = SrTheme024.palette(Appearance021.isDark(activity))
        val (label, color) = when {
            journeyId == null -> "Iniciar jornada" to palette.good
            healthy -> "Encerrar jornada" to palette.bad
            else -> "Recuperar leitura" to palette.warn
        }
        target.text = label
        target.setTextColor(Color.WHITE)
        target.background = SrUi023.rounded(color, 14, color, 1, activity)
        target.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
        target.setOnClickListener {
            if (journeyId != null && !healthy) {
                activity.recoverCurrentJourneyCapture()
            } else {
                activity.toggleJourneyFromNow()
            }
        }
    }

    private fun matchReadyIconToBorder(context: Context, now: NowPanel023) {
        val ready = findText(now, setOf("Tudo pronto")).firstOrNull() ?: return
        val textColumn = ready.parent as? View ?: return
        val row = textColumn.parent as? ViewGroup ?: return
        val iconBox = (0 until row.childCount)
            .map { row.getChildAt(it) }
            .firstOrNull { child ->
                child is LinearLayout &&
                    child !== textColumn &&
                    child.childCount > 0 &&
                    child.getChildAt(0) is ImageView
            } ?: return
        iconBox.background = SrUi023.rounded(
            StatusVisual0242.toneColor(context, "good"),
            14,
            null,
            0,
            context,
        )
    }

    private fun decorateSettings(root: View) {
        val settings = findFirst(root) { it is SettingsHub023 } as? SettingsHub023 ?: return

        val screenshotTitles = findText(
            settings,
            setOf("Screenshots das ofertas", "Screenshots das corridas"),
        )
        screenshotTitles.forEach { title ->
            title.text = "Screenshots das corridas"
            val card = ancestor(title) { it is SrSoftShadowCard023 } ?: return@forEach
            card.setOnClickListener { showScreenshotDialog(settings) }

            val info = title.parent as? LinearLayout
            if (info != null) {
                val repo = SettingsRepository(settings.context)
                val enabled = repo.load().privateScreenshotEnabled
                val state = (0 until info.childCount)
                    .mapNotNull { info.getChildAt(it) as? TextView }
                    .firstOrNull { it.contentDescription == "sr0263_screenshot_state" }
                    ?: TextView(settings.context).also { badge ->
                        badge.contentDescription = "sr0263_screenshot_state"
                        badge.textSize = 10.5f
                        badge.setTypeface(badge.typeface, Typeface.BOLD)
                        info.addView(badge)
                    }
                state.text = if (enabled) "Ativado" else "Desativado"
                state.setTextColor(
                    if (enabled) StatusVisual0242.toneColor(settings.context, "good")
                    else SrUi023.palette(settings.context).muted,
                )
            }
        }

        findText(settings, setOf("Dados e sincronização")).forEach { title ->
            ancestor(title) { it is SrSoftShadowCard023 }?.visibility = View.GONE
        }
    }

    private fun showScreenshotDialog(settings: SettingsHub023) {
        val context = settings.context
        val repo = SettingsRepository(context)
        val enabled = repo.load().privateScreenshotEnabled
        AlertDialog.Builder(context)
            .setTitle("Screenshots das corridas")
            .setSingleChoiceItems(
                arrayOf("Ativado", "Desativado"),
                if (enabled) 0 else 1,
            ) { dialog, which ->
                repo.save(repo.load().copy(privateScreenshotEnabled = which == 0))
                dialog.dismiss()
                settings.refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun removeDescriptions(root: View) {
        val exact = setOf(
            "Ajustes do aparelho, jornada, HUD e aparência.",
            "A Base Coletiva reúne amostras agregadas de participantes para ampliar a leitura de região, horário e desempenho. OCR bruto, screenshots e endereços textuais não são exibidos como dados coletivos.",
            "Km inicial/final ainda não informados.",
            "Defina limites, métricas e como o Painel de Rota aparece durante a jornada.",
            "Sua conta, plano, créditos e acessos.",
            "Converse sobre ofertas, estratégia e jornada.",
            "Escolha uma sugestão ou escreva sua própria pergunta abaixo.",
            "Desempenho, corridas, comparativos e jornadas.",
            "Suas jornadas, ofertas e desempenho.",
            "Seu desempenho primeiro. Abra uma área para consultar corridas, comparativos, análises, categorias, período ou jornadas.",
            "Indicadores principais e evolução do seu desempenho.",
            "Ofertas recentes, digitalização da Uber e confirmação do que realmente foi realizado.",
            "Compare o período selecionado com a janela anterior equivalente.",
            "Veja quais categorias aparecem mais e como elas performam.",
            "Escolha período, classificação, categoria e tipo de oferta.",
            "Hodômetro, distância rodada e gastos reais de combustível/recarga.",
            "Status, acessos e última jornada",
            "Métricas, prévia, janela e mensagens rápidas",
            "Estimativa histórica exibida diretamente no HUD",
            "Sugestões de regiões próximas quando faltar oferta",
            "Tema Claro, Escuro ou Automático",
            "Alertas, resumos e avisos",
            "Backup visual das ofertas reconhecidas no próprio aparelho",
            "Backup e sincronização",
            "Privacidade, termos e ajuda",
            "Dados fictícios para screenshots e regressão visual",
            "O APK mostra o essencial. Alterações de cobrança, segurança e dados completos permanecem na Central Web protegida.",
            "Este aparelho usa as configurações locais de jornada/HUD e sincroniza os dados permitidos quando a conta está conectada.",
            "Linha diária de R$/km para visualizar crescimento ou redução ao longo do intervalo.",
            "Ofertas e confirmações",
            "Período x anterior",
            "Desempenho e evolução",
            "Resultado por serviço",
            "Filtros e visão detalhada",
            "Sessões registradas",
        )
        findText(root, exact).forEach { it.visibility = View.GONE }
    }

    private fun centerMetricSubcards(root: View) {
        val labels = setOf(
            "Viagens realizadas",
            "Faturamento",
            "Distância rodada",
            "Gastos informados",
            "Ofertas",
            "R$/km médio",
            "R$/h médio",
            "R$/min médio",
            "Oferta média",
            "Valor observado*",
            "Lucro est. observado*",
        )
        findText(root, labels).forEach { label ->
            val box = label.parent as? LinearLayout ?: return@forEach
            box.gravity = Gravity.CENTER
            for (i in 0 until box.childCount) {
                (box.getChildAt(i) as? TextView)?.apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            }
        }

        // Campo vazio de gasto não ocupa espaço no histórico.
        findText(root, setOf("Gastos informados")).forEach { label ->
            val box = label.parent as? LinearLayout ?: return@forEach
            val value = (0 until box.childCount)
                .mapNotNull { box.getChildAt(it) as? TextView }
                .drop(1)
                .firstOrNull()
                ?.text
                ?.toString()
                ?.trim()
            if (value == "—") box.visibility = View.GONE
        }
    }


    private fun polishJourneyHistory(root: View) {
        val candidates = mutableListOf<TextView>()
        walk(root) { view ->
            val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
            if (text.startsWith("Inicial ") && text.contains(" · Final ")) {
                candidates += view as TextView
            }
        }

        candidates.forEach { odometer ->
            val original = odometer.text.toString()
            val lines = original.lines()
            val first = lines.firstOrNull().orEmpty()
            val marker = " · Final "
            val split = first.removePrefix("Inicial ").split(marker, limit = 2)
            if (split.size != 2) return@forEach
            val start = split[0].trim()
            val end = split[1].trim()
            val distance = lines
                .firstOrNull { it.startsWith("Distância da jornada ") }
                ?.removePrefix("Distância da jornada ")
                ?.trim()

            val journeyCard = odometer.parent as? ViewGroup
            var spend: String? = null
            if (journeyCard != null) {
                val spendLabel = findText(journeyCard, setOf("Gastos informados")).firstOrNull()
                val spendBox = spendLabel?.parent as? ViewGroup
                if (spendBox != null) {
                    spend = (0 until spendBox.childCount)
                        .mapNotNull { spendBox.getChildAt(it) as? TextView }
                        .firstOrNull { it !== spendLabel }
                        ?.text
                        ?.toString()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && it != "—" }
                    spendBox.visibility = View.GONE
                }
            }

            odometer.text = buildString {
                when {
                    start != "—" && end != "—" ->
                        append("Odômetro: $start → $end")
                    start != "—" ->
                        append("Odômetro inicial: $start")
                    end != "—" ->
                        append("Odômetro final: $end")
                }
                if (!distance.isNullOrBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("Percorrido: $distance")
                    spend?.let { append(" · Gasto: $it") }
                } else if (!spend.isNullOrBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("Gasto: $spend")
                }
            }
        }
    }

    private fun decorateStatistics(root: View) {
        val tones = mapOf(
            "Histórico de corridas" to Pair(R.drawable.sr23_ic_route, SrUi023.palette(root.context).teal),
            "Comparativos" to Pair(R.drawable.sr23_ic_sliders, SrUi023.palette(root.context).cyan),
            "Análises" to Pair(R.drawable.sr23_ic_search, SrUi023.palette(root.context).blue),
            "Categorias" to Pair(R.drawable.sr23_ic_info, SrUi023.palette(root.context).purple),
            "Detalhes do período" to Pair(R.drawable.sr23_ic_sliders, SrUi023.palette(root.context).orange),
            "Jornadas" to Pair(R.drawable.sr23_ic_route, SrUi023.palette(root.context).userGreen),
        )
        tones.forEach { (text, config) ->
            findText(root, setOf(text)).forEach { title ->
                if (ancestor(title) { it is HistoryPanel } == null) return@forEach
                val active = title.currentTextColor == Color.WHITE
                title.setCompoundDrawablesWithIntrinsicBounds(config.first, 0, 0, 0)
                title.compoundDrawablePadding = SrUi023.dp(root.context, 6)
                title.compoundDrawableTintList =
                    ColorStateList.valueOf(if (active) Color.WHITE else config.second)
            }
        }
    }

    private fun findText(root: View, values: Set<String>): List<TextView> {
        val out = mutableListOf<TextView>()
        walk(root) { view ->
            val text = (view as? TextView)?.text?.toString()?.trim()
            if (text != null && values.contains(text)) out += view
        }
        return out
    }

    private fun findFirst(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun walk(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                walk(root.getChildAt(i), action)
            }
        }
    }

    private fun ancestor(view: View, predicate: (View) -> Boolean): View? {
        var current = view.parent
        while (current is View) {
            if (predicate(current)) return current
            current = current.parent
        }
        return null
    }
}
