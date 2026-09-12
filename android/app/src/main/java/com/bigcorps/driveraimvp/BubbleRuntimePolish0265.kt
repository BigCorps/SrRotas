package com.srrotas.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Correções de campo da janela flutuante.
 *
 * 0.27 RC2:
 * - estabiliza o modo compacto sem disputar largura com JourneyBubbleController;
 * - aplica o polish após mudanças reais de layout, reduzindo polling;
 * - remove a borda colorida dos cards e mantém a cor na bolinha/categoria;
 * - compacta check e controles inferiores;
 * - abrevia probabilidade e textos da câmera;
 * - ações de corrida continuam ligadas ao localOfferId da própria oferta;
 * - preserva todas as seleções independentes de relatório.
 */
object BubbleRuntimePolish0265 {
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false
    private var polishing = false
    private val compacted = WeakHashMap<View, Boolean>()
    private val observedPanels = WeakHashMap<View, Boolean>()
    private val styledOfferRows = WeakHashMap<View, Boolean>()
    private val styledFooterButtons = WeakHashMap<View, Boolean>()
    private val styledDigitizationButtons = WeakHashMap<View, Boolean>()

    fun install(application: Application) {
        app = application.applicationContext
        if (!running) {
            running = true
            main.post(watcher)
        }
    }

    /**
     * RC1 usava 450 ms e também alterava a largura do painel. Como o controller
     * já controla essa largura, os dois loops podiam alternar o tamanho.
     * RC2 deixa a geometria principal com o controller e usa este watcher apenas
     * como fallback; mudanças de layout disparam polish imediatamente.
     */
    private val watcher = object : Runnable {
        override fun run() {
            app?.let { runCatching { polish(it) } }
            if (running) main.postDelayed(this, 1500L)
        }
    }

    private fun polish(context: Context) {
        if (polishing) return
        polishing = true
        try {
            privateField<ViewGroup>(JourneyBubbleController, "railHost")
                ?.let(::fixMessageRail)

            val panel =
                privateField<ViewGroup>(JourneyBubbleController, "panel")
                    ?: return

            observePanel(panel, context)
            normalizeServiceLabels(panel)
            applyCompactMode(context, panel)
            fixOfferCards(context, panel)
            fixProbabilityLabels(panel)
            fixFooterControls(context, panel)
            fixDigitizationLabels(context, panel)
            fixDeepDetails(context, panel)
        } finally {
            polishing = false
        }
    }

