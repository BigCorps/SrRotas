package com.srrotas.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout

/** Identidade visual da Base/Inteligência Coletiva — refinada na 0.26. */
object CollectiveVisual0242 {
    fun frame(
        context: Context,
        child: View,
        borderDp: Int = 1,
    ): View = FrameLayout(context).apply {
        val purple = SrUi023.palette(context).purple
        background = GradientDrawable().apply {
            setColor(purple)
            cornerRadius = SrUi023.dp(context, 19).toFloat()
        }
        val effectiveBorder = 1
        setPadding(
            SrUi023.dp(context, effectiveBorder),
            SrUi023.dp(context, effectiveBorder),
            SrUi023.dp(context, effectiveBorder),
            SrUi023.dp(context, effectiveBorder),
        )
        addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
}
