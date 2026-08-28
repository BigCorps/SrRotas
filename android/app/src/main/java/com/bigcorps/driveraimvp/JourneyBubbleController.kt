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

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var watcherRunning = false
    private var lastVisualSignature: String? = null

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        main.post {
            appContext = app
            if (root == null) create(app) else refreshNow(app)
            ensureWatcher()
            MessagePresetClient023.refresh(app) {
                if (messagesOpen) main.post { rebuildMessageRail(app) }
            }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
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
            lastVisualSignature = null
        }
    }

    fun collapse() {
        main.post {
            expanded = false
            messagesOpen = false
            expandedOfferId = null
            panel?.visibility = View.GONE
            railHost?.visibility = View.GONE
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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
            .onFailure {
                root = null
                LocalLog.append(context, "Menu flutuante indisponível: ${it.message}")
            }

        refreshNow(context)
        ensureWatcher()
    }

    private fun refreshNow(context: Context) {
        lastVisualSignature = currentVisualSignature(context)
        applyBubbleStyle(context)
        if (expanded) rebuildPanel(context)
        if (messagesOpen) rebuildMessageRail(context)
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
            main.postDelayed(this, 350L)
        }
    }

    private fun currentVisualSignature(context: Context): String {
        val prefs = JourneyUiPreferences(context)
        val settings = SettingsRepository(context).load()
        return listOf(
            prefs.sizeDp(), prefs.opacityPercent(), settings.hudCardSize,
            settings.hudTheme, settings.hudFontSize, settings.colorBlindMode,
            prefs.position().first, prefs.position().second, messagesOpen,
            MessagePresetStore023.syncedAt(context),
        ).joinToString("|")
    }

    private fun rebuildPanel(context: Context) {
        val holder = panel ?: return
        holder.removeAllViews()
        val p = UiKit.palette(context)
        val snapshot = JourneyCoordinator.snapshot(context)
        val offers = LocalStore.get(context).recentOffers(12)
            .filterNot { it.captureMethod.startsWith("historical-import/") }
            .take(3)

        if (expandedOfferId != null && offers.none { it.localId == expandedOfferId }) expandedOfferId = null

        holder.addView(header(context))
        holder.addView(divider(context))
        if (offers.isEmpty()) {
            holder.addView(UiKit.margin(UiKit.body(context, "As 3 últimas ofertas aparecerão aqui durante a jornada.", 12f), top = 10, bottom = 6))
        } else {
            offers.forEachIndexed { index, offer ->
                holder.addView(offerRow(context, offer))
                if (index < offers.lastIndex) holder.addView(divider(context))
            }
        }
        holder.addView(divider(context))
        holder.addView(footerControls(context, snapshot))

        val queue = SyncCoordinator.pending(context)
        if (queue.total > 0) {
            holder.addView(TextView(context).apply {
                text = if (SyncCoordinator.isRunning()) "Sincronizando ${queue.total} item(ns)…" else "${queue.total} item(ns) aguardando sincronização"
                textSize = 9.5f
                setTextColor(p.muted)
                gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(context, 7), 0, 0)
            })
        }
    }

    private fun header(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, UiKit.dp(context, 7))
        addView(UiKit.body(context, "Últimas ofertas", 12f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(bubbleInk(context))
            setPadding(UiKit.dp(context, 9), 0, UiKit.dp(context, 3), 0)
            contentDescription = "Fechar menu"
            setOnClickListener { collapse() }
        })
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
        top.addView(UiKit.title(context, serviceLabel(offer), 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(UiKit.title(context, "R$ ${money(offer.fare)}", 16f).apply {
            setTextColor(bubblePrimaryDark(context)); gravity = Gravity.END
        })

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
            expandedOfferId = if (expandedOfferId == offer.localId) null else offer.localId
            rebuildPanel(context)
        }
        card.addView(top)
        if (expandedOfferId == offer.localId) card.addView(expandedOffer(context, offer, outcome))
        return card
    }

    private fun expandedOffer(context: Context, offer: RideOffer, outcome: RideOutcome?): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 10), UiKit.dp(context, 9), UiKit.dp(context, 10), UiKit.dp(context, 3))
        }
        val ctx = offer.context
        box.addView(infoLine(context, "Busca", ctx?.pickupLabel?.takeIf(String::isNotBlank) ?: "Não identificado"))
        box.addView(UiKit.margin(infoLine(context, "Destino", ctx?.destinationLabel?.takeIf(String::isNotBlank) ?: "Não identificado"), top = 7))

        if (!ctx?.destinationLabel.isNullOrBlank() || !ctx?.destinationCell.isNullOrBlank()) {
            box.addView(UiKit.margin(destinationContinuityView(context, DestinationContinuityClient0211.get(offer.localId)), top = 7))
        }
        val searchGrade = pickupGrade(context, offer)
        box.addView(UiKit.margin(UiKit.pill(context, "Busca ${searchGrade.first}", when (searchGrade.second) { 2 -> "good"; 0 -> "bad"; else -> "warn" }), top = 7))
        box.addView(UiKit.margin(UiKit.body(context, buildString {
            offer.perKm?.let { append("R$ ${money(it)}/km") }
            offer.perMinute?.let { if (isNotEmpty()) append(" · "); append("R$ ${money(it)}/min") }
            offer.perHour?.let { if (isNotEmpty()) append(" · "); append("R$ ${money(it)}/h") }
            offer.totalKm?.let { if (isNotEmpty()) append("\n"); append("${money(it)} km") }
            offer.totalMinutes?.let { append(" · $it min") }
            offer.estimatedProfit?.let { append("\nLucro est.* R$ ${money(it)}") }
        }.ifBlank { "Detalhes financeiros disponíveis no HUD." }, 11f), top = 7))

        val pickupIntent = OfferMaps.searchIntent(ctx?.pickupLabel, ctx?.pickupLat, ctx?.pickupLng)
        val destinationIntent = OfferMaps.searchIntent(ctx?.destinationLabel, ctx?.destinationLat, ctx?.destinationLng)
        val combinedIntent = CombinedRoute0212.intent(ctx)
        val maps = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        maps.addView(compactButton(context, "BUSCAR", true, pickupIntent != null) { pickupIntent?.let { runCatching { context.startActivity(it) } } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        maps.addView(compactButton(context, "DESTINO", false, destinationIntent != null) { destinationIntent?.let { runCatching { context.startActivity(it) } } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 6) })
        box.addView(UiKit.margin(maps, top = 8))
        box.addView(UiKit.margin(compactButton(context, "COMBINADO · MAPS", false, combinedIntent != null) { combinedIntent?.let { runCatching { context.startActivity(it) } } }, top = 6))

        if (outcome?.status == RideOperationalStatus.DOING_RIDE) {
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(compactButton(context, "REALIZADA", true) {
                JourneyCoordinator.completeCurrentRide(context, "bubble_023"); rebuildPanel(context)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(compactButton(context, "NÃO REALIZADA", false) {
                JourneyCoordinator.cancelCurrentRide(context, "bubble_023"); rebuildPanel(context)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 6) })
            box.addView(UiKit.margin(actions, top = 6))
        }
        return box
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
            addView(UiKit.body(context, insight?.let(DestinationContinuityPresentation0211::cardTitle) ?: "Nova corrida no destino: analisando…", 11f).apply {
                setTextColor(color); setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(UiKit.body(context, insight?.let { value ->
                buildString {
                    value.regionLabel?.let { append("$it · ") }
                    append(DestinationContinuityPresentation0211.detail(value))
                }
            } ?: "Consultando região, dia e faixa de horário sem interromper o OCR.", 9f).apply { setTextColor(bubbleMuted(context)) })
        }

    /** 0.23: não trunca endereços; deixa o TextView quebrar linha naturalmente. */
    private fun infoLine(context: Context, label: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(UiKit.body(context, label, 10f).apply {
            setTextColor(bubblePrimary(context)); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(UiKit.body(context, value, 12f).apply { setTextColor(bubbleInk(context)) })
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
            this.text = text; textSize = 10.5f; gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(UiKit.dp(context, 8), UiKit.dp(context, 9), UiKit.dp(context, 8), UiKit.dp(context, 9))
            minHeight = UiKit.dp(context, 38)
            setTextColor(if (primary) Color.WHITE else p.ink)
            background = UiKit.rounded(context, if (primary) p.primaryDark else p.surface, 11, if (primary) p.primaryDark else p.line, 1)
            isEnabled = enabled; alpha = if (enabled) 1f else .4f
            setOnClickListener { if (enabled) action() }
        }
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
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        icon.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) {
                        lp.x = (startX + dx).coerceAtLeast(0); lp.y = (startY + dy).coerceAtLeast(0)
                        runCatching { root?.let { windowManager?.updateViewLayout(it, lp) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) JourneyUiPreferences(context).savePosition(lp.x, lp.y) else icon.performClick(); true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
}
