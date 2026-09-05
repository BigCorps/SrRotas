package com.srrotas.app

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Header comercial 0.26.4.
 *
 * Mantém a marca oficial sempre à esquerda e o nome da seção à direita.
 * Nesta revisão fina, o logo avança mais para a borda esquerda, o título fica
 * um pouco menor e nomes longos passam a ter proteção por ellipsize.
 */
class SrAppHeader023(
    context: Context,
    titleText: String,
    @Suppress("UNUSED_PARAMETER") subtitleText: String,
    @Suppress("UNUSED_PARAMETER") trailingDrawable: Int? = null,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 8),
            SrUi023.dp(context, 12),
            SrUi023.dp(context, 8),
        )
        setBackgroundColor(
            SrTheme024.palette(Appearance021.isDark(context)).background,
        )

        val compact = context.resources.configuration.screenWidthDp < 360
        val logoWidth = if (compact) 156 else 182
        val logoHeight = if (compact) 42 else 48

        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.sr_rotas_brand_official_0263)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Senhor Rotas"
            },
            LayoutParams(
                SrUi023.dp(context, logoWidth),
                SrUi023.dp(context, logoHeight),
            ),
        )

        addView(
            SrUi023.title(
                context,
                sectionName(titleText),
                if (compact) 16.5f else 18.5f,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = SrUi023.dp(context, 6)
            },
        )
    }

    private fun sectionName(original: String): String = when (original.trim()) {
        "Histórico" -> "Estatísticas"
        "IA do Sr. Rotas" -> "IA"
        else -> original.trim()
    }
}
