package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Design system nativo da 0.23.
 *
 * O kit de referência foi convertido para Views Android, sem Compose e sem
 * alterar a arquitetura validada do aplicativo. Light/dark usam a mesma
 * hierarquia, dimensões e identidade; muda somente a paleta.
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
        val successSoft: Int,
        val infoSoft: Int,
        val warningSoft: Int,
        val dangerSoft: Int,
    )

    fun palette(context: Context): Palette = if (Appearance021.isDark(context)) {
        Palette(
            background = Color.rgb(4, 25, 42),
            surface = Color.rgb(7, 43, 64),
            surfaceMuted = Color.rgb(11, 53, 75),
            ink = Color.rgb(248, 245, 240),
            muted = Color.rgb(181, 198, 211),
            outline = Color.rgb(37, 72, 94),
            navy = Color.rgb(0, 34, 67),
            navyDeep = Color.rgb(0, 27, 54),
            blue = Color.rgb(8, 126, 245),
            blueBright = Color.rgb(7, 155, 255),
            teal = Color.rgb(0, 168, 144),
            tealDark = Color.rgb(0, 139, 131),
            orange = Color.rgb(255, 160, 0),
            red = Color.rgb(255, 88, 66),
            purple = Color.rgb(112, 96, 255),
            successSoft = Color.rgb(8, 76, 73),
            infoSoft = Color.rgb(9, 61, 103),
            warningSoft = Color.rgb(86, 62, 17),
            dangerSoft = Color.rgb(91, 42, 35),
        )
    } else {
        Palette(
            background = Color.rgb(248, 245, 240),
            surface = Color.rgb(255, 254, 252),
            surfaceMuted = Color.rgb(244, 242, 238),
            ink = Color.rgb(6, 43, 80),
            muted = Color.rgb(104, 113, 125),
            outline = Color.rgb(231, 226, 219),
            navy = Color.rgb(0, 41, 79),
            navyDeep = Color.rgb(0, 34, 67),
            blue = Color.rgb(8, 126, 245),
            blueBright = Color.rgb(7, 155, 255),
            teal = Color.rgb(0, 168, 144),
            tealDark = Color.rgb(0, 139, 131),
            orange = Color.rgb(255, 160, 0),
            red = Color.rgb(240, 75, 18),
            purple = Color.rgb(85, 69, 245),
            successSoft = Color.rgb(229, 247, 242),
            infoSoft = Color.rgb(231, 241, 255),
            warningSoft = Color.rgb(255, 242, 216),
            dangerSoft = Color.rgb(255, 230, 225),
        )
    }

    fun applyBars(activity: Activity) {
        val p = palette(activity)
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = p.navy
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = p.surface
    }

    fun screen(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(palette(context).background)
    }

    fun curvedHeader(context: Context, padding: Int = 20) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, 18), dp(context, padding), dp(context, 28))
        background = bottomRounded(palette(context).navy, 42)
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
        setTextColor(if (onNavy) 0xFFD7E7F2.toInt() else palette(context).muted)
    }

    fun card(context: Context, padding: Int = 16, radius: Int = 20) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        background = rounded(palette(context).surface, radius, palette(context).outline, 1, context)
        elevation = dp(context, 2).toFloat()
    }

    fun softCard(context: Context, tone: String, padding: Int = 12) = card(context, padding, 16).apply {
        val p = palette(context)
        val color = when (tone) {
            "good" -> p.successSoft
            "info" -> p.infoSoft
            "warn" -> p.warningSoft
            "bad" -> p.dangerSoft
            else -> p.surfaceMuted
        }
        background = rounded(color, 16, p.outline, 1, context)
        elevation = 0f
    }

    fun icon(context: Context, drawable: Int, tint: Int, sizeDp: Int = 24): ImageView = ImageView(context).apply {
        setImageResource(drawable)
        setColorFilter(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    fun iconBox(context: Context, drawable: Int, tone: Int, sizeDp: Int = 58): LinearLayout {
        val p = palette(context)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = rounded(tone, 14, null, 0, context)
            addView(icon(context, drawable, Color.WHITE, 29))
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }
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
        minimumHeight = dp(context, 108)
        addView(iconBox(context, drawable, tone), LinearLayout.LayoutParams(dp(context, 58), dp(context, 58)))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 13), 0, dp(context, 6), 0)
                addView(SrUi023.title(context, titleText, 15.5f))
                addView(body(context, subtitle, 11.5f))
                badge?.let { addView(body(context, it, 10.5f).apply { setTextColor(tone); setTypeface(typeface, Typeface.BOLD) }) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(icon(context, R.drawable.sr23_ic_chevron_right, palette(context).ink, 18))
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
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
            background = rounded(bg, 999, null, 0, context)
        }
    }

    fun primaryButton(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(context, 48)
        setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
        setTextColor(Color.WHITE)
        background = rounded(palette(context).navy, 14, palette(context).navy, 1, context)
        setOnClickListener { onClick() }
    }

    fun segment(context: Context, label: String, active: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = dp(context, 42)
        setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (active) Color.WHITE else palette(context).ink)
        background = rounded(if (active) palette(context).navy else Color.TRANSPARENT, 11, null, 0, context)
        setOnClickListener { onClick() }
    }

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int?, strokeDp: Int, context: Context) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null && strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
    }

    private fun bottomRounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        val r = radiusDp.toFloat()
        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
    }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
