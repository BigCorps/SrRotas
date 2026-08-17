package com.srrotas.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Painel de Rota — HUD 0.6.
 *
 * A lógica financeira continua no Offer Engine v1 (parser 0.5.4).
 * Aqui ficam somente apresentação e gestos do card.
 */
class OverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val repo = SettingsRepository(appContext)

    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var hideRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var dragArmed = false
    private var dragging = false

    fun show(offer: RideOffer, durationMs: Long = 8000L) {
        main.post {
            if (!Settings.canDrawOverlays(appContext)) {
                LocalLog.append(appContext, "Overlay sem permissão SYSTEM_ALERT_WINDOW")
                return@post
            }
            hideNow()
            val s = repo.load()
            val view = buildCard(offer, s)
            val lp = buildParams(s)
            installTouch(view, lp, s, durationMs)
            runCatching {
                windowManager.addView(view, lp)
                overlay = view
                params = lp
                scheduleHide(durationMs)
            }.onFailure { LocalLog.append(appContext, "Overlay falhou: ${it.message}") }
        }
    }

    private fun buildParams(s: DriverSettings): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val custom = repo.loadHudPosition()
        if (custom != null) {
            lp.x = custom.first; lp.y = custom.second
        } else {
            val width = appContext.resources.displayMetrics.widthPixels
            lp.x = when (s.hudPosition) {
                "right" -> max(UiKit.dp(appContext, 8), width - estimatedWidth(s) - UiKit.dp(appContext, 8))
                "center" -> max(UiKit.dp(appContext, 8), (width - estimatedWidth(s)) / 2)
                else -> UiKit.dp(appContext, 8)
            }
            lp.y = UiKit.dp(appContext, 70)
        }
        return lp
    }

    private fun installTouch(view: View, lp: WindowManager.LayoutParams, s: DriverSettings, durationMs: Long) {
        val slop = ViewConfiguration.get(appContext).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY; downX = lp.x; downY = lp.y
                    dragArmed = false; dragging = false
                    longPressRunnable?.let(main::removeCallbacks)
                    if (s.hudDragEnabled) {
                        longPressRunnable = Runnable {
                            if (overlay === view) {
                                dragArmed = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                cancelHide()
                            }
                        }.also { main.postDelayed(it, 360L) }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt(); val dy = (event.rawY - downRawY).toInt()
                    if (!dragArmed && (abs(dx) > slop || abs(dy) > slop)) longPressRunnable?.let(main::removeCallbacks)
                    if (dragArmed) {
                        dragging = true
                        lp.x = clampX(downX + dx, view)
                        lp.y = clampY(downY + dy, view)
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let(main::removeCallbacks)
                    val dx = abs(event.rawX - downRawX); val dy = abs(event.rawY - downRawY)
                    if (dragging) {
                        repo.saveHudPosition(lp.x, lp.y)
                        LocalLog.append(appContext, "HUD reposicionado x=${lp.x} y=${lp.y}")
                        scheduleHide(durationMs)
                    } else if (!dragArmed && s.hudDismissOnTap && dx <= slop && dy <= slop) {
                        LocalLog.append(appContext, "HUD fechado por toque")
                        hideNow()
                    } else {
                        scheduleHide(durationMs)
                    }
                    dragArmed = false; dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let(main::removeCallbacks)
                    dragArmed = false; dragging = false; scheduleHide(durationMs); true
                }
                else -> true
            }
        }
    }

    private fun buildCard(o: RideOffer, s: DriverSettings): View {
        val dark = isDark(s.hudTheme)
        val bg = if (dark) Color.rgb(15, 31, 45) else Color.rgb(255, 253, 248)
        val ink = if (dark) Color.rgb(248, 250, 252) else Color.rgb(15, 42, 59)
        val muted = if (dark) Color.rgb(184, 201, 213) else Color.rgb(91, 111, 124)
        val line = if (dark) Color.rgb(48, 74, 91) else Color.rgb(216, 229, 234)
        val grade = gradeColor(overallGrade(o), s.colorBlindMode)
        val alpha = (255 * s.hudOpacity.coerceIn(30, 100) / 100f).toInt().coerceIn(77, 255)
        val size = when (s.hudCardSize) { "compact", "large" -> s.hudCardSize; else -> "normal" }

        val outer = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(appContext, 2), UiKit.dp(appContext, 2), UiKit.dp(appContext, 2), UiKit.dp(appContext, 2))
        }
        outer.addView(RouteRailView(appContext, Color.rgb(27, 183, 199), grade, Color.rgb(245, 158, 11)), LinearLayout.LayoutParams(UiKit.dp(appContext, 18), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            marginEnd = UiKit.dp(appContext, 6)
        })

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val h = when (size) { "compact" -> 11; "large" -> 17; else -> 14 }
            val v = when (size) { "compact" -> 9; "large" -> 15; else -> 12 }
            setPadding(UiKit.dp(appContext, h), UiKit.dp(appContext, v), UiKit.dp(appContext, h), UiKit.dp(appContext, v))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(appContext, 18).toFloat(); setColor(withAlpha(bg, alpha)); setStroke(UiKit.dp(appContext, 1), withAlpha(line, alpha))
            }
            elevation = UiKit.dp(appContext, 8).toFloat()
            minimumWidth = estimatedWidth(s) - UiKit.dp(appContext, 24)
        }
        outer.addView(card)

        val header = LinearLayout(appContext).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = TextView(appContext).apply {
            text = when (o.verdict) { "boa" -> "BOA"; "ruim" -> "ABAIXO"; else -> "ATENÇÃO" }
            setTextColor(Color.WHITE); textSize = if (size == "compact") 10f else 11f; setTypeface(typeface, Typeface.BOLD)
            setPadding(UiKit.dp(appContext, 9), UiKit.dp(appContext, 4), UiKit.dp(appContext, 9), UiKit.dp(appContext, 4))
            background = GradientDrawable().apply { cornerRadius = UiKit.dp(appContext, 999).toFloat(); setColor(grade) }
        }
        header.addView(badge)
        if (size != "compact") {
            header.addView(TextView(appContext).apply {
                text = serviceLabel(o); setTextColor(muted); textSize = (s.hudFontSize - 4).coerceAtLeast(11).toFloat(); setPadding(UiKit.dp(appContext, 8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        } else header.addView(View(appContext), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(TextView(appContext).apply {
            text = "R$ ${fmt(o.fare)}"; setTextColor(ink); textSize = (s.hudFontSize + if (size == "large") 8 else 5).toFloat(); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
        })
        card.addView(header)

        val metrics = orderedMetrics(o, s)
        if (size == "compact") {
            val text = metrics.take(3).joinToString("  ·  ") { "${it.first} ${it.second}" }
            if (text.isNotBlank()) card.addView(TextView(appContext).apply {
                this.text = text; setTextColor(ink); textSize = s.hudFontSize.toFloat(); setTypeface(typeface, Typeface.BOLD); setPadding(0, UiKit.dp(appContext, 6), 0, 0); maxLines = 1
            })
        } else {
            metrics.take(if (size == "large") 6 else 4).chunked(2).forEach { rowMetrics ->
                val row = LinearLayout(appContext).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, UiKit.dp(appContext, 7), 0, 0) }
                rowMetrics.forEach { pair -> row.addView(metricBlock(pair.first, pair.second, ink, muted, s.hudFontSize, size), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
                if (rowMetrics.size == 1) row.addView(View(appContext), LinearLayout.LayoutParams(0, 1, 1f))
                card.addView(row)
            }
        }

        val details = listOfNotNull(o.totalMinutes?.let { "$it min" }, o.totalKm?.let { "${fmt(it)} km" }, o.passengerRating?.let { "★ ${fmt(it)}" }).joinToString("  ·  ")
        if (details.isNotBlank()) card.addView(TextView(appContext).apply {
            text = details; setTextColor(muted); textSize = (s.hudFontSize - 3).coerceAtLeast(11).toFloat(); setPadding(0, UiKit.dp(appContext, 7), 0, 0); maxLines = 1
        })
        if (size == "large" && (s.hudDismissOnTap || s.hudDragEnabled)) card.addView(TextView(appContext).apply {
            text = buildString { if (s.hudDismissOnTap) append("Toque fecha"); if (s.hudDismissOnTap && s.hudDragEnabled) append("  •  "); if (s.hudDragEnabled) append("Segure e arraste") }
            setTextColor(withAlpha(muted, 190)); textSize = 10f; setPadding(0, UiKit.dp(appContext, 7), 0, 0)
        })
        return outer
    }

    private fun metricBlock(label: String, value: String, ink: Int, muted: Int, base: Int, size: String): View = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(appContext).apply { text = label; setTextColor(muted); textSize = (base - 4).coerceAtLeast(10).toFloat() })
        addView(TextView(appContext).apply { text = value; setTextColor(ink); textSize = (base + if (size == "large") 2 else 0).toFloat(); setTypeface(typeface, Typeface.BOLD) })
    }

    private fun orderedMetrics(o: RideOffer, s: DriverSettings): List<Pair<String, String>> {
        val enabled = s.hudEnabledMetrics.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val values = mapOf(
            "per_minute" to o.perMinute?.let { "R$/min" to fmt(it) },
            "per_km" to o.perKm?.let { "R$/km" to fmt(it) },
            "rating" to o.passengerRating?.let { "Nota" to fmt(it) },
            "per_hour" to o.perHour?.let { "R$/h" to fmt(it) },
            "profit_hour" to o.profitPerHour?.let { "Lucro/h" to "R$ ${fmt(it)}" },
            "profit_percent" to o.profitPercent?.let { "Margem" to "${fmt(it)}%" },
            "profit" to o.estimatedProfit?.let { "Lucro" to "R$ ${fmt(it)}" },
        )
        return s.hudMetricOrder.split(',').map(String::trim).filter { it in enabled }.mapNotNull { values[it] }
    }

    private fun serviceLabel(o: RideOffer): String {
        val service = o.serviceType.takeIf { it != "unknown" }?.replaceFirstChar { it.uppercase() } ?: "Uber"
        val mode = if (o.offerType == "radar") "Radar" else "Exclusivo"
        return "$service  •  $mode"
    }

    private fun isDark(theme: String): Boolean = when (theme) {
        "dark" -> true
        "light" -> false
        else -> (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun estimatedWidth(s: DriverSettings): Int = UiKit.dp(appContext, when (s.hudCardSize) { "compact" -> 270; "large" -> 350; else -> 315 })
    private fun clampX(x: Int, view: View): Int = x.coerceIn(0, max(0, appContext.resources.displayMetrics.widthPixels - max(view.width, UiKit.dp(appContext, 220))))
    private fun clampY(y: Int, view: View): Int = y.coerceIn(UiKit.dp(appContext, 8), max(UiKit.dp(appContext, 8), appContext.resources.displayMetrics.heightPixels - max(view.height, UiKit.dp(appContext, 90)) - UiKit.dp(appContext, 24)))
    private fun scheduleHide(durationMs: Long) { cancelHide(); hideRunnable = Runnable { if (!dragging) hideNow() }.also { main.postDelayed(it, durationMs) } }
    private fun cancelHide() { hideRunnable?.let(main::removeCallbacks); hideRunnable = null }
    fun hide() = main.post { hideNow() }
    private fun hideNow() { cancelHide(); longPressRunnable?.let(main::removeCallbacks); longPressRunnable = null; overlay?.let { runCatching { windowManager.removeView(it) } }; overlay = null; params = null }
    private fun overallGrade(o: RideOffer) = when (o.verdict) { "boa" -> 2; "ruim" -> 0; else -> 1 }
    private fun gradeColor(g: Int, colorBlind: Boolean) = if (colorBlind) { when (g) { 2 -> Color.rgb(0, 114, 178); 0 -> Color.rgb(213, 94, 0); else -> Color.rgb(230, 159, 0) } } else { when (g) { 2 -> Color.rgb(20, 184, 166); 0 -> Color.rgb(225, 64, 93); else -> Color.rgb(217, 151, 16) } }
    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    private fun fmt(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)

    private class RouteRailView(context: Context, private val lineColor: Int, private val topColor: Int, private val bottomColor: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val top = UiKit.dp(context, 12).toFloat(); val bottom = height - UiKit.dp(context, 12).toFloat()
            paint.strokeWidth = UiKit.dp(context, 3).toFloat(); paint.color = lineColor; paint.strokeCap = Paint.Cap.ROUND; canvas.drawLine(cx, top, cx, bottom, paint)
            paint.style = Paint.Style.FILL; paint.color = topColor; canvas.drawCircle(cx, top, UiKit.dp(context, 5).toFloat(), paint)
            paint.color = bottomColor; canvas.drawCircle(cx, bottom, UiKit.dp(context, 4).toFloat(), paint)
        }
    }
}
