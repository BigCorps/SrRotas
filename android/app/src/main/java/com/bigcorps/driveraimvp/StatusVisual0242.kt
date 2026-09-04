package com.srrotas.app

import android.content.Context
import android.graphics.Color

/** Semáforo visual compartilhado entre Agora e Configurações. */
object StatusVisual0242 {
    fun toneColor(context: Context, tone: String): Int {
        val p = SrUi023.palette(context)
        val dark = Appearance021.isDark(context)
        return when {
            tone == "good" && dark -> Color.rgb(112, 255, 134)
            tone == "good" -> SrTheme024.palette(false).good
            tone == "bad" -> p.red
            else -> p.orange
        }
    }

    fun card(
        context: Context,
        tone: String,
        padding: Int = 18,
    ): SrSoftShadowCard023 {
        val p = SrUi023.palette(context)
        val dark = Appearance021.isDark(context)
        val fill = when {
            tone == "good" && dark -> p.surface
            tone == "good" -> p.successSoft
            tone == "bad" -> p.dangerSoft
            else -> p.warningSoft
        }
        val stroke = toneColor(context, tone)
        return SrSoftShadowCard023(
            context = context,
            fillColor = fill,
            strokeColor = stroke,
            radiusDp = 18,
            shadowEnabled = true,
        ).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setContentPadding(padding)
            if (tone == "good" && dark) setStrokeWidthDp(2)
        }
    }
}
