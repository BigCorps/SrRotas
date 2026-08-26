package com.srrotas.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Painel de Rota 0.22.1.
 *
 * - largura física limitada também em tablets;
 * - sem a antiga "maçaneta" lateral;
 * - cada indicador recebe sua própria cor;
 * - o modo compacto pode exibir somente os indicadores;
 * - fechar manualmente silencia somente a oferta atual.
 */
class OverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val repo = SettingsRepository(appContext)

    private var overlay: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastVisualFingerprint: String? = null
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
            if (DismissedOfferRegistry0221.shouldSuppress(offer)) return@post
            if (!Settings.canDrawOverlays(appContext)) {
                LocalLog.append(appContext, "Overlay sem permissão SYSTEM_ALERT_WINDOW")
                return@post
            }

            val s = repo.load()
            val layout = HudLayoutPrefs0221.load(appContext)
            val maxPickupMinutes = Strategy021Store.load(appContext).maxPickupMinutes
            val fingerprint = buildString {
                append(HudPresentation.visualFingerprint(offer, s))
                append("|indicatorsOnly=").append(layout.indicatorsOnlyCompact)
                append("|showFare=").append(layout.showFare)
                append("|metricGrades=")
                append(
                    s.hudMetricOrder.split(',').joinToString(",") { key ->
                        "$key:${HudMetricEvaluation0221.grade(key.trim(), offer, s, maxPickupMinutes) ?: -1}"
                    },
                )
                append("|destination=")
                append(DestinationContinuityClient0211.fingerprint(offer.localId))
            }

            val current = overlay
            val currentParams = params
            if (current != null && currentParams != null) {
                if (dragging || dragArmed) return@post
                val nextWidth = estimatedWidth(s)
                if (currentParams.width != nextWidth) {
                    currentParams.width = nextWidth
                    runCatching { windowManager.updateViewLayout(current, currentParams) }
                }
                if (lastVisualFingerprint != fingerprint) {
                    current.removeAllViews()
                    current.addView(buildCard(offer, s, layout))
                    current.requestLayout()
                    installTouch(current, currentParams, s, durationMs, offer)
                    if (repo.loadHudPosition() == null) applyPreferredPosition(currentParams, s)
                    lastVisualFingerprint = fingerprint
                    current.post { constrainLayout(current, currentParams) }
                }
                scheduleHide(durationMs)
                return@post
            }

            val host = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                addView(buildCard(offer, s, layout))
            }
            val lp = buildParams(s)
            installTouch(host, lp, s, durationMs, offer)

            runCatching {
                windowManager.addView(host, lp)
                overlay = host
                params = lp
                lastVisualFingerprint = fingerprint
                host.post { constrainLayout(host, lp) }
                scheduleHide(durationMs)
            }.onFailure { LocalLog.append(appContext, "Overlay falhou: ${it.message}") }
        }
    }

    private fun buildParams(s: DriverSettings): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            estimatedWidth(s),
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
            lp.x = custom.first
            lp.y = custom.second
        } else {
            applyPreferredPosition(lp, s)
        }
        return lp
    }

    private fun applyPreferredPosition(lp: WindowManager.LayoutParams, s: DriverSettings) {
        val safe = usableBounds()
        val margin = UiKit.dp(appContext, 8)
        val width = estimatedWidth(s)
        lp.x = when (s.hudPosition) {
            "right" -> max(safe.left + margin, safe.right - width - margin)
            "center" -> max(safe.left + margin, safe.left + (safe.width() - width) / 2)
            else -> safe.left + margin
        }
        lp.y = safe.top + UiKit.dp(appContext, 48)
    }

    private fun installTouch(
        view: View,
        lp: WindowManager.LayoutParams,
        s: DriverSettings,
        durationMs: Long,
        offer: RideOffer,
    ) {
        val slop = ViewConfiguration.get(appContext).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = lp.x
                    downY = lp.y
                    dragArmed = false
                    dragging = false
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
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!dragArmed && (abs(dx) > slop || abs(dy) > slop)) {
                        longPressRunnable?.let(main::removeCallbacks)
                    }
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
                    val dx = abs(event.rawX - downRawX)
                    val dy = abs(event.rawY - downRawY)
                    if (dragging) {
                        repo.saveHudPosition(lp.x, lp.y)
                        LocalLog.append(appContext, "HUD reposicionado x=${lp.x} y=${lp.y}")
                        scheduleHide(durationMs)
                    } else if (!dragArmed && s.hudDismissOnTap && dx <= slop && dy <= slop) {
                        DismissedOfferRegistry0221.dismiss(offer)
                        LocalLog.append(appContext, "HUD fechado por toque · oferta atual silenciada")
                        hideNow()
                    } else {
                        scheduleHide(durationMs)
                    }
                    dragArmed = false
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let(main::removeCallbacks)
                    dragArmed = false
                    dragging = false
                    scheduleHide(durationMs)
                    true
                }

                else -> true
            }
        }
    }

    private data class MetricItem(
        val key: String,
        val label: String,
        val value: String,
    )

    private fun buildCard(o: RideOffer, s: DriverSettings, layout: HudLayoutPrefs0221.State): View {
        val dark = isDark(s.hudTheme)
        val bg = if (dark) Color.rgb(7, 55, 70) else Color.rgb(255, 253, 246)
        val ink = if (dark) Color.rgb(248, 244, 223) else Color.rgb(7, 55, 70)
        val muted = if (dark) Color.rgb(169, 200, 199) else Color.rgb(96, 119, 122)
        val overall = gradeColor(overallGrade(o), s.colorBlindMode)
        val alpha = (255 * s.hudOpacity.coerceIn(30, 100) / 100f).toInt().coerceIn(77, 255)
        val size = normalizedSize(s.hudCardSize)
        val metrics = orderedMetrics(o, s)
        val maxPickupMinutes = Strategy021Store.load(appContext).maxPickupMinutes
        val indicatorsOnly = size == "compact" && layout.indicatorsOnlyCompact

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val h = when (size) { "compact" -> 5; "large" -> 9; else -> 7 }
            val v = when (size) { "compact" -> 4; "large" -> 8; else -> 6 }
            setPadding(UiKit.dp(appContext, h), UiKit.dp(appContext, v), UiKit.dp(appContext, h), UiKit.dp(appContext, v))
            if (!indicatorsOnly) {
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(appContext, 15).toFloat()
                    setColor(withAlpha(bg, alpha))
                    setStroke(UiKit.dp(appContext, 2), withAlpha(overall, alpha))
                }
                elevation = UiKit.dp(appContext, 4).toFloat()
            }
        }

        if (!indicatorsOnly) {
            val header = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(appContext).apply {
                text = when (o.verdict) {
                    "boa" -> "BOA"
                    "ruim" -> "ABAIXO"
                    else -> "ATENÇÃO"
                }
                setTextColor(Color.WHITE)
                textSize = if (size == "compact") 9f else 10f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(UiKit.dp(appContext, 7), UiKit.dp(appContext, 2), UiKit.dp(appContext, 7), UiKit.dp(appContext, 2))
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(appContext, 999).toFloat()
                    setColor(overall)
                }
            })

            if (size != "compact") {
                header.addView(TextView(appContext).apply {
                    text = serviceLabel(o)
                    setTextColor(muted)
                    textSize = (s.hudFontSize - 5).coerceIn(9, 12).toFloat()
                    setPadding(UiKit.dp(appContext, 6), 0, 0, 0)
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            } else {
                header.addView(View(appContext), LinearLayout.LayoutParams(0, 1, 1f))
            }

            if (layout.showFare) {
                header.addView(TextView(appContext).apply {
                    text = "R$ ${fmt(o.fare)}"
                    setTextColor(ink)
                    textSize = (s.hudFontSize + if (size == "large") 2 else 0).coerceIn(13, 20).toFloat()
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.END
                })
            }
            card.addView(header)
        }

        val limit = when (size) {
            "compact" -> if (indicatorsOnly) 4 else 2
            "large" -> 6
            else -> 4
        }
        val visible = metrics.take(limit)
        visible.chunked(2).forEachIndexed { rowIndex, rowMetrics ->
            val row = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                if (!indicatorsOnly || rowIndex > 0) {
                    setPadding(0, UiKit.dp(appContext, if (size == "compact") 4 else 5), 0, 0)
                }
            }
            rowMetrics.forEachIndexed { index, item ->
                val grade = HudMetricEvaluation0221.grade(item.key, o, s, maxPickupMinutes) ?: 1
                row.addView(
                    metricBox(item, grade, ink, muted, s.hudFontSize, size, s.colorBlindMode, alpha),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = UiKit.dp(appContext, 5)
                    },
                )
            }
            if (rowMetrics.size == 1) {
                row.addView(View(appContext), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    marginStart = UiKit.dp(appContext, 5)
                })
            }
            card.addView(row)
        }

        if (!indicatorsOnly && size != "compact") {
            DestinationContinuityClient0211.get(o.localId)?.let { insight ->
                val grade = when (insight.level) {
                    "high" -> 2
                    "medium" -> 1
                    "low" -> 0
                    else -> 1
                }
                val color = gradeColor(grade, s.colorBlindMode)
                card.addView(TextView(appContext).apply {
                    text = DestinationContinuityPresentation0211.hudLabel(insight)
                    setTextColor(color)
                    textSize = (s.hudFontSize - 3).coerceIn(9, 13).toFloat()
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, UiKit.dp(appContext, 5), 0, 0)
                    maxLines = 1
                })
            }
        }

        return card
    }

    private fun metricBox(
        item: MetricItem,
        grade: Int,
        ink: Int,
        muted: Int,
        base: Int,
        size: String,
        colorBlind: Boolean,
        alpha: Int,
    ): View {
        val color = gradeColor(grade, colorBlind)
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = if (size == "compact") 6 else 7
            val vertical = if (size == "compact") 4 else 5
            setPadding(
                UiKit.dp(appContext, horizontal),
                UiKit.dp(appContext, vertical),
                UiKit.dp(appContext, horizontal),
                UiKit.dp(appContext, vertical),
            )
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(appContext, 10).toFloat()
                setColor(withAlpha(color, (alpha * 0.13f).toInt().coerceIn(18, 42)))
                setStroke(UiKit.dp(appContext, 2), withAlpha(color, alpha))
            }
            addView(TextView(appContext).apply {
                text = item.label
                setTextColor(muted)
                textSize = (base - 5).coerceIn(8, 11).toFloat()
                maxLines = 1
            })
            addView(TextView(appContext).apply {
                text = item.value
                setTextColor(ink)
                textSize = (base - if (size == "compact") 2 else 1).coerceIn(11, 17).toFloat()
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            })
        }
    }

    private fun orderedMetrics(o: RideOffer, s: DriverSettings): List<MetricItem> {
        val enabled = s.hudEnabledMetrics.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val values = mapOf(
            "per_minute" to o.perMinute?.let { MetricItem("per_minute", "R$/min", fmt(it)) },
            "per_km" to o.perKm?.let { MetricItem("per_km", "R$/km", fmt(it)) },
            "rating" to o.passengerRating?.let { MetricItem("rating", "Avaliação", fmt(it)) },
            "per_hour" to o.perHour?.let { MetricItem("per_hour", "R$/h", fmt(it)) },
            "profit_hour" to o.profitPerHour?.let { MetricItem("profit_hour", "Lucro est./h", "R$ ${fmt(it)}") },
            "profit_percent" to o.profitPercent?.let { MetricItem("profit_percent", "Margem est.", "${fmt(it)}%") },
            "profit" to o.estimatedProfit?.let { MetricItem("profit", "Lucro est.*", "R$ ${fmt(it)}") },
            "pickup" to pickupLabel(o, s)?.let { MetricItem("pickup", "Busca", it) },
        )
        return s.hudMetricOrder
            .split(',')
            .map(String::trim)
            .filter { it in enabled }
            .mapNotNull { values[it] }
    }

    private fun pickupLabel(o: RideOffer, s: DriverSettings): String? {
        if (o.pickupKm == null && o.pickupMinutes == null) return null
        return PickupPresentation0211.grade(
            o.pickupKm,
            o.pickupMinutes,
            s.maxPickupKm,
            Strategy021Store.load(appContext).maxPickupMinutes,
        ).label
    }

    private fun serviceLabel(o: RideOffer): String {
        val platform = when (o.platform.lowercase(Locale.ROOT)) {
            "99" -> "99"
            "uber" -> "Uber"
            "indrive" -> "inDrive"
            "maxim" -> "Maxim"
            else -> "Motorista"
        }
        val service = o.serviceType
            .takeIf { it != "unknown" && !it.equals(o.platform, ignoreCase = true) }
            ?.replaceFirstChar { it.uppercase() }
        return if (service.isNullOrBlank()) platform else "$platform • $service"
    }

    private fun isDark(theme: String): Boolean = when (theme) {
        "dark" -> true
        "light" -> false
        else ->
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /** Limites absolutos para impedir crescimento proporcional em tablets. */
    private fun estimatedWidth(s: DriverSettings): Int = UiKit.dp(
        appContext,
        when (normalizedSize(s.hudCardSize)) {
            "compact" -> 220
            "large" -> 300
            else -> 260
        },
    )

    private fun normalizedSize(value: String) = when (value) {
        "compact", "large" -> value
        else -> "normal"
    }

    private fun usableBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val metrics = windowManager.maximumWindowMetrics
                val bounds = Rect(metrics.bounds)
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                return Rect(
                    bounds.left + insets.left,
                    bounds.top + insets.top,
                    bounds.right - insets.right,
                    bounds.bottom - insets.bottom,
                )
            }
        }
        val dm = appContext.resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun clampX(x: Int, view: View): Int {
        val safe = usableBounds()
        val margin = UiKit.dp(appContext, 4)
        val width = max(view.width, UiKit.dp(appContext, 180))
        val minX = safe.left + margin
        val maxX = max(minX, safe.right - width - margin)
        return x.coerceIn(minX, maxX)
    }

    private fun clampY(y: Int, view: View): Int {
        val safe = usableBounds()
        val margin = UiKit.dp(appContext, 4)
        val height = max(view.height, UiKit.dp(appContext, 48))
        val minY = safe.top + margin
        val maxY = max(minY, safe.bottom - height - margin)
        return y.coerceIn(minY, maxY)
    }

    private fun constrainLayout(view: View, lp: WindowManager.LayoutParams) {
        val nextX = clampX(lp.x, view)
        val nextY = clampY(lp.y, view)
        if (nextX == lp.x && nextY == lp.y) return
        lp.x = nextX
        lp.y = nextY
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun scheduleHide(durationMs: Long) {
        cancelHide()
        hideRunnable = Runnable {
            if (!dragging && !dragArmed) hideNow()
        }.also { main.postDelayed(it, durationMs) }
    }

    private fun cancelHide() {
        hideRunnable?.let(main::removeCallbacks)
        hideRunnable = null
    }

    fun hide() = main.post { hideNow() }

    private fun hideNow() {
        cancelHide()
        longPressRunnable?.let(main::removeCallbacks)
        longPressRunnable = null
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        params = null
        lastVisualFingerprint = null
        dragArmed = false
        dragging = false
    }

    private fun overallGrade(o: RideOffer) = when (o.verdict) {
        "boa" -> 2
        "ruim" -> 0
        else -> 1
    }

    private fun gradeColor(g: Int, colorBlind: Boolean) = if (colorBlind) {
        when (g) {
            2 -> Color.rgb(0, 114, 178)
            0 -> Color.rgb(213, 94, 0)
            else -> Color.rgb(230, 159, 0)
        }
    } else {
        when (g) {
            2 -> Color.rgb(16, 168, 134)
            0 -> Color.rgb(217, 92, 82)
            else -> Color.rgb(230, 182, 49)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun fmt(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
}
