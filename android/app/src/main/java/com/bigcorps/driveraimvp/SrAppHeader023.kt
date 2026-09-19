package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Cabeçalho estável do Sr. Rotas. */
class SrAppHeader023(
    context: Context,
    titleText: String,
    @Suppress("UNUSED_PARAMETER") subtitleText: String,
    @Suppress("UNUSED_PARAMETER") trailingDrawable: Int? = null,
) : LinearLayout(context) {

    /**
     * RC3.6.1: RC3.5 e RC3.6 ainda possuem camadas antigas que tentavam
     * reservar/remover espaço à direita do header em intervalos diferentes.
     * Fixar o padding útil elimina o 58dp <-> 6dp que fazia título e ações
     * superiores pularem sem depender da ordem dos watchers legados.
     */
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, SrUi023.dp(context, 6), bottom)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            SrUi023.dp(context, 4),
            SrUi023.dp(context, 7),
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 7),
        )
        setBackgroundColor(
            SrTheme024.palette(Appearance021.isDark(context)).background,
        )

        val compact = context.resources.configuration.screenWidthDp < 360
        val logoWidth = if (compact) 142 else 164
        val logoHeight = if (compact) 39 else 45
        val logoRes = if (Appearance021.isDark(context)) {
            R.drawable.sr0265_header_dark
        } else {
            R.drawable.sr0265_header_light
        }

        addView(
            ImageView(context).apply {
                setImageResource(logoRes)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
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
                if (compact) 15.5f else 17.5f,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val russoOneId = resources.getIdentifier(
                    "russo_one_regular",
                    "font",
                    context.packageName,
                )
                typeface = if (russoOneId != 0) {
                    runCatching { resources.getFont(russoOneId) }
                        .getOrElse { Typeface.create("sans-serif-condensed", Typeface.NORMAL) }
                } else {
                    Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                }
                letterSpacing = 0.015f
            },
            LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = SrUi023.dp(context, 5)
            },
        )
    }

    private fun sectionName(original: String): String = when (original.trim()) {
        "Histórico" -> "Estatísticas"
        "IA do Sr. Rotas" -> "IA"
        else -> original.trim()
    }
}
