package com.bigcorps.driveraimvp

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlay: TextView? = null

    fun show(offer: RideOffer) {
        hide()
        val view = TextView(service).apply {
            text = OfferParser.humanSummary(offer)
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(
                    when (offer.verdict) {
                        "boa" -> Color.rgb(24, 120, 72)
                        "ruim" -> Color.rgb(160, 45, 45)
                        else -> Color.rgb(145, 104, 24)
                    }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        runCatching {
            windowManager.addView(view, params)
            overlay = view
            view.postDelayed({ if (overlay === view) hide() }, 8500)
        }.onFailure {
            LocalLog.append(service, "Overlay falhou: ${it.message}")
        }
    }

    fun hide() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}
