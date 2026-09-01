package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.widget.EditText

/**
 * Campo numérico 0.24.2 com rótulo persistente.
 * O nome da métrica/unidade permanece visível mesmo depois do preenchimento.
 */
class SrPersistentHintEditText0242(
    context: Context,
    private val persistentLabel: String,
) : EditText(context) {
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiKit.palette(context).muted
        textSize = sp(context, 9.5f)
    }

    init {
        hint = null
        gravity = Gravity.BOTTOM
        minHeight = UiKit.dp(context, 58)
        setPadding(
            UiKit.dp(context, 13),
            UiKit.dp(context, 21),
            UiKit.dp(context, 13),
            UiKit.dp(context, 8),
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(
            persistentLabel,
            paddingLeft.toFloat(),
            UiKit.dp(context, 14).toFloat(),
            labelPaint,
        )
        super.onDraw(canvas)
    }

    private fun sp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.scaledDensity
}
