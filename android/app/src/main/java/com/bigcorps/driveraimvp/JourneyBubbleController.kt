package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.os.Build
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
 * Janela flutuante final 0.23.
 *
 * Preserva as três últimas ofertas, expansão de detalhes, seleção para relatório,
 * Maps, continuidade de destino e estado operacional. A mudança desta versão é
 * visual: barra inferior por ícones e trilho opcional de mensagens rápidas.
 */
object JourneyBubbleController {
    private var root: LinearLayout? = null
    private var mainColumn: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var bubble: ImageView? = null
    private var railHost: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var appContext: Context? = null

    @Volatile private var expanded = false
    @Volatile private var messagesOpen = false
    @Volatile private var expandedOfferId: String? = null
    @Volatile private var deepExpandedOfferId: String? = null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var watcherRunning = false
    private var lastVisualSignature: String? = null

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        if (!JourneyUiPreferences(app).enabled()) {
            hide(app)
            return
        }
        main.post {
            appContext = app
            if (root == null) create(app) else refreshNow(app)
            ensureWatcher()
            MessagePresetClient023.refreshIfDue(
                app,
                force = true,
            ) {
                if (messagesOpen) {
                    main.post { rebuildMessageRail(app) }
                }
            }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!JourneyUiPreferences(app).enabled()) {
            hide(app)
            return
        }
        main.post {
            if (root != null) refreshNow(app)
            ensureWatcher()
        }
    }

    fun refreshOffer(context: Context) {
        if (!expanded) return
        val app = context.applicationContext
        main.post { if (root != null && expanded) rebuildPanel(app) }
    }

    fun hide(context: Context) {
        main.post {
            val view = root ?: return@post
            runCatching {
                (windowManager ?: context.getSystemService(WindowManager::class.java)).removeView(view)
            }
            stopWatcher()
            root = null
            mainColumn = null
            panel = null
            bubble = null
            railHost = null
            params = null
            windowManager = null
            appContext = null
            expanded = false
            messagesOpen = false
            expandedOfferId = null
            deepExpandedOfferId = null
            lastVisualSignature = null
        }
    }

    fun collapse() {
        main.post {
            expanded = false
            messagesOpen = false
            expandedOfferId = null
            deepExpandedOfferId = null
            panel?.visibility = View.GONE
            railHost?.visibility = View.GONE
            appContext?.let { app ->
                root?.post {
                    clampToVisibleBounds(
                        app,
                        persist = true,
                    )
                }
            }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.first
            y = position.second
        }

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            clipChildren = false
            clipToPadding = false
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    collapse(); true
                } else false
            }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipChildren = false
            clipToPadding = false
        }
        outer.addView(column)

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.srrotas_bubble_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = UiKit.rounded(context, bubbleSurface(context), 999, bubbleLine(context), 1)
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
            contentDescription = "Abrir menu do Sr. Rotas"
        }
        column.addView(icon)

        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            background = UiKit.rounded(context, bubbleSurface(context), 20, bubbleLine(context), 1)
            setPadding(
                UiKit.dp(context, 12), UiKit.dp(context, 10),
                UiKit.dp(context, 12), UiKit.dp(context, 11),
            )
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
        }
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
        val panelWidthDp = min(336, (screenWidthDp - 20).coerceAtLeast(270))
        column.addView(
            detail,
            LinearLayout.LayoutParams(UiKit.dp(context, panelWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UiKit.dp(context, 6)
            },
        )

        val rail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        outer.addView(
            rail,
            LinearLayout.LayoutParams(FloatingWindowChrome023.railWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = FloatingWindowChrome023.railGapPx(context)
                topMargin = UiKit.dp(context, prefs.sizeDp() + 6)
            },
        )

        root = outer
        mainColumn = column
        panel = detail
        bubble = icon
        railHost = rail
        params = lp
        windowManager = wm

        applyBubbleStyle(context)
        installDrag(icon, context)

        icon.setOnClickListener {
            expanded = !expanded
            detail.visibility = if (expanded) View.VISIBLE else View.GONE
            if (!expanded) {
                messagesOpen = false
                rail.visibility = View.GONE
                outer.post {
                    clampToVisibleBounds(
                        context,
                        persist = true,
                    )
                }
            } else {
                rebuildPanel(context)
            }
        }
        icon.setOnLongClickListener {
            prefs.cycleSize()
            applyBubbleStyle(context)
            true
        }

        runCatching { wm.addView(outer, lp) }
            .onSuccess {
                outer.post { clampToVisibleBounds(context, persist = true) }
            }
            .onFailure {
                root = null
                LocalLog.append(context, "Menu flutuante indisponível: ${it.message}")
            }

        refreshNow(context)
        ensureWatcher()
    }

    private fun refreshNow(context: Context) {
        if (!JourneyUiPreferences(context).enabled()) {
            hide(context)
            return
        }
        lastVisualSignature = currentVisualSignature(context)
        applyBubbleStyle(context)
        if (expanded) rebuildPanel(context)
        if (messagesOpen) rebuildMessageRail(context)
        root?.post { clampToVisibleBounds(context, persist = true) }
    }

    private fun applyBubbleStyle(context: Context) {
        val icon = bubble ?: return
        val detail = panel
        val prefs = JourneyUiPreferences(context)
        val settings = SettingsRepository(context).load()
        val size = UiKit.dp(context, prefs.sizeDp())
        icon.layoutParams = LinearLayout.LayoutParams(size, size)
        icon.alpha = prefs.opacityPercent() / 100f

        val screenWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
        val railReserve = if (messagesOpen) 54 else 0
        val usable = (screenWidthDp - 16 - railReserve).coerceAtLeast(238)
        val panelWidthDp = when (settings.hudCardSize.lowercase(Locale.ROOT)) {
            "compact" -> min(252, usable)
            "large" -> min(336, usable.coerceAtLeast(300))
            else -> min(286, usable.coerceAtLeast(260))
        }
        detail?.layoutParams = (detail?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = UiKit.dp(context, panelWidthDp)
        }

        val colorBlind = settings.colorBlindMode
        val surface = when {
            colorBlind -> 0xFFFDFBF3.toInt()
            bubbleDark(context) -> 0xFF0A3440.toInt()
            else -> bubbleSurface(context)
        }
        val line = when {
            colorBlind -> 0xFFC9C2A4.toInt()
            bubbleDark(context) -> 0xFF31535D.toInt()
            else -> bubbleLine(context)
        }
        icon.background = UiKit.rounded(context, surface, 999, line, 1)
        detail?.background = UiKit.rounded(context, surface, 20, line, 1)
        railHost?.layoutParams = (railHost?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            topMargin = UiKit.dp(context, prefs.sizeDp() + 6)
        }
        root?.requestLayout()
    }

    private fun ensureWatcher() {
        if (watcherRunning) return
        watcherRunning = true
        main.post(watcher)
    }

    private fun stopWatcher() {
        watcherRunning = false
        main.removeCallbacks(watcher)
    }

    private val watcher = object : Runnable {
        override fun run() {
            val context = appContext
            if (!watcherRunning || context == null || root == null) {
                watcherRunning = false
                return
            }
            val current = currentVisualSignature(context)
            if (current != lastVisualSignature) refreshNow(context)
            MessagePresetClient023.refreshIfDue(context) {
                if (messagesOpen) {
                    main.post { rebuildMessageRail(context) }
                }
            }
            main.postDelayed(this, 350L)
        }
    }

    private fun currentVisualSignature(context: Context): String {
        val prefs = JourneyUiPreferences(context)
        val settings = SettingsRepository(context).load()
        return listOf(
            prefs.enabled(), prefs.offerCount(), prefs.textSize(),
            prefs.sizeDp(), prefs.opacityPercent(), settings.hudCardSize,
            settings.hudTheme, settings.hudFontSize, settings.colorBlindMode,
            prefs.position().first, prefs.position().second, messagesOpen,
            MessagePresetStore023.syncedAt(context),
            viewportSize(context).first,
            viewportSize(context).second,
        ).joinToString("|")
    }

    private fun rebuildPanel(context: Context) {
        val holder = panel ?: return
        holder.removeAllViews()
        val p = UiKit.palette(context)
        val snapshot = JourneyCoordinator.snapshot(context)
        val prefs = JourneyUiPreferences(context)
        val offers = LocalStore.get(context).recentOffers(12)
            .filterNot { it.captureMethod.startsWith("historical-import/") }
            .take(prefs.offerCount())

        if (expandedOfferId != null && offers.none { it.localId == expandedOfferId }) expandedOfferId = null

        if (offers.isEmpty()) {
            holder.addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "As ${prefs.offerCount()} últimas ofertas aparecerão aqui durante a jornada.",
                        bubbleTextSp(context, 11f),
                    ),
                    bottom = 7,
                ),
            )
        } else {
            offers.forEachIndexed { index, offer ->
                holder.addView(offerRow(context, offer))
                if (index < offers.lastIndex) holder.addView(divider(context))
            }
        }
        holder.addView(divider(context))
        holder.addView(footerControls(context, snapshot))
        holder.post { clampToVisibleBounds(context, persist = true) }
    }

    private fun offerRow(context: Context, offer: RideOffer): View {
        val outcome = storeOutcome(context, offer.localId)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(context, 7), 0, UiKit.dp(context, 7))
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, bubbleSurfaceAlt(context), 14, verdictColor(context, offer.verdict), 3)
            setPadding(UiKit.dp(context, 10), UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9))
        }
        top.addView(TextView(context).apply {
            text = "●"; textSize = 17f; setTextColor(verdictColor(context, offer.verdict)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(UiKit.dp(context, 28), LinearLayout.LayoutParams.WRAP_CONTENT))
        top.addView(
            UiKit.title(context, serviceLabel(offer), bubbleTextSp(context, 14.5f)).apply {
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        top.addView(
            UiKit.title(context, "R$ ${money(offer.fare)}", bubbleTextSp(context, 15f)).apply {
                setTextColor(bubblePrimaryDark(context))
                gravity = Gravity.END
                setSingleLine(true)
            },
        )

        val isSelected = ReportSelection0211.isSelected(context, offer)
        val quick = TextView(context).apply {
            text = "✓"; textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isSelected) bubblePrimaryDark(context) else bubbleInk(context))
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(context, 10), UiKit.dp(context, 3), UiKit.dp(context, 10), UiKit.dp(context, 3))
            background = if (isSelected) UiKit.rounded(context, bubbleSurfaceAlt(context), 10, bubblePrimary(context), 2)
            else UiKit.rounded(context, bubbleSurface(context), 10, bubbleLine(context), 1)
            contentDescription = if (isSelected) "Remover dos relatórios" else "Selecionar para relatórios"
            setOnClickListener {
                ReportSelection0211.toggle(context, offer) {
                    expandedOfferId = offer.localId
                    rebuildPanel(context)
                }
            }
        }
        top.addView(quick)
        top.addView(TextView(context).apply {
            text = if (expandedOfferId == offer.localId) "⌃" else "⌄"
            textSize = 19f; gravity = Gravity.CENTER; setTextColor(bubbleInk(context))
            setPadding(UiKit.dp(context, 6), 0, UiKit.dp(context, 2), 0)
        })
        top.setOnClickListener {
            if (expandedOfferId == offer.localId) {
                expandedOfferId = null
                deepExpandedOfferId = null
            } else {
                expandedOfferId = offer.localId
                deepExpandedOfferId = null
            }
            rebuildPanel(context)
        }
        card.addView(top)
        if (expandedOfferId == offer.localId) card.addView(expandedOffer(context, offer, outcome))
        return card
    }

    /**
     * Primeiro nível: decisão rápida.
     * Destino + três ações + Busca + chance de nova corrida permanecem sempre
     * visíveis. Estatísticas secundárias só aparecem no segundo nível.
     */
    private fun expandedOffer(
        context: Context,
        offer: RideOffer,
        outcome: RideOutcome?,
    ): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiKit.dp(context, 8),
                UiKit.dp(context, 8),
                UiKit.dp(context, 8),
                UiKit.dp(context, 3),
            )
        }
        val ctx = offer.context

        box.addView(
            infoLine(
                context,
                "Destino",
                ctx?.destinationLabel?.takeIf(String::isNotBlank)
                    ?: "Destino não identificado",
            ),
        )

        val pickupIntent = OfferMaps.searchIntent(
            ctx?.pickupLabel,
            ctx?.pickupLat,
            ctx?.pickupLng,
        )
        val destinationIntent = OfferMaps.searchIntent(
            ctx?.destinationLabel,
            ctx?.destinationLat,
            ctx?.destinationLng,
        )
        val combinedIntent = CombinedRoute0212.intent(ctx)

        box.addView(
            routeActions(
                context,
                pickupIntent,
                destinationIntent,
                combinedIntent,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 8)
            },
        )

        val compactSignals = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val searchGrade = pickupGrade(context, offer)
        compactSignals.addView(
            compactSignal(
                context,
                "Busca",
                searchGrade.first,
                searchGrade.second,
            ),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val continuity =
            DestinationContinuityClient0211.get(offer.localId)
        compactSignals.addView(
            compactSignal(
                context,
                "Destino",
                continuity?.let(DestinationContinuityPresentation0211::cardTitle)
                    ?: "dados insuficientes",
                1,
            ),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(context, 6)
            },
        )
        box.addView(
            compactSignals,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 7)
            },
        )

        val deepOpen = deepExpandedOfferId == offer.localId
        box.addView(
            TextView(context).apply {
                text = if (deepOpen) "MENOS DETALHES" else "MAIS DETALHES"
                textSize = bubbleTextSp(context, 9.5f)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(bubblePrimary(context))
                setPadding(
                    UiKit.dp(context, 8),
                    UiKit.dp(context, 8),
                    UiKit.dp(context, 8),
                    UiKit.dp(context, 8),
                )
                setOnClickListener {
                    deepExpandedOfferId =
                        if (deepOpen) null else offer.localId
                    rebuildPanel(context)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 3)
            },
        )

        if (deepOpen) {
            box.addView(
                deepDetails(context, offer, outcome),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        return box
    }

    private fun routeActions(
        context: Context,
        pickupIntent: Intent?,
        destinationIntent: Intent?,
        combinedIntent: Intent?,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val actions = listOf(
                Triple("BUSCAR", pickupIntent, true),
                Triple("DESTINO", destinationIntent, false),
                Triple("COMBINADO", combinedIntent, false),
            )
            actions.forEachIndexed { index, (label, intent, primary) ->
                addView(
                    compactButton(
                        context,
                        label,
                        primary,
                        intent != null,
                    ) {
                        intent?.let {
                            runCatching { context.startActivity(it) }
                        }
                    },
                    LinearLayout.LayoutParams(
                        0,
                        UiKit.dp(context, 44),
                        1f,
                    ).apply {
                        if (index > 0) {
                            marginStart = UiKit.dp(context, 5)
                        }
                    },
                )
            }
        }

    private fun compactSignal(
        context: Context,
        label: String,
        value: String,
        rank: Int,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiKit.rounded(
                context,
                bubbleSurfaceAlt(context),
                11,
                when (rank) {
                    2 -> UiKit.palette(context).good
                    0 -> UiKit.palette(context).bad
                    else -> UiKit.palette(context).warn
                },
                1,
            )
            setPadding(
                UiKit.dp(context, 6),
                UiKit.dp(context, 6),
                UiKit.dp(context, 6),
                UiKit.dp(context, 6),
            )
            addView(
                UiKit.body(context, label, bubbleTextSp(context, 8.5f)).apply {
                    gravity = Gravity.CENTER
                },
            )
            addView(
                UiKit.body(context, value, bubbleTextSp(context, 9.5f)).apply {
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(bubbleInk(context))
                    maxLines = 2
                },
            )
        }

    private fun deepDetails(
        context: Context,
        offer: RideOffer,
        outcome: RideOutcome?,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(context, 5), 0, 0)
            val ctx = offer.context

            addView(
                infoLine(
                    context,
                    "Busca / retirada",
                    ctx?.pickupLabel?.takeIf(String::isNotBlank)
                        ?: "Não identificado",
                ),
            )

            if (
                !ctx?.destinationLabel.isNullOrBlank() ||
                !ctx?.destinationCell.isNullOrBlank()
            ) {
                addView(
                    UiKit.margin(
                        destinationContinuityView(
                            context,
                            DestinationContinuityClient0211.get(offer.localId),
                        ),
                        top = 7,
                    ),
                )
            }

            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        buildString {
                            offer.perKm?.let { append("R$ ${money(it)}/km") }
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
                            offer.totalMinutes?.let { append(" · $it min") }
                            offer.estimatedProfit?.let {
                                append("\nLucro est.* R$ ${money(it)}")
                            }
                        }.ifBlank {
                            "Detalhes financeiros disponíveis no HUD."
                        },
                        bubbleTextSp(context, 10.5f),
                    ),
                    top = 7,
                ),
            )

            if (outcome?.status == RideOperationalStatus.DOING_RIDE) {
                val actions = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actions.addView(
                    compactButton(context, "REALIZADA", true) {
                        JourneyCoordinator.completeCurrentRide(
                            context,
                            "bubble_024",
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
                    compactButton(context, "NÃO REALIZADA", false) {
                        JourneyCoordinator.cancelCurrentRide(
                            context,
                            "bubble_024",
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
                addView(UiKit.margin(actions, top = 6))
            }
        }

    private fun destinationContinuityView(context: Context, insight: DestinationContinuityInsight0211?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, bubbleSurfaceAlt(context), 12, bubbleLine(context), 1)
            setPadding(UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9), UiKit.dp(context, 8))
            val color = when (insight?.level) {
                "high" -> UiKit.palette(context).good
                "medium" -> UiKit.palette(context).warn
                "low" -> UiKit.palette(context).bad
                else -> bubbleMuted(context)
            }
            addView(UiKit.body(context, insight?.let(DestinationContinuityPresentation0211::cardTitle) ?: "Nova corrida no destino: analisando…", bubbleTextSp(context, 11f)).apply {
                setTextColor(color); setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(UiKit.body(context, insight?.let { value ->
                buildString {
                    value.regionLabel?.let { append("$it · ") }
                    append(DestinationContinuityPresentation0211.detail(value))
                }
            } ?: "Consultando região, dia e faixa de horário sem interromper o OCR.", bubbleTextSp(context, 9f)).apply { setTextColor(bubbleMuted(context)) })
        }

    /** 0.23: não trunca endereços; deixa o TextView quebrar linha naturalmente. */
    private fun infoLine(context: Context, label: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(UiKit.body(context, label, bubbleTextSp(context, 10f)).apply {
            setTextColor(bubblePrimary(context)); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(UiKit.body(context, value, bubbleTextSp(context, 12f)).apply { setTextColor(bubbleInk(context)) })
    }

    private fun footerControls(context: Context, snapshot: JourneyOperationalSnapshot): View {
        val active = snapshot.journeyState == JourneyOperationalState.ACTIVE
        val paused = snapshot.journeyState == JourneyOperationalState.PAUSED
        return FloatingWindowChrome023.bottomBar(
            context = context,
            messagesOpen = messagesOpen,
            playEnabled = !active,
            pauseEnabled = active,
            stopEnabled = active || paused,
            actions = FloatingWindowChrome023.Actions(
                play = {
                    if (paused) JourneyCoordinator.resumeJourney(context) else openMainForStart(context)
                    rebuildPanel(context)
                },
                pause = {
                    JourneyCoordinator.pauseJourney(context)
                    rebuildPanel(context)
                },
                stop = {
                    JourneyCoordinator.endJourney(context, "bubble_023_end")
                    messagesOpen = false
                    railHost?.visibility = View.GONE
                    show(context)
                },
                history = {
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_HISTORY)
                    })
                },
                toggleMessages = {
                    messagesOpen = !messagesOpen
                    applyBubbleStyle(context)
                    if (messagesOpen) {
                        MessagePresetClient023.refreshIfDue(
                            context,
                            force = true,
                        ) {
                            main.post {
                                rebuildMessageRail(context)
                            }
                        }
                    }
                    rebuildMessageRail(context)
                    rebuildPanel(context)
                },
            ),
        )
    }

    private fun rebuildMessageRail(context: Context) {
        val host = railHost ?: return
        host.removeAllViews()
        if (!expanded || !messagesOpen) {
            host.visibility = View.GONE
            return
        }
        host.visibility = View.VISIBLE
        val shortcuts = MessagePresetStore023.load(context)
        host.addView(
            FloatingWindowChrome023.messageRail(context, shortcuts) { shortcut ->
                MessageShortcutClipboard023.copy(context, shortcut)
            },
            LinearLayout.LayoutParams(FloatingWindowChrome023.railWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun openMainForStart(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_START)
        })
        collapse()
    }

    private fun compactButton(context: Context, text: String, primary: Boolean, enabled: Boolean = true, action: () -> Unit): TextView {
        val p = UiKit.palette(context)
        return TextView(context).apply {
            this.text = text; textSize = bubbleTextSp(context, 10.5f); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(UiKit.dp(context, 8), UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9))
            minHeight = UiKit.dp(context, 38)
            setTextColor(if (primary) Color.WHITE else p.ink)
            background = UiKit.rounded(context, if (primary) p.primaryDark else p.surface, 11, if (primary) p.primaryDark else p.line, 1)
            isEnabled = enabled; alpha = if (enabled) 1f else .4f
            setOnClickListener { if (enabled) action() }
        }
    }

    private fun bubbleTextSp(
        context: Context,
        base: Float,
    ): Float =
        when (JourneyUiPreferences(context).textSize()) {
            "small" -> base * 0.88f
            "large" -> base * 1.16f
            else -> base
        }

    private fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(bubbleLine(context))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun storeOutcome(context: Context, localOfferId: String): RideOutcome? = LocalStore.get(context).rideOutcomeForOffer(localOfferId)

    private fun verdictColor(context: Context, verdict: String): Int = when (verdict) {
        "boa" -> UiKit.palette(context).good
        "ruim" -> UiKit.palette(context).bad
        else -> UiKit.palette(context).warn
    }

    private fun bubbleDark(context: Context): Boolean = when (SettingsRepository(context).load().hudTheme.lowercase(Locale.ROOT)) {
        "dark" -> true
        "light" -> false
        else -> Appearance021.isDark(context)
    }
    private fun bubblePalette(context: Context): UiKit.Palette = UiKit.palette(bubbleDark(context))
    private fun bubbleSurface(context: Context): Int = bubblePalette(context).surface
    private fun bubbleSurfaceAlt(context: Context): Int = bubblePalette(context).surfaceAlt
    private fun bubbleInk(context: Context): Int = bubblePalette(context).ink
    private fun bubbleMuted(context: Context): Int = bubblePalette(context).muted
    private fun bubbleLine(context: Context): Int = bubblePalette(context).line
    private fun bubblePrimary(context: Context): Int = bubblePalette(context).primary
    private fun bubblePrimaryDark(context: Context): Int = if (bubbleDark(context)) bubblePalette(context).primary else bubblePalette(context).primaryDark

    private fun pickupGrade(context: Context, offer: RideOffer): Pair<String, Int> {
        val s = SettingsRepository(context).load()
        val grade = PickupPresentation0211.grade(offer.pickupKm, offer.pickupMinutes, s.maxPickupKm, Strategy021Store.load(context).maxPickupMinutes)
        return grade.label to grade.rank
    }

    private fun serviceLabel(offer: RideOffer): String {
        val raw = offer.serviceType.lowercase(Locale.ROOT).replace("_", "").replace("-", "")
        val service = when (raw) {
            "uberx" -> "UberX"
            "comfort", "ubercomfort" -> "Comfort"
            "black", "uberblack" -> "Black"
            "electric", "uberelectric" -> "Electric"
            "priority", "uberpriority" -> "Priority"
            "moto", "ubermoto" -> "Moto"
            "99pop", "pop" -> "99Pop"
            "99plus", "plus" -> "99Plus"
            "99moto" -> "99Moto"
            "99taxi", "taxi" -> "99Táxi"
            else -> offer.serviceType.takeIf { it.isNotBlank() && it != "unknown" }?.replaceFirstChar { it.uppercase() }
        }
        return service ?: when (offer.platform.lowercase(Locale.ROOT)) {
            "99" -> "99"
            "uber" -> "Uber"
            else -> "Oferta"
        }
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
                        val viewport = viewportSize(context)
                        // O gesto pertence ao botão. Não usamos a largura
                        // do painel expandido para limitar o alcance do botão,
                        // especialmente em tablets.
                        val contentWidth =
                            icon.width.takeIf { it > 0 }
                                ?: UiKit.dp(
                                    context,
                                    JourneyUiPreferences(context).sizeDp(),
                                )
                        val contentHeight =
                            icon.height.takeIf { it > 0 }
                                ?: contentWidth
                        val safe = OverlayBounds024.clamp(
                            startX + dx,
                            startY + dy,
                            viewport.first,
                            viewport.second,
                            contentWidth,
                            contentHeight,
                            UiKit.dp(context, 6),
                        )
                        lp.x = safe.x
                        lp.y = safe.y
                        runCatching {
                            root?.let {
                                windowManager?.updateViewLayout(it, lp)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        clampToVisibleBounds(context, persist = true)
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

    /** Reposiciona um botão salvo fora da tela e reage a rotação/multi-window. */
    fun restorePosition(context: Context) {
        val app = context.applicationContext
        main.post {
            val position = JourneyUiPreferences(app).resetPosition()
            params?.let { lp ->
                lp.x = position.first
                lp.y = position.second
                clampToVisibleBounds(app, persist = true)
                root?.let { view ->
                    runCatching { windowManager?.updateViewLayout(view, lp) }
                }
            }
        }
    }

    private fun viewportSize(context: Context): Pair<Int, Int> {
        val wm = windowManager
            ?: context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // TYPE_APPLICATION_OVERLAY percorre o display. Em tablets,
            // currentWindowMetrics pode refletir somente a janela do app.
            // maximumWindowMetrics representa a área máxima do display.
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = context.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun clampToVisibleBounds(
        context: Context,
        persist: Boolean,
    ) {
        val lp = params ?: return
        val view = root ?: return
        val viewport = viewportSize(context)
        val bubbleSize =
            bubble?.width?.takeIf { it > 0 }
                ?: UiKit.dp(context, JourneyUiPreferences(context).sizeDp())
        val contentWidth =
            if (expanded) {
                view.width.takeIf { it > 0 } ?: bubbleSize
            } else {
                bubbleSize
            }
        val contentHeight =
            if (expanded) {
                view.height.takeIf { it > 0 } ?: bubbleSize
            } else {
                bubbleSize
            }

        val safe = OverlayBounds024.clamp(
            x = lp.x,
            y = lp.y,
            viewportWidth = viewport.first,
            viewportHeight = viewport.second,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            margin = UiKit.dp(context, 6),
        )

        if (safe.x == lp.x && safe.y == lp.y) return
        lp.x = safe.x
        lp.y = safe.y
        runCatching { windowManager?.updateViewLayout(view, lp) }
        if (persist) {
            JourneyUiPreferences(context).savePosition(lp.x, lp.y)
        }
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
}
