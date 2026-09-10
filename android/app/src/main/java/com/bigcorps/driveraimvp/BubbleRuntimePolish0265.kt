package com.srrotas.app

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import java.util.WeakHashMap
import kotlin.math.min

/**
 * Correções de campo da janela flutuante.
 *
 * 0.27:
 * - modo compacto real para o painel expandido;
 * - ações de corrida ligadas ao localOfferId da própria oferta;
 * - preserva todas as seleções independentes de relatório.
 */
object BubbleRuntimePolish0265 {
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false
    private val compacted = WeakHashMap<View, Boolean>()

    fun install(application: Application) {
        app = application.applicationContext
        if (!running) {
            running = true
            main.post(watcher)
        }
    }

    private val watcher = object : Runnable {
        override fun run() {
            app?.let { runCatching { polish(it) } }
            if (running) main.postDelayed(this, 450L)
        }
    }

    private fun polish(context: Context) {
        privateField<ViewGroup>(JourneyBubbleController, "railHost")
            ?.let(::fixMessageRail)

        val panel =
            privateField<ViewGroup>(JourneyBubbleController, "panel")
                ?: return

        textViews(panel).forEach { text ->
            if (text.text?.toString()?.trim() == "UberX") {
                text.text = "X"
            }
        }

        applyCompactMode(context, panel)
        fixDeepDetails(context, panel)
    }

    private fun applyCompactMode(
        context: Context,
        panel: ViewGroup,
    ) {
        val compact = JourneyUiPreferences(context).compactPanel()
        val density = context.resources.displayMetrics.density
        val screenWidthDp =
            (context.resources.displayMetrics.widthPixels / density).toInt()

        val params = panel.layoutParams
        if (params is LinearLayout.LayoutParams) {
            val widthDp =
                if (compact) {
                    min(292, (screenWidthDp - 16).coerceAtLeast(260))
                } else {
                    min(336, (screenWidthDp - 20).coerceAtLeast(270))
                }
            val wanted = SrUi023.dp(context, widthDp)
            if (params.width != wanted) {
                params.width = wanted
                panel.layoutParams = params
            }
        }

        if (compact) {
            panel.setPadding(
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 6),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 7),
            )
            compactTree(context, panel, panel)
        } else {
            panel.setPadding(
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
    ) {
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
                compactTree(context, root.getChildAt(i), panelRoot)
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

        // 0.27: cada botão atua sobre a oferta que está aberta, nunca sobre
        // um "currentRide" global que pode já ter mudado para outra corrida.
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

        if (
            findFirst(deepRoot) {
                it.contentDescription == "sr0265_verdict_underline"
            } == null
        ) {
            val color =
                when (offer.verdict) {
                    "boa" -> UiKit.palette(context).good
                    "ruim" -> UiKit.palette(context).bad
                    else -> UiKit.palette(context).warn
                }
            val financialChild =
                (0 until deepRoot.childCount)
                    .map { deepRoot.getChildAt(it) }
                    .firstOrNull { child ->
                        textViews(child).any { value ->
                            val raw = value.text?.toString().orEmpty()
                            raw.contains("/km") ||
                                raw.contains("/min") ||
                                raw.contains("/h")
                        }
                    }
            val insertAt =
                financialChild
                    ?.let { deepRoot.indexOfChild(it) + 1 }
                    ?.coerceIn(0, deepRoot.childCount)
                    ?: deepRoot.childCount

            deepRoot.addView(
                View(context).apply {
                    contentDescription = "sr0265_verdict_underline"
                    setBackgroundColor(color)
                },
                insertAt,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    SrUi023.dp(context, 2),
                ).apply {
                    topMargin = SrUi023.dp(context, 4)
                    bottomMargin = SrUi023.dp(context, 3)
                },
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            val field =
                target.javaClass
                    .getDeclaredField(name)
                    .apply { isAccessible = true }
            field.get(target) as? T
        }.getOrNull()

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(view: View) {
            if (view is TextView) out += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(root)
        return out
    }

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
