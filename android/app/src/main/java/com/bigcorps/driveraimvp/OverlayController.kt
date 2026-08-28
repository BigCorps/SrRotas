package com.srrotas.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
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
import kotlin.math.abs
import kotlin.math.max

/**
 * Overlay 0.23.0: mantém a mecânica validada da 0.22.1 e troca somente o renderer.
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

            val settings = repo.load()
            val layout = Hud023LayoutPrefs.load(appContext)
            val maxPickupMinutes = Strategy021Store.load(appContext).maxPickupMinutes
            val fingerprint = buildString {
                append(HudPresentation.visualFingerprint(offer, settings))
                append("|ui=023")
                append("|size=").append(Hud023Spec.normalizeSize(settings.hudCardSize))
                append("|fare=").append(layout.showFare)
                append("|distance=").append(layout.showDistance)
                append("|totalTime=").append(layout.showTotalTime)
                append("|grades=")
                append(settings.hudMetricOrder.split(',').joinToString(",") { key ->
                    "$key:${HudMetricEvaluation0221.grade(key.trim(), offer, settings, maxPickupMinutes) ?: -1}"
                })
            }

            val current = overlay
            val currentParams = params
            if (current != null && currentParams != null) {
                if (dragging || dragArmed) return@post
                val nextWidth = estimatedWidth(settings)
                if (currentParams.width != nextWidth) {
                    currentParams.width = nextWidth
                    runCatching { windowManager.updateViewLayout(current, currentParams) }
                }
                if (lastVisualFingerprint != fingerprint) {
                    current.removeAllViews()
                    current.addView(Hud023Renderer.build(appContext, offer, settings, layout))
                    current.requestLayout()
                    installTouch(current, currentParams, settings, durationMs, offer)
                    if (repo.loadHudPosition() == null) applyPreferredPosition(currentParams, settings)
                    lastVisualFingerprint = fingerprint
                    current.post { constrainLayout(current, currentParams) }
                }
                scheduleHide(durationMs)
                return@post
            }

            val host = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                addView(Hud023Renderer.build(appContext, offer, settings, layout))
            }
            val lp = buildParams(settings)
            installTouch(host, lp, settings, durationMs, offer)

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

    private fun buildParams(settings: DriverSettings): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            estimatedWidth(settings),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val custom = repo.loadHudPosition()
            if (custom != null) {
                x = custom.first
                y = custom.second
            } else {
                applyPreferredPosition(this, settings)
            }
        }

    private fun applyPreferredPosition(lp: WindowManager.LayoutParams, settings: DriverSettings) {
        val safe = usableBounds()
        val margin = UiKit.dp(appContext, 8)
        val width = estimatedWidth(settings)
        lp.x = when (settings.hudPosition) {
            "right" -> max(safe.left + margin, safe.right - width - margin)
            "center" -> max(safe.left + margin, safe.left + (safe.width() - width) / 2)
            else -> safe.left + margin
        }
        lp.y = safe.top + UiKit.dp(appContext, 48)
    }

    private fun installTouch(
        view: View,
        lp: WindowManager.LayoutParams,
        settings: DriverSettings,
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
                    if (settings.hudDragEnabled) {
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
                    } else if (!dragArmed && settings.hudDismissOnTap && dx <= slop && dy <= slop) {
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

    private fun estimatedWidth(settings: DriverSettings): Int =
        UiKit.dp(appContext, Hud023Renderer.preferredWidthDp(settings.hudCardSize))

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
        hideRunnable = Runnable { if (!dragging && !dragArmed) hideNow() }
            .also { main.postDelayed(it, durationMs) }
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
}
