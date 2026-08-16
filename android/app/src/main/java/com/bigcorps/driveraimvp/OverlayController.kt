package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null

    fun show(offer: RideOffer) {
        main.post {
            if (!Settings.canDrawOverlays(appContext)) {
                LocalLog.append(appContext, "Overlay sem permissão SYSTEM_ALERT_WINDOW")
                return@post
            }
            hideNow()
            val view = TextView(appContext).apply {
                text = OfferParser.humanSummary(offer)
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(
                        when (offer.verdict) {
                            "boa" -> Color.rgb(22, 122, 78)
                            "ruim" -> Color.rgb(169, 54, 46)
                            else -> Color.rgb(177, 119, 24)
                        }
                    )
                    setStroke(dp(2), Color.rgb(247, 240, 200))
                }
                elevation = dp(8).toFloat()
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    // Evita o próprio HUD aparecer nas capturas do MediaProjection.
                    WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(56)
            }
            runCatching {
                windowManager.addView(view, params)
                overlay = view
                view.postDelayed({ if (overlay === view) hide() }, 8000)
            }.onFailure {
                LocalLog.append(appContext, "Overlay falhou: ${it.message}")
            }
        }
    }

    fun hide() = main.post { hideNow() }

    private fun hideNow() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()
}
