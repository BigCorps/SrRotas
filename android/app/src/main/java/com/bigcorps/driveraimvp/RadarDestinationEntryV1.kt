package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** CTA discreto da Faceta 1. Invisível quando a UI contextual está desligada. */
class RadarDestinationEntryV1(
    context: Context,
    private val onOpen: (RadarDestinationSpecV1) -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        visibility = GONE
        setPadding(0, UiKit.dp(context, 4), 0, 0)
        refresh()
    }

    fun refresh() {
        removeAllViews()
        if (!RadarContextualFlagsV1.uiEnabled(context)) {
            visibility = GONE
            return
        }
        val spec = RadarDestinationContextV1.current(context)
        visibility = if (spec == null) GONE else VISIBLE
        if (spec == null) return

        addView(
            TextView(context).apply {
                text = "⌖  Ver oportunidades no destino"
                gravity = Gravity.CENTER
                textSize = 11f
                minHeight = UiKit.dp(context, 38)
                setTextColor(SrUi023.palette(context).blue)
                background = SrUi023.rounded(
                    android.graphics.Color.TRANSPARENT,
                    14,
                    SrUi023.palette(context).blue,
                    1,
                    context,
                )
                setOnClickListener {
                    val current=RadarDestinationContextV1.current(context)
                    if(current!=null) onOpen(current)
                    else {
                        RadarContextualDiagnosticV1.surfaceRequested("now_entry","real")
                        RadarContextualDiagnosticV1.surfaceBlocked("current_spec_unavailable")
                        android.widget.Toast.makeText(context,"Destino da corrida indisponível agora.",android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }
}
