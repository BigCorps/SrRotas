package com.srrotas.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Invólucro estático da IA: aplica uma única vez o enquadramento aprovado do
 * mascote. Não registra ticker, GlobalLayoutListener ou reflection.
 */
class ConsolidatedAiPanel027037(context: android.content.Context) : FrameLayout(context) {
    private val panel = AiPanel023(context)

    init {
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        panel.post { enlargeMascotOnce() }
    }

    private fun enlargeMascotOnce() {
        val mascot = findFirst(panel) {
            it is ImageView && it.contentDescription?.toString()?.contains("aguardando", true) == true
        } as? ImageView ?: return
        val compact = resources.configuration.screenWidthDp < 360
        val width = SrUi023.dp(context, if (compact) 268 else 310)
        val height = SrUi023.dp(context, if (compact) 214 else 250)
        mascot.layoutParams = LinearLayout.LayoutParams(width, height).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = SrUi023.dp(context, 1)
        }
        mascot.setImageResource(R.drawable.sr0265_ai_mascot)
        mascot.scaleType = ImageView.ScaleType.FIT_CENTER
        mascot.adjustViewBounds = true

        val parent = mascot.parent as? LinearLayout ?: return
        if (parent.findViewWithTag<View>("sr37_ai_fade") == null) {
            val fade = View(context).apply {
                tag = "sr37_ai_fade"
                contentDescription = "sr37_ai_fade"
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, UiKit.palette(context).background),
                )
            }
            parent.addView(
                fade,
                (parent.indexOfChild(mascot) + 1).coerceAtMost(parent.childCount),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    SrUi023.dp(context, 44),
                ).apply { topMargin = -SrUi023.dp(context, 38) },
            )
        }
    }

    private fun findFirst(root: View, test: (View) -> Boolean): View? {
        if (test(root)) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst(root.getChildAt(i), test)?.let { return it }
            }
        }
        return null
    }
}
