package com.srrotas.app

import android.content.Context

/** Semáforo visual compartilhado entre Agora e Configurações. */
object StatusVisual0242 {
    fun card(
        context: Context,
        tone: String,
        padding: Int = 18,
    ): SrSoftShadowCard023 {
        val p = SrUi023.palette(context)
        val fill = when (tone) {
            "good" -> p.successSoft
            "bad" -> p.dangerSoft
            else -> p.warningSoft
        }
        val stroke = when (tone) {
            "good" -> p.teal
            "bad" -> p.red
            else -> p.orange
        }
        return SrSoftShadowCard023(
            context = context,
            fillColor = fill,
            strokeColor = stroke,
            radiusDp = 18,
            shadowEnabled = true,
        ).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setContentPadding(padding)
        }
    }
}
