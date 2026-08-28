package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Cabeçalho único das telas principais do APK. */
class SrAppHeader023(
    context: Context,
    titleText: String,
    subtitleText: String,
    trailingDrawable: Int? = null,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        background = SrUi023.headerBackground(context)
        setPadding(SrUi023.dp(context, 16), SrUi023.dp(context, 12), SrUi023.dp(context, 16), SrUi023.dp(context, 19))

        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(context).apply {
            setImageResource(R.drawable.logo_srrotas)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Sr. Rotas"
        }, LayoutParams(SrUi023.dp(context, 46), SrUi023.dp(context, 46)))
        top.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(SrUi023.dp(context, 10), 0, 0, 0)
            addView(SrUi023.title(context, "Sr. Rotas", 17f, true))
            addView(SrUi023.body(context, "Seu copiloto de rentabilidade", 10f, true))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        trailingDrawable?.let { drawable ->
            top.addView(ImageView(context).apply {
                setImageResource(drawable)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LayoutParams(SrUi023.dp(context, 76), SrUi023.dp(context, 58)))
        }
        addView(top)

        addView(SrUi023.title(context, titleText, 25f, true), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = SrUi023.dp(context, 12)
        })
        addView(SrUi023.body(context, subtitleText, 11.5f, true), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = SrUi023.dp(context, 2)
        })
    }
}
