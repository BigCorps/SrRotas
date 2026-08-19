package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object JourneyBubbleController {
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var bubble: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var appContext: Context? = null
    private var expanded = false

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            appContext = app
            if (root == null) create(app) else refreshNow(app)
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).post { if (root != null) refreshNow(app) }
    }

    fun hide(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val view = root ?: return@post
            runCatching { (windowManager ?: context.getSystemService(WindowManager::class.java)).removeView(view) }
            root = null; panel = null; bubble = null; params = null; windowManager = null; appContext = null; expanded = false
        }
    }

    private fun create(context: Context) {
        val prefs = JourneyUiPreferences(context)
        val wm = context.getSystemService(WindowManager::class.java)
        val position = prefs.position()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.first
            y = position.second
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.logo_srrotas)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = UiKit.rounded(context, UiKit.palette(context).surface, 18)
            elevation = UiKit.dp(context, 8).toFloat()
        }
        container.addView(icon)
        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = UiKit.rounded(context, UiKit.palette(context).surface, 16)
            setPadding(UiKit.dp(context, 12), UiKit.dp(context, 10), UiKit.dp(context, 12), UiKit.dp(context, 10))
            elevation = UiKit.dp(context, 8).toFloat()
        }
        container.addView(detail, LinearLayout.LayoutParams(UiKit.dp(context, 286), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 6) })

        root = container; panel = detail; bubble = icon; params = lp; windowManager = wm
        applyBubbleStyle(context)
        installDrag(icon, context)
        icon.setOnClickListener {
            expanded = !expanded
            detail.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded) rebuildPanel(context)
        }
        icon.setOnLongClickListener {
            prefs.cycleSize(); applyBubbleStyle(context); true
        }
        runCatching { wm.addView(container, lp) }.onFailure {
            root = null
            LocalLog.append(context, "Mascote flutuante indisponível: ${it.message}")
        }
        refreshNow(context)
    }

    private fun refreshNow(context: Context) {
        applyBubbleStyle(context)
        if (expanded) rebuildPanel(context)
    }

    private fun applyBubbleStyle(context: Context) {
        val icon = bubble ?: return
        val prefs = JourneyUiPreferences(context)
        val size = UiKit.dp(context, prefs.sizeDp())
        icon.layoutParams = LinearLayout.LayoutParams(size, size)
        icon.alpha = prefs.opacityPercent() / 100f
        root?.requestLayout()
    }

    private fun rebuildPanel(context: Context) {
        val holder = panel ?: return
        holder.removeAllViews()
        val snapshot = JourneyCoordinator.snapshot(context)
        val latest = snapshot.latestOffer
        val title = when {
            snapshot.isDoingRide -> "Corrida em andamento"
            snapshot.journeyState == JourneyOperationalState.PAUSED -> "Jornada pausada"
            snapshot.journeyState == JourneyOperationalState.ACTIVE -> "Jornada ativa"
            else -> "Sr. Rotas"
        }
        holder.addView(UiKit.title(context, title, 17f))
        latest?.let { offer ->
            val verdict = when (offer.verdict) { "boa" -> "BOA"; "ruim" -> "ABAIXO"; else -> "ATENÇÃO" }
            val metrics = buildString {
                append("R$ ${money(offer.fare)} · $verdict")
                offer.perKm?.let { append("\nR$ ${money(it)}/km") }
                offer.perMinute?.let { append(" · R$ ${money(it)}/min") }
                offer.context?.destinationLabel?.takeIf { it.isNotBlank() }?.let { append("\n→ ${it.take(72)}") }
            }
            holder.addView(UiKit.margin(UiKit.body(context, metrics, 12f), top = 5))
        }

        when {
            snapshot.isDoingRide -> {
                mapsButton(context, holder, latest, destination = true)
                holder.addView(UiKit.margin(UiKit.primaryButton(context, "Finalizar corrida") { JourneyCoordinator.completeCurrentRide(context, "bubble") }, top = 7))
                holder.addView(UiKit.margin(UiKit.secondaryButton(context, "Cancelar / não concluída") { JourneyCoordinator.cancelCurrentRide(context, "bubble") }, top = 6))
                historyButton(context, holder)
            }
            snapshot.journeyState == JourneyOperationalState.PAUSED -> {
                holder.addView(UiKit.margin(UiKit.primaryButton(context, "Retomar jornada") { JourneyCoordinator.resumeJourney(context) }, top = 8))
                holder.addView(UiKit.margin(UiKit.secondaryButton(context, "Encerrar jornada") { JourneyCoordinator.endJourney(context, "bubble_end") }, top = 6))
                historyButton(context, holder)
            }
            snapshot.journeyState == JourneyOperationalState.ACTIVE -> {
                mapsButton(context, holder, latest, destination = false)
                mapsButton(context, holder, latest, destination = true)
                if (latest != null && latest.journeyId == snapshot.journeyId) {
                    holder.addView(UiKit.margin(UiKit.primaryButton(context, "Estou fazendo") { JourneyCoordinator.markDoingRide(context, latest.localId, "bubble") }, top = 7))
                }
                holder.addView(UiKit.margin(UiKit.secondaryButton(context, "Pausar jornada") { JourneyCoordinator.pauseJourney(context) }, top = 6))
                historyButton(context, holder)
            }
            else -> holder.addView(UiKit.body(context, "Abra o Sr. Rotas para iniciar uma jornada."))
        }

        val prefs = JourneyUiPreferences(context)
        val configRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        configRow.addView(smallAction(context, "Tamanho ${prefs.sizeDp()}") { prefs.cycleSize(); applyBubbleStyle(context); rebuildPanel(context) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        configRow.addView(smallAction(context, "Opacidade ${prefs.opacityPercent()}%") { prefs.cycleOpacity(); applyBubbleStyle(context); rebuildPanel(context) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        holder.addView(UiKit.margin(configRow, top = 8))
    }

    private fun mapsButton(context: Context, holder: LinearLayout, offer: RideOffer?, destination: Boolean) {
        val ctx = offer?.context ?: return
        val intent = if (destination) OfferMaps.searchIntent(ctx.destinationLabel, ctx.destinationLat, ctx.destinationLng)
        else OfferMaps.searchIntent(ctx.pickupLabel, ctx.pickupLat, ctx.pickupLng)
        if (intent == null) return
        val label = if (destination) "Abrir destino no Maps" else "Abrir retirada no Maps"
        holder.addView(UiKit.margin(UiKit.secondaryButton(context, label) { runCatching { context.startActivity(intent) } }, top = 6))
    }

    private fun historyButton(context: Context, holder: LinearLayout) {
        holder.addView(UiKit.margin(UiKit.secondaryButton(context, "Histórico") {
            context.startActivity(Intent(context, HistoryQuickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, top = 6))
    }

    private fun smallAction(context: Context, text: String, action: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        textSize = 10f
        setTextColor(UiKit.palette(context).primaryDark)
        gravity = Gravity.CENTER
        setPadding(UiKit.dp(context, 4), UiKit.dp(context, 7), UiKit.dp(context, 4), UiKit.dp(context, 7))
        setOnClickListener { action() }
    }

    private fun installDrag(icon: ImageView, context: Context) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        icon.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        lp.x = (startX + dx).coerceAtLeast(0); lp.y = (startY + dy).coerceAtLeast(0)
                        runCatching { root?.let { view -> windowManager?.updateViewLayout(view, lp) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) JourneyUiPreferences(context).savePosition(lp.x, lp.y) else icon.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
}
