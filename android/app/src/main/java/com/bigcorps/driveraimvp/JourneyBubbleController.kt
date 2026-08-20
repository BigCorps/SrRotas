package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import kotlin.math.abs

/**
 * 0.20.1 hotfix
 *
 * Correções:
 * - remove a sombra pontuda/retangular do ícone e do card;
 * - passa a ler e aplicar o Painel de Rota em tempo real;
 * - amplia a personalização visual do novo card sem mexer no Offer Engine.
 */
object JourneyBubbleController {
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var bubble: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var appContext: Context? = null

    @Volatile private var expanded = false
    @Volatile private var expandedOfferId: String? = null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var prefsSignature: String? = null
    private var prefsWatcherRunning = false

    private val prefsWatcher = object : Runnable {
        override fun run() {
            val context = appContext
            if (context == null || root == null) {
                prefsWatcherRunning = false
                return
            }

            val visual = JourneyBubbleVisualPrefs.snapshot(context)
            val newSignature = visual.signature()
            if (prefsSignature != newSignature) {
                prefsSignature = newSignature
                applyBubbleStyle(context, visual, reposition = true)
                if (expanded) rebuildPanel(context, visual)
            }

            main.postDelayed(this, 350L)
        }
    }

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return

        main.post {
            appContext = app
            if (root == null) {
                create(app)
            } else {
                refreshNow(app)
            }
            ensurePrefsWatcher()
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        main.post {
            if (root != null) refreshNow(app)
            ensurePrefsWatcher()
        }
    }

    fun refreshOffer(context: Context) {
        if (!expanded) return
        val app = context.applicationContext
        main.post {
            if (root != null && expanded) {
                rebuildPanel(app, JourneyBubbleVisualPrefs.snapshot(app))
            }
        }
    }

    fun hide(context: Context) {
        main.post {
            val view = root ?: return@post
            runCatching {
                (
                    windowManager
                        ?: context.getSystemService(
                            WindowManager::class.java,
                        )
                    ).removeView(view)
            }
            stopPrefsWatcher()
            root = null
            panel = null
            bubble = null
            params = null
            windowManager = null
            appContext = null
            expanded = false
            expandedOfferId = null
            prefsSignature = null
        }
    }

    fun collapse() {
        main.post {
            expanded = false
            expandedOfferId = null
            panel?.visibility = View.GONE
        }
    }

