package com.srrotas.app

import android.app.Application
import android.content.Context
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
 * 0.33.6 — superfície canônica do Assistente Ativo + observabilidade local.
 *
 * O motor/ranking continua em ActiveAssistant026. Esta camada não altera a
 * decisão do motor; apenas torna a sugestão mais identificável e registra se
 * o overlay foi efetivamente decorado/interagido.
 *
 * Contratos preservados:
 * - ações explícitas IGNORAR | VER;
 * - tocar fora apenas fecha;
 * - nenhuma ação visual vira silenciosamente "estou em corrida";
 * - balão continua ancorado à posição REAL do ícone;
 * - telemetria 0.33.6 não coleta região, OCR, coordenadas ou valores da oferta.
 */
object ActiveAssistantPolish0265 {
    private const val LEGACY_PREFS = "sr_active_assistant_026"
    private const val LEGACY_MANUAL_RIDE_OFFER = "manual_ride_offer"

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var lastDecorated: View? = null
    private var running = false

    fun install(application: Application) {
        app = application.applicationContext
        ActiveAssistantTelemetry0336.install(application)

        // A UX antiga podia gravar supressão manual ao tocar fora do balão.
        // A ação silenciosa deixa de existir; limpamos somente esse marcador
        // legado, sem tocar em cooldown, enabled ou histórico.
        application.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_MANUAL_RIDE_OFFER)
            .apply()

        if (!running) {
            running = true
            main.post(watcher)
        }
    }

    private val watcher = object : Runnable {
        override fun run() {
            val context = app
            if (context != null) {
                runCatching { decorateCurrent(context) }
                    .onFailure { ActiveAssistantTelemetry0336.markPolishError(context) }
            }
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

        if (overlay !== lastDecorated || overlay.tag != "sr0336_assistant_observable") {
            buildCard(context, card)
            lastDecorated = overlay
            ActiveAssistantTelemetry0336.markDecorated(context)
        }
        positionCard(context, card, wm, lp)
    }

    private fun buildCard(context: Context, card: LinearLayout) {
        val p = SrUi023.palette(context)
        card.tag = "sr0336_assistant_observable"
        card.removeAllViews()
        card.orientation = LinearLayout.VERTICAL
        card.elevation = SrUi023.dp(context, 6).toFloat()

        card.addView(
            TextView(context).apply {
                text = "SR • ASSISTENTE ATIVO"
                textSize = 8.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(p.purple)
                letterSpacing = 0.08f
                maxLines = 1
            },
        )

        card.addView(
            TextView(context).apply {
                text = "Tenho uma sugestão para agora"
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(p.ink)
                setPadding(0, SrUi023.dp(context, 3), 0, 0)
                maxLines = 2
            },
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actions.addView(
            actionButton(
                context = context,
                label = "IGNORAR",
                primary = false,
            ) {
                ActiveAssistantTelemetry0336.markIgnore(context)
                LocalLog.append(context, "ASSISTENTE 0.33.6 ignorado pelo motorista")
                dismiss()
            },
            LinearLayout.LayoutParams(
                0,
                SrUi023.dp(context, 34),
                1f,
            ),
        )

        actions.addView(
            actionButton(
                context = context,
                label = "VER",
                primary = true,
            ) {
                ActiveAssistantTelemetry0336.markView(context)
                LocalLog.append(context, "ASSISTENTE 0.33.6 abriu Agora")
                dismiss()
                FieldValidationPolish0265.openNowFromAssistant(context)
            },
            LinearLayout.LayoutParams(
                0,
                SrUi023.dp(context, 34),
                1f,
            ).apply {
                marginStart = SrUi023.dp(context, 6)
            },
        )

        card.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 7)
            },
        )

        card.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                ActiveAssistantTelemetry0336.markOutsideDismiss(context)
                LocalLog.append(context, "ASSISTENTE 0.33.6 fechado fora · sem alterar estado de corrida")
                dismiss()
                true
            } else {
                false
            }
        }
    }

    private fun actionButton(
        context: Context,
        label: String,
        primary: Boolean,
        action: () -> Unit,
    ): TextView {
        val p = SrUi023.palette(context)
        return TextView(context).apply {
            text = label
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (primary) android.graphics.Color.WHITE else p.ink)
            background = SrUi023.rounded(
                if (primary) p.blue else p.surface,
                9,
                if (primary) p.blue else p.outline,
                1,
                context,
            )
            setOnClickListener { action() }
        }
    }

    private fun positionCard(
        context: Context,
        card: LinearLayout,
        wm: WindowManager,
        lp: WindowManager.LayoutParams,
    ) {
        val prefs = JourneyUiPreferences(context)

        val liveBubbleParams =
            privateField<WindowManager.LayoutParams>(JourneyBubbleController, "params")
        val iconX = liveBubbleParams?.x ?: prefs.position().first
        val iconY = liveBubbleParams?.y ?: prefs.position().second

        val iconSize = SrUi023.dp(context, prefs.sizeDp())
        val width = SrUi023.dp(context, 224)
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
            (iconY - SrUi023.dp(context, 4))
                .coerceIn(
                    gap,
                    (viewport.second - SrUi023.dp(context, 96)).coerceAtLeast(gap),
                )

        card.setPadding(
            SrUi023.dp(context, 9) + if (placeRight) tail else 0,
            SrUi023.dp(context, 8),
            SrUi023.dp(context, 9) + if (!placeRight) tail else 0,
            SrUi023.dp(context, 8),
        )
        card.background = SpeechBubbleDrawable0265(
            fill = p.surface,
            stroke = p.blue,
            radiusPx = SrUi023.dp(context, 13).toFloat(),
            tailPx = tail.toFloat(),
            tailOnLeft = placeRight,
        )
        runCatching { wm.updateViewLayout(card, lp) }
            .onFailure { ActiveAssistantTelemetry0336.markPolishError(context) }
    }

    private fun dismiss() {
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
