package com.srrotas.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout

/** Identidade visual única da Base/Inteligência Coletiva. */
object CollectiveVisual0242 {
    fun frame(
        context: Context,
        child: View,
        borderDp: Int = 4,
    ): View = FrameLayout(context).apply {
        val dark = Appearance021.isDark(context)
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            SrTheme024.collectiveGradientStops(dark),
        ).apply {
            cornerRadius = SrUi023.dp(context, 19).toFloat()
        }
        setPadding(
            SrUi023.dp(context, borderDp),
            SrUi023.dp(context, borderDp),
            SrUi023.dp(context, borderDp),
            SrUi023.dp(context, borderDp),
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