    private fun create(context: Context) {
        val visual = JourneyBubbleVisualPrefs.snapshot(context)
        val wm = context.getSystemService(WindowManager::class.java)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = UiKit.dp(context, visual.posX)
            y = UiKit.dp(context, visual.posY)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipToPadding = false
            clipChildren = false
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    collapse()
                    true
                } else {
                    false
                }
            }
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.logo_srrotas)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Abrir menu do Sr. Rotas"
            // Hotfix 0.20.1: remove elevação nativa, que gerava sombra retangular/pontuda.
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
        }
        container.addView(icon)

        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            clipToPadding = false
            clipChildren = false
            setPadding(
                UiKit.dp(context, 12),
                UiKit.dp(context, 10),
                UiKit.dp(context, 12),
                UiKit.dp(context, 11),
            )
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
        }

        container.addView(
            detail,
            LinearLayout.LayoutParams(
                UiKit.dp(context, visual.panelWidthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 8)
            },
        )

        root = container
        panel = detail
        bubble = icon
        params = lp
        windowManager = wm
        prefsSignature = visual.signature()

        applyBubbleStyle(context, visual, reposition = false)
        installDrag(icon, context)

        icon.setOnClickListener {
            expanded = !expanded
            detail.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded) rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
        }

        runCatching {
            wm.addView(container, lp)
        }.onFailure {
            root = null
            LocalLog.append(context, "Menu flutuante indisponível: ${it.message}")
        }

        refreshNow(context)
        ensurePrefsWatcher()
    }

    private fun ensurePrefsWatcher() {
        if (prefsWatcherRunning) return
        prefsWatcherRunning = true
        main.post(prefsWatcher)
    }

    private fun stopPrefsWatcher() {
        prefsWatcherRunning = false
        main.removeCallbacks(prefsWatcher)
    }

    private fun refreshNow(context: Context) {
        val visual = JourneyBubbleVisualPrefs.snapshot(context)
        prefsSignature = visual.signature()
        applyBubbleStyle(context, visual, reposition = false)
        if (expanded) rebuildPanel(context, visual)
    }

    private fun applyBubbleStyle(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, reposition: Boolean) {
        val icon = bubble ?: return
        val holder = panel
        val lp = params

        icon.layoutParams = LinearLayout.LayoutParams(
            UiKit.dp(context, visual.iconSizeDp),
            UiKit.dp(context, visual.iconSizeDp),
        )
        icon.alpha = visual.opacityPercent / 100f
        icon.background = bubbleBackground(context, visual)

        holder?.layoutParams = (holder?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = UiKit.dp(context, visual.panelWidthDp)
        }
        holder?.background = panelBackground(context, visual)

        if (reposition && lp != null) {
            lp.x = UiKit.dp(context, visual.posX)
            lp.y = UiKit.dp(context, visual.posY)
            root?.let { currentRoot ->
                runCatching {
                    windowManager?.updateViewLayout(currentRoot, lp)
                }
            }
        }

        root?.requestLayout()
    }

    private fun bubbleBackground(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot) =
        UiKit.rounded(
            context,
            surfaceColor(context, visual),
            if (visual.iconSizeDp >= 70) 22 else 18,
            lineColor(context, visual),
            1,
        )

    private fun panelBackground(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot) =
        UiKit.rounded(
            context,
            surfaceColor(context, visual),
            20,
            lineColor(context, visual),
            1,
        )

    private fun rebuildPanel(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot) {
        val holder = panel ?: return
        holder.removeAllViews()

        val snapshot = JourneyCoordinator.snapshot(context)
        val store = LocalStore.get(context)
        val offers = store.recentOffers(12)
            .filterNot { it.captureMethod.startsWith("historical-import/") }
            .take(3)

        if (expandedOfferId != null && offers.none { it.localId == expandedOfferId }) {
            expandedOfferId = null
        }

        holder.addView(header(context, visual))
        holder.addView(divider(context, visual))

        if (offers.isEmpty()) {
            holder.addView(
                UiKit.margin(
                    body(context, visual, "As 3 últimas ofertas aparecerão aqui durante a jornada.", visual.fontSp - 1f),
                    top = 10,
                    bottom = 6,
                ),
            )
        } else {
            offers.forEachIndexed { index, offer ->
                holder.addView(offerRow(context, offer, snapshot, visual))
                if (index < offers.lastIndex) holder.addView(divider(context, visual))
            }
        }

        holder.addView(divider(context, visual))
        holder.addView(footerControls(context, snapshot, visual))

        val queue = SyncCoordinator.pending(context)
        if (queue.total > 0) {
            val status = TextView(context).apply {
                text = if (SyncCoordinator.isRunning()) {
                    "Sincronizando ${queue.total} item(ns)…"
                } else {
                    "${queue.total} item(ns) aguardando sincronização"
                }
                textSize = (visual.fontSp - 3f).coerceAtLeast(9f)
                setTextColor(mutedColor(context, visual))
                gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(context, 7), 0, 0)
            }
            holder.addView(status)
        }
    }

    private fun header(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dp(context, 7))
        }

        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.logo_srrotas)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                background = bubbleBackground(context, visual)
                elevation = 0f
                translationZ = 0f
            },
            LinearLayout.LayoutParams(UiKit.dp(context, 34), UiKit.dp(context, 34)),
        )

        row.addView(
            title(context, visual, "Sr. Rotas", visual.fontSp + 1f).apply {
                setPadding(UiKit.dp(context, 8), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        row.addView(
            TextView(context).apply {
                text = "×"
                textSize = (visual.fontSp + 9f).coerceAtLeast(24f)
                gravity = Gravity.CENTER
                setTextColor(inkColor(context, visual))
                setPadding(UiKit.dp(context, 9), 0, UiKit.dp(context, 3), 0)
                contentDescription = "Fechar menu"
                setOnClickListener { collapse() }
            },
        )

        return row
    }

    private fun offerRow(
        context: Context,
        offer: RideOffer,
        snapshot: JourneyOperationalSnapshot,
        visual: JourneyBubbleVisualPrefs.Snapshot,
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(context, 7), 0, UiKit.dp(context, 7))
        }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(
                context,
                surfaceAltColor(context, visual),
                14,
                lineColor(context, visual),
                1,
            )
            setPadding(
                UiKit.dp(context, 10),
                UiKit.dp(context, 9),
                UiKit.dp(context, 8),
                UiKit.dp(context, 9),
            )
        }

        val service = serviceLabel(offer.serviceType)
        top.addView(
            TextView(context).apply {
                text = "●"
                textSize = (visual.fontSp + 2f).coerceAtMost(19f)
                setTextColor(verdictColor(context, visual, offer.verdict))
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(UiKit.dp(context, 28), LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        top.addView(
            title(context, visual, service, visual.fontSp + 1f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        top.addView(
            title(context, visual, "R$ ${money(offer.fare)}", visual.fontSp + 1f).apply {
                setTextColor(primaryColor(context, visual))
                gravity = Gravity.END
            },
        )

        val outcome = LocalStore.get(context).rideOutcomeForOffer(offer.localId)
        val isDoing = outcome?.status == RideOperationalStatus.DOING_RIDE

        val quick = TextView(context).apply {
            text = if (isDoing) "✓" else "○"
            textSize = (visual.fontSp + 4f).coerceAtLeast(18f)
            setTextColor(
                if (isDoing) primaryColor(context, visual) else mutedColor(context, visual),
            )
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(context, 10), 0, UiKit.dp(context, 4), 0)
            contentDescription = if (isDoing) "Corrida em andamento" else "Marcar Estou fazendo"
            isEnabled = snapshot.journeyState == JourneyOperationalState.ACTIVE &&
                offer.journeyId == snapshot.journeyId &&
                !snapshot.isDoingRide
            alpha = if (isEnabled || isDoing) 1f else .35f
            setOnClickListener {
                if (isDoing) return@setOnClickListener
                val started = JourneyCoordinator.markDoingRide(context, offer.localId, "bubble_0201")
                if (started != null) {
                    expandedOfferId = offer.localId
                    rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
                }
            }
        }
        top.addView(quick)

        top.addView(
            TextView(context).apply {
                text = if (expandedOfferId == offer.localId) "⌃" else "⌄"
                textSize = (visual.fontSp + 3f).coerceAtLeast(17f)
                gravity = Gravity.CENTER
                setTextColor(inkColor(context, visual))
                setPadding(UiKit.dp(context, 6), 0, UiKit.dp(context, 2), 0)
            },
        )

        top.setOnClickListener {
            expandedOfferId = if (expandedOfferId == offer.localId) null else offer.localId
            rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
        }

        card.addView(top)
        if (expandedOfferId == offer.localId) {
            card.addView(expandedOffer(context, offer, outcome, visual))
        }
        return card
    }

    private fun expandedOffer(
        context: Context,
        offer: RideOffer,
        outcome: RideOutcome?,
        visual: JourneyBubbleVisualPrefs.Snapshot,
    ): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 10), UiKit.dp(context, 9), UiKit.dp(context, 10), UiKit.dp(context, 3))
        }

        val ctx = offer.context
        box.addView(infoLine(context, visual, "Embarque", ctx?.pickupLabel?.takeIf(String::isNotBlank) ?: "Não identificado"))
        box.addView(UiKit.margin(infoLine(context, visual, "Destino", ctx?.destinationLabel?.takeIf(String::isNotBlank) ?: "Não identificado"), top = 7))

        box.addView(
            UiKit.margin(
                body(
                    context,
                    visual,
                    buildString {
                        offer.perKm?.let { append("R$ ${money(it)}/km") }
                        offer.perMinute?.let { if (isNotEmpty()) append(" · "); append("R$ ${money(it)}/min") }
                        offer.perHour?.let { if (isNotEmpty()) append(" · "); append("R$ ${money(it)}/h") }
                        offer.totalKm?.let { if (isNotEmpty()) append("\n"); append("${money(it)} km") }
                        offer.totalMinutes?.let { append(" · ${it} min") }
                        offer.estimatedProfit?.let { append("\nLucro est.* R$ ${money(it)}") }
                    }.ifBlank { "Detalhes financeiros disponíveis no HUD." },
                    visual.fontSp - 1f,
                ),
                top = 7,
            ),
        )

        val maps = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pickupIntent = OfferMaps.searchIntent(ctx?.pickupLabel, ctx?.pickupLat, ctx?.pickupLng)
        val destinationIntent = OfferMaps.searchIntent(ctx?.destinationLabel, ctx?.destinationLat, ctx?.destinationLng)

        maps.addView(
            compactButton(context, visual, "EMBARQUE", primary = true, enabled = pickupIntent != null) {
                pickupIntent?.let { runCatching { context.startActivity(it) } }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        maps.addView(
            compactButton(context, visual, "DESTINO", primary = false, enabled = destinationIntent != null) {
                destinationIntent?.let { runCatching { context.startActivity(it) } }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(context, 6)
            },
        )

        box.addView(UiKit.margin(maps, top = 8))

        if (outcome?.status == RideOperationalStatus.DOING_RIDE) {
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(
                compactButton(context, visual, "REALIZADA", primary = true) {
                    JourneyCoordinator.completeCurrentRide(context, "bubble_0201")
                    rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            actions.addView(
                compactButton(context, visual, "NÃO REALIZADA", primary = false) {
                    JourneyCoordinator.cancelCurrentRide(context, "bubble_0201")
                    rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = UiKit.dp(context, 6)
                },
            )
            box.addView(UiKit.margin(actions, top = 6))
        }
        return box
    }

    private fun infoLine(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, label: String, value: String): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                body(context, visual, label, (visual.fontSp - 2f).coerceAtLeast(10f)).apply {
                    setTextColor(primaryColor(context, visual))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
            )
            addView(
                body(context, visual, value.take(110), visual.fontSp - 1f).apply {
                    setTextColor(inkColor(context, visual))
                },
            )
        }

    private fun footerControls(context: Context, snapshot: JourneyOperationalSnapshot, visual: JourneyBubbleVisualPrefs.Snapshot): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiKit.dp(context, 9), 0, 0)
        }

        val journeyText = when (snapshot.journeyState) {
            JourneyOperationalState.ACTIVE -> "PAUSAR"
            JourneyOperationalState.PAUSED -> "RETOMAR"
            else -> "INICIAR"
        }

        row.addView(
            footerButton(context, visual, journeyText) {
                when (snapshot.journeyState) {
                    JourneyOperationalState.ACTIVE -> JourneyCoordinator.pauseJourney(context)
                    JourneyOperationalState.PAUSED -> JourneyCoordinator.resumeJourney(context)
                    else -> openMainForStart(context)
                }
                rebuildPanel(context, JourneyBubbleVisualPrefs.snapshot(context))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        if (snapshot.journeyState == JourneyOperationalState.ACTIVE || snapshot.journeyState == JourneyOperationalState.PAUSED) {
            row.addView(
                footerButton(context, visual, "ENCERRAR") {
                    JourneyCoordinator.endJourney(context, "bubble_0201_end")
                    show(context)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = UiKit.dp(context, 4)
                },
            )
        }

        row.addView(
            footerButton(context, visual, "HISTÓRICO") {
                context.startActivity(
                    Intent(context, HistoryQuickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(context, 4)
            },
        )

        return row
    }

    private fun openMainForStart(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_START)
            },
        )
        collapse()
    }

    private fun compactButton(
        context: Context,
        visual: JourneyBubbleVisualPrefs.Snapshot,
        text: String,
        primary: Boolean,
        enabled: Boolean = true,
        action: () -> Unit,
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = (visual.fontSp - 2f).coerceAtLeast(10.5f)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(UiKit.dp(context, 8), UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9))
        minHeight = UiKit.dp(context, 38)
        setTextColor(if (primary) Color.WHITE else inkColor(context, visual))
        background = UiKit.rounded(
            context,
            if (primary) primaryColor(context, visual) else surfaceColor(context, visual),
            11,
            if (primary) primaryColor(context, visual) else lineColor(context, visual),
            1,
        )
        isEnabled = enabled
        alpha = if (enabled) 1f else .4f
        setOnClickListener { if (enabled) action() }
    }

    private fun footerButton(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, text: String, action: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = (visual.fontSp - 3f).coerceAtLeast(9.5f)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primaryColor(context, visual))
            setPadding(UiKit.dp(context, 5), UiKit.dp(context, 8), UiKit.dp(context, 5), UiKit.dp(context, 8))
            background = UiKit.rounded(
                context,
                surfaceAltColor(context, visual),
                11,
                lineColor(context, visual),
                1,
            )
            setOnClickListener { action() }
        }

    private fun divider(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): View =
        View(context).apply {
            setBackgroundColor(lineColor(context, visual))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }

    private fun body(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, text: String, size: Float): TextView =
        UiKit.body(context, text, size).apply {
            setTextColor(inkColor(context, visual))
        }

    private fun title(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, text: String, size: Float): TextView =
        UiKit.title(context, text, size).apply {
            setTextColor(inkColor(context, visual))
        }

    private fun installDrag(icon: ImageView, context: Context) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        icon.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) {
                        lp.x = (startX + dx).coerceAtLeast(0)
                        lp.y = (startY + dy).coerceAtLeast(0)
                        runCatching {
                            root?.let { view -> windowManager?.updateViewLayout(view, lp) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        JourneyUiPreferences(context).savePosition(lp.x, lp.y)
                        prefsSignature = null
                    } else {
                        icon.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun surfaceColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int {
        val p = UiKit.palette(context)
        return when {
            visual.colorBlind -> 0xFFFDFBF3.toInt()
            visual.theme.equals("dark", true) -> 0xFF0A3440.toInt()
            else -> p.surface
        }
    }

    private fun surfaceAltColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int = when {
        visual.colorBlind -> 0xFFF7F2DE.toInt()
        visual.theme.equals("dark", true) -> 0xFF12414E.toInt()
        else -> UiKit.palette(context).surfaceAlt
    }

    private fun inkColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int = when {
        visual.theme.equals("dark", true) -> 0xFFF9F6EC.toInt()
        visual.colorBlind -> 0xFF08394A.toInt()
        else -> UiKit.palette(context).ink
    }

    private fun primaryColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int = when {
        visual.colorBlind -> 0xFF0A6F93.toInt()
        else -> UiKit.palette(context).primaryDark
    }

    private fun mutedColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int = when {
        visual.theme.equals("dark", true) -> 0xFFD0D6D6.toInt()
        else -> UiKit.palette(context).muted
    }

    private fun lineColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot): Int = when {
        visual.theme.equals("dark", true) -> 0xFF2B5560.toInt()
        visual.colorBlind -> 0xFFC3BEA4.toInt()
        else -> UiKit.palette(context).line
    }

    private fun verdictColor(context: Context, visual: JourneyBubbleVisualPrefs.Snapshot, verdict: String): Int = when (verdict) {
        "boa" -> if (visual.colorBlind) 0xFF1476A8.toInt() else UiKit.palette(context).good
        "ruim" -> if (visual.colorBlind) 0xFFB4681F.toInt() else UiKit.palette(context).bad
        else -> if (visual.colorBlind) 0xFF7D6A1D.toInt() else UiKit.palette(context).warn
    }

    private fun serviceLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
        "uberx" -> "UberX"
        "comfort" -> "Comfort"
        "black" -> "Black"
        "electric" -> "Electric"
        "priority" -> "Priority"
        "moto" -> "Moto"
        else -> "Oferta"
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
}
