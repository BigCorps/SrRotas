package com.srrotas.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.ln

/**
 * Base Pessoal + Coletiva 0.26.4.
 *
 * Quando o motorista participa da Base Coletiva, monta uma lista visual única
 * com as melhores regiões das duas fontes. Alterna as fontes somente quando
 * a qualidade é comparável; uma região claramente melhor continua primeiro.
 */
object NowRegionalBlend0264 {
    private data class State(
        var signature: String = "",
        var fetchedAt: Long = 0L,
        var pending: Boolean = false,
    )

    private val states = WeakHashMap<NowPanel023, State>()

    fun decorate(now: NowPanel023) {
        val personalButton = findText(now, "Base pessoal") ?: return
        val collectiveButton = findText(now, "Base coletiva") ?: return
        decorateSourceButtons(personalButton, collectiveButton)

        val collectiveActive = isBold(collectiveButton)
        val sourceParent = collectiveButton.parent as? ViewGroup ?: return
        val content = sourceParent.parent as? LinearLayout ?: return
        val originalResults = locateOriginalResults(content) ?: return
        val mixedHost = ensureMixedHost(content, originalResults)

        if (!collectiveActive) {
            mixedHost.visibility = View.GONE
            originalResults.visibility = View.VISIBLE
            return
        }

        val mode = activeLabel(now, listOf("Momento" to "now", "Hoje" to "today", "Semana" to "week", "Pesquisa" to "search")) ?: "now"
        val profile = activeLabel(now, listOf("Todas" to "all", "Popular" to "popular", "Conforto" to "comfort", "Premium" to "premium")) ?: "all"
        val region = findEditText(now, "Bairro ou região")?.text?.toString()?.trim().orEmpty()
        val signature = "$mode|$profile|$region"
        val state = states.getOrPut(now) { State() }
        val nowMs = System.currentTimeMillis()

        if (state.pending) return
        if (state.signature == signature && nowMs - state.fetchedAt < 20_000L && mixedHost.childCount > 0) {
            mixedHost.visibility = View.VISIBLE
            originalResults.visibility = View.GONE
            return
        }

        state.pending = true
        state.signature = signature
        mixedHost.removeAllViews()
        mixedHost.addView(status(now, "Combinando Base Pessoal e Base Coletiva…"))
        mixedHost.visibility = View.VISIBLE
        originalResults.visibility = View.GONE

        var personal: RegionalClient.Result? = null
        var collective: RegionalClient.Result? = null
        var completed = 0

        fun finishOne() {
            completed++
            if (completed < 2) return
            state.pending = false
            state.fetchedAt = System.currentTimeMillis()
            val collectiveResult = collective
            if (collectiveResult == null || !collectiveResult.collectiveOptIn) {
                mixedHost.visibility = View.GONE
                originalResults.visibility = View.VISIBLE
                return
            }
            renderMixed(now, mixedHost, personal, collectiveResult)
            originalResults.visibility = View.GONE
            mixedHost.visibility = View.VISIBLE
        }

        RegionalClient.fetch(now.context, mode, "personal", region, profile) { result ->
            personal = result.getOrNull()
            finishOne()
        }
        RegionalClient.fetch(now.context, mode, "collective", region, profile) { result ->
            collective = result.getOrNull()
            finishOne()
        }
    }

