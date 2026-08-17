package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class OverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    fun show(offer: RideOffer, durationMs: Long = 8000L) {
        main.post {
            if (!Settings.canDrawOverlays(appContext)) {
                LocalLog.append(appContext, "Overlay sem permissão SYSTEM_ALERT_WINDOW")
                return@post
            }
            hideNow()
            val settings = SettingsRepository(appContext).load()
            val view = buildCard(offer, settings)
            val gravity = when (settings.hudPosition) {
                "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                "right" -> Gravity.TOP or Gravity.END
                else -> Gravity.TOP or Gravity.START
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    // Mantém o próprio HUD fora do MediaProjection para evitar auto-OCR.
                    WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                this.gravity = gravity
                y = dp(72)
                if (settings.hudPosition == "left") x = dp(10)
                if (settings.hudPosition == "right") x = dp(10)
            }
            runCatching {
                windowManager.addView(view, params)
                overlay = view
                view.postDelayed({ if (overlay === view) hide() }, durationMs)
            }.onFailure { LocalLog.append(appContext, "Overlay falhou: ${it.message}") }
        }
    }

    private fun buildCard(o: RideOffer, s: DriverSettings): View {
        val alpha = (255 * s.hudOpacity.coerceIn(30, 100) / 100f).toInt().coerceIn(77, 255)
        val theme = when (s.hudTheme) {
            "dark" -> intArrayOf(Color.rgb(31, 35, 38), Color.WHITE)
            "green" -> intArrayOf(Color.rgb(30, 122, 73), Color.WHITE)
            else -> intArrayOf(Color.rgb(250, 250, 248), Color.rgb(18, 42, 49))
        }
        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(withAlpha(theme[0], alpha))
                setStroke(dp(2), gradeColor(overallGrade(o), s.colorBlindMode))
            }
            elevation = dp(7).toFloat()
        }

        root.addView(TextView(appContext).apply {
            text = "${when (o.verdict) { "boa" -> "BOA"; "ruim" -> "RUIM"; else -> "ATENÇÃO" }}  •  R$ ${fmt(o.fare)}"
            setTextColor(theme[1])
            textSize = (s.hudFontSize + 2).toFloat()
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        val enabled = s.hudEnabledMetrics.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val order = s.hudMetricOrder.split(',').map(String::trim).filter(String::isNotBlank)
        for (key in order) {
            if (key in enabled) metric(key, o, s, theme[1])?.let(root::addView)
        }

        val detail = listOfNotNull(
            o.totalMinutes?.let { "$it min" },
            o.totalKm?.let { "${fmt(it)} km" },
            o.serviceType.takeIf { it != "unknown" }?.uppercase(Locale.ROOT),
        ).joinToString(" • ")
        if (detail.isNotBlank()) {
            root.addView(TextView(appContext).apply {
                text = detail
                setTextColor(withAlpha(theme[1], 210))
                textSize = (s.hudFontSize - 2).coerceAtLeast(12).toFloat()
                setPadding(0, dp(5), 0, 0)
            })
        }
        return root
    }

    /**
     * 0.5 remove as bordas internas grossas da 0.4. Em aparelhos 320 dpi elas
     * podiam encostar na fonte e parecer um traço atravessando a métrica.
     * Agora cada linha usa apenas símbolo + cor, mantendo o contorno no card externo.
     */
    private fun metric(key: String, o: RideOffer, s: DriverSettings, textColor: Int): TextView? {
        val pair: Pair<String, Int>? = when (key) {
            "per_km" -> o.perKm?.let { "R$/km   ${fmt(it)}" to OfferParser.gradeHigher(it, s.redPerKmBelow, s.minPerKm) }
            "per_hour" -> o.perHour?.let { "R$/h     ${fmt(it)}" to OfferParser.gradeHigher(it, s.redPerHourBelow, s.minPerHour) }
            "per_minute" -> o.perMinute?.let { "R$/min  ${fmt(it)}" to OfferParser.gradeHigher(it, s.redPerMinuteBelow, s.minPerMinute) }
            "rating" -> o.passengerRating?.let { "★       ${fmt(it)}" to OfferParser.gradeHigher(it, s.redRatingBelow, s.goodRatingFrom) }
            "profit" -> o.estimatedProfit?.let { "Lucro   R$ ${fmt(it)}" to if (s.minProfit <= 0) 1 else if (it >= s.minProfit) 2 else 0 }
            "profit_hour" -> o.profitPerHour?.let { "Lucro/h R$ ${fmt(it)}" to OfferParser.gradeHigher(it, s.redProfitPerHourBelow, s.minProfitPerHour) }
            "profit_percent" -> o.profitPercent?.let { "Margem  ${fmt(it)}%" to OfferParser.gradeHigher(it, s.redProfitPercentBelow, s.minProfitPercent) }
            else -> null
        }
        return pair?.let { (label, grade) ->
            TextView(appContext).apply {
                val symbol = gradeSymbol(grade)
                val full = "$symbol  $label"
                val styled = SpannableString(full).apply {
                    setSpan(
                        ForegroundColorSpan(gradeColor(grade, s.colorBlindMode)),
                        0,
                        symbol.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                text = styled
                setTextColor(textColor)
                textSize = s.hudFontSize.toFloat()
                setPadding(dp(2), dp(3), dp(2), dp(3))
                setTypeface(typeface, Typeface.BOLD)
            }
        }
    }

    private fun overallGrade(o: RideOffer) = when (o.verdict) { "boa" -> 2; "ruim" -> 0; else -> 1 }
    private fun gradeSymbol(g: Int) = when (g) { 2 -> "●"; 0 -> "■"; else -> "▲" }
    private fun gradeColor(g: Int, colorBlind: Boolean) = if (colorBlind) {
        when (g) { 2 -> Color.rgb(0, 114, 178); 0 -> Color.rgb(213, 94, 0); else -> Color.rgb(230, 159, 0) }
    } else {
        when (g) { 2 -> Color.rgb(32, 183, 91); 0 -> Color.rgb(226, 59, 48); else -> Color.rgb(250, 190, 25) }
    }
    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    fun hide() = main.post { hideNow() }
    private fun hideNow() { overlay?.let { runCatching { windowManager.removeView(it) } }; overlay = null }
    private fun fmt(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
    private fun dp(v: Int) = (v * appContext.resources.displayMetrics.density).toInt()
}
