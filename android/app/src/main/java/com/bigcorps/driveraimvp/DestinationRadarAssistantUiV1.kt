package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Conteúdo visual reutilizável pelo renderer/overlay já existente do mascote.
 * NÃO adiciona WindowManager e NÃO mexe no ActiveAssistant026.
 */
object DestinationRadarAssistantUiV1 {
    fun content(
        context:Context,
        signal:DestinationRadarAssistantBridgeV1.Signal,
        onIgnore:()->Unit,
        onView:()->Unit,
    ):LinearLayout {
        val p=SrUi023.palette(context)
        return LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(UiKit.dp(context,12),UiKit.dp(context,9),UiKit.dp(context,12),UiKit.dp(context,9))
            background=SrUi023.rounded(p.surface,16,p.purple,1,context)
            addView(TextView(context).apply {
                text=signal.headline
                textSize=10.5f
                setTextColor(p.ink)
                maxLines=2
            })
            addView(LinearLayout(context).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.END
                addView(action(context,"Ignorar",onIgnore))
                addView(action(context,signal.action,onView).apply {
                    setPadding(UiKit.dp(context,12),0,0,0)
                })
            })
        }
    }

    private fun action(context:Context,label:String,onClick:()->Unit)=TextView(context).apply {
        text=label.uppercase()
        textSize=9f
        minHeight=UiKit.dp(context,30)
        gravity=Gravity.CENTER
        setTextColor(SrUi023.palette(context).blue)
        setOnClickListener { onClick() }
    }
}
