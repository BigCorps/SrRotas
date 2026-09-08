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

/** Correções de campo da janela flutuante sem reabrir o ciclo do OCR. */
object BubbleRuntimePolish0265 {
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false

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
            if (running) main.postDelayed(this, 550L)
        }
    }

    private fun polish(context: Context) {
        privateField<ViewGroup>(JourneyBubbleController, "railHost")?.let(::fixMessageRail)
        val panel = privateField<ViewGroup>(JourneyBubbleController, "panel") ?: return
        textViews(panel).forEach { text ->
            if (text.text?.toString()?.trim() == "UberX") text.text = "X"
        }
        fixDeepDetails(context, panel)
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
            val blockParent = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            view.parent?.requestDisallowInterceptTouchEvent(blockParent)
            false
        }
    }

    private fun fixDeepDetails(context: Context, panel: ViewGroup) {
        val id = privateField<String>(JourneyBubbleController, "deepExpandedOfferId") ?: return
        val offer = LocalStore.get(context).recentOffers(30).firstOrNull { it.localId == id } ?: return
        val pickupLabel = textViews(panel).firstOrNull { it.text?.toString()?.trim() == "Busca / retirada" } ?: return
        val pickupLine = pickupLabel.parent as? View ?: return
        val deepRoot = pickupLine.parent as? LinearLayout ?: return

        val destination = offer.context?.destinationLabel?.takeIf(String::isNotBlank)
            ?: offer.context?.destinationCell?.takeIf(String::isNotBlank)
        if (destination != null && textViews(deepRoot).none { it.text?.toString()?.trim() == "Destino" }) {
            val position = deepRoot.indexOfChild(pickupLine).coerceAtLeast(0) + 1
            deepRoot.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(SrUi023.body(context, "Destino", 10.5f).apply {
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(SrUi023.palette(context).blue)
                    })
                    addView(SrUi023.body(context, destination, 12.5f).apply {
                        setTextColor(SrUi023.palette(context).ink)
                    })
                },
                position.coerceAtMost(deepRoot.childCount),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 6) },
            )
        }

        textViews(deepRoot).forEach { view ->
            val value = view.text?.toString().orEmpty()
            if (value.contains("R$ ") || value.contains(" km") || value.contains(" min")) {
                if (view.textSize / context.resources.displayMetrics.scaledDensity < 11.5f) view.textSize = 11.5f
            }
        }

        if (findFirst(deepRoot) { it.contentDescription == "sr0265_verdict_underline" } == null) {
            val color = when (offer.verdict) {
                "boa" -> UiKit.palette(context).good
                "ruim" -> UiKit.palette(context).bad
                else -> UiKit.palette(context).warn
            }
            val financialChild = (0 until deepRoot.childCount)
                .map { deepRoot.getChildAt(it) }
                .firstOrNull { child ->
                    textViews(child).any { value ->
                        val raw = value.text?.toString().orEmpty()
                        raw.contains("/km") || raw.contains("/min") || raw.contains("/h")
                    }
                }
            val insertAt = financialChild
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
    private fun <T> privateField(target: Any, name: String): T? = runCatching {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.get(target) as? T
    }.getOrNull()

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(view: View) {
            if (view is TextView) out += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return out
    }

    private fun findFirst(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findFirst(root.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }
}
