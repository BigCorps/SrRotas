package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.min

/**
 * Componentes da UI 0.23.x sobre a identidade oficial do UiKit.
 * Nenhum background/surface/texto-base é definido fora do UiKit.
 */
object SrUi023 {
    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceMuted: Int,
        val ink: Int,
        val muted: Int,
        val outline: Int,
        val navy: Int,
        val navyDeep: Int,
        val blue: Int,
        val blueBright: Int,
        val teal: Int,
        val tealDark: Int,
        val orange: Int,
        val red: Int,
        val purple: Int,
        val userGreen: Int,
        val cyan: Int,
        val magenta: Int,
        val successSoft: Int,
        val infoSoft: Int,
        val warningSoft: Int,
        val dangerSoft: Int,
    )

    fun palette(context: Context): Palette = paletteFrom(UiKit.palette(context), Appearance021.isDark(context))

    fun paletteForDark(dark: Boolean): Palette = paletteFrom(UiKit.palette(dark), dark)

    private fun paletteFrom(base: UiKit.Palette, dark: Boolean): Palette {
        val theme = SrTheme024.palette(dark)
        fun soft(accent: Int): Int =
            blend(theme.surfaceAlt, accent, if (dark) .20f else .09f)

        return Palette(
            background = theme.background,
            surface = theme.surface,
            surfaceMuted = theme.surfaceAlt,
            ink = theme.ink,
            muted = theme.muted,
            outline = theme.line,
            navy = theme.navy,
            navyDeep = theme.navyDeep,
            blue = theme.now,
            blueBright = theme.nowGlow,
            teal = theme.history,
            tealDark = theme.history,
            orange = theme.settings,
            red = theme.bad,
            purple = theme.ai,
            userGreen = theme.user,
            cyan = theme.cyan,
            magenta = theme.magenta,
            successSoft = soft(theme.good),
            infoSoft = soft(theme.now),
            warningSoft = soft(theme.warn),
            dangerSoft = soft(theme.bad),
        )
    }

    fun applyBars(activity: Activity) = UiKit.applySystemBars(activity)

    fun screen(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(palette(context).background)
    }

    fun headerBackground(context: Context) =
        bottomRounded(UiKit.brandHeaderColor(), dp(context, 34).toFloat())

    fun curvedHeader(context: Context, padding: Int = 20) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, 16), dp(context, padding), dp(context, 22))
        background = headerBackground(context)
    }

    fun title(context: Context, text: String, size: Float = 28f, onNavy: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (onNavy) Color.WHITE else palette(context).ink)
    }

    fun body(context: Context, text: String, size: Float = 13f, onNavy: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setLineSpacing(0f, 1.12f)
        setTextColor(if (onNavy) 0xFFD6E7E5.toInt() else palette(context).muted)
    }

    fun card(context: Context, padding: Int = 16, radius: Int = 20) =
        SrSoftShadowCard023(
            context = context,
            fillColor = palette(context).surface,
            strokeColor = palette(context).outline,
            radiusDp = radius,
            shadowEnabled = true,
        ).apply {
            orientation = LinearLayout.VERTICAL
            setContentPadding(padding)
        }

    fun softCard(context: Context, tone: String, padding: Int = 12): SrSoftShadowCard023 {
        val p = palette(context)
        val color = when (tone) {
            "good" -> p.successSoft
            "info" -> p.infoSoft
            "warn" -> p.warningSoft
            "bad" -> p.dangerSoft
            else -> p.surfaceMuted
        }
        return SrSoftShadowCard023(
            context = context,
            fillColor = color,
            strokeColor = p.outline,
            radiusDp = 16,
            shadowEnabled = false,
        ).apply {
            orientation = LinearLayout.VERTICAL
            setContentPadding(padding)
        }
    }

    fun icon(context: Context, drawable: Int, tint: Int, sizeDp: Int = 24): ImageView = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    fun iconBox(context: Context, drawable: Int, tone: Int, sizeDp: Int = 58): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        background = rounded(tone, 14, null, 0, context)
        addView(icon(context, drawable, Color.WHITE, min(29, sizeDp - 12)))
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    fun menuTile(
        context: Context,
        titleText: String,
        subtitle: String,
        drawable: Int,
        tone: Int,
        badge: String? = null,
        onClick: () -> Unit,
    ): View = card(context, 14, 18).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 96)
        addView(iconBox(context, drawable, tone, 52), LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 12), 0, dp(context, 5), 0)
                addView(title(context, titleText, 15f))
                addView(body(context, subtitle, 11f))
                badge?.let { addView(body(context, it, 10f).apply { setTextColor(tone); setTypeface(typeface, Typeface.BOLD) }) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(icon(context, R.drawable.sr23_ic_chevron_right, palette(context).muted, 17))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun pill(context: Context, text: String, tone: String = "good"): TextView {
        val p = palette(context)
        val bg = when (tone) {
            "warn" -> p.orange
            "bad" -> p.red
            "blue" -> p.blue
            "purple" -> p.purple
            else -> p.teal
        }
        return TextView(context).apply {
            this.text = text
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (tone == "warn") p.ink else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5))
            background = rounded(bg, 999, null, 0, context)
        }
    }

    fun primaryButton(context: Context, label: String, iconRes: Int? = null, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(context, 48)
        setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
        setTextColor(Color.WHITE)
        background = rounded(palette(context).navy, 14, palette(context).navy, 1, context)
        iconRes?.let {
            setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
            compoundDrawablePadding = dp(context, 8)
        }
        setOnClickListener { onClick() }
    }

    fun segment(context: Context, label: String, active: Boolean, onClick: () -> Unit) = TextView(context).apply {
        val p = palette(context)
        val collective = label.equals("Base coletiva", ignoreCase = true)
        val dark = Appearance021.isDark(context)

        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = dp(context, 42)
        setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)

        if (collective) {
            val ratio = when {
                dark && active -> .42f
                dark -> .25f
                active -> .48f
                else -> .24f
            }
            val stops = SrTheme024.collectiveGradientStops(dark)
                .map { blend(p.surface, it, ratio) }
                .toIntArray()
            setTextColor(if (dark) p.ink else p.navyDeep)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                stops,
            ).apply {
                cornerRadius = dp(context, 11).toFloat()
                setStroke(dp(context, if (active) 2 else 1), p.purple)
            }
        } else {
            setTextColor(if (active) Color.WHITE else p.ink)
            background = rounded(if (active) p.navy else Color.TRANSPARENT, 11, null, 0, context)
        }
        setOnClickListener { onClick() }
    }

    fun spinner(context: Context, values: List<String>): Spinner = Spinner(context).apply {
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, values) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent), false)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getDropDownView(position, convertView, parent), true)
            private fun style(view: View, dropdown: Boolean): View = view.apply {
                if (this is TextView) {
                    setTextColor(palette(context).ink)
                    textSize = 13f
                    setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
                    if (dropdown) setBackgroundColor(palette(context).surface)
                }
            }
        }
        background = rounded(palette(context).surfaceMuted, 12, palette(context).outline, 1, context)
    }

    fun maxContentWidthPx(context: Context, maxDp: Int = 760, horizontalMarginDp: Int = 16): Int {
        val screenDp = context.resources.configuration.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
        return dp(context, (screenDp - horizontalMarginDp * 2).coerceAtMost(maxDp).coerceAtLeast(280))
    }

    fun preferredColumns(context: Context): Int =
        if (context.resources.configuration.screenWidthDp >= 400) 2 else 1

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int?, strokeDp: Int, context: Context) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null && strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
    }

    private fun bottomRounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx)
    }

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun c(x: Int, y: Int) = (x + (y - x) * r).toInt().coerceIn(0, 255)
        return Color.rgb(c(Color.red(a), Color.red(b)), c(Color.green(a), Color.green(b)), c(Color.blue(a), Color.blue(b)))
    }

    fun dp(context: Context, value: Int) = UiKit.dp(context, value)
}
