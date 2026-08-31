package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/**
 * Cabeçalho padrão das telas internas 0.24.
 *
 * Não repete logo, nome do aplicativo ou slogan. Cada tela mostra somente
 * seu título e, quando útil, um subtítulo específico.
 */
open class SrSectionHeader024(
    context: Context,
    titleText: String,
    subtitleText: String? = null,
    accent: Int? = null,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(
            SrUi023.dp(context, 16),
            SrUi023.dp(context, 18),
            SrUi023.dp(context, 16),
            SrUi023.dp(context, 12),
        )
        setBackgroundColor(SrTheme024.palette(Appearance021.isDark(context)).background)

        val title = SrUi023.title(context, titleText, 26f).apply {
            accent?.let { setTextColor(it) }
        }
        addView(
            title,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        if (!subtitleText.isNullOrBlank()) {
            addView(
                SrUi023.body(context, subtitleText, 11.5f),
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = SrUi023.dp(context, 3)
                },
            )
        }
    }
}
