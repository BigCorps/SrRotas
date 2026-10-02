package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Componente discreto para a tela/janela em corrida.
 * O host decide onde encaixar. Não muda JourneyCoordinator.
 */
class RadarDestinationEntryV1(
    context:Context,
    private val onOpen:(RadarDestinationSpecV1)->Unit,
):LinearLayout(context) {
    init {
        orientation=VERTICAL
        visibility=GONE
        setPadding(0,UiKit.dp(context,4),0,0)
        refresh()
    }

    fun refresh() {
        removeAllViews()
        val spec=RadarDestinationContextV1.current(context)
        visibility=if(spec==null) GONE else VISIBLE
        if(spec==null) return

        addView(TextView(context).apply {
            text="⌖  Ver oportunidades no destino"
            gravity=Gravity.CENTER
            textSize=11f
            minHeight=UiKit.dp(context,38)
            setTextColor(SrUi023.palette(context).blue)
            background=SrUi023.rounded(
                android.graphics.Color.TRANSPARENT,14,
                SrUi023.palette(context).blue,1,context
            )
            setOnClickListener { onOpen(spec) }
        },LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
    }
}
