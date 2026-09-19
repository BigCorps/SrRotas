package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

/**
 * RC3.6.1 — correções da primeira validação de campo.
 *
 * Esta camada existe para retirar mutações visuais concorrentes das RC3.5/3.6
 * sem tocar OfferParser, dedupe, fórmulas ou persistência oficial do M1.
 */
object Rc361FieldFixes0270361 {
    private val main = Handler(Looper.getMainLooper())
    private var currentMain = WeakReference<MainActivity>(null)
    private val bubblePreDraw = WeakHashMap<ViewGroup, ViewTreeObserver.OnPreDrawListener>()
    private val compactedBubbleViews = WeakHashMap<View, Boolean>()

    fun install(application: Application) {
        // RC3.5 e RC3.6 tinham watchers concorrentes (700/500 ms) que alteravam
        // header e árvore da janela depois do layout. Mantemos seus callbacks de
        // Activity, mas substituímos apenas o polling visual pelo controlador estável.
        stopLegacyWatcher(Rc35UiPolish027035)
        stopLegacyWatcher(Rc36ClosingPolish027036)
        stopLegacyWatcher(BubbleRuntimePolish0265)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    currentMain = WeakReference(activity)
                    activity.window.decorView.post { decorateMain(activity) }
                }
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (currentMain.get() === activity) currentMain.clear()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
        main.postDelayed(ticker, 650L)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val activity = currentMain.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                runCatching { decorateMain(activity) }
                runCatching { ReaderLab027036.pollOfficialM1(activity.applicationContext) }
                runCatching { invokePrivate(Rc35UiPolish027035, "enforceAssistantTimeout", arrayOf(Context::class.java), arrayOf(activity.applicationContext)) }
                runCatching { installStableBubbleDecorator(activity.applicationContext) }
            }
            main.postDelayed(this, 900L)
        }
    }

    private fun decorateMain(activity: MainActivity) {
        // Reaplica somente o acabamento de telas/headers da RC3.6 para a aba
        // atualmente anexada. O watcher original foi desligado porque também
        // mutava a janela flutuante depois do layout.
        invokePrivate(
            Rc36ClosingPolish027036,
            "decorateMain",
            arrayOf(MainActivity::class.java),
            arrayOf(activity),
        )
        styleHeaderActions(activity)
        (findFirst(activity.window.decorView) { it is NowPanel023 } as? NowPanel023)?.let {
            styleNow(activity, it)
        }
        (findFirst(activity.window.decorView) { it is SettingsHub023 } as? SettingsHub023)?.let(::fixSettingsHub)
    }

    /** Referência do redesign: duas ações laterais + estado central, sem textura. */
    private fun styleNow(activity: MainActivity, now: NowPanel023) {
        val repo = SettingsRepository(activity)
        val active = repo.currentJourneyId().isNotBlank()
        Rc35JourneyDraft027035.attach(now, active)

        val statusHost = privateField<LinearLayout>(now, "statusHost") ?: return
        val stateKey = if (active) "active" else "idle"
        val existing = findFirst(statusHost) { it.contentDescription == "sr361_journey_rail_$stateKey" }
        if (existing == null) {
            statusHost.removeAllViews()
            statusHost.addView(
                journeyRail(activity, now, active),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }

        // Pesquisa fica como porta única; Momento/Hoje/Semana/Base/Perfil
        // continuam dentro do bloco expansível existente.
        styleRegionalCards(now)

        val toggle = findFirst(now) { it.contentDescription == "sr0264_search_toggle" } as? TextView
        toggle?.apply {
            if (!isSelected) text = "Pesquisar região   ▾"
            minHeight = UiKit.dp(context, 50)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(context, 14), UiKit.dp(context, 9), UiKit.dp(context, 14), UiKit.dp(context, 9))
        }
    }

    /** Aproxima os cards regionais da referência: R$/km · R$/min · distância. */
    private fun styleRegionalCards(now: NowPanel023) {
        val results = privateField<LinearLayout>(now, "results") ?: return
        val p = SrUi023.palette(now.context)
        textViews(results).filter { it.text?.toString()?.trim() == "R$/h" }.forEach { label ->
            val metric = ancestor(label) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return@forEach
            val labels = textViews(metric)
            val value = labels.getOrNull(labels.indexOf(label) + 1)
            val raw = value?.text?.toString()?.trim()?.replace('.', ' ')?.replace(',', '.')
                ?.replace(" ", "")?.toDoubleOrNull()
            label.text = "R$/min"
            label.contentDescription = "sr361_per_minute"
            if (raw != null) value.text = String.format(Locale("pt", "BR"), "%.2f", raw / 60.0)
            metric.setSurfaceColors(SrUi023.palette(now.context).surface, p.blue)
            metric.setStrokeWidthDp(2)
        }

        textViews(results).filter { it.text?.toString()?.trim() == "Busca" }.forEach { label ->
            val metric = ancestor(label) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return@forEach
            val regionCard = ancestor(metric) { parent ->
                parent is SrSoftShadowCard023 && parent !== metric
            } as? ViewGroup ?: return@forEach
            val distance = textViews(regionCard)
                .map { it.text?.toString()?.trim().orEmpty() }
                .firstOrNull { Regex("^\\d+[,.]\\d+\\s*km$", RegexOption.IGNORE_CASE).matches(it) }
                ?: return@forEach
            val labels = textViews(metric)
            label.text = "Distância"
            labels.getOrNull(labels.indexOf(label) + 1)?.text = distance
            labels.getOrNull(labels.indexOf(label) + 2)?.text = "até a região"
            metric.setSurfaceColors(SrUi023.palette(now.context).surface, p.purple)
            metric.setStrokeWidthDp(2)
        }

        textViews(results).filter { it.text?.toString()?.trim() == "R$/km" }.forEach { label ->
            (ancestor(label) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023)?.let { metric ->
                metric.setSurfaceColors(SrUi023.palette(now.context).surface, p.userGreen)
                metric.setStrokeWidthDp(2)
            }
        }
    }

    private fun journeyRail(activity: MainActivity, now: NowPanel023, active: Boolean): View {
        val p = UiKit.palette(activity)
        val journeyId = SettingsRepository(activity).currentJourneyId().takeIf(String::isNotBlank)
        val healthy = CaptureHealthState0263.isHealthy(activity, journeyId, SettingsRepository(activity).isProjectionActive())
        val rail = LinearLayout(activity).apply {
            contentDescription = "sr361_journey_rail_${if (active) "active" else "idle"}"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(activity, 5), UiKit.dp(activity, 5), UiKit.dp(activity, 5), UiKit.dp(activity, 5))
            background = UiKit.rounded(activity, p.surface, 22, p.line, 1)
        }

        fun side(label: String, symbol: String, tone: Int, enabled: Boolean, action: () -> Unit) =
            TextView(activity).apply {
                text = "$label   $symbol"
                textSize = 10.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                minHeight = UiKit.dp(activity, 50)
                setTextColor(if (enabled) Color.WHITE else p.muted)
                alpha = if (enabled) 1f else .55f
                background = UiKit.rounded(
                    activity,
                    if (enabled) tone else p.surfaceAlt,
                    18,
                    if (enabled) tone else p.line,
                    1,
                )
                isEnabled = enabled
                setOnClickListener { if (enabled) action() }
            }

        val start = side("Iniciar", "▶", p.good, !active) {
            Rc35JourneyDraft027035.persist(now)
            activity.toggleJourneyFromNow()
        }
        val end = side("Encerrar", "■", p.bad, active) {
            val invoked = invokePrivate(
                Rc35UiPolish027035,
                "askEndOdometer",
                arrayOf(MainActivity::class.java),
                arrayOf(activity),
            )
            if (!invoked) activity.toggleJourneyFromNow()
        }
        val centerTone = when {
            !active -> p.good
            healthy -> p.good
            else -> p.warn
        }
        val center = TextView(activity).apply {
            text = when {
                !active -> "✓\nOK"
                healthy -> "✓\nATIVA"
                else -> "↻\nRECUPERAR"
            }
            textSize = 9.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(centerTone)
            minHeight = UiKit.dp(activity, 48)
            if (active && !healthy) setOnClickListener { CaptureRecoveryActivity0270.open(activity) }
        }

        rail.addView(start, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f))
        rail.addView(center, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.16f))
        rail.addView(end, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f))
        return rail
    }

    private fun styleHeaderActions(activity: MainActivity) {
        findAll(activity.window.decorView) { it.contentDescription == "sr36_header_actions" }.forEach { box ->
            val images = directImageViews(box as? ViewGroup ?: return@forEach)
            val p = SrUi023.palette(activity)
            images.getOrNull(0)?.imageTintList = ColorStateList.valueOf(p.userGreen)
            images.getOrNull(1)?.imageTintList = ColorStateList.valueOf(p.orange)
        }
    }

    /** Remove apenas duplicações visuais; a Activity única de Janela mantém todas as opções. */
    fun fixSettingsHub(settings: SettingsHub023) {
        findFirst(settings) { it.contentDescription == "sr027034_floating_window_tile" }?.visibility = View.GONE
        findFirst(settings) { it.contentDescription == "sr027034_platform_status" }?.visibility = View.GONE

        val hudTitle = textViews(settings).firstOrNull { it.text?.toString()?.trim() == "Configuração do HUD" }
        val hudCard = hudTitle?.let { ancestor(it) { v -> v is SrSoftShadowCard023 } as? ViewGroup }
        hudCard?.let { card ->
            textViews(card).firstOrNull { it !== hudTitle && it.text?.toString()?.isNotBlank() == true }?.text =
                "Métricas, limites, perfis e prévia do HUD"
        }

        val readerTitle = textViews(settings).firstOrNull { it.text?.toString()?.trim() == "Leitor M1 × M2" }
        val readerCard = readerTitle?.let { ancestor(it) { v -> v is SrSoftShadowCard023 } as? ViewGroup }
        readerCard?.let { card ->
            textViews(card).firstOrNull { it !== readerTitle && it.text?.toString()?.isNotBlank() == true }?.text =
                "Comparação ao vivo: M1 MediaProjection × M2 Acessibilidade · Galeria não aciona M2"
        }

        // O diagnóstico compartilhado desta build inclui ReaderLab + saúde M2,
        // evitando que um novo relatório venha sem evidência do AccessibilityService.
        val diagnosticTitle = textViews(settings).firstOrNull { it.text?.toString()?.trim() == "Diagnóstico de leitura" }
        val diagnosticCard = diagnosticTitle?.let { ancestor(it) { v -> v is SrSoftShadowCard023 } as? ViewGroup }
        diagnosticCard?.setOnClickListener { ReaderLabCombinedDiagnostic0270361.share(settings.context) }

        val grid = privateField<LinearLayout>(settings, "grid") ?: return
        if (findFirst(grid) { it.contentDescription == "sr361_m2_health" } == null) {
            val health = ReaderLabTelemetry0270361.snapshot(settings.context)
            val p = SrUi023.palette(settings.context)
            val card = SrUi023.card(settings.context, 11, 15).apply {
                contentDescription = "sr361_m2_health"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                addView(SrUi023.iconBox(context, R.drawable.sr36_ic_radar, p.userGreen, 40))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiKit.dp(context, 10), 0, 0, 0)
                    addView(SrUi023.title(context, "Saúde do M2 ao vivo", 13.5f))
                    addView(SrUi023.body(
                        context,
                        "Eventos Uber ${health.uberEvents} · árvore ${health.treeOffers} · visual ${health.visualOffers}",
                        9.5f,
                    ))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                setOnClickListener {
                    AlertDialog.Builder(settings.context)
                        .setTitle("Método 2 · diagnóstico ao vivo")
                        .setMessage(ReaderLabTelemetry0270361.summary(settings.context))
                        .setNegativeButton("Fechar", null)
                        .setNeutralButton("Zerar M2") { _, _ -> ReaderLabTelemetry0270361.reset(settings.context) }
                        .show()
                }
            }
            grid.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UiKit.dp(settings.context, 8)
            })
        }
    }

    /**
     * Decora antes do draw. Se a árvore mudou, cancela aquele frame e deixa o
     * Android medir novamente; assim o usuário nunca vê "texto -> bloco -> novo tamanho".
     */
    private fun installStableBubbleDecorator(context: Context) {
        val panel = privateField<ViewGroup>(JourneyBubbleController, "panel") ?: return
        if (bubblePreDraw.containsKey(panel)) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            val changed = runCatching { decorateBubbleBeforeDraw(context, panel) }.getOrDefault(false)
            if (changed) {
                panel.requestLayout()
                panel.post { clampBubble(context) }
                false
            } else true
        }
        bubblePreDraw[panel] = listener
        panel.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun decorateBubbleBeforeDraw(context: Context, panel: ViewGroup): Boolean {
        var changed = false
        val prefs = JourneyUiPreferences(context)
        val dark = when (prefs.windowThemeMode()) {
            "dark" -> true
            "light" -> false
            else -> Appearance021.isDark(context)
        }
        val p = UiKit.palette(dark)
        panel.background = UiKit.rounded(context, p.surface, 14, p.line, 1)
        panel.alpha = prefs.windowOpacityPercent() / 100f
        if (prefs.compactPanel()) {
            changed = setPaddingIfChanged(panel, UiKit.dp(context, 8), UiKit.dp(context, 6), UiKit.dp(context, 8), UiKit.dp(context, 7)) || changed
            changed = compactBubbleTree(context, panel, panel) || changed
        } else {
            changed = setPaddingIfChanged(panel, UiKit.dp(context, 12), UiKit.dp(context, 10), UiKit.dp(context, 12), UiKit.dp(context, 11)) || changed
        }

        // Elimina resíduos das decorações pós-layout da RC anterior, caso a
        // atualização tenha ocorrido sem o processo ser encerrado.
        listOf("sr35_metric_blocks", "sr36_reader_badge").forEach { tag ->
            val old = findFirst(panel) { it.contentDescription == tag }
            if (old != null) {
                (old.parent as? ViewGroup)?.removeView(old)
                changed = true
            }
        }

        val snap = ReaderLab027036.snapshot(context)
        val selectedId = privateField<String>(JourneyBubbleController, "deepExpandedOfferId")
            ?: privateField<String>(JourneyBubbleController, "expandedOfferId")
        val selectedOffer = selectedId?.let { id -> LocalStore.get(context).recentOffers(30).firstOrNull { it.localId == id } }
        val label = when {
            selectedOffer != null -> ReaderLab027036.methodForOfficial(selectedOffer)
            snap.mode == ReaderLab027036.MODE_M1 -> "M1"
            snap.mode == ReaderLab027036.MODE_M2 -> "M2"
            else -> snap.latest.takeIf { it in setOf("M1", "M2", "M1+M2") } ?: "M1+M2"
        }
        val badge = findFirst(panel) { it.contentDescription == "sr361_reader_badge" } as? TextView
        if (badge == null) {
            panel.addView(TextView(context).apply {
                contentDescription = "sr361_reader_badge"
                text = label
                textSize = 8.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = UiKit.rounded(context, badgeTone(context, label), 999)
                setPadding(UiKit.dp(context, 8), UiKit.dp(context, 3), UiKit.dp(context, 8), UiKit.dp(context, 3))
            }, 0, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                bottomMargin = UiKit.dp(context, 4)
            })
            changed = true
        } else if (badge.text?.toString() != label) {
            badge.text = label
            badge.background = UiKit.rounded(context, badgeTone(context, label), 999)
            changed = true
        }

        textViews(panel).forEach { tv ->
            when (tv.text?.toString()?.trim()) {
                "Busca / retirada", "Retirada" -> tv.text = "Busca"
                "dados insuficientes", "Dados insuficientes", "% Dados insuf." -> tv.text = "—"
            }
        }

        val id = privateField<String>(JourneyBubbleController, "deepExpandedOfferId") ?: return changed
        val offer = LocalStore.get(context).recentOffers(30).firstOrNull { it.localId == id } ?: return changed
        val original = textViews(panel).firstOrNull {
            val t = it.text?.toString().orEmpty()
            t.contains("/km") && t.contains("/min") && t.contains("/h")
        } ?: return changed
        val parent = original.parent as? LinearLayout ?: return changed
        if (findFirst(parent) { it.contentDescription == "sr361_metric_blocks" } == null) {
            original.visibility = View.GONE
            val settings = SettingsRepository(context).load()
            val metrics = listOf(
                Metric("R$/km", offer.perKm, 2, classifyHigher(offer.perKm, settings.redPerKmBelow, settings.minPerKm)),
                Metric("R$/min", offer.perMinute, 2, classifyHigher(offer.perMinute, settings.redPerMinuteBelow, settings.minPerMinute)),
                Metric("R$/h", offer.perHour, 0, classifyHigher(offer.perHour, settings.redPerHourBelow, settings.minPerHour)),
                Metric("km", offer.totalKm, 1, null),
                Metric("min", offer.totalMinutes?.toDouble(), 0, null),
            )
            val row = LinearLayout(context).apply {
                contentDescription = "sr361_metric_blocks"
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = UiKit.dp(context, 49)
            }
            metrics.forEachIndexed { index, metric ->
                val border = when (metric.grade) {
                    2 -> p.good
                    0 -> p.bad
                    1 -> p.warn
                    else -> p.line
                }
                row.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    minimumHeight = UiKit.dp(context, 46)
                    background = UiKit.rounded(context, p.surfaceAlt, 8, border, if (metric.grade == null) 1 else 2)
                    setPadding(2, UiKit.dp(context, 4), 2, UiKit.dp(context, 4))
                    addView(UiKit.body(context, metric.label, 7.5f).apply { gravity = Gravity.CENTER })
                    addView(UiKit.body(context, metric.value?.let {
                        String.format(Locale("pt", "BR"), "%.${metric.decimals}f", it)
                    } ?: "—", 9f).apply {
                        gravity = Gravity.CENTER
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(p.ink)
                    })
                }, LinearLayout.LayoutParams(0, UiKit.dp(context, 49), 1f).apply {
                    if (index > 0) marginStart = UiKit.dp(context, 3)
                })
            }
            parent.addView(row, (parent.indexOfChild(original) + 1).coerceAtMost(parent.childCount), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 49)).apply {
                topMargin = UiKit.dp(context, 5)
            })
            changed = true
        }
        return changed
    }

    private data class Metric(val label: String, val value: Double?, val decimals: Int, val grade: Int?)

    private fun classifyHigher(value: Double?, redBelow: Double, goodFrom: Double): Int? = value?.let {
        when {
            it >= goodFrom -> 2
            it < redBelow -> 0
            else -> 1
        }
    }

    private fun badgeTone(context: Context, label: String): Int = when (label) {
        "M1" -> UiKit.palette(context).primary
        "M2" -> SrUi023.palette(context).purple
        else -> SrUi023.palette(context).teal
    }

    private fun compactBubbleTree(context: Context, root: View, panelRoot: ViewGroup): Boolean {
        var changed = false
        if (root !== panelRoot && compactedBubbleViews[root] != true) {
            compactedBubbleViews[root] = true
            changed = true
            if (root is TextView) {
                val sp = root.textSize / context.resources.displayMetrics.scaledDensity
                if (sp > 10f) root.textSize = (sp * 0.93f).coerceAtLeast(9.5f)
                if (root.minHeight > UiKit.dp(context, 34)) root.minHeight = UiKit.dp(context, 34)
            }
            if (root is ViewGroup) {
                root.setPadding(
                    (root.paddingLeft * .82f).toInt(),
                    (root.paddingTop * .72f).toInt(),
                    (root.paddingRight * .82f).toInt(),
                    (root.paddingBottom * .72f).toInt(),
                )
            }
            (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = (lp.topMargin * .72f).toInt()
                lp.bottomMargin = (lp.bottomMargin * .72f).toInt()
                lp.marginStart = (lp.marginStart * .85f).toInt()
                lp.marginEnd = (lp.marginEnd * .85f).toInt()
                root.layoutParams = lp
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                changed = compactBubbleTree(context, root.getChildAt(i), panelRoot) || changed
            }
        }
        return changed
    }

    private fun setPaddingIfChanged(view: View, l: Int, t: Int, r: Int, b: Int): Boolean {
        if (view.paddingLeft == l && view.paddingTop == t && view.paddingRight == r && view.paddingBottom == b) return false
        view.setPadding(l, t, r, b)
        return true
    }

    private fun clampBubble(context: Context) {
        invokePrivate(
            JourneyBubbleController,
            "clampToVisibleBounds",
            arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!),
            arrayOf(context, false),
        )
    }

    private fun stopLegacyWatcher(target: Any) {
        val handler = privateField<Handler>(target, "main") ?: return
        val watcher = privateField<Runnable>(target, "watcher") ?: return
        handler.removeCallbacks(watcher)
    }

    private fun directImageViews(root: ViewGroup): List<ImageView> =
        (0 until root.childCount).map { root.getChildAt(it) }.filterIsInstance<ImageView>()

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        walk(root) { if (it is TextView) out += it }
        return out
    }

    private fun findAll(root: View, predicate: (View) -> Boolean): List<View> {
        val out = mutableListOf<View>()
        walk(root) { if (predicate(it)) out += it }
        return out
    }

    private fun findFirst(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findFirst(root.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }

    private fun ancestor(view: View, predicate: (View) -> Boolean): View? {
        var current: View? = view
        while (current != null) {
            if (predicate(current)) return current
            current = current.parent as? View
        }
        return null
    }

    private fun walk(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) walk(root.getChildAt(i), action)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? = runCatching {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as? T
    }.getOrNull()

    private fun invokePrivate(target: Any, name: String, types: Array<Class<*>>, args: Array<Any?>): Boolean = runCatching {
        target.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(target, *args)
    }.isSuccess
}
