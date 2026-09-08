package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.WeakHashMap

/**
 * Ajustes de campo 0.26.5 sobre a base 0.26.4 já validada no CI.
 * Mantém OCR/dispatcher/jornada desacoplados das mudanças puramente visuais.
 */
object FieldValidationPolish0265 {
    private val attached = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()
    private val pullStates = WeakHashMap<NowPanel023, PullState>()
    @Volatile private var pendingOpenNow = false

    private data class PullState(
        var startY: Float = 0f,
        var eligible: Boolean = false,
        var pulling: Boolean = false,
        val indicator: TextView,
    )

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

    fun openNowFromAssistant(context: Context) {
        pendingOpenNow = true
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun attach(activity: Activity) {
        if (attached.containsKey(activity)) return
        val root = activity.window.decorView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching { decorate(activity, root) }
                .onFailure { LocalLog.append(activity, "Polish 0.26.5 ignorou ajuste visual: ${it.message}") }
        }
        attached[activity] = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.post { runCatching { decorate(activity, root) } }
    }

    private fun detach(activity: Activity) {
        val listener = attached.remove(activity) ?: return
        val root = activity.window?.decorView ?: return
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    private fun decorate(activity: Activity, root: View) {
        if (activity !is MainActivity) return
        if (pendingOpenNow) navigateNow(activity)

        val now = findFirst(root) { it is NowPanel023 } as? NowPanel023
        if (now != null) {
            if (pendingOpenNow) forceCurrentNow(now)
            installPullToRefresh(now)
            polishMiniCards(now)
            polishPersonalIcons(now)
        }

        (findFirst(root) { it is SettingsHub023 } as? SettingsHub023)?.let(::decorateSettings)
        (findFirst(root) { it is AiPanel023 } as? AiPanel023)?.let(::decorateAi)
        (findFirst(root) { it is HistoryPanel } as? HistoryPanel)?.let(::decorateJourneys)
    }

    private fun navigateNow(activity: MainActivity) {
        val success = runCatching {
            val method = MainActivity::class.java.getDeclaredMethod(
                "navigate",
                SrBottomNav023.Route::class.java,
            ).apply { isAccessible = true }
            method.invoke(activity, SrBottomNav023.Route.NOW)
        }.isSuccess
        // Mantém pendingOpenNow até a instância NowPanel existir; então o
        // filtro é forçado para “Momento” e a consulta é renovada.
        if (!success) pendingOpenNow = false
    }

    private fun forceCurrentNow(now: NowPanel023) {
        runCatching {
            val mode = NowPanel023::class.java.getDeclaredField("mode").apply { isAccessible = true }
            mode.set(now, "now")
            val region = NowPanel023::class.java.getDeclaredField("region").apply { isAccessible = true }
            (region.get(now) as? EditText)?.setText("")
            now.refresh()
        }.onSuccess { pendingOpenNow = false }
    }

    // ---------------- Agora ----------------

    private fun installPullToRefresh(now: NowPanel023) {
        if (pullStates.containsKey(now)) return
        val content = now.getChildAt(0) as? LinearLayout ?: return
        val indicator = TextView(now.context).apply {
            contentDescription = "sr0265_pull_refresh"
            text = "↻ Puxe para atualizar"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(SrUi023.palette(context).blue)
            setTypeface(typeface, Typeface.BOLD)
            visibility = View.GONE
            setPadding(0, SrUi023.dp(context, 4), 0, SrUi023.dp(context, 4))
        }
        val insertAt = if (content.childCount > 0) 1.coerceAtMost(content.childCount) else 0
        content.addView(
            indicator,
            insertAt,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val state = PullState(indicator = indicator)
        pullStates[now] = state
        val threshold = SrUi023.dp(now.context, 86).toFloat()
        now.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    state.startY = event.rawY
                    state.eligible = now.scrollY <= 0
                    state.pulling = false
                }
                MotionEvent.ACTION_MOVE -> if (state.eligible) {
                    val delta = event.rawY - state.startY
                    if (delta > SrUi023.dp(now.context, 18)) {
                        state.pulling = true
                        state.indicator.visibility = View.VISIBLE
                        state.indicator.text = if (delta >= threshold) {
                            "↻ Solte para atualizar"
                        } else {
                            "↻ Puxe para atualizar"
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val delta = event.rawY - state.startY
                    if (state.eligible && state.pulling && delta >= threshold) {
                        state.indicator.text = "↻ Atualizando…"
                        now.refresh()
                        state.indicator.postDelayed({ state.indicator.visibility = View.GONE }, 700L)
                    } else {
                        state.indicator.visibility = View.GONE
                    }
                    state.eligible = false
                    state.pulling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    state.indicator.visibility = View.GONE
                    state.eligible = false
                    state.pulling = false
                }
            }
            false
        }
    }

    private fun polishMiniCards(now: NowPanel023) {
        val p = SrUi023.palette(now.context)
        findAll(now) { it is SrSoftShadowCard023 }.forEach { view ->
            val card = view as SrSoftShadowCard023
            // Só os TextViews diretos pertencem ao mini-card. Usar todos os
            // descendentes também capturava o card externo da região.
            val labels = directTextViews(card)
            val label = labels.firstOrNull {
                it.text?.toString()?.trim() in setOf("R$/km", "R$/h", "R$/hora", "Busca", "Busca/min.")
            } ?: return@forEach
            val normalized = when (label.text?.toString()?.trim()) {
                "R$/h" -> "R$/hora"
                "Busca" -> "Busca/min."
                else -> label.text?.toString()?.trim().orEmpty()
            }
            label.text = normalized
            val tone = when (normalized) {
                "R$/km" -> p.tealDark
                "R$/hora" -> p.orange
                else -> p.red
            }
            card.gravity = Gravity.CENTER
            card.setSurfaceColors(p.surface, tone)
            card.setStrokeWidthDp(2)
            labels.forEach {
                it.gravity = Gravity.CENTER
                it.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
        }
    }

    private fun polishPersonalIcons(now: NowPanel023) {
        val tone = SrUi023.palette(now.context).blue
        textViews(now)
            .filter { it.text?.toString()?.startsWith("Base pessoal:", ignoreCase = true) == true }
            .forEach { footer ->
                val card = ancestor(footer) { it is SrSoftShadowCard023 } as? ViewGroup ?: return@forEach
                findAll(card) { it is ImageView }.take(3).forEach { image ->
                    (image as ImageView).imageTintList = ColorStateList.valueOf(tone)
                }
            }
    }

    // ---------------- Configurações ----------------

    private fun decorateSettings(settings: SettingsHub023) {
        replaceReadyMascot(settings)
        overrideAssistantCard(settings)
        ensureFloatingFontTile(settings)
    }

    private fun replaceReadyMascot(settings: SettingsHub023) {
        val title = textViews(settings).firstOrNull {
            it.text?.toString() in setOf(
                "Sr. Rotas está pronto",
                "Sr. Rotas precisa de um ajuste",
                "Sr. Rotas precisa de atenção",
            )
        } ?: return
        val card = ancestor(title) { it is SrSoftShadowCard023 } as? ViewGroup ?: return
        val mascot = findFirst(card) { it is ImageView } as? ImageView ?: return
        mascot.setImageResource(R.drawable.sr0265_settings_ready)
        mascot.scaleType = ImageView.ScaleType.CENTER_INSIDE
        mascot.layoutParams?.let { lp ->
            lp.width = SrUi023.dp(settings.context, 60)
            lp.height = SrUi023.dp(settings.context, 60)
            mascot.layoutParams = lp
        }
    }

    private fun overrideAssistantCard(settings: SettingsHub023) {
        val title = textViews(settings).firstOrNull { it.text?.toString()?.trim() == "Assistente ativo" } ?: return
        val card = ancestor(title) { it is SrSoftShadowCard023 } as? SrSoftShadowCard023 ?: return
        val minutes = ActiveAssistantPrefs0265.intervalMinutes(settings.context)
        textViews(card).firstOrNull {
            val value = it.text?.toString().orEmpty()
            value.startsWith("ATIVO") || value.startsWith("DESATIVADO")
        }?.text = if (ActiveAssistant026.isEnabled(settings.context)) "ATIVO · $minutes MIN" else "DESATIVADO"
        card.setOnClickListener { showAssistantSettings(settings) }
    }

    private fun showAssistantSettings(settings: SettingsHub023) {
        val context = settings.context
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                SrUi023.dp(context, 20), SrUi023.dp(context, 10),
                SrUi023.dp(context, 20), SrUi023.dp(context, 6),
            )
        }
        val enabled = Switch(context).apply {
            text = "Assistente ativo"
            textSize = 14f
            setTextColor(SrUi023.palette(context).ink)
            isChecked = ActiveAssistant026.isEnabled(context)
        }
        holder.addView(enabled)
        holder.addView(
            SrUi023.body(context, "Intervalo após ignorar uma sugestão", 10.5f),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 12) },
        )
        val choices = listOf("10 minutos", "12 minutos", "15 minutos")
        val spinner = SrUi023.spinner(context, choices).apply {
            setSelection(when (ActiveAssistantPrefs0265.intervalMinutes(context)) {
                10 -> 0
                12 -> 1
                else -> 2
            })
        }
        holder.addView(spinner)
        holder.addView(
            SrUi023.body(
                context,
                "A sugestão aparece como um pequeno balão ligado à janela flutuante. Toque fora para ignorar.",
                10f,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 10) },
        )
        AlertDialog.Builder(context)
            .setTitle("Assistente ativo")
            .setView(holder)
            .setPositiveButton("Salvar") { _, _ ->
                ActiveAssistant026.setEnabled(context, enabled.isChecked)
                ActiveAssistantPrefs0265.setIntervalMinutes(
                    context,
                    listOf(10, 12, 15)[spinner.selectedItemPosition.coerceIn(0, 2)],
                )
                settings.refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ensureFloatingFontTile(settings: SettingsHub023) {
        val grid = getPrivate<LinearLayout>(settings, "grid") ?: return
        if (findFirst(grid) { it.contentDescription == "sr0265_bubble_font_tile" } != null) return
        val p = SrUi023.palette(settings.context)
        val card = SrUi023.card(settings.context, 10, 16).apply {
            contentDescription = "sr0265_bubble_font_tile"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            addView(SrUi023.iconBox(context, R.drawable.sr23_ic_file_text, p.cyan, 38))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(SrUi023.dp(context, 10), 0, 0, 0)
                    addView(SrUi023.title(context, "Fonte da janela flutuante", 13f))
                    addView(
                        SrUi023.body(
                            context,
                            when (JourneyUiPreferences(context).textSize()) {
                                "small" -> "Compacta"
                                "large" -> "Grande"
                                else -> "Padrão"
                            },
                            9.5f,
                        ),
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            setOnClickListener { showFloatingFontSetting(settings) }
        }
        grid.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(settings.context, 8)
                bottomMargin = SrUi023.dp(settings.context, 4)
            },
        )
    }

    private fun showFloatingFontSetting(settings: SettingsHub023) {
        val context = settings.context
        val prefs = JourneyUiPreferences(context)
        val values = arrayOf("Compacta", "Padrão", "Grande")
        val selected = when (prefs.textSize()) {
            "small" -> 0
            "large" -> 2
            else -> 1
        }
        AlertDialog.Builder(context)
            .setTitle("Fonte da janela flutuante")
            .setSingleChoiceItems(values, selected) { dialog, which ->
                prefs.setTextSize(when (which) { 0 -> "small"; 2 -> "large"; else -> "standard" })
                JourneyBubbleController.refresh(context)
                dialog.dismiss()
                settings.refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------- IA ----------------

    private fun decorateAi(ai: AiPanel023) {
        val question = getPrivate<EditText>(ai, "question") ?: return
        val credit = getPrivate<TextView>(ai, "creditStatus") ?: return
        val intro = getPrivate<LinearLayout>(ai, "introHost") ?: return
        val messages = getPrivate<LinearLayout>(ai, "messagesHost") ?: return
        val content = intro.parent as? LinearLayout ?: return

        val mascot = findFirst(intro) {
            it is ImageView && it.contentDescription?.toString()?.contains("aguardando", ignoreCase = true) == true
        } as? ImageView
        mascot?.apply {
            setImageResource(R.drawable.sr0265_ai_mascot)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                SrUi023.dp(ai.context, if (resources.configuration.screenWidthDp < 340) 214 else 236),
                SrUi023.dp(ai.context, if (resources.configuration.screenWidthDp < 340) 164 else 184),
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = SrUi023.dp(ai.context, 2)
            }
        }

        textViews(intro).firstOrNull { it.text?.toString()?.trim() == "Como posso ajudar?" }?.visibility = View.GONE
        textViews(intro).firstOrNull {
            it.text?.toString()?.startsWith("Escolha uma sugestão", ignoreCase = true) == true
        }?.visibility = View.GONE

        question.hint = "Como posso ajudar?"
        question.minLines = 2
        question.maxLines = 4

        val inputRow = question.parent as? LinearLayout ?: return
        val composer = inputRow.parent as? LinearLayout ?: return
        composer.contentDescription = "sr0265_ai_composer"

        if (composer.parent !== content) {
            (composer.parent as? ViewGroup)?.removeView(composer)
            val index = content.indexOfChild(messages).coerceAtLeast(0)
            content.addView(
                composer,
                index,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(ai.context, 6)
                },
            )
        }

        val meta = (0 until composer.childCount)
            .map { composer.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .firstOrNull { it !== inputRow && findText(it, "Período") != null }
        if (credit.parent is ViewGroup) (credit.parent as ViewGroup).removeView(credit)
        credit.gravity = Gravity.START
        credit.textSize = 9f
        normalizeCredits(credit)
        if (credit.parent !== composer) {
            val inputIndex = composer.indexOfChild(inputRow)
            composer.addView(
                credit,
                (inputIndex + 1).coerceAtMost(composer.childCount),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(ai.context, 3)
                    marginStart = SrUi023.dp(ai.context, 2)
                },
            )
        }
        if (meta != null && composer.indexOfChild(meta) < composer.indexOfChild(inputRow)) {
            composer.removeView(meta)
            composer.addView(
                meta,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(ai.context, 4) },
            )
        }

        val suggestion = intro.childCount.takeIf { it > 0 }?.let { intro.getChildAt(intro.childCount - 1) }
        if (suggestion != null && suggestion !== mascot && suggestion is LinearLayout && suggestion.contentDescription != "sr0265_ai_suggestions") {
            intro.removeView(suggestion)
            suggestion.contentDescription = "sr0265_ai_suggestions"
            val composerIndex = content.indexOfChild(composer)
            content.addView(
                suggestion,
                (composerIndex + 1).coerceAtMost(content.childCount),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(ai.context, 8) },
            )
        }
        val movedSuggestions = findFirst(content) { it.contentDescription == "sr0265_ai_suggestions" }
        val interacted = getPrivate<Boolean>(ai, "hasInteracted") ?: false
        movedSuggestions?.visibility = if (interacted) View.GONE else View.VISIBLE
        normalizeCredits(credit)
    }

    private fun normalizeCredits(view: TextView) {
        val raw = view.text?.toString().orEmpty()
        val amount = Regex("(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        if (amount != null) {
            val suffix = when {
                raw.contains("teste", true) -> " · Teste"
                raw.contains("assine", true) -> " · Assinatura necessária"
                else -> ""
            }
            view.text = "$amount créditos restantes$suffix"
        }
    }

    // ---------------- Estatísticas / Jornadas ----------------

    private fun decorateJourneys(panel: HistoryPanel) {
        val active = getPrivate<StatisticsSection026.Section>(panel, "activeSection") ?: return
        if (active != StatisticsSection026.Section.JOURNEYS) return
        val data = getPrivate<HistoryAnalytics>(panel, "currentData") ?: return
        val content = getPrivate<LinearLayout>(panel, "content") ?: return
        val store = JourneyMetricsStore026.get(panel.context)
        val records = data.journeys.mapNotNull { journey ->
            val snapshot = store.snapshot(journey.id)
            snapshot.metric?.let { Triple(journey, it, snapshot.energyEntries) }
        }.sortedByDescending { it.first.startedAt }

        val signature = records.joinToString("|") {
            "${it.first.id}:${it.second.odometerStartKm}:${it.second.odometerEndKm}:${it.second.distanceKm}:${it.third.size}"
        }
        val existing = findFirst(content) { it.contentDescription == "sr0265_odometer_registry" }
        if (existing?.tag == signature) return
        (existing?.parent as? ViewGroup)?.removeView(existing)
        if (records.isEmpty()) return

        val card = SrUi023.card(panel.context, 12, 17).apply {
            contentDescription = "sr0265_odometer_registry"
            tag = signature
            addView(SrUi023.title(context, "Odômetros registrados", 14f))
            addView(
                SrUi023.body(
                    context,
                    "Registros já salvos nas jornadas do período, inclusive quando só um dos odômetros foi informado.",
                    9.5f,
                ),
            )
            records.take(24).forEachIndexed { index, (journey, metric, energy) ->
                if (index > 0) addView(View(context).apply {
                    setBackgroundColor(SrUi023.palette(context).outline)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    SrUi023.dp(context, 1),
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                    bottomMargin = SrUi023.dp(context, 8)
                })
                addView(SrUi023.body(context, journeyDate(journey.startedAt), 10f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(SrUi023.palette(context).ink)
                })
                addView(
                    SrUi023.body(context, buildString {
                        val start = metric.odometerStartKm
                        val end = metric.odometerEndKm
                        when {
                            start != null && end != null -> append("Odômetro: ${km(start)} → ${km(end)}")
                            start != null -> append("Odômetro inicial: ${km(start)}")
                            end != null -> append("Odômetro final: ${km(end)}")
                        }
                        metric.distanceKm?.let { append("\nPercorrido: ${km(it)}") }
                        val spend = energy.mapNotNull { it.amountPaid }.sum().takeIf { it > 0.0 }
                        spend?.let { append(String.format(Locale("pt", "BR"), " · Gasto: R$ %.2f", it)) }
                    }, 11.5f),
                )
                addView(
                    JourneyFlow026.editorButton(panel.context, journey.id) { panel.refresh(true) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = SrUi023.dp(panel.context, 5) },
                )
            }
        }
        content.addView(
            card,
            1.coerceAtMost(content.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(panel.context, 8)
                bottomMargin = SrUi023.dp(panel.context, 8)
            },
        )
    }

    private fun journeyDate(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value))
    }.getOrDefault(value.take(16).replace('T', ' '))

    private fun km(value: Double): String = String.format(Locale("pt", "BR"), "%,.1f km", value)

    // ---------------- helpers ----------------

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivate(target: Any, name: String): T? = runCatching {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.get(target) as? T
    }.getOrNull()

    private fun findText(root: View, exact: String): TextView? =
        textViews(root).firstOrNull { it.text?.toString()?.trim() == exact }

    private fun directTextViews(root: ViewGroup): List<TextView> =
        (0 until root.childCount).mapNotNull { root.getChildAt(it) as? TextView }

    private fun textViews(root: View): List<TextView> =
        findAll(root) { it is TextView }.map { it as TextView }

    private fun findFirst(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun findAll(root: View, predicate: (View) -> Boolean): List<View> {
        val out = mutableListOf<View>()
        fun walk(view: View) {
            if (predicate(view)) out += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return out
    }

    private fun ancestor(view: View, predicate: (View) -> Boolean): View? {
        var current: View? = view
        repeat(10) {
            current = current?.parent as? View
            val value = current ?: return null
            if (predicate(value)) return value
        }
        return null
    }
}
