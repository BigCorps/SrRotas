package com.srrotas.app

import android.content.Context
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
        if (!Settings.canDrawOverlays(app)) {
            RadarContextualDiagnosticV1.assistantRenderBlocked("overlay_permission_missing")
            return
        }
        JourneyBubbleController.show(app)
        main.postDelayed({
            if (!RadarContextualIntegrationV1.assistantAllowed(app) ||
                RadarDestinationContextV1.current(app)?.localOfferId != spec.localOfferId ||
                DestinationRadarRuntimeV1.latestFor(spec.localOfferId) !== result) return@postDelayed
            attach(
                app,
                signal,
                onShown = {
                    RadarContextualDiagnosticV1.assistantRendered(signal.kind)
                    DestinationRadarInteractionV1.shown(app, spec, result, signal)
                },
                onIgnore = {
                    DestinationRadarInteractionV1.ignore(app, spec, result, signal)
                },
                onView = {
                    if (!RadarContextualIntegrationV1.assistantAllowed(app)) return@attach
                    DestinationRadarInteractionV1.viewIfCurrentRide(
                        spec.localOfferId, RadarDestinationContextV1.current(app)?.localOfferId, signal,
                        onView = { DestinationRadarInteractionV1.view(app, spec, result, signal) },
                        launch = { RadarDestinationLauncherV1.openRadar(app, it) },
                    )
                },
            )
        }, 80L)
    }

    /** Preview visual da Faceta 4. Não envia telemetria. */
    fun showPreview(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) {
            RadarContextualDiagnosticV1.assistantRenderBlocked("overlay_permission_missing_demo")
            return
        }
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
                onView = { RadarDestinationLauncherV1.openDemo(app) },
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
            RadarContextualDiagnosticV1.assistantRenderBlocked("hud_host_indisponivel")
            LocalLog.append(context, "Radar contextual: host do HUD indisponível")
            return
        }
        if (!column.isAttachedToWindow) {
            RadarContextualDiagnosticV1.assistantRenderBlocked("hud_host_not_attached")
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
        if (!DestinationRadarInteractionV1.shownIfAttached(
                column.isAttachedToWindow, card.isAttachedToWindow, onShown,
            )) {
            hideNow()
            RadarContextualDiagnosticV1.assistantRenderBlocked("hud_card_not_attached")
            return
        }
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? =
        runCatching {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as? T
        }.getOrNull()
}
