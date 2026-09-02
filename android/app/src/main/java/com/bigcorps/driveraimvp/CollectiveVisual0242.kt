package com.srrotas.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout

/**
 * Identidade visual única da Base/Inteligência Coletiva.
 *
 * 0.25 mantém o gradiente aprovado no Histórico, mas reduz a moldura para uma
 * assinatura fina. Callers antigos ainda podem passar 4dp; o componente limita
 * a espessura visual a 2dp para manter consistência em Histórico e Agora.
 */
object CollectiveVisual0242 {
    fun frame(
        context: Context,
        child: View,
        borderDp: Int = 2,
    ): View = FrameLayout(context).apply {
        val dark = Appearance021.isDark(context)
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            SrTheme024.collectiveGradientStops(dark),
        ).apply {
            cornerRadius = SrUi023.dp(context, 19).toFloat()
        }
        val effectiveBorder = borderDp.coerceIn(1, 2)
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
