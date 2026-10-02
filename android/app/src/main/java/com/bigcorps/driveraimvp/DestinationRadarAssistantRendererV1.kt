package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout

/**
 * Renderer do assistente contextual usando o MESMO root de overlay do
 * JourneyBubbleController. Não cria WindowManager próprio.
 */
object DestinationRadarAssistantRendererV1 {
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var hideTask: Runnable? = null

    fun show(
        context: Context,
        spec: RadarDestinationSpecV1,
        result: RadarContextualResultV1,
        signal: DestinationRadarAssistantBridgeV1.Signal,
    ) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        JourneyBubbleController.show(app)
        main.postDelayed({
            attach(
                app,
                signal,
                onShown = {
                    DestinationRadarInteractionV1.shown(app, spec, result, signal)
                },
                onIgnore = {
                    DestinationRadarInteractionV1.ignore(app, spec, result, signal)
                },
                onView = {
                    DestinationRadarInteractionV1.view(app, spec, result, signal)
                    openRadar(app, signal.opportunityId)
                },
            )
        }, 80L)
    }

    /** Preview visual da Faceta 4. Não envia telemetria. */
    fun showPreview(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        JourneyBubbleController.show(app)
        val signal = DestinationRadarAssistantBridgeV1.Signal(
            headline = "Boa chance de continuidade no destino.",
            action = "Ver",
            opportunityId = "demo:radar-contextual",
        )
        main.postDelayed({
            attach(
                app,
                signal,
                onShown = {},
                onIgnore = {},
                onView = { openDemo(app) },
            )
        }, 80L)
    }

    fun hide() {
        main.post { hideNow() }
    }

    private fun attach(
        context: Context,
        signal: DestinationRadarAssistantBridgeV1.Signal,
        onShown: () -> Unit,
        onIgnore: () -> Unit,
        onView: () -> Unit,
    ) {
        val column = privateField<LinearLayout>(JourneyBubbleController, "mainColumn")
        if (column == null) {
            LocalLog.append(context, "Radar contextual: host do HUD indisponível")
            return
        }
        hideNow()
        val card = DestinationRadarAssistantUiV1.content(
            context = context,
            signal = signal,
            onIgnore = {
                onIgnore()
                hideNow()
            },
            onView = {
                onView()
                hideNow()
            },
        ).apply {
            contentDescription = "sr_radar_contextual_assistant"
        }
        current = card
        val index = 1.coerceAtMost(column.childCount)
        column.addView(
            card,
            index,
            LinearLayout.LayoutParams(
                SrUi023.dp(context, 252),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 6) },
        )
        onShown()
        val task = Runnable { hideNow() }
        hideTask = task
        main.postDelayed(
            task,
            JourneyUiPreferences(context).assistantDisplaySeconds().coerceIn(5, 30) * 1_000L,
        )
    }

    private fun hideNow() {
        hideTask?.let(main::removeCallbacks)
        hideTask = null
        val view = current
        current = null
        (view?.parent as? LinearLayout)?.removeView(view)
    }

    private fun openRadar(context: Context, opportunityId: String) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_RADAR)
                putExtra(MainActivity.EXTRA_RADAR_OPPORTUNITY_ID, opportunityId)
            },
        )
    }

    private fun openDemo(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_RADAR_DEMO)
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as? T
        }.getOrNull()
}
