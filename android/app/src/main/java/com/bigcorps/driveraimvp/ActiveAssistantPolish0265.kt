package com.srrotas.app

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Converte o Assistente 0.26 em um balão pequeno apontando para a janela flutuante. */
object ActiveAssistantPolish0265 {
    private const val KEY_LAST_SUGGESTION_AT = "last_suggestion_at"
    private const val ACTIVE_PREFS = "sr_active_assistant_026"
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
            if (running) main.postDelayed(this, 500L)
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

        if (overlay !== lastDecorated || overlay.tag != "sr0265_speech") {
            buildCard(context, card)
            lastDecorated = overlay
        }
        // Reposiciona sempre: se o motorista arrastar o ícone enquanto o balão
        // está aberto, a ponta continua visualmente ligada a ele.
        positionCard(context, card, wm, lp)
    }

    private fun buildCard(context: Context, card: LinearLayout) {
        val p = SrUi023.palette(context)
        card.tag = "sr0265_speech"
        card.removeAllViews()
        card.orientation = LinearLayout.VERTICAL
        card.elevation = SrUi023.dp(context, 5).toFloat()

        card.addView(TextView(context).apply {
            text = "Sr. Rotas pode ajudar"
            textSize = 10.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(p.ink)
        })
        card.addView(
            TextView(context).apply {
                text = "Verificar locais para novas corridas"
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = SrUi023.rounded(p.blue, 12, p.blue, 1, context)
                setPadding(
                    SrUi023.dp(context, 9), SrUi023.dp(context, 8),
                    SrUi023.dp(context, 9), SrUi023.dp(context, 8),
                )
                setOnClickListener {
                    dismiss(context, ignored = false)
                    FieldValidationPolish0265.openNowFromAssistant(context)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 7) },
        )

        card.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                dismiss(context, ignored = true)
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
        val (iconX, iconY) = prefs.position()
        val iconSize = SrUi023.dp(context, prefs.sizeDp())
        val width = SrUi023.dp(context, 252)
        val gap = SrUi023.dp(context, 7)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val placeRight = iconX + iconSize + gap + width <= screenWidth - gap
        val tail = SrUi023.dp(context, 10)
        val p = SrUi023.palette(context)

        lp.flags = lp.flags or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = width
        lp.x = if (placeRight) {
            iconX + iconSize + gap
        } else {
            (iconX - width - gap).coerceAtLeast(gap)
        }
        lp.y = (iconY - SrUi023.dp(context, 8))
            .coerceIn(gap, (screenHeight - SrUi023.dp(context, 120)).coerceAtLeast(gap))

        card.setPadding(
            SrUi023.dp(context, 12) + if (placeRight) tail else 0,
            SrUi023.dp(context, 10),
            SrUi023.dp(context, 12) + if (!placeRight) tail else 0,
            SrUi023.dp(context, 11),
        )
        card.background = SpeechBubbleDrawable0265(
            fill = p.surface,
            stroke = p.blue,
            radiusPx = SrUi023.dp(context, 16).toFloat(),
            tailPx = tail.toFloat(),
            tailOnLeft = placeRight,
        )
        runCatching { wm.updateViewLayout(card, lp) }
    }

    private fun dismiss(context: Context, ignored: Boolean) {
        if (ignored) {
            val interval = ActiveAssistantPrefs0265.intervalMinutes(context)
            context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(
                    KEY_LAST_SUGGESTION_AT,
                    ActiveAssistantPrefs0265.adjustedLastSuggestion(System.currentTimeMillis(), interval),
                )
                .apply()
        }
        runCatching {
            val method = ActiveAssistant026::class.java
                .getDeclaredMethod("dismissOverlay")
                .apply { isAccessible = true }
            method.invoke(ActiveAssistant026)
        }
        lastDecorated = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? = runCatching {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.get(target) as? T
    }.getOrNull()
}
