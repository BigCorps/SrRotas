package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Assistente Ativo 0.26.0.
 *
 * Não usa LLM para decidir deslocamento. Reaproveita a inteligência regional
 * pessoal/coletiva já existente e só mostra uma sugestão quando há evidência
 * suficiente, proximidade e tempo sem novas ofertas.
 */
object ActiveAssistant026 {
    private const val PREFS = "sr_active_assistant_026"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SUGGESTION_AT = "last_suggestion_at"
    private const val KEY_MANUAL_RIDE_OFFER = "manual_ride_offer"
    private const val LOOP_MS = 60_000L
    private const val ARM_WINDOW_MS = 120_000L

    private val main = Handler(Looper.getMainLooper())
    private val fetching = AtomicBoolean(false)
    private var appContext: Context? = null
    private var running = false
    private var armedUntilMs = 0L
    private var lastEvaluationMs = 0L
    private var overlay: View? = null
    private var overlayManager: WindowManager? = null
    private var autoHide: Runnable? = null

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) ensureRunning(context) else stop(context, clearManualSuppression = false)
    }

    fun showSettings(context: Context, onChanged: () -> Unit = {}) {
        val values = arrayOf(
            "Ativado · sugerir após 10 min sem oferta",
            "Desativado",
        )
        val selected = if (isEnabled(context)) 0 else 1
        AlertDialog.Builder(context)
            .setTitle("Assistente ativo")
            .setMessage("Sugere regiões próximas com histórico melhor. Ele não mostra sugestões enquanto uma corrida estiver marcada como ativa.")
            .setSingleChoiceItems(values, selected) { dialog, which ->
                setEnabled(context, which == 0)
                dialog.dismiss()
                onChanged()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Chamada antes de solicitar MediaProjection; aguarda o journey_id real. */
    fun arm(context: Context) {
        if (!isEnabled(context)) return
        appContext = context.applicationContext
        armedUntilMs = System.currentTimeMillis() + ARM_WINDOW_MS
        startLoop()
    }

    /** Reidrata o monitor quando a tela Agora é reconstruída com jornada ativa. */
    fun ensureRunning(context: Context) {
        if (!isEnabled(context)) return
        val app = context.applicationContext
        if (SettingsRepository(app).currentJourneyId().isBlank()) return
        appContext = app
        armedUntilMs = 0L
        startLoop()
    }

    fun stop(context: Context, clearManualSuppression: Boolean = true) {
        appContext = context.applicationContext
        running = false
        armedUntilMs = 0L
        fetching.set(false)
        main.removeCallbacks(loop)
        dismissOverlay()
        if (clearManualSuppression) {
            prefs(context).edit().remove(KEY_MANUAL_RIDE_OFFER).apply()
        }
    }

    private fun startLoop() {
        if (running) return
        running = true
        main.removeCallbacks(loop)
        main.postDelayed(loop, 1_000L)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val app = appContext ?: return
            if (!isEnabled(app)) {
                stop(app, clearManualSuppression = false)
                return
            }

            val journeyId = SettingsRepository(app).currentJourneyId().trim()
            if (journeyId.isBlank()) {
                dismissOverlay()
                if (armedUntilMs > System.currentTimeMillis()) {
                    main.postDelayed(this, 1_000L)
                } else {
                    running = false
                }
                return
            }
            armedUntilMs = 0L
            tick(app, journeyId)
            if (running) main.postDelayed(this, LOOP_MS)
        }
    }

    private fun tick(context: Context, journeyId: String) {
        val now = System.currentTimeMillis()
        val snapshot = JourneyCoordinator.snapshot(context)
        if (snapshot.journeyState != JourneyOperationalState.ACTIVE || snapshot.currentRide != null) {
            dismissOverlay()
            return
        }

        val store = LocalStore.get(context)
        val latest = store.recentOffers(100).firstOrNull { it.journeyId == journeyId }
        val currentOfferKey = latest?.localId ?: "none:$journeyId"
        val p = prefs(context)
        val suppressedAtOffer = p.getString(KEY_MANUAL_RIDE_OFFER, "").orEmpty()
        if (suppressedAtOffer.isNotBlank() && suppressedAtOffer != currentOfferKey) {
            p.edit().remove(KEY_MANUAL_RIDE_OFFER).apply()
        } else if (suppressedAtOffer.isNotBlank()) {
            dismissOverlay()
            return
        }

        val anchorMs = latest?.observedAt?.let(::epochMs)
            ?: store.journey(journeyId)?.startedAt?.let(::epochMs)
            ?: now
        if (!ActiveAssistantRules026.idleEnough(now, anchorMs)) {
            dismissOverlay()
            return
        }
        if (!ActiveAssistantRules026.evaluationAllowed(now, lastEvaluationMs)) return
        val lastSuggestion = p.getLong(KEY_LAST_SUGGESTION_AT, 0L)
        if (!ActiveAssistantRules026.suggestionAllowed(now, lastSuggestion)) return
        if (!Settings.canDrawOverlays(context) || !fetching.compareAndSet(false, true)) return

        lastEvaluationMs = now
        fetchCandidates(context) { candidates ->
            fetching.set(false)
            if (!isEnabled(context)) return@fetchCandidates
            val activeJourney = SettingsRepository(context).currentJourneyId().trim()
            val live = JourneyCoordinator.snapshot(context)
            if (
                activeJourney != journeyId ||
                live.journeyState != JourneyOperationalState.ACTIVE ||
                live.currentRide != null
            ) return@fetchCandidates
            if (prefs(context).getString(KEY_MANUAL_RIDE_OFFER, "").orEmpty().isNotBlank()) return@fetchCandidates

            val settings = SettingsRepository(context).load()
            val ranked = ActiveAssistantRules026.rank(
                candidates,
                targetPerKm = settings.minPerKm,
                targetPerHour = settings.minPerHour,
            ) ?: return@fetchCandidates

            val latestNow = LocalStore.get(context).recentOffers(100).firstOrNull { it.journeyId == journeyId }
            val latestAnchor = latestNow?.observedAt?.let(::epochMs)
                ?: LocalStore.get(context).journey(journeyId)?.startedAt?.let(::epochMs)
                ?: System.currentTimeMillis()
            if (!ActiveAssistantRules026.idleEnough(System.currentTimeMillis(), latestAnchor)) return@fetchCandidates

            prefs(context).edit().putLong(KEY_LAST_SUGGESTION_AT, System.currentTimeMillis()).apply()
            showSuggestion(context, journeyId, ranked)
        }
    }

    private fun fetchCandidates(
        context: Context,
        onDone: (List<ActiveAssistantRules026.Candidate>) -> Unit,
    ) {
        val all = mutableListOf<ActiveAssistantRules026.Candidate>()
        val profile = when (Strategy021Store.load(context).strategyPreset) {
            "popular" -> "popular"
            "comfort" -> "comfort"
            "premium" -> "premium"
            else -> "all"
        }

        fun add(result: RegionalClient.Result) {
            result.tips.forEach { tip ->
                all += ActiveAssistantRules026.Candidate(
                    region = tip.region,
                    distanceKm = tip.distanceKm,
                    samples = tip.samples,
                    perKm = tip.medianPerKm,
                    perHour = tip.medianPerHour,
                    confidence = tip.confidence,
                    source = tip.source.ifBlank { result.resolvedSource },
                )
            }
        }

        fun fetchCollectiveOrFinish() {
            if (!SettingsRepository(context).load().collectiveStatsOptIn) {
                onDone(all)
                return
            }
            RegionalClient.fetch(context, "now", "collective", "", profile) { result ->
                result.onSuccess(::add)
                onDone(all)
            }
        }

        RegionalClient.fetch(context, "now", "personal", "", profile) { result ->
            result.onSuccess(::add)
            fetchCollectiveOrFinish()
        }
    }

    private fun showSuggestion(
        context: Context,
        journeyId: String,
        ranked: ActiveAssistantRules026.Ranked,
    ) {
        dismissOverlay()
        val wm = context.getSystemService(WindowManager::class.java)
        val p = SrUi023.palette(context)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = SrUi023.rounded(p.surface, 18, p.purple, 1, context)
            elevation = dp(context, 8).toFloat()

            addView(TextView(context).apply {
                text = "SR  Assistente ativo"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.purple)
            })
            addView(TextView(context).apply {
                text = "Há uma região próxima com histórico melhor"
                textSize = 11f
                setTextColor(p.muted)
                setPadding(0, dp(context, 3), 0, 0)
            })
            addView(TextView(context).apply {
                text = ranked.region
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.ink)
                setPadding(0, dp(context, 7), 0, 0)
            })
            addView(TextView(context).apply {
                text = detailText(ranked)
                textSize = 11.5f
                setTextColor(p.ink)
                setPadding(0, dp(context, 3), 0, 0)
            })
            addView(TextView(context).apply {
                text = "Base: ${ranked.sources.sorted().joinToString(" + ")} · tendência, não garantia"
                textSize = 9.5f
                setTextColor(p.muted)
                setPadding(0, dp(context, 3), 0, 0)
            })

            val buttons = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            buttons.addView(actionButton(context, "Estou em corrida", p.purple) {
                val latest = LocalStore.get(context).recentOffers(100).firstOrNull { it.journeyId == journeyId }
                prefs(context).edit()
                    .putString(KEY_MANUAL_RIDE_OFFER, latest?.localId ?: "none:$journeyId")
                    .apply()
                dismissOverlay()
                LocalLog.append(context, "ASSISTENTE 0.26 silenciado manualmente · motorista informou corrida ativa")
            }, LinearLayout.LayoutParams(0, dp(context, 44), 1f))
            buttons.addView(actionButton(context, "Ignorar", p.blue) {
                dismissOverlay()
            }, LinearLayout.LayoutParams(0, dp(context, 44), 1f).apply {
                marginStart = dp(context, 8)
            })
            addView(buttons, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 10) })
        }

        val params = WindowManager.LayoutParams(
            dp(context, 330),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(context, 10)
            y = dp(context, 92)
        }

        runCatching { wm.addView(card, params) }
            .onSuccess {
                overlay = card
                overlayManager = wm
                val hide = Runnable { dismissOverlay() }
                autoHide = hide
                main.postDelayed(hide, ActiveAssistantRules026.CARD_TIMEOUT_MS)
                LocalLog.append(
                    context,
                    "ASSISTENTE 0.26 sugeriu região=${ranked.region} distância=${String.format(Locale.US, "%.1f", ranked.distanceKm)}km fontes=${ranked.sources.joinToString("+")}",
                )
            }
    }

    private fun detailText(ranked: ActiveAssistantRules026.Ranked): String = buildString {
        append(String.format(Locale("pt", "BR"), "%.1f km", ranked.distanceKm))
        ranked.perKm?.let { append(String.format(Locale("pt", "BR"), " · R$ %.2f/km", it)) }
        ranked.perHour?.let { append(String.format(Locale("pt", "BR"), " · R$ %.0f/h", it)) }
        if (ranked.samples > 0) append(" · ${ranked.samples} amostras")
    }

    private fun actionButton(context: Context, label: String, accent: Int, click: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = SrUi023.rounded(accent, 12, accent, 1, context)
            setOnClickListener { click() }
        }

    private fun dismissOverlay() {
        autoHide?.let(main::removeCallbacks)
        autoHide = null
        val view = overlay
        val wm = overlayManager
        overlay = null
        overlayManager = null
        if (view != null && wm != null) runCatching { wm.removeView(view) }
    }

    private fun epochMs(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(context: Context, value: Int): Int = SrUi023.dp(context, value)
}
