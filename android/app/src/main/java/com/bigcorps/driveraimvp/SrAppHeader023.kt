package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Cabeçalho 0.26.5 da identidade oficial do Sr. Rotas.
 *
 * - assinatura específica para tema claro/escuro;
 * - marca encostada no limite útil esquerdo;
 * - seção alinhada ao limite útil direito;
 * - título menor e condensado para telas estreitas / fonte ampliada.
 *
 * A família Android condensada é o fallback seguro do APK para a linguagem
 * visual solicitada. Não adicionamos dependência de fonte externa ao runtime.
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
            SrUi023.dp(context, 4),
            SrUi023.dp(context, 7),
            SrUi023.dp(context, 10),
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
                typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                letterSpacing = 0.025f
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
