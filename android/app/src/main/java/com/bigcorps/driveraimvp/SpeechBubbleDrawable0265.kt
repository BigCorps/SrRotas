package com.srrotas.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Balão lateral cuja ponta pode mirar o ícone flutuante à esquerda ou direita. */
class SpeechBubbleDrawable0265(
    private val fill: Int,
    private val stroke: Int,
    private val radiusPx: Float,
    private val tailPx: Float,
    private val tailOnLeft: Boolean,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fill
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = stroke
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val body = if (tailOnLeft) {
            RectF(b.left + tailPx, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        } else {
            RectF(b.left.toFloat(), b.top.toFloat(), b.right - tailPx, b.bottom.toFloat())
        }
        canvas.drawRoundRect(body, radiusPx, radiusPx, fillPaint)
        canvas.drawRoundRect(body, radiusPx, radiusPx, strokePaint)

        val centerY = body.top + (body.height() * 0.34f).coerceAtLeast(radiusPx * 1.15f)
        val half = (tailPx * 0.62f).coerceAtLeast(5f)
        val path = Path().apply {
            if (tailOnLeft) {
                moveTo(body.left + 1f, centerY - half)
                lineTo(body.left + 1f, centerY + half)
                lineTo(body.left - tailPx, centerY)
            } else {
                moveTo(body.right - 1f, centerY - half)
                lineTo(body.right - 1f, centerY + half)
                lineTo(body.right + tailPx, centerY)
            }
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
