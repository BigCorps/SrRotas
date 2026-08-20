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
import kotlin.math.min

/**
 * 0.20 — Menu flutuante Sr. Rotas.
 *
 * Referência funcional aprovada:
 * - mascote abre/recolhe o menu;
 * - 3 últimas ofertas;
 * - só uma oferta expandida por vez;
 * - ação rápida "Estou fazendo";
 * - Embarque/Destino no Maps;
 * - iniciar/pausar/retomar jornada e Histórico sempre acessíveis;
 * - X recolhe o menu sem remover o mascote;
 * - toque fora recolhe quando o Android entrega ACTION_OUTSIDE.
 *
 * A paleta vem exclusivamente de UiKit (identidade oficial srrotas.com).
 * Offer Engine/parsing/HUD financeiro não são alterados aqui.
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

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return

        android.os.Handler(
            android.os.Looper.getMainLooper(),
        ).post {
            appContext = app
            if (root == null) {
                create(app)
            } else {
                refreshNow(app)
            }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        android.os.Handler(
            android.os.Looper.getMainLooper(),
        ).post {
            if (root != null) refreshNow(app)
        }
    }

    /**
     * Atualização barata: só reconstrói ofertas quando o menu está aberto.
     */
    fun refreshOffer(context: Context) {
        if (!expanded) return
        val app = context.applicationContext
        android.os.Handler(
            android.os.Looper.getMainLooper(),
        ).post {
            if (root != null && expanded) rebuildPanel(app)
        }
    }

    /**
     * Remove completamente a View overlay.
     * Usado em logout/encerramento de processo, não para o X visual do menu.
     */
    fun hide(context: Context) {
        android.os.Handler(
            android.os.Looper.getMainLooper(),
        ).post {
            val view = root ?: return@post
            runCatching {
                (
                    windowManager
                        ?: context.getSystemService(
                            WindowManager::class.java,
                        )
                    ).removeView(view)
            }
            root = null
            panel = null
            bubble = null
            params = null
            windowManager = null
            appContext = null
            expanded = false
            expandedOfferId = null
        }
    }

    fun collapse() {
        android.os.Handler(
            android.os.Looper.getMainLooper(),
        ).post {
            expanded = false
            expandedOfferId = null
            panel?.visibility = View.GONE
        }
    }

    private fun create(context: Context) {
        val prefs = JourneyUiPreferences(context)
        val wm =
            context.getSystemService(WindowManager::class.java)
        val position = prefs.position()

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
            x = position.first
            y = position.second
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setOnTouchListener { _, event ->
                if (
                    event.actionMasked ==
                    MotionEvent.ACTION_OUTSIDE
                ) {
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
            background =
                UiKit.rounded(
                    context,
                    UiKit.palette(context).surface,
                    18,
                    UiKit.palette(context).line,
                    1,
                )
            elevation = UiKit.dp(context, 8).toFloat()
            contentDescription = "Abrir menu do Sr. Rotas"
        }
        container.addView(icon)

        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background =
                UiKit.rounded(
                    context,
                    UiKit.palette(context).surface,
                    20,
                    UiKit.palette(context).line,
                    1,
                )
            setPadding(
                UiKit.dp(context, 12),
                UiKit.dp(context, 10),
                UiKit.dp(context, 12),
                UiKit.dp(context, 11),
            )
            elevation = UiKit.dp(context, 10).toFloat()
        }

        val screenWidthDp =
            (
                context.resources.displayMetrics.widthPixels /
                    context.resources.displayMetrics.density
                ).toInt()
        val panelWidthDp = min(336, (screenWidthDp - 20).coerceAtLeast(270))

        container.addView(
            detail,
            LinearLayout.LayoutParams(
                UiKit.dp(context, panelWidthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 6)
            },
        )

        root = container
        panel = detail
        bubble = icon
        params = lp
        windowManager = wm

        applyBubbleStyle(context)
        installDrag(icon, context)

        icon.setOnClickListener {
            expanded = !expanded
            detail.visibility =
                if (expanded) View.VISIBLE else View.GONE
            if (expanded) rebuildPanel(context)
        }

        icon.setOnLongClickListener {
            prefs.cycleSize()
            applyBubbleStyle(context)
            true
        }

        runCatching {
            wm.addView(container, lp)
        }.onFailure {
            root = null
            LocalLog.append(
                context,
                "Menu flutuante indisponível: ${it.message}",
            )
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
        icon.layoutParams =
            LinearLayout.LayoutParams(size, size)
        icon.alpha = prefs.opacityPercent() / 100f
        root?.requestLayout()
    }

    private fun rebuildPanel(context: Context) {
        val holder = panel ?: return
        holder.removeAllViews()

        val p = UiKit.palette(context)
        val snapshot = JourneyCoordinator.snapshot(context)
        val store = LocalStore.get(context)
        val offers =
            store.recentOffers(12)
                .filterNot {
                    it.captureMethod.startsWith(
                        "historical-import/",
                    )
                }
                .take(3)

        // Se a oferta antes expandida saiu das três últimas, recolhe.
        if (
            expandedOfferId != null &&
            offers.none { it.localId == expandedOfferId }
        ) {
            expandedOfferId = null
        }

        holder.addView(
            header(context),
        )
        holder.addView(divider(context))

        if (offers.isEmpty()) {
            holder.addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "As 3 últimas ofertas aparecerão aqui durante a jornada.",
                        12f,
                    ),
                    top = 10,
                    bottom = 6,
                ),
            )
        } else {
            offers.forEachIndexed { index, offer ->
                holder.addView(
                    offerRow(
                        context,
                        offer,
                        snapshot,
                    ),
                )
                if (index < offers.lastIndex) {
                    holder.addView(divider(context))
                }
            }
        }

        holder.addView(divider(context))
        holder.addView(
            footerControls(
                context,
                snapshot,
            ),
        )

        val queue = SyncCoordinator.pending(context)
        if (queue.total > 0) {
            val status = TextView(context).apply {
                text =
                    if (SyncCoordinator.isRunning()) {
                        "Sincronizando ${queue.total} item(ns)…"
                    } else {
                        "${queue.total} item(ns) aguardando sincronização"
                    }
                textSize = 9.5f
                setTextColor(p.muted)
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    UiKit.dp(context, 7),
                    0,
                    0,
                )
            }
            holder.addView(status)
        }
    }

    private fun header(context: Context): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                0,
                0,
                0,
                UiKit.dp(context, 7),
            )
        }

        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.logo_srrotas)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(
                UiKit.dp(context, 34),
                UiKit.dp(context, 34),
            ),
        )

        row.addView(
            UiKit.title(
                context,
                "Sr. Rotas",
                18f,
            ).apply {
                setPadding(
                    UiKit.dp(context, 8),
                    0,
                    0,
                    0,
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        row.addView(
            TextView(context).apply {
                text = "×"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(
                    UiKit.palette(context).ink,
                )
                setPadding(
                    UiKit.dp(context, 9),
                    0,
                    UiKit.dp(context, 3),
                    0,
                )
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
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                0,
                UiKit.dp(context, 7),
                0,
                UiKit.dp(context, 7),
            )
        }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background =
                UiKit.rounded(
                    context,
                    UiKit.palette(context).surfaceAlt,
                    14,
                    UiKit.palette(context).line,
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
                textSize = 17f
                setTextColor(
                    verdictColor(context, offer.verdict),
                )
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                UiKit.dp(context, 28),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        top.addView(
            UiKit.title(
                context,
                service,
                16f,
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        top.addView(
            UiKit.title(
                context,
                "R$ ${money(offer.fare)}",
                16f,
            ).apply {
                setTextColor(
                    UiKit.palette(context).primaryDark,
                )
                gravity = Gravity.END
            },
        )

        val outcome = storeOutcome(context, offer.localId)
        val isDoing =
            outcome?.status ==
                RideOperationalStatus.DOING_RIDE

        val quick = TextView(context).apply {
            text =
                if (isDoing) "✓" else "○"
            textSize = 20f
            setTextColor(
                if (isDoing) {
                    UiKit.palette(context).primary
                } else {
                    UiKit.palette(context).muted
                },
            )
            gravity = Gravity.CENTER
            setPadding(
                UiKit.dp(context, 10),
                0,
                UiKit.dp(context, 4),
                0,
            )
            contentDescription =
                if (isDoing) {
                    "Corrida em andamento"
                } else {
                    "Marcar Estou fazendo"
                }

            isEnabled =
                snapshot.journeyState ==
                    JourneyOperationalState.ACTIVE &&
                    offer.journeyId == snapshot.journeyId &&
                    !snapshot.isDoingRide

            alpha = if (isEnabled || isDoing) 1f else .35f

            setOnClickListener {
                if (isDoing) return@setOnClickListener

                val started =
                    JourneyCoordinator.markDoingRide(
                        context,
                        offer.localId,
                        "bubble_020",
                    )
                if (started != null) {
                    expandedOfferId = offer.localId
                    rebuildPanel(context)
                }
            }
        }
        top.addView(quick)

        top.addView(
            TextView(context).apply {
                text =
                    if (
                        expandedOfferId ==
                        offer.localId
                    ) {
                        "⌃"
                    } else {
                        "⌄"
                    }
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(
                    UiKit.palette(context).ink,
                )
                setPadding(
                    UiKit.dp(context, 6),
                    0,
                    UiKit.dp(context, 2),
                    0,
                )
            },
        )

        top.setOnClickListener {
            expandedOfferId =
                if (
                    expandedOfferId ==
                    offer.localId
                ) {
                    null
                } else {
                    offer.localId
                }
            rebuildPanel(context)
        }

        card.addView(top)

        if (expandedOfferId == offer.localId) {
            card.addView(
                expandedOffer(
                    context,
                    offer,
                    outcome,
                ),
            )
        }

        return card
    }

    private fun expandedOffer(
        context: Context,
        offer: RideOffer,
        outcome: RideOutcome?,
    ): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiKit.dp(context, 10),
                UiKit.dp(context, 9),
                UiKit.dp(context, 10),
                UiKit.dp(context, 3),
            )
        }

        val ctx = offer.context

        box.addView(
            infoLine(
                context,
                "Embarque",
                ctx?.pickupLabel
                    ?.takeIf(String::isNotBlank)
                    ?: "Não identificado",
            ),
        )

        box.addView(
            UiKit.margin(
                infoLine(
                    context,
                    "Destino",
                    ctx?.destinationLabel
                        ?.takeIf(String::isNotBlank)
                        ?: "Não identificado",
                ),
                top = 7,
            ),
        )

        box.addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    buildString {
                        offer.perKm?.let {
                            append("R$ ${money(it)}/km")
                        }
                        offer.perMinute?.let {
                            if (isNotEmpty()) append(" · ")
                            append("R$ ${money(it)}/min")
                        }
                        offer.perHour?.let {
                            if (isNotEmpty()) append(" · ")
                            append("R$ ${money(it)}/h")
                        }
                        offer.totalKm?.let {
                            if (isNotEmpty()) append("\n")
                            append("${money(it)} km")
                        }
                        offer.totalMinutes?.let {
                            append(" · ${it} min")
                        }
                        offer.estimatedProfit?.let {
                            append("\nLucro est.* R$ ${money(it)}")
                        }
                    }.ifBlank {
                        "Detalhes financeiros disponíveis no HUD."
                    },
                    11f,
                ),
                top = 7,
            ),
        )

        val maps = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pickupIntent =
            OfferMaps.searchIntent(
                ctx?.pickupLabel,
                ctx?.pickupLat,
                ctx?.pickupLng,
            )
        val destinationIntent =
            OfferMaps.searchIntent(
                ctx?.destinationLabel,
                ctx?.destinationLat,
                ctx?.destinationLng,
            )

        maps.addView(
            compactButton(
                context,
                "EMBARQUE",
                primary = true,
                enabled = pickupIntent != null,
            ) {
                pickupIntent?.let {
                    runCatching {
                        context.startActivity(it)
                    }
                }
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        maps.addView(
            compactButton(
                context,
                "DESTINO",
                primary = false,
                enabled = destinationIntent != null,
            ) {
                destinationIntent?.let {
                    runCatching {
                        context.startActivity(it)
                    }
                }
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = UiKit.dp(context, 6)
            },
        )

        box.addView(
            UiKit.margin(
                maps,
                top = 8,
            ),
        )

        if (
            outcome?.status ==
            RideOperationalStatus.DOING_RIDE
        ) {
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            actions.addView(
                compactButton(
                    context,
                    "REALIZADA",
                    primary = true,
                ) {
                    JourneyCoordinator.completeCurrentRide(
                        context,
                        "bubble_020",
                    )
                    rebuildPanel(context)
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            actions.addView(
                compactButton(
                    context,
                    "NÃO REALIZADA",
                    primary = false,
                ) {
                    JourneyCoordinator.cancelCurrentRide(
                        context,
                        "bubble_020",
                    )
                    rebuildPanel(context)
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = UiKit.dp(context, 6)
                },
            )
            box.addView(
                UiKit.margin(
                    actions,
                    top = 6,
                ),
            )
        }

        return box
    }

    private fun infoLine(
        context: Context,
        label: String,
        value: String,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                UiKit.body(
                    context,
                    label,
                    10f,
                ).apply {
                    setTextColor(
                        UiKit.palette(context).primary,
                    )
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD,
                    )
                },
            )
            addView(
                UiKit.body(
                    context,
                    value.take(110),
                    12f,
                ).apply {
                    setTextColor(
                        UiKit.palette(context).ink,
                    )
                },
            )
        }

    private fun footerControls(
        context: Context,
        snapshot: JourneyOperationalSnapshot,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                0,
                UiKit.dp(context, 9),
                0,
                0,
            )
        }

        val journeyText =
            when (snapshot.journeyState) {
                JourneyOperationalState.ACTIVE ->
                    "PAUSAR"
                JourneyOperationalState.PAUSED ->
                    "RETOMAR"
                else ->
                    "INICIAR"
            }

        row.addView(
            footerButton(
                context,
                journeyText,
            ) {
                when (snapshot.journeyState) {
                    JourneyOperationalState.ACTIVE ->
                        JourneyCoordinator.pauseJourney(context)
                    JourneyOperationalState.PAUSED ->
                        JourneyCoordinator.resumeJourney(context)
                    else ->
                        openMainForStart(context)
                }
                rebuildPanel(context)
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        if (
            snapshot.journeyState ==
            JourneyOperationalState.ACTIVE ||
            snapshot.journeyState ==
            JourneyOperationalState.PAUSED
        ) {
            row.addView(
                footerButton(
                    context,
                    "ENCERRAR",
                ) {
                    JourneyCoordinator.endJourney(
                        context,
                        "bubble_020_end",
                    )
                    // JourneyCoordinator.hide() é legado. Reabre o mascote
                    // imediatamente para permitir iniciar a próxima jornada.
                    show(context)
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = UiKit.dp(context, 4)
                },
            )
        }

        row.addView(
            footerButton(
                context,
                "HISTÓRICO",
            ) {
                context.startActivity(
                    Intent(
                        context,
                        HistoryQuickActivity::class.java,
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = UiKit.dp(context, 4)
            },
        )

        return row
    }

    private fun openMainForStart(context: Context) {
        context.startActivity(
            Intent(
                context,
                MainActivity::class.java,
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(
                    MainActivity.EXTRA_BUBBLE_ACTION,
                    MainActivity.BUBBLE_ACTION_START,
                )
            },
        )
        collapse()
    }

    private fun compactButton(
        context: Context,
        text: String,
        primary: Boolean,
        enabled: Boolean = true,
        action: () -> Unit,
    ): TextView {
        val p = UiKit.palette(context)
        return TextView(context).apply {
            this.text = text
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD,
            )
            setPadding(
                UiKit.dp(context, 8),
                UiKit.dp(context, 9),
                UiKit.dp(context, 8),
                UiKit.dp(context, 9),
            )
            minHeight = UiKit.dp(context, 38)
            setTextColor(
                if (primary) Color.WHITE else p.ink,
            )
            background =
                UiKit.rounded(
                    context,
                    if (primary) {
                        p.primaryDark
                    } else {
                        p.surface
                    },
                    11,
                    if (primary) {
                        p.primaryDark
                    } else {
                        p.line
                    },
                    1,
                )
            isEnabled = enabled
            alpha = if (enabled) 1f else .4f
            setOnClickListener {
                if (enabled) action()
            }
        }
    }

    private fun footerButton(
        context: Context,
        text: String,
        action: () -> Unit,
    ): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD,
            )
            setTextColor(
                UiKit.palette(context).primaryDark,
            )
            setPadding(
                UiKit.dp(context, 5),
                UiKit.dp(context, 8),
                UiKit.dp(context, 5),
                UiKit.dp(context, 8),
            )
            background =
                UiKit.rounded(
                    context,
                    UiKit.palette(context).surfaceAlt,
                    11,
                    UiKit.palette(context).line,
                    1,
                )
            setOnClickListener { action() }
        }

    private fun divider(context: Context): View =
        View(context).apply {
            setBackgroundColor(
                UiKit.palette(context).line,
            )
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1,
                )
        }

    private fun storeOutcome(
        context: Context,
        localOfferId: String,
    ): RideOutcome? =
        LocalStore.get(context)
            .rideOutcomeForOffer(localOfferId)

    private fun verdictColor(
        context: Context,
        verdict: String,
    ): Int =
        when (verdict) {
            "boa" -> UiKit.palette(context).good
            "ruim" -> UiKit.palette(context).bad
            else -> UiKit.palette(context).warn
        }

    private fun serviceLabel(value: String): String =
        when (value.lowercase(Locale.ROOT)) {
            "uberx" -> "UberX"
            "comfort" -> "Comfort"
            "black" -> "Black"
            "electric" -> "Electric"
            "priority" -> "Priority"
            "moto" -> "Moto"
            else -> "Oferta"
        }

    private fun installDrag(
        icon: ImageView,
        context: Context,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        icon.setOnTouchListener { _, event ->
            val lp =
                params
                    ?: return@setOnTouchListener false

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
                    val dx =
                        (event.rawX - downX).toInt()
                    val dy =
                        (event.rawY - downY).toInt()

                    if (
                        abs(dx) > 8 ||
                        abs(dy) > 8
                    ) {
                        moved = true
                    }

                    if (moved) {
                        lp.x =
                            (startX + dx)
                                .coerceAtLeast(0)
                        lp.y =
                            (startY + dy)
                                .coerceAtLeast(0)

                        runCatching {
                            root?.let { view ->
                                windowManager
                                    ?.updateViewLayout(
                                        view,
                                        lp,
                                    )
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        JourneyUiPreferences(context)
                            .savePosition(
                                lp.x,
                                lp.y,
                            )
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

    private fun money(value: Double): String =
        String.format(
            Locale("pt", "BR"),
            "%.2f",
            value,
        )
}
