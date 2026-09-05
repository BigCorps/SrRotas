package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.util.WeakHashMap

/** Ajustes de validação 0.26.4 aplicados sobre a base 0.26.3 já aprovada no CI. */
object FieldValidationPolish0264 {
    private val attached = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

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
            runCatching { if (activity is MainActivity) decorate(activity, root) }
                .onFailure { LocalLog.append(activity, "Polish 0.26.4 ignorou ajuste visual: ${it.message}") }
        }
        attached[activity] = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.post { runCatching { if (activity is MainActivity) decorate(activity, root) } }
    }

    private fun detach(activity: Activity) {
        val listener = attached.remove(activity) ?: return
        val root = activity.window?.decorView ?: return
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    private fun decorate(activity: MainActivity, root: View) {
        JourneyInlineDraft0264.maybeApplyToCurrentJourney(activity)
        val now = findFirst(root) { it is NowPanel023 } as? NowPanel023
        if (now != null) {
            decorateNow(now)
            NowRegionalBlend0264.decorate(now)
        }
        decorateSettings(root)
        decorateCapturedHistory(root)
    }

    private fun decorateNow(now: NowPanel023) {
        collapseSearch(now)
        addRadarBackToTop(now)
        findTextStarts(now, listOf("Km / abastecimento ou recarga", "Dados preparados"))
            .forEach(JourneyInlineDraft0264::attach)
    }

    private fun collapseSearch(now: NowPanel023) {
        val title = findText(now, setOf("Pesquisa"))
            .firstOrNull { ancestor(it) { view -> view is SrSoftShadowCard023 } != null }
            ?: return
        val card = ancestor(title) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return
        if ((0 until card.childCount).any { card.getChildAt(it).contentDescription == "sr0264_search_toggle" }) return

        card.setInnerPadding(10, 8, 10, 8)
        val originalChildren = (0 until card.childCount).map { card.getChildAt(it) }
        originalChildren.forEach { it.visibility = View.GONE }

        val toggle = TextView(now.context).apply {
            contentDescription = "sr0264_search_toggle"
            text = "Pesquisar região"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(SrUi023.palette(context).blue)
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 40)
            setPadding(
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 8),
            )
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.sr23_ic_search, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(SrUi023.palette(context).blue)
            compoundDrawablePadding = SrUi023.dp(context, 7)
            background = SrUi023.rounded(Color.TRANSPARENT, 12, SrUi023.palette(context).blue, 1, context)
            isSelected = false
            setOnClickListener {
                isSelected = !isSelected
                text = if (isSelected) "Recolher pesquisa" else "Pesquisar região"
                originalChildren.forEach { child ->
                    child.visibility = if (isSelected && child !== title) View.VISIBLE else View.GONE
                }
            }
        }
        card.addView(
            toggle,
            0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun addRadarBackToTop(now: NowPanel023) {
        val radarTitle = findText(now, setOf("Sr. Rotas Radar")).firstOrNull() ?: return
        val radarCard = ancestor(radarTitle) { it is SrSoftShadowCard023 } ?: return
        val parent = radarCard.parent as? LinearLayout ?: return
        if ((0 until parent.childCount).any { parent.getChildAt(it).contentDescription == "sr0264_radar_top" }) return

        val button = TextView(now.context).apply {
            contentDescription = "sr0264_radar_top"
            text = "Voltar ao topo  ↑"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(SrUi023.palette(context).blue)
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 38)
            background = SrUi023.rounded(Color.TRANSPARENT, 12, SrUi023.palette(context).blue, 1, context)
            setOnClickListener { now.smoothScrollTo(0, 0) }
        }
        val index = parent.indexOfChild(radarCard)
        parent.addView(
            button,
            (index + 1).coerceAtMost(parent.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(now.context, 7)
                bottomMargin = SrUi023.dp(now.context, 4)
            },
        )
    }

    private fun decorateSettings(root: View) {
        val settings = findFirst(root) { it is SettingsHub023 } as? SettingsHub023 ?: return
        compactSettingsCards(settings)
        decorateReadyBox(settings)

        findText(settings, setOf("Probabilidade de novas corridas", "Prob. novas corridas")).forEach { title ->
            title.text = "Prob. novas corridas"
            title.maxLines = 1
        }

        val assistantTitle = findText(settings, setOf("Assistente ativo")).firstOrNull()
        val assistantCard = assistantTitle?.let { ancestor(it) { view -> view is SrSoftShadowCard023 } as? SrSoftShadowCard023 }
        if (assistantCard != null) {
            recolorIconBox(assistantCard, SrUi023.palette(settings.context).magenta)
            assistantCard.setOnClickListener { showAssistantControl(settings) }
        }
    }

    private fun compactSettingsCards(settings: SettingsHub023) {
        if (settings.context.resources.configuration.screenWidthDp >= 400) return
        val titles = setOf(
            "Jornada e permissões",
            "Configuração do HUD",
            "Probabilidade de novas corridas",
            "Prob. novas corridas",
            "Assistente ativo",
            "Aparência",
            "Notificações",
            "Screenshots das ofertas",
            "Screenshots das corridas",
            "Dados e sincronização",
            "Privacidade e suporte",
            "Modo Demonstração",
        )
        findText(settings, titles).forEach { title ->
            val card = ancestor(title) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return@forEach
            card.minimumHeight = SrUi023.dp(settings.context, 104)
            card.setInnerPadding(9, 8, 9, 8)
            card.layoutParams?.let { lp ->
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                card.layoutParams = lp
            }
            val iconBox = directIconBox(card)
            if (iconBox != null) {
                iconBox.layoutParams?.let { lp ->
                    lp.width = SrUi023.dp(settings.context, 42)
                    lp.height = SrUi023.dp(settings.context, 42)
                    iconBox.layoutParams = lp
                }
            }
            title.textSize = 13.5f
        }
    }

    private fun decorateReadyBox(settings: SettingsHub023) {
        val title = findText(
            settings,
            setOf("Sr. Rotas está pronto", "Sr. Rotas precisa de um ajuste", "Sr. Rotas precisa de atenção"),
        ).firstOrNull() ?: return
        val card = ancestor(title) { it is SrSoftShadowCard023 } as? ViewGroup ?: return
        val mascot = findFirst(card) { it is ImageView } as? ImageView ?: return

        // A arte nova do relatório poderá entrar com este nome sem outra mudança de Kotlin.
        // Enquanto o arquivo ainda não foi anexado, preservamos o mascote atual como fallback.
        val newArt = settings.context.resources.getIdentifier(
            "sr0264_ready_mascot",
            "drawable",
            settings.context.packageName,
        )
        if (newArt != 0) mascot.setImageResource(newArt)
        mascot.scaleType = ImageView.ScaleType.CENTER_INSIDE
        mascot.layoutParams?.let { lp ->
            lp.width = SrUi023.dp(settings.context, 60)
            lp.height = SrUi023.dp(settings.context, 60)
            mascot.layoutParams = lp
        }
    }

    private fun showAssistantControl(settings: SettingsHub023) {
        val context = settings.context
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                SrUi023.dp(context, 20),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 20),
                SrUi023.dp(context, 8),
            )
            addView(
                SrUi023.body(
                    context,
                    "Sugere regiões próximas com histórico melhor depois de um período sem novas ofertas. Não interfere enquanto uma corrida estiver marcada como ativa.",
                    11.5f,
                ),
            )
            addView(
                Switch(context).apply {
                    text = "Assistente ativo"
                    textSize = 14f
                    setTextColor(SrUi023.palette(context).ink)
                    isChecked = ActiveAssistant026.isEnabled(context)
                    setOnCheckedChangeListener { _, checked ->
                        ActiveAssistant026.setEnabled(context, checked)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 12) },
            )
        }
        AlertDialog.Builder(context)
            .setTitle("Assistente ativo")
            .setView(holder)
            .setPositiveButton("Concluir") { _, _ -> settings.refresh() }
            .show()
    }

    private fun decorateCapturedHistory(root: View) {
        val title = findText(root, setOf("Digitalizar Uber")).firstOrNull() ?: return
        val card = ancestor(title) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return
        if ((0 until card.childCount).any { card.getChildAt(it).contentDescription == "sr0264_edit_captured" }) return

        val button = TextView(card.context).apply {
            contentDescription = "sr0264_edit_captured"
            text = "Editar histórico capturado"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(SrUi023.palette(context).blue)
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 42)
            setPadding(SrUi023.dp(context, 10), SrUi023.dp(context, 8), SrUi023.dp(context, 10), SrUi023.dp(context, 8))
            background = SrUi023.rounded(
                SrUi023.palette(context).surfaceMuted,
                12,
                SrUi023.palette(context).blue,
                1,
                context,
            )
            setOnClickListener { UberCapturedHistoryEditor0264.show(context) }
        }
        card.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(card.context, 7) },
        )
    }

    private fun recolorIconBox(card: ViewGroup, color: Int) {
        val box = directIconBox(card) ?: return
        box.background = SrUi023.rounded(color, 14, null, 0, card.context)
    }

    private fun directIconBox(card: ViewGroup): LinearLayout? =
        (0 until card.childCount)
            .map { card.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .firstOrNull { box -> box.childCount > 0 && box.getChildAt(0) is ImageView }

    private fun findText(root: View, exact: Set<String>): List<TextView> {
        val found = mutableListOf<TextView>()
        walk(root) { view ->
            val text = (view as? TextView)?.text?.toString()?.trim()
            if (text != null && exact.contains(text)) found += view
        }
        return found
    }

    private fun findTextStarts(root: View, prefixes: List<String>): List<TextView> {
        val found = mutableListOf<TextView>()
        walk(root) { view ->
            val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
            if (prefixes.any(text::startsWith)) found += view as TextView
        }
        return found
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
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), action)
        }
    }
}
