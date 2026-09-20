package com.srrotas.app

import android.content.Context
import android.util.TypedValue
import android.widget.TextView

/**
 * Regras compartilhadas de responsividade para superfícies fixas do Sr. Rotas.
 *
 * Conteúdo longo continua rolável e respeita a escala de fonte do Android.
 * Elementos que não podem crescer horizontalmente (header/nav/pills críticos)
 * usam auto-size para evitar corte e sobreposição em telas estreitas ou com
 * fonte ampliada.
 */
object ResponsiveUi027038 {
    fun screenWidthDp(context: Context): Int =
        context.resources.configuration.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()

    fun isNarrow(context: Context): Boolean = ResponsiveLayoutMath027038.isNarrow(screenWidthDp(context))
    fun isVeryNarrow(context: Context): Boolean = ResponsiveLayoutMath027038.isVeryNarrow(screenWidthDp(context))

    fun contentWidthPx(context: Context, maxDp: Int = 760, horizontalMarginDp: Int = 16): Int =
        UiKit.dp(context, ResponsiveLayoutMath027038.contentWidthDp(screenWidthDp(context), maxDp, horizontalMarginDp))

    fun autoSizeSingleLine(view: TextView, minSp: Int, maxSp: Int, stepSp: Int = 1) {
        view.maxLines = 1
        view.setAutoSizeTextTypeUniformWithConfiguration(
            minSp.coerceAtLeast(1),
            maxSp.coerceAtLeast(minSp.coerceAtLeast(1)),
            stepSp.coerceAtLeast(1),
            TypedValue.COMPLEX_UNIT_SP,
        )
    }

    fun autoSizeHeader(view: TextView, maxLines: Int = 2, minSp: Int = 12, maxSp: Int = 20) {
        view.maxLines = maxLines.coerceAtLeast(1)
        view.setAutoSizeTextTypeUniformWithConfiguration(
            minSp.coerceAtLeast(1),
            maxSp.coerceAtLeast(minSp.coerceAtLeast(1)),
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
    }
}
