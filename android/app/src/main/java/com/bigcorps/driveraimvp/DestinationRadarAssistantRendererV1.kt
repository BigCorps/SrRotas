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
        FieldPipelineTraceV1.event(app,spec.localOfferId,"ASSISTANT_RENDER_ATTEMPT","ready")
        if (!Settings.canDrawOverlays(app)) {
            FieldPipelineTraceV1.event(app,spec.localOfferId,"ASSISTANT_VISIBLE_BLOCKED","overlay_permission_missing")
            RadarContextualDiagnosticV1.assistantRenderBlocked("overlay_permission_missing")
            return
        }
        JourneyBubbleController.show(app)
        main.postDelayed({
            val block = when {
                !RadarContextualIntegrationV1.assistantAllowed(app) -> "assistant_disabled"
                RadarDestinationContextV1.current(app)?.localOfferId != spec.localOfferId -> "current_ride_changed"
                DestinationRadarRuntimeV1.latestFor(spec.localOfferId) !== result -> "stale_result"
                else -> null
            }
            if(block != null) {
                FieldPipelineTraceV1.event(app,spec.localOfferId,"ASSISTANT_VISIBLE_BLOCKED",block)
                return@postDelayed
            }
            attach(
                app,
                signal,
                traceOfferId = spec.localOfferId,
                onShown = {
                    FieldPipelineTraceV1.event(app,spec.localOfferId,"ASSISTANT_HOST_ATTACHED","hud_attached_visibility_pending")
                    RadarContextualDiagnosticV1.assistantRendered(signal.kind)
                    DestinationRadarInteractionV1.shown(app, spec, result, signal)
                },
                onIgnore = {
                    DestinationRadarInteractionV1.ignore(app, spec, result, signal)
                },
                onView = {
                    if (!RadarContextualIntegrationV1.assistantAllowed(app)) return@attach
                    if(RadarDestinationContextV1.current(app)?.localOfferId != spec.localOfferId)
                        FieldPipelineTraceV1.event(app,spec.localOfferId,"RADAR_OPENED_BLOCKED","stale_click")
                    DestinationRadarInteractionV1.viewIfCurrentRide(
                        spec.localOfferId, RadarDestinationContextV1.current(app)?.localOfferId, signal,
                        onView = {
                            FieldPipelineTraceV1.event(app,spec.localOfferId,"ASSISTANT_VIEW_CLICKED","accepted_current_ride_click")
                            DestinationRadarInteractionV1.view(app, spec, result, signal)
                        },
                        launch = { RadarDestinationLauncherV1.openRadar(app, it, source="assistant_real") },
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
        traceOfferId: String? = null,
        onShown: () -> Unit,
        onIgnore: () -> Unit,
        onView: () -> Unit,
    ) {
        val column = privateField<LinearLayout>(JourneyBubbleController, "mainColumn")
        if (column == null) {
            FieldPipelineTraceV1.event(context,traceOfferId,"ASSISTANT_VISIBLE_BLOCKED","hud_host_indisponivel")
            RadarContextualDiagnosticV1.assistantRenderBlocked("hud_host_indisponivel")
            LocalLog.append(context, "Radar contextual: host do HUD indisponível")
            return
        }
        if (!column.isAttachedToWindow) {
            FieldPipelineTraceV1.event(context,traceOfferId,"ASSISTANT_VISIBLE_BLOCKED","hud_host_not_attached")
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
            FieldPipelineTraceV1.event(context,traceOfferId,"ASSISTANT_VISIBLE_BLOCKED","hud_card_not_attached")
            RadarContextualDiagnosticV1.assistantRenderBlocked("hud_card_not_attached")
            return
        }
        // Pre-draw proves layout happened; a plain post can run before the first traversal.
        var visibilityRecorded = false
        val observer = card.viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if(observer.isAlive) observer.removeOnPreDrawListener(this)
                if(!visibilityRecorded) {
                    visibilityRecorded = true
                    val visible = current === card && column.isAttachedToWindow && card.isAttachedToWindow &&
                        column.isShown && card.isShown && card.width > 0 && card.height > 0
                    FieldPipelineTraceV1.event(context,traceOfferId,
                        if(visible) "ASSISTANT_VISIBLE" else "ASSISTANT_VISIBLE_BLOCKED",
                        if(visible) "host_and_card_attached" else "card_not_measured_or_hidden")
                }
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
        card.postDelayed({
            if(observer.isAlive) observer.removeOnPreDrawListener(listener)
            if(!visibilityRecorded) {
                visibilityRecorded = true
                FieldPipelineTraceV1.event(context,traceOfferId,"ASSISTANT_VISIBLE_BLOCKED","card_not_measured_or_hidden")
            }
        },1_000L)
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
