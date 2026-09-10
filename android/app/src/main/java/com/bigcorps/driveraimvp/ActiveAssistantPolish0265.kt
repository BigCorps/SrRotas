package com.srrotas.app

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 0.27 — Assistente Ativo minimalista e ancorado à posição REAL do ícone.
 *
 * Tocar fora equivale a informar que o motorista está em corrida:
 * a sugestão fecha e fica suprimida até surgir uma nova oferta.
 */
object ActiveAssistantPolish0265 {
    private const val ACTIVE_PREFS = "sr_active_assistant_026"
    private const val KEY_MANUAL_RIDE_OFFER = "manual_ride_offer"

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var lastDecorated: View? = null
    private var running = false

    fun install(application: Application) {
        app = application.applicationContext
        if (!running) {
            running = true
            main.post(watcher)
        }
    }

    private val watcher = object : Runnable {
        override fun run() {
            val context = app
            if (context != null) runCatching { decorateCurrent(context) }
            if (running) main.postDelayed(this, 350L)
        }
    }

    private fun decorateCurrent(context: Context) {
        val overlay = privateField<View>(ActiveAssistant026, "overlay") ?: run {
            lastDecorated = null
            return
        }
        val card = overlay as? LinearLayout ?: return
        val wm = privateField<WindowManager>(ActiveAssistant026, "overlayManager") ?: return
        val lp = card.layoutParams as? WindowManager.LayoutParams ?: return

        if (overlay !== lastDecorated || overlay.tag != "sr0270_assistant_minimal") {
            buildCard(context, card)
            lastDecorated = overlay
        }
        positionCard(context, card, wm, lp)
    }

    private fun buildCard(context: Context, card: LinearLayout) {
        val p = SrUi023.palette(context)
        card.tag = "sr0270_assistant_minimal"
        card.removeAllViews()
        card.orientation = LinearLayout.VERTICAL
        card.elevation = SrUi023.dp(context, 5).toFloat()

        card.addView(
            TextView(context).apply {
                text = "Você está fazendo uma corrida?"
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(p.ink)
            },
        )

        card.addView(
            TextView(context).apply {
                text = "Quero uma dica de local"
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = SrUi023.rounded(p.blue, 11, p.blue, 1, context)
                setPadding(
                    SrUi023.dp(context, 8),
                    SrUi023.dp(context, 7),
                    SrUi023.dp(context, 8),
                    SrUi023.dp(context, 7),
                )
                setOnClickListener {
                    dismiss(suppressAsRide = false, context = context)
                    FieldValidationPolish0265.openNowFromAssistant(context)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 6)
            },
        )

        card.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                dismiss(suppressAsRide = true, context = context)
                true
            } else {
                false
            }
        }
    }

    private fun positionCard(
        context: Context,
        card: LinearLayout,
        wm: WindowManager,
        lp: WindowManager.LayoutParams,
    ) {
        val prefs = JourneyUiPreferences(context)

        // 0.27: lê primeiro a posição AO VIVO do ícone. Antes usávamos apenas
        // SharedPreferences, que podia estar defasado enquanto o motorista arrastava.
        val liveBubbleParams =
            privateField<WindowManager.LayoutParams>(JourneyBubbleController, "params")
        val iconX = liveBubbleParams?.x ?: prefs.position().first
        val iconY = liveBubbleParams?.y ?: prefs.position().second

        val iconSize = SrUi023.dp(context, prefs.sizeDp())
        val width = SrUi023.dp(context, 228)
        val gap = SrUi023.dp(context, 7)
        val viewport = viewport(context, wm)
        val placeRight = iconX + iconSize + gap + width <= viewport.first - gap
        val tail = SrUi023.dp(context, 9)
        val p = SrUi023.palette(context)

        lp.flags = lp.flags or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = width
        lp.x =
            if (placeRight) {
                iconX + iconSize + gap
            } else {
                (iconX - width - gap).coerceAtLeast(gap)
            }
        lp.y =
            (iconY - SrUi023.dp(context, 6))
                .coerceIn(
                    gap,
                    (viewport.second - SrUi023.dp(context, 100)).coerceAtLeast(gap),
                )

        card.setPadding(
            SrUi023.dp(context, 10) + if (placeRight) tail else 0,
            SrUi023.dp(context, 8),
            SrUi023.dp(context, 10) + if (!placeRight) tail else 0,
            SrUi023.dp(context, 9),
        )
        card.background = SpeechBubbleDrawable0265(
            fill = p.surface,
            stroke = p.blue,
            radiusPx = SrUi023.dp(context, 14).toFloat(),
            tailPx = tail.toFloat(),
            tailOnLeft = placeRight,
        )
        runCatching { wm.updateViewLayout(card, lp) }
    }

    private fun dismiss(
        suppressAsRide: Boolean,
        context: Context,
    ) {
        if (suppressAsRide) {
            val journeyId = SettingsRepository(context).currentJourneyId().trim()
            if (journeyId.isNotBlank()) {
                val latest =
                    LocalStore.get(context)
                        .recentOffers(100)
                        .firstOrNull { it.journeyId == journeyId }
                context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        KEY_MANUAL_RIDE_OFFER,
                        latest?.localId ?: "none:$journeyId",
                    )
                    .apply()
                LocalLog.append(
                    context.applicationContext,
                    "ASSISTENTE 0.27 fechado fora · tratado como motorista em corrida",
                )
            }
        }

        runCatching {
            val method =
                ActiveAssistant026::class.java
                    .getDeclaredMethod("dismissOverlay")
                    .apply { isAccessible = true }
            method.invoke(ActiveAssistant026)
        }
        lastDecorated = null
    }

    private fun viewport(
        context: Context,
        wm: WindowManager,
    ): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.let {
                it.widthPixels to it.heightPixels
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            val field =
                target.javaClass
                    .getDeclaredField(name)
                    .apply { isAccessible = true }
            field.get(target) as? T
        }.getOrNull()
}