    private fun observePanel(
        panel: View,
        context: Context,
    ) {
        if (observedPanels[panel] == true) return
        observedPanels[panel] = true
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (polishing) return@addOnLayoutChangeListener
            runCatching { polish(context.applicationContext) }
        }
    }

    private fun normalizeServiceLabels(panel: View) {
        textViews(panel).forEach { text ->
            if (text.text?.toString()?.trim() == "UberX") {
                text.text = "X"
            }
        }
    }

    /**
     * Importante: RC2 NÃO altera mais a largura do painel.
     * JourneyBubbleController é a única fonte de verdade para largura, evitando
     * o efeito de alternância/pulo observado em campo.
     */
    private fun applyCompactMode(
        context: Context,
        panel: ViewGroup,
    ) {
        val compact = JourneyUiPreferences(context).compactPanel()

        if (compact) {
            setPaddingIfChanged(
                panel,
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 6),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 7),
            )
            val footer =
                (0 until panel.childCount)
                    .map { panel.getChildAt(it) }
                    .firstOrNull { child ->
                        findFirst(child) { view ->
                            view is ImageButton &&
                                view.contentDescription?.toString() == "Digitalizar Uber"
                        } != null
                    }
            compactTree(context, panel, panel, footer)
        } else {
            setPaddingIfChanged(
                panel,
                SrUi023.dp(context, 12),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 12),
                SrUi023.dp(context, 11),
            )
        }
    }

    private fun compactTree(
        context: Context,
        root: View,
        panelRoot: ViewGroup,
        skipRoot: View?,
    ) {
        if (root === skipRoot) return

        if (root !== panelRoot && compacted[root] != true) {
            compacted[root] = true

            if (root is TextView) {
                val sp =
                    root.textSize /
                        context.resources.displayMetrics.scaledDensity
                if (sp > 10f) {
                    root.textSize = (sp * 0.93f).coerceAtLeast(9.5f)
                }
                if (root.minHeight > SrUi023.dp(context, 34)) {
                    root.minHeight = SrUi023.dp(context, 34)
                }
            }

            if (root is ViewGroup) {
                root.setPadding(
                    (root.paddingLeft * 0.82f).toInt(),
                    (root.paddingTop * 0.72f).toInt(),
                    (root.paddingRight * 0.82f).toInt(),
                    (root.paddingBottom * 0.72f).toInt(),
                )
            }

            (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = (lp.topMargin * 0.72f).toInt()
                lp.bottomMargin = (lp.bottomMargin * 0.72f).toInt()
                lp.marginStart = (lp.marginStart * 0.85f).toInt()
                lp.marginEnd = (lp.marginEnd * 0.85f).toInt()
                root.layoutParams = lp
            }
        }

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                compactTree(context, root.getChildAt(i), panelRoot, skipRoot)
            }
        }
    }

    /**
     * Cards compactos: sem moldura verde/amarela/vermelha. A cor de avaliação
     * fica apenas na bolinha e no nome do serviço (X/Comfort/Black/etc.).
     */
    private fun fixOfferCards(
        context: Context,
        panel: ViewGroup,
    ) {
        viewGroups(panel).forEach { group ->
            if (group !is LinearLayout) return@forEach
            val directTexts =
                (0 until group.childCount)
                    .mapNotNull { group.getChildAt(it) as? TextView }
            val bullet =
                directTexts.firstOrNull {
                    it.text?.toString()?.trim() == "●"
                } ?: return@forEach
            val check =
                directTexts.firstOrNull {
                    it.text?.toString()?.trim() == "✓"
                } ?: return@forEach

            val service =
                directTexts.firstOrNull { value ->
                    val raw = value.text?.toString()?.trim().orEmpty()
                    raw.isNotBlank() &&
                        raw != "●" &&
                        raw != "✓" &&
                        raw != "⌃" &&
                        raw != "⌄" &&
                        !raw.startsWith("R$")
                }

            group.background =
                UiKit.rounded(
                    context,
                    bubblePalette(context).surfaceAlt,
                    14,
                    null,
                    0,
                )
            service?.setTextColor(bullet.currentTextColor)

            if (styledOfferRows[group] != true) {
                styledOfferRows[group] = true
                check.textSize = 15f
                check.setPadding(
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 2),
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 2),
                )
                check.minHeight = SrUi023.dp(context, 28)
                check.minimumWidth = SrUi023.dp(context, 28)
            }
        }
    }

    /**
     * No card aberto o usuário pediu somente "% Baixo/Médio/Alto".
     * Mantemos os cálculos intactos; esta função altera apenas apresentação.
     */
    private fun fixProbabilityLabels(panel: View) {
        textViews(panel).forEach { view ->
            val raw = view.text?.toString()?.trim().orEmpty()
            if (
                raw.startsWith("Probabilidade de novas corridas", ignoreCase = true) ||
                raw.startsWith("Nova corrida no destino", ignoreCase = true)
            ) {
                view.text = probabilityShortLabel(raw)
            }
        }

        // O sinal compacto deve mostrar somente "% Alto/Médio/Baixo".
        // Também remove a borda semafórica desse subcard: a cor da oferta fica
        // apenas na bolinha e no nome da categoria, como pedido no teste.
        viewGroups(panel).forEach { group ->
            if (group !is LinearLayout || group.orientation != LinearLayout.VERTICAL) {
                return@forEach
            }
            val directTexts =
                (0 until group.childCount)
                    .mapNotNull { group.getChildAt(it) as? TextView }
            if (directTexts.size != 2) return@forEach
            val value = directTexts[1].text?.toString()?.trim().orEmpty()
            if (!value.startsWith("%")) return@forEach
            val parent = group.parent as? LinearLayout ?: return@forEach
            if (parent.orientation != LinearLayout.HORIZONTAL) return@forEach

            directTexts[0].visibility = View.GONE
            group.background =
                UiKit.rounded(
                    group.context,
                    bubblePalette(group.context).surfaceAlt,
                    11,
                    null,
                    0,
                )
        }

        // O card de "Busca" ao lado usava a mesma borda verde/amarela/vermelha.
        // Remove somente quando ele faz parte da dupla de sinais compactos.
        viewGroups(panel).forEach { group ->
            if (group !is LinearLayout || group.orientation != LinearLayout.VERTICAL) {
                return@forEach
            }
            val directTexts =
                (0 until group.childCount)
                    .mapNotNull { group.getChildAt(it) as? TextView }
            if (directTexts.size != 2) return@forEach
            if (directTexts[0].text?.toString()?.trim() != "Busca") return@forEach
            val parent = group.parent as? LinearLayout ?: return@forEach
            if (parent.orientation != LinearLayout.HORIZONTAL) return@forEach
            group.background =
                UiKit.rounded(
                    group.context,
                    bubblePalette(group.context).surfaceAlt,
                    11,
                    null,
                    0,
                )
        }
    }

    private fun probabilityShortLabel(raw: String): String =
        when {
            raw.contains("alta", ignoreCase = true) ||
                raw.contains("alto", ignoreCase = true) -> "% Alto"
            raw.contains("média", ignoreCase = true) ||
                raw.contains("media", ignoreCase = true) ||
                raw.contains("médio", ignoreCase = true) ||
                raw.contains("medio", ignoreCase = true) -> "% Médio"
            raw.contains("baixa", ignoreCase = true) ||
                raw.contains("baixo", ignoreCase = true) -> "% Baixo"
            raw.contains("analis", ignoreCase = true) -> "% Analisando…"
            else -> "% Dados insuf."
        }

    private fun fixFooterControls(
        context: Context,
        panel: ViewGroup,
    ) {
        val actionDescriptions =
            setOf(
                "Iniciar ou retomar jornada",
                "Pausar jornada",
                "Encerrar jornada",
                "Abrir Estatísticas",
                "Digitalizar Uber",
                "Abrir mensagens",
                "Fechar mensagens",
                "Diagnóstico",
            )

        val buttons =
            allViews(panel)
                .filterIsInstance<ImageButton>()
                .filter {
                    it.contentDescription?.toString() in actionDescriptions
                }

        buttons.forEach { button ->
            if (styledFooterButtons[button] != true) {
                styledFooterButtons[button] = true
                button.setPadding(
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                )
                button.minimumWidth = SrUi023.dp(context, 30)
                button.minimumHeight = SrUi023.dp(context, 40)
                (button.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.height = SrUi023.dp(context, 40)
                    if (lp.marginStart > 0) {
                        lp.marginStart = SrUi023.dp(context, 2)
                    }
                    button.layoutParams = lp
                }
            }

            val palette = bubblePalette(context)
            val active =
                button.contentDescription?.toString() == "Fechar mensagens"
            button.background =
                UiKit.rounded(
                    context,
                    if (active) palette.primary else palette.surfaceAlt,
                    9,
                    if (active) palette.primary else palette.line,
                    1,
                )
        }

        buttons.firstOrNull()?.parent
            ?.let { it as? LinearLayout }
            ?.let { row ->
                setPaddingIfChanged(
                    row,
                    SrUi023.dp(context, 5),
                    SrUi023.dp(context, 5),
                    SrUi023.dp(context, 5),
                    SrUi023.dp(context, 5),
                )
            }
    }

    private fun fixDigitizationLabels(
        context: Context,
        panel: ViewGroup,
    ) {
        textViews(panel).forEach { view ->
            when (view.text?.toString()?.trim()) {
                "Digitalizar jornada" -> view.text = "Jornada"
                "Digitalizar histórico" -> view.text = "Histórico"
            }

            if (
                view.text?.toString()?.trim() in setOf("Jornada", "Histórico") &&
                styledDigitizationButtons[view] != true
            ) {
                styledDigitizationButtons[view] = true
                view.minHeight = SrUi023.dp(context, 32)
                view.setPadding(
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                    SrUi023.dp(context, 6),
                )
            }
        }
    }

    private fun fixMessageRail(rail: ViewGroup) {
        val scroll = findFirst(rail) { it is ScrollView } as? ScrollView ?: return
        if (scroll.tag == "sr0265_message_scroll") return
        scroll.tag = "sr0265_message_scroll"
        scroll.isSmoothScrollingEnabled = false
        scroll.isVerticalScrollBarEnabled = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isFocusable = true
        scroll.isFocusableInTouchMode = true
        scroll.setOnTouchListener { view, event ->
            val blockParent =
                event.actionMasked != MotionEvent.ACTION_UP &&
                    event.actionMasked != MotionEvent.ACTION_CANCEL
            view.parent?.requestDisallowInterceptTouchEvent(blockParent)
            false
        }
    }

    private fun fixDeepDetails(
        context: Context,
        panel: ViewGroup,
    ) {
        val id =
            privateField<String>(
                JourneyBubbleController,
                "deepExpandedOfferId",
            ) ?: return

        val offer =
            LocalStore.get(context)
                .recentOffers(30)
                .firstOrNull { it.localId == id }
                ?: return

        val pickupLabel =
            textViews(panel)
                .firstOrNull {
                    it.text?.toString()?.trim() == "Busca / retirada"
                } ?: return
        val pickupLine = pickupLabel.parent as? View ?: return
        val deepRoot = pickupLine.parent as? LinearLayout ?: return

        val destination =
            offer.context?.destinationLabel?.takeIf(String::isNotBlank)
                ?: offer.context?.destinationCell?.takeIf(String::isNotBlank)

        if (
            destination != null &&
            textViews(deepRoot).none {
                it.text?.toString()?.trim() == "Destino"
            }
        ) {
            val position =
                deepRoot.indexOfChild(pickupLine).coerceAtLeast(0) + 1
            deepRoot.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        SrUi023.body(context, "Destino", 10.5f).apply {
                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD,
                            )
                            setTextColor(SrUi023.palette(context).blue)
                        },
                    )
                    addView(
                        SrUi023.body(context, destination, 12.5f).apply {
                            setTextColor(SrUi023.palette(context).ink)
                        },
                    )
                },
                position.coerceAtMost(deepRoot.childCount),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 6)
                },
            )
        }

        // Cada botão atua sobre a oferta aberta, nunca sobre currentRide global.
        textViews(deepRoot)
            .firstOrNull {
                it.text?.toString()?.trim() == "REALIZADA"
            }
            ?.setOnClickListener {
                JourneyCoordinator.correctRide(
                    context,
                    offer.localId,
                    RideOperationalStatus.COMPLETED,
                )
                JourneyBubbleController.refresh(context)
            }

        textViews(deepRoot)
            .firstOrNull {
                it.text?.toString()?.trim() == "NÃO REALIZADA"
            }
            ?.setOnClickListener {
                JourneyCoordinator.correctRide(
                    context,
                    offer.localId,
                    RideOperationalStatus.NOT_COMPLETED,
                )
                JourneyBubbleController.refresh(context)
            }

        textViews(deepRoot).forEach { view ->
            val value = view.text?.toString().orEmpty()
            if (
                value.contains("R$ ") ||
                value.contains(" km") ||
                value.contains(" min")
            ) {
                val sp =
                    view.textSize /
                        context.resources.displayMetrics.scaledDensity
                if (sp < 11.5f && !JourneyUiPreferences(context).compactPanel()) {
                    view.textSize = 11.5f
                }
            }
        }

        // RC2: remove a antiga faixa verde/amarela/vermelha. A cor de avaliação
        // deve aparecer apenas na bolinha e no nome da categoria do card.
        findFirst(deepRoot) {
            it.contentDescription == "sr0265_verdict_underline"
        }?.let { underline ->
            (underline.parent as? ViewGroup)?.removeView(underline)
        }
    }


    private fun setPaddingIfChanged(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (
            view.paddingLeft == left &&
            view.paddingTop == top &&
            view.paddingRight == right &&
            view.paddingBottom == bottom
        ) {
            return
        }
        view.setPadding(left, top, right, bottom)
    }

    private fun bubbleDark(context: Context): Boolean =
        when (SettingsRepository(context).load().hudTheme.lowercase()) {
            "dark" -> true
            "light" -> false
            else -> Appearance021.isDark(context)
        }

    private fun bubblePalette(context: Context): UiKit.Palette =
        UiKit.palette(bubbleDark(context))

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            val field =
                target.javaClass
                    .getDeclaredField(name)
                    .apply { isAccessible = true }
            field.get(target) as? T
        }.getOrNull()

    private fun allViews(root: View): List<View> {
        val out = mutableListOf<View>()
        fun walk(view: View) {
            out += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(root)
        return out
    }

    private fun viewGroups(root: View): List<ViewGroup> =
        allViews(root).filterIsInstance<ViewGroup>()

    private fun textViews(root: View): List<TextView> =
        allViews(root).filterIsInstance<TextView>()

    private fun findFirst(
        root: View,
        predicate: (View) -> Boolean,
    ): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst(root.getChildAt(i), predicate)
                    ?.let { return it }
            }
        }
        return null
    }
}
