package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.LinearLayout

/**
 * Superfície nativa com sombra suave desenhada dentro dos próprios limites.
 *
 * Evita o corte reto da elevation nativa em grids, ScrollViews e no footer.
 * Cards usam os defaults. O footer usa insets menores para preservar altura.
 */
open class SrSoftShadowCard023(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    radiusDp: Int,
    private val shadowEnabled: Boolean = true,
    shadowHorizontalDp: Int = 7,
    shadowTopDp: Int = 5,
    shadowBottomDp: Int = 11,
    blurRadiusDp: Float = 8f,
    shadowOffsetYDp: Float = 3f,
) : LinearLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val radiusPx = radiusDp * density

    private var surfaceColor = fillColor
    private var borderColor = strokeColor

    private val shadowHorizontal = dp(if (shadowEnabled) shadowHorizontalDp else 0)
    private val shadowTop = dp(if (shadowEnabled) shadowTopDp else 0)
    private val shadowBottom = dp(if (shadowEnabled) shadowBottomDp else 0)
    private val blurRadius = dpF(if (shadowEnabled) blurRadiusDp else 0f)
    private val shadowOffsetY = dpF(if (shadowEnabled) shadowOffsetYDp else 0f)

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
        setInnerPadding(paddingDp, paddingDp, paddingDp, paddingDp)
    }

    fun setInnerPadding(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int) {
        super.setPadding(
            shadowHorizontal + dp(leftDp),
            shadowTop + dp(topDp),
            shadowHorizontal + dp(rightDp),
            shadowBottom + dp(bottomDp),
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
