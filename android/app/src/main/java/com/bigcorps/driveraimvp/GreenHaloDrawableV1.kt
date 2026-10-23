package com.srrotas.app
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Soft halo drawn inside the existing floating icon, without a second overlay. */
class GreenHaloDrawableV1(context: Context, private val surface: Int, green: Boolean) : Drawable() {
    private val density=context.resources.displayMetrics.density
    private val tint=if(green) 0xFF2ADD87.toInt() else 0xFFD3A338.toInt()
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL; color=surface; setShadowLayer(5f*density,0f,0f,tint)
    }
    private val outline=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE; strokeWidth=1.8f*density; color=tint
    }
    override fun draw(canvas: Canvas) {
        val r=(min(bounds.width(),bounds.height())/2f-5f*density).coerceAtLeast(1f)
        canvas.drawCircle(bounds.exactCenterX(),bounds.exactCenterY(),r,fill)
        canvas.drawCircle(bounds.exactCenterX(),bounds.exactCenterY(),r,outline)
    }
    override fun setAlpha(alpha: Int) { fill.alpha=alpha; outline.alpha=alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { fill.colorFilter=cf; outline.colorFilter=cf }
    @Deprecated("Android API") override fun getOpacity(): Int=PixelFormat.TRANSLUCENT
}
