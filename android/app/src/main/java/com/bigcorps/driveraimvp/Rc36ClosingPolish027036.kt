package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

/**
 * RC3.6 — fechamento visual/operacional antes da validação 1.0.
 * Camada idempotente para não descongelar parser/dedupe/fórmulas.
 */
object Rc36ClosingPolish027036 {
    private val main = Handler(Looper.getMainLooper())
    private var currentMain = WeakReference<MainActivity>(null)
    private val bubbleListeners = WeakHashMap<ViewGroup, Boolean>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    currentMain = WeakReference(activity)
                    activity.window.decorView.post { decorateMain(activity) }
                }
            }
            override fun onActivityDestroyed(activity: Activity) { if (currentMain.get() === activity) currentMain.clear() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
        main.post(watcher)
    }

    private val watcher = object : Runnable {
        override fun run() {
            currentMain.get()?.let { activity -> runCatching { decorateMain(activity) } }
            currentMain.get()?.applicationContext?.let { context ->
                runCatching { ReaderLab027036.pollOfficialM1(context) }
                runCatching { decorateBubble(context) }
            }
            main.postDelayed(this, 500L)
        }
    }

    private fun decorateMain(activity: MainActivity) {
        hideLegacyFloatingGear(activity)
        integrateHeaderActions(activity)
        decorateBottomIcons(activity)
        findFirst(activity.window.decorView) { it is NowPanel023 }?.let { decorateNow(activity, it as NowPanel023) }
        findFirst(activity.window.decorView) { it is AiPanel023 }?.let { decorateAi(it as AiPanel023) }
    }

    private fun hideLegacyFloatingGear(activity: MainActivity) {
        activity.findViewById<FrameLayout>(android.R.id.content)
            ?.findViewWithTag<View>("sr_rc35_settings_gear")
            ?.visibility = View.GONE
    }

    private fun integrateHeaderActions(activity: MainActivity) {
        findAll(activity.window.decorView) { it is SrAppHeader023 }.forEach { item ->
            val header = item as SrAppHeader023
            // A RC3.5 reservava espaço para uma engrenagem flutuante. Nesta
            // versão as ações pertencem ao próprio header, então removemos a reserva.
            if (header.paddingRight != UiKit.dp(activity, 6)) {
                header.setPadding(header.paddingLeft, header.paddingTop, UiKit.dp(activity, 6), header.paddingBottom)
            }
            if (findFirst(header) { it.contentDescription == "sr36_header_actions" } != null) return@forEach
            val p = SrUi023.palette(activity)
            val compact = activity.resources.configuration.screenWidthDp < 360
            val box = LinearLayout(activity).apply {
                contentDescription = "sr36_header_actions"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = SrUi023.rounded(p.surface, 18, p.outline, 1, activity)
                setPadding(UiKit.dp(activity, 2), UiKit.dp(activity, 2), UiKit.dp(activity, 2), UiKit.dp(activity, 2))
            }
            fun action(icon: Int, description: String, click: () -> Unit) = ImageView(activity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(p.blue)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = description
                setPadding(UiKit.dp(activity, 7), UiKit.dp(activity, 7), UiKit.dp(activity, 7), UiKit.dp(activity, 7))
                setOnClickListener { click() }
            }
            val size = UiKit.dp(activity, if (compact) 32 else 36)
            box.addView(action(R.drawable.sr23_ic_settings, "Configurações") { Rc36MainOverlay027036.openSettings(activity) }, LinearLayout.LayoutParams(size, size))
            box.addView(action(R.drawable.sr23_ic_user, "Usuário") { Rc36MainOverlay027036.openUser(activity) }, LinearLayout.LayoutParams(size, size))
            header.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = UiKit.dp(activity, 4)
            })
        }
    }

    private fun decorateBottomIcons(activity: MainActivity) {
        listOf("Estatísticas" to R.drawable.sr36_ic_chart, "Radar" to R.drawable.sr36_ic_radar).forEach { (label, res) ->
            findFirst(activity.window.decorView) { it.contentDescription?.toString() == label }?.let { item ->
                (findFirst(item) { it is ImageView } as? ImageView)?.setImageResource(res)
            }
        }
    }

    private fun decorateNow(activity: MainActivity, now: NowPanel023) {
        // Consolida o bloco introduzido na RC3.5 sob um único conceito: Pesquisar região.
        findFirst(now) { it.contentDescription == "sr35_now_filters" }?.let { card ->
            textViews(card).firstOrNull { it.text?.toString()?.contains("Filtros e pesquisa", true) == true }?.text = "⌕  Pesquisar região"
            textViews(card).firstOrNull { it.text?.toString()?.trim() == "Pesquisa" }?.visibility = View.GONE
        }
        polishJourneyControl(activity, now)
    }

    private fun polishJourneyControl(activity: MainActivity, now: NowPanel023) {
        val target = textViews(now).firstOrNull {
            it.text?.toString()?.trim() in setOf(
                "Iniciar", "Encerrar", "Iniciar jornada", "Encerrar jornada", "Recuperar leitura",
                "● Pronto para iniciar   INICIAR", "● Jornada ativa   ENCERRAR", "● Leitura precisa recuperar   RECUPERAR",
            )
        } ?: return
        val repo = SettingsRepository(activity)
        val journey = repo.currentJourneyId().takeIf(String::isNotBlank)
        val healthy = CaptureHealthState0263.isHealthy(activity, journey, repo.isProjectionActive())
        val p = UiKit.palette(activity)
        val (text, accent) = when {
            journey == null -> "● Pronto para iniciar   INICIAR" to p.good
            !healthy -> "● Leitura precisa recuperar   RECUPERAR" to p.warn
            else -> "● Jornada ativa   ENCERRAR" to p.bad
        }
        target.text = text
        target.textSize = 11.5f
        target.setTypeface(target.typeface, Typeface.BOLD)
        target.setTextColor(p.ink)
        target.gravity = Gravity.CENTER
        target.minHeight = UiKit.dp(activity, 42)
        target.background = UiKit.rounded(activity, p.surface, 14, accent, 2)
        target.compoundDrawableTintList = ColorStateList.valueOf(accent)
        target.setPadding(UiKit.dp(activity, 10), UiKit.dp(activity, 7), UiKit.dp(activity, 10), UiKit.dp(activity, 7))
    }

    private fun decorateAi(ai: AiPanel023) {
        val mascot = findFirst(ai) {
            it is ImageView && it.contentDescription?.toString()?.contains("aguardando", true) == true
        } as? ImageView ?: return
        val compact = ai.resources.configuration.screenWidthDp < 360
        val w = UiKit.dp(ai.context, if (compact) 268 else 310)
        val h = UiKit.dp(ai.context, if (compact) 214 else 250)
        val lp = mascot.layoutParams as? LinearLayout.LayoutParams
        if (lp == null || lp.width != w || lp.height != h) {
            mascot.layoutParams = LinearLayout.LayoutParams(w, h).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = UiKit.dp(ai.context, 1)
            }
            mascot.setImageResource(R.drawable.sr0265_ai_mascot)
            mascot.scaleType = ImageView.ScaleType.FIT_CENTER
            mascot.adjustViewBounds = true
        }
        val parent = mascot.parent as? LinearLayout ?: return
        if (findFirst(parent) { it.contentDescription == "sr36_ai_fade" } == null) {
            val fade = View(ai.context).apply {
                contentDescription = "sr36_ai_fade"
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, UiKit.palette(ai.context).background),
                )
            }
            parent.addView(fade, (parent.indexOfChild(mascot) + 1).coerceAtMost(parent.childCount), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ai.context, 44)).apply {
                topMargin = -UiKit.dp(ai.context, 38)
            })
        }
    }

    fun decorateReaderLabSettings(settings: SettingsHub023) {
        val grid = privateField<LinearLayout>(settings, "grid") ?: return
        if (findFirst(grid) { it.contentDescription == "sr36_reader_lab" } == null) {
            val p = SrUi023.palette(settings.context)
            grid.addView(settingsRow(
                settings.context,
                "sr36_reader_lab",
                "Leitor M1 × M2",
                "Comparação de campo: MediaProjection × Acessibilidade",
                R.drawable.sr36_ic_radar,
                p.purple,
            ) { showReaderLabMode(settings.context as Activity) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UiKit.dp(settings.context, 8)
            })
        }
        if (findFirst(grid) { it.contentDescription == "sr36_reader_results" } == null) {
            val p = SrUi023.palette(settings.context)
            grid.addView(settingsRow(
                settings.context,
                "sr36_reader_results",
                "Diagnóstico M1 × M2",
                ReaderLab027036.snapshot(settings.context).let { "M1 ${it.m1Seen} · M2 ${it.m2Seen} · ambos ${it.matched}" },
                R.drawable.sr23_ic_file_text,
                p.teal,
            ) { showReaderLabResults(settings.context as Activity) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UiKit.dp(settings.context, 8)
            })
        }
    }

    private fun showReaderLabMode(activity: Activity) {
        val values = arrayOf(
            "Método 1 — MediaProjection",
            "Comparativo — M1 + M2",
            "Método 2 — Acessibilidade (shadow)",
        )
        val keys = arrayOf(ReaderLab027036.MODE_M1, ReaderLab027036.MODE_COMPARE, ReaderLab027036.MODE_M2)
        val selected = keys.indexOf(ReaderLab027036.mode(activity)).coerceAtLeast(1)
        var choice = selected
        AlertDialog.Builder(activity)
            .setTitle("Leitor de ofertas · build de campo")
            .setSingleChoiceItems(values, selected) { _, which -> choice = which }
            .setMessage("M2 nunca toca no Uber e nesta build não grava sozinho na Base Coletiva. O modo Comparativo é o indicado para a validação.")
            .setNeutralButton("Ativar Acessibilidade") { _, _ -> ReaderLab027036.showDisclosureAndOpenSettings(activity) }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                ReaderLab027036.setMode(activity, keys[choice.coerceIn(0, 2)])
                if (keys[choice.coerceIn(0, 2)] != ReaderLab027036.MODE_M1 && !ReaderLab027036.isAccessibilityEnabled(activity)) {
                    ReaderLab027036.showDisclosureAndOpenSettings(activity)
                }
            }
            .show()
    }

    private fun showReaderLabResults(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Diagnóstico M1 × M2")
            .setMessage(ReaderLab027036.summary(activity))
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Zerar teste") { _, _ -> ReaderLab027036.reset(activity) }
            .setPositiveButton("Compartilhar") { _, _ -> ReaderLab027036.shareSummary(activity) }
            .show()
    }

    private fun settingsRow(context: Context, tag: String, title: String, subtitle: String, icon: Int, tone: Int, action: () -> Unit): View =
        SrUi023.card(context, 11, 15).apply {
            contentDescription = tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            addView(SrUi023.iconBox(context, icon, tone, 40))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(context, 10), 0, 0, 0)
                addView(SrUi023.title(context, title, 13.5f))
                addView(SrUi023.body(context, subtitle, 9.5f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { action() }
        }

    private fun decorateBubble(context: Context) {
        val panel = privateField<ViewGroup>(JourneyBubbleController, "panel") ?: return
        if (bubbleListeners[panel] != true) {
            bubbleListeners[panel] = true
            panel.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                private var posted = false
                private fun schedule() {
                    if (posted) return
                    posted = true
                    panel.post {
                        posted = false
                        runCatching { decorateBubbleContent(context, panel) }
                    }
                }
                override fun onChildViewAdded(parent: View?, child: View?) = schedule()
                override fun onChildViewRemoved(parent: View?, child: View?) = schedule()
            })
        }
        decorateBubbleContent(context, panel)
    }

    private fun decorateBubbleContent(context: Context, panel: ViewGroup) {
        ensureReaderBadge(context, panel)
        val id = privateField<String>(JourneyBubbleController, "deepExpandedOfferId") ?: return
        val offer = LocalStore.get(context).recentOffers(30).firstOrNull { it.localId == id } ?: return
        val original = textViews(panel).firstOrNull {
            val t = it.text?.toString().orEmpty()
            it.visibility == View.VISIBLE && t.contains("/km") && t.contains("/min") && t.contains("/h")
        } ?: return
        val parent = original.parent as? LinearLayout ?: return
        if (findFirst(parent) { it.contentDescription == "sr35_metric_blocks" } != null) {
            original.visibility = View.GONE
            return
        }
        original.visibility = View.GONE
        val settings = SettingsRepository(context).load()
        val p = UiKit.palette(context)
        val metrics = listOf(
            Metric("R$/km", offer.perKm, 2, classifyHigher(offer.perKm, settings.redPerKmBelow, settings.minPerKm)),
            Metric("R$/min", offer.perMinute, 2, classifyHigher(offer.perMinute, settings.redPerMinuteBelow, settings.minPerMinute)),
            Metric("R$/h", offer.perHour, 0, classifyHigher(offer.perHour, settings.redPerHourBelow, settings.minPerHour)),
            Metric("km", offer.totalKm, 1, null),
            Metric("min", offer.totalMinutes?.toDouble(), 0, null),
        )
        val row = LinearLayout(context).apply {
            contentDescription = "sr35_metric_blocks"
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = UiKit.dp(context, 49)
        }
        metrics.forEachIndexed { index, metric ->
            val border = when (metric.grade) { 2 -> p.good; 0 -> p.bad; 1 -> p.warn; else -> p.line }
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = UiKit.dp(context, 46)
                background = UiKit.rounded(context, p.surfaceAlt, 8, border, if (metric.grade == null) 1 else 2)
                setPadding(2, UiKit.dp(context, 4), 2, UiKit.dp(context, 4))
                addView(UiKit.body(context, metric.label, 7.5f).apply { gravity = Gravity.CENTER })
                addView(UiKit.body(context, metric.value?.let { String.format(Locale("pt", "BR"), "%.${metric.decimals}f", it) } ?: "—", 9f).apply {
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(p.ink)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (index > 0) marginStart = UiKit.dp(context, 3)
            })
        }
        parent.addView(row, (parent.indexOfChild(original) + 1).coerceAtMost(parent.childCount), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 49)).apply {
            topMargin = UiKit.dp(context, 5)
        })
    }

    private data class Metric(val label: String, val value: Double?, val decimals: Int, val grade: Int?)
    private fun classifyHigher(value: Double?, redBelow: Double, goodFrom: Double): Int? = value?.let {
        when { it >= goodFrom -> 2; it < redBelow -> 0; else -> 1 }
    }

    private fun ensureReaderBadge(context: Context, panel: ViewGroup) {
        val snap = ReaderLab027036.snapshot(context)
        val existing = findFirst(panel) { it.contentDescription == "sr36_reader_badge" } as? TextView
        val selectedId = privateField<String>(JourneyBubbleController, "deepExpandedOfferId")
            ?: privateField<String>(JourneyBubbleController, "expandedOfferId")
        val selectedOffer = selectedId?.let { id -> LocalStore.get(context).recentOffers(30).firstOrNull { it.localId == id } }
        val label = when {
            selectedOffer != null -> ReaderLab027036.methodForOfficial(selectedOffer)
            snap.mode == ReaderLab027036.MODE_M1 -> "M1"
            snap.mode == ReaderLab027036.MODE_M2 -> "M2"
            else -> snap.latest.takeIf { it in setOf("M1", "M2", "M1+M2") } ?: "M1+M2"
        }
        val tone = when (label) {
            "M1" -> UiKit.palette(context).primary
            "M2" -> SrUi023.palette(context).purple
            else -> UiKit.palette(context).good
        }
        if (existing != null) {
            if (existing.text?.toString() != label) existing.text = label
            existing.background = UiKit.rounded(context, Color.TRANSPARENT, 999, tone, 1)
            existing.setTextColor(tone)
            return
        }
        val badge = TextView(context).apply {
            contentDescription = "sr36_reader_badge"
            text = label
            textSize = 8.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(tone)
            background = UiKit.rounded(context, Color.TRANSPARENT, 999, tone, 1)
            setPadding(UiKit.dp(context, 7), UiKit.dp(context, 2), UiKit.dp(context, 7), UiKit.dp(context, 2))
        }
        panel.addView(badge, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(context, 24)))
    }

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
        if (root is ViewGroup) for (i in 0 until root.childCount) findFirst(root.getChildAt(i), predicate)?.let { return it }
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
}