    private fun renderMixed(
        now: NowPanel023,
        host: LinearLayout,
        personalResult: RegionalClient.Result?,
        collectiveResult: RegionalClient.Result,
    ) {
        host.removeAllViews()
        val personalTips = personalResult
            ?.takeIf { it.resolvedSource == "personal" }
            ?.tips
            .orEmpty()
        val collectiveTips = collectiveResult
            .takeIf { it.resolvedSource == "collective" }
            ?.tips
            .orEmpty()

        val settings = SettingsRepository(now.context).load()
        val merged = RegionalDisplayRules0264.mergeBestAlternating(
            personal = personalTips,
            collective = collectiveTips,
            score = { tip -> qualityScore(tip, settings) },
            key = { tip -> "${tip.region}|${tip.profile}" },
            limit = 12,
        )

        if (merged.isEmpty()) {
            host.addView(status(now, "Ainda não há amostras suficientes para combinar as duas bases."))
            return
        }

        host.addView(
            status(
                now,
                "${merged.size} regiões priorizadas pelas melhores condições · Base Pessoal + Base Coletiva",
            ),
        )
        merged.forEach { item ->
            host.addView(
                regionCard(now, item.value, item.source, item.score),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(now.context, 9) },
            )
        }
    }

    private fun regionCard(
        now: NowPanel023,
        tip: RegionalClient.Tip,
        source: String,
        @Suppress("UNUSED_PARAMETER") score: Double,
    ): View {
        val context = now.context
        val p = SrUi023.palette(context)
        val collective = source == "collective"
        val card = SrUi023.card(context, 13, 18).apply {
            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                SrUi023.iconBox(
                    context,
                    R.drawable.sr23_ic_location,
                    if (collective) p.purple else p.blue,
                    40,
                ),
            )
            top.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(SrUi023.title(context, tip.region, 15.5f))
                    addView(
                        SrUi023.body(
                            context,
                            "${profileName(tip.profile)} · ${tip.samples} amostras",
                            9f,
                        ),
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = SrUi023.dp(context, 8)
                },
            )
            top.addView(
                TextView(context).apply {
                    text = if (collective) "BASE COLETIVA" else "BASE PESSOAL"
                    textSize = 8.5f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(
                        SrUi023.dp(context, 7),
                        SrUi023.dp(context, 4),
                        SrUi023.dp(context, 7),
                        SrUi023.dp(context, 4),
                    )
                    if (collective) {
                        setTextColor(Color.WHITE)
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            SrTheme024.collectiveGradientStops(Appearance021.isDark(context)),
                        ).apply { cornerRadius = SrUi023.dp(context, 999).toFloat() }
                    } else {
                        setTextColor(p.blue)
                        background = SrUi023.rounded(Color.TRANSPARENT, 999, p.blue, 1, context)
                    }
                },
            )
            addView(top)

            if (tip.wording.isNotBlank()) {
                addView(
                    SrUi023.body(context, tip.wording, 10f),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = SrUi023.dp(context, 7) },
                )
            }

            val metrics = LinearLayout(context).apply {
                orientation = if (context.resources.configuration.screenWidthDp < 360) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            }
            val items = listOf(
                metric(context, "R$/km", tip.medianPerKm?.let(::fmt) ?: "—", p.teal),
                metric(context, "R$/h", tip.medianPerHour?.let(::fmt) ?: "—", p.orange),
                metric(context, "Busca", tip.pickupMinutes?.let { "${fmt(it)} min" } ?: "—", p.red),
            )
            items.forEachIndexed { index, item ->
                val lp = if (metrics.orientation == LinearLayout.HORIZONTAL) {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                } else {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                if (index > 0) {
                    if (metrics.orientation == LinearLayout.HORIZONTAL) lp.marginStart = SrUi023.dp(context, 5)
                    else lp.topMargin = SrUi023.dp(context, 5)
                }
                metrics.addView(item, lp)
            }
            addView(metrics, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 8) })
        }

        return if (collective) diagonalFrame(context, card) else card
    }

    private fun metric(context: android.content.Context, label: String, value: String, tone: Int): View =
        SrUi023.softCard(context, "neutral", 8).apply {
            gravity = Gravity.CENTER
            addView(SrUi023.body(context, label, 8.5f).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(tone)
            })
            addView(SrUi023.title(context, value, 15f).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            })
        }

    private fun diagonalFrame(context: android.content.Context, child: View): View =
        FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                SrTheme024.collectiveGradientStops(Appearance021.isDark(context)),
            ).apply { cornerRadius = SrUi023.dp(context, 19).toFloat() }
            val border = SrUi023.dp(context, 1)
            setPadding(border, border, border, border)
            addView(
                child,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

    private fun qualityScore(tip: RegionalClient.Tip, settings: DriverSettings): Double {
        val perKm = (tip.medianPerKm ?: 0.0) / settings.minPerKm.coerceAtLeast(0.5)
        val perHour = (tip.medianPerHour ?: 0.0) / settings.minPerHour.coerceAtLeast(10.0)
        val pickupMinutes = tip.pickupMinutes ?: tip.pickupKm?.times(3.0) ?: 15.0
        val pickupScore = 1.0 - pickupMinutes.coerceIn(0.0, 30.0) / 30.0
        val sampleScore = (ln(1.0 + tip.samples.coerceAtLeast(0).toDouble()) / 5.0).coerceIn(0.0, 1.0)
        val distanceScore = 1.0 - (tip.distanceKm ?: 8.0).coerceIn(0.0, 20.0) / 20.0
        return perKm * 0.40 + perHour * 0.40 + pickupScore * 0.08 + sampleScore * 0.07 + distanceScore * 0.05
    }

    private fun decorateSourceButtons(personal: TextView, collective: TextView) {
        val context = personal.context
        val p = SrUi023.palette(context)
        val personalActive = isBold(personal)
        val collectiveActive = isBold(collective)

        personal.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        personal.setTextColor(if (personalActive) p.blue else p.ink)
        personal.background = SrUi023.rounded(
            Color.TRANSPARENT,
            11,
            if (personalActive) p.blue else p.outline,
            if (personalActive) 2 else 1,
            context,
        )

        collective.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        if (collectiveActive) {
            collective.setTextColor(Color.WHITE)
            collective.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                SrTheme024.collectiveGradientStops(Appearance021.isDark(context)),
            ).apply {
                cornerRadius = SrUi023.dp(context, 11).toFloat()
                setStroke(SrUi023.dp(context, 1), p.purple)
            }
        } else {
            collective.setTextColor(p.ink)
            collective.background = SrUi023.rounded(Color.TRANSPARENT, 11, p.outline, 1, context)
        }
    }

    private fun ensureMixedHost(content: LinearLayout, original: LinearLayout): LinearLayout {
        (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .firstOrNull { it.contentDescription == "sr0264_mixed_regions" }
            ?.let { return it }
        val host = LinearLayout(content.context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "sr0264_mixed_regions"
        }
        val index = content.indexOfChild(original)
        content.addView(
            host,
            (index + 1).coerceAtMost(content.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return host
    }

    private fun locateOriginalResults(content: LinearLayout): LinearLayout? {
        for (i in 0 until content.childCount - 1) {
            val child = content.getChildAt(i)
            if (child is TextView && child.contentDescription == null) {
                val next = content.getChildAt(i + 1)
                if (next is LinearLayout && next.contentDescription != "sr0264_mixed_regions") return next
            }
        }
        return null
    }

    private fun status(now: NowPanel023, text: String): TextView =
        SrUi023.body(now.context, text, 10.5f).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, SrUi023.dp(now.context, 7), 0, 0)
        }

    private fun activeLabel(root: View, choices: List<Pair<String, String>>): String? {
        choices.forEach { (label, key) ->
            val view = findText(root, label)
            if (view != null && isBold(view)) return key
        }
        return null
    }

    private fun findEditText(root: View, hint: String): EditText? {
        if (root is EditText && root.hint?.toString() == hint) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findEditText(root.getChildAt(i), hint)?.let { return it }
        }
        return null
    }

    private fun findText(root: View, text: String): TextView? {
        if (root is TextView && root.text?.toString()?.trim()?.equals(text, true) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findText(root.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun isBold(view: TextView): Boolean =
        (view.typeface?.style ?: Typeface.NORMAL).and(Typeface.BOLD) != 0

    private fun fmt(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)

    private fun profileName(value: String): String = when (value) {
        "popular" -> "Popular"
        "comfort" -> "Conforto"
        "premium" -> "Premium"
        else -> "Todas"
    }
}
