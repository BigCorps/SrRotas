package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object FloatingWindowChrome023 {
    private const val RAIL_VISIBLE_SLOTS = 6
    private const val RAIL_ITEM_HEIGHT_DP = 48
    private const val RAIL_CHEVRON_HEIGHT_DP = 34

    data class Actions(
        val play: () -> Unit,
        val pause: () -> Unit,
        val stop: () -> Unit,
        val history: () -> Unit,
        val toggleMessages: () -> Unit,
    )

    fun bottomBar(
        context: Context,
        messagesOpen: Boolean,
        playEnabled: Boolean,
        pauseEnabled: Boolean,
        stopEnabled: Boolean,
        actions: Actions,
    ): View {
        val palette = palette(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7))
            background = rounded(palette.control, 14, palette.border, 1, context)
            addView(actionButton(context, R.drawable.sr23_float_play, "Iniciar ou retomar jornada", playEnabled, false, actions.play), slot(context))
            addView(actionButton(context, R.drawable.sr23_float_pause, "Pausar jornada", pauseEnabled, false, actions.pause), slot(context, 4))
            addView(actionButton(context, R.drawable.sr23_float_stop, "Encerrar jornada", stopEnabled, false, actions.stop), slot(context, 4))
            addView(actionButton(context, R.drawable.sr23_float_history, "Abrir Estatísticas", true, false, actions.history), slot(context, 4))
            addView(actionButton(context, R.drawable.sr23_ic_search, "Digitalizar Uber", true, false) { UberDigitizationActivity026.open(context) }, slot(context, 4))
            addView(actionButton(context, R.drawable.sr23_float_message, if (messagesOpen) "Fechar mensagens" else "Abrir mensagens", true, messagesOpen, actions.toggleMessages), slot(context, 4))
        }
    }

    /**
     * 0.26.1: o trilho tem altura externa fixa. Dez ou trinta mensagens ocupam
     * exatamente o mesmo espaço de seis; o restante é acessado por gesto ou
     * pelos botões de subir/descer.
     */
    fun messageRail(
        context: Context,
        shortcuts: List<MessageShortcut023>,
        onCopy: (MessageShortcut023) -> Unit,
    ): View {
        val p = palette(context)
        val visible = MessageShortcutRules023.visible(shortcuts)
        val rendered = if (visible.isEmpty()) {
            (0 until RAIL_VISIBLE_SLOTS).map { index ->
                MessageShortcut023(
                    id = "placeholder-${index + 1}",
                    order = index,
                    shortLabel = (index + 1).toString(),
                    accessibilityLabel = "Mensagem ${index + 1} ainda não configurada",
                    text = "",
                    colorToken = MessageShortcutRules023.colorFor(index),
                    enabled = false,
                )
            }
        } else {
            visible
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 4), 0, dp(context, 4), 0)
            rendered.forEach { shortcut ->
                addView(
                    shortcutButton(context, shortcut, onCopy),
                    LinearLayout.LayoutParams(dp(context, 40), dp(context, RAIL_ITEM_HEIGHT_DP)),
                )
            }
        }

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            isFillViewport = false
            clipToPadding = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                list,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val canScroll = rendered.size > RAIL_VISIBLE_SLOTS
        val jump = dp(context, RAIL_ITEM_HEIGHT_DP * 3)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(p.panel, 16, p.border, 1, context)
            setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3))
            addView(
                railChevron(context, R.drawable.sr23_float_chevron_up, "Mensagens anteriores", canScroll) {
                    scroll.smoothScrollBy(0, -jump)
                },
                LinearLayout.LayoutParams(dp(context, 40), dp(context, RAIL_CHEVRON_HEIGHT_DP)),
            )
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    dp(context, 42),
                    dp(context, RAIL_ITEM_HEIGHT_DP * RAIL_VISIBLE_SLOTS),
                ),
            )
            addView(
                railChevron(context, R.drawable.sr23_float_chevron_down, "Próximas mensagens", canScroll) {
                    scroll.smoothScrollBy(0, jump)
                },
                LinearLayout.LayoutParams(dp(context, 40), dp(context, RAIL_CHEVRON_HEIGHT_DP)),
            )
        }
    }

    fun railWidthPx(context: Context): Int = dp(context, 48)
    fun railGapPx(context: Context): Int = dp(context, 4)

    fun panelWidthPx(context: Context, availableWidthPx: Int, messagesOpen: Boolean): Int {
        val density = context.resources.displayMetrics.density
        val availableDp = (availableWidthPx / density).toInt()
        val reserve = 24 + if (messagesOpen) 52 else 0
        return dp(context, minOf(360, (availableDp - reserve).coerceAtLeast(220)))
    }

    private fun actionButton(
        context: Context,
        icon: Int,
        description: String,
        enabled: Boolean,
        active: Boolean,
        action: () -> Unit,
    ): ImageButton {
        val p = palette(context)
        return ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = description
            background = rounded(if (active) p.active else p.control, 12, if (active) p.active else p.border, 1, context)
            imageTintList = ColorStateList.valueOf(if (active) p.onActive else p.ink)
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            minimumWidth = dp(context, 46)
            minimumHeight = dp(context, 48)
            isEnabled = enabled
            alpha = if (enabled) 1f else .35f
            setOnClickListener { if (enabled) action() }
        }
    }

    private fun shortcutButton(
        context: Context,
        shortcut: MessageShortcut023,
        onCopy: (MessageShortcut023) -> Unit,
    ): View {
        val enabled = shortcut.enabled && shortcut.text.isNotBlank()
        return FrameLayout(context).apply {
            contentDescription = shortcut.accessibilityLabel
                ?.takeIf(String::isNotBlank)
                ?: "Copiar mensagem ${shortcut.shortLabel}"
            isEnabled = enabled
            alpha = if (enabled) 1f else .35f
            isClickable = true
            isFocusable = true
            addView(
                TextView(context).apply {
                    text = shortcut.shortLabel
                    gravity = Gravity.CENTER
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = rounded(shortcutColor(shortcut.colorToken), 999, null, 0, context)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(context, 32), dp(context, 32), Gravity.CENTER),
            )
            setOnClickListener { if (isEnabled) onCopy(shortcut) }
        }
    }

    private fun railChevron(
        context: Context,
        icon: Int,
        description: String,
        enabled: Boolean,
        action: () -> Unit,
    ): View {
        val p = palette(context)
        return ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(p.ink)
            background = null
            contentDescription = description
            isClickable = enabled
            isFocusable = enabled
            isEnabled = enabled
            alpha = if (enabled) 1f else .30f
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            setOnClickListener { if (enabled) action() }
        }
    }

    private data class P(
        val panel: Int,
        val control: Int,
        val ink: Int,
        val muted: Int,
        val border: Int,
        val active: Int,
        val onActive: Int,
    )

    private fun palette(context: Context): P {
        val dark = when (SettingsRepository(context).load().hudTheme.lowercase()) {
            "dark" -> true
            "light" -> false
            else -> Appearance021.isDark(context)
        }
        val p = UiKit.palette(dark)
        return P(p.surface, p.surfaceAlt, p.ink, p.muted, p.line, p.primary, Color.WHITE)
    }

    private fun shortcutColor(token: String): Int = when (token) {
        "shortcut02" -> 0xFFF2A51D.toInt()
        "shortcut03" -> 0xFF168CC8.toInt()
        "shortcut04" -> 0xFFEF5B4D.toInt()
        "shortcut05" -> 0xFF6754C6.toInt()
        "shortcut06" -> 0xFF3EA957.toInt()
        else -> 0xFF13A7B5.toInt()
    }

    private fun slot(context: Context, marginStartDp: Int = 0) =
        LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply {
            if (marginStartDp > 0) marginStart = dp(context, marginStartDp)
        }

    private fun rounded(
        color: Int,
        radiusDp: Int,
        stroke: Int?,
        strokeDp: Int,
        context: Context,
    ) = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(color)
        if (stroke != null && strokeDp > 0) setStroke(dp(context, strokeDp), stroke)
    }

    private fun dp(context: Context, value: Int) = UiKit.dp(context, value)
}
