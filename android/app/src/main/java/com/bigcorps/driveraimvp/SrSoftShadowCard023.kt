package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.LinearLayout

/**
 * Card nativo com sombra suave desenhada dentro dos próprios limites.
 *
 * Motivo: elevation/translationZ do Android é recortado pelos pais e produz
 * cortes retos em grids/ScrollViews. Aqui a sombra já possui uma área de
 * respiro própria, ficando visualmente próxima ao box-shadow da Web.
 */
class SrSoftShadowCard023(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    radiusDp: Int,
    private val shadowEnabled: Boolean = true,
) : LinearLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val radiusPx = radiusDp * density

    private var surfaceColor = fillColor
    private var borderColor = strokeColor

    private val shadowHorizontal = dp(if (shadowEnabled) 7 else 0)
    private val shadowTop = dp(if (shadowEnabled) 5 else 0)
    private val shadowBottom = dp(if (shadowEnabled) 11 else 0)
    private val blurRadius = dpF(8f)
    private val shadowOffsetY = dpF(3f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1f)
    }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        background = null
        elevation = 0f
        translationZ = 0f
    }

    fun setContentPadding(paddingDp: Int) {
        val p = dp(paddingDp)
        super.setPadding(
            shadowHorizontal + p,
            shadowTop + p,
            shadowHorizontal + p,
            shadowBottom + p,
        )
    }

    fun setSurfaceColors(fillColor: Int, strokeColor: Int) {
        surfaceColor = fillColor
        borderColor = strokeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(
            shadowHorizontal.toFloat(),
            shadowTop.toFloat(),
            (width - shadowHorizontal).toFloat(),
            (height - shadowBottom).toFloat(),
        )

        if (rect.width() <= 0f || rect.height() <= 0f) {
            super.onDraw(canvas)
            return
        }

        fillPaint.color = surfaceColor
        if (shadowEnabled) {
            val dark = Appearance021.isDark(context)
            fillPaint.setShadowLayer(
                blurRadius,
                0f,
                shadowOffsetY,
                if (dark) Color.argb(82, 0, 0, 0) else Color.argb(24, 7, 55, 70),
            )
        } else {
            fillPaint.clearShadowLayer()
        }

        canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
        fillPaint.clearShadowLayer()

        strokePaint.color = borderColor
        canvas.drawRoundRect(rect, radiusPx, radiusPx, strokePaint)

        super.onDraw(canvas)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpF(value: Float): Float = value * density
}
