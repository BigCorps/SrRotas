package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceAlt: Int,
        val ink: Int,
        val muted: Int,
        val line: Int,
        val primary: Int,
        val primaryDark: Int,
        val orange: Int,
        val good: Int,
        val warn: Int,
        val bad: Int,
    )

    fun palette(context: Context): Palette {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (dark) Palette(
            background = Color.rgb(8, 20, 32), surface = Color.rgb(18, 35, 49), surfaceAlt = Color.rgb(25, 46, 61),
            ink = Color.rgb(245, 249, 252), muted = Color.rgb(176, 194, 207), line = Color.rgb(51, 75, 91),
            primary = Color.rgb(38, 198, 218), primaryDark = Color.rgb(17, 118, 136), orange = Color.rgb(245, 158, 11),
            good = Color.rgb(45, 212, 191), warn = Color.rgb(251, 191, 36), bad = Color.rgb(251, 113, 133),
        ) else Palette(
            background = Color.rgb(247, 250, 251), surface = Color.WHITE, surfaceAlt = Color.rgb(239, 247, 249),
            ink = Color.rgb(15, 42, 59), muted = Color.rgb(86, 108, 121), line = Color.rgb(216, 229, 234),
            primary = Color.rgb(27, 183, 199), primaryDark = Color.rgb(10, 105, 124), orange = Color.rgb(245, 158, 11),
            good = Color.rgb(20, 184, 166), warn = Color.rgb(217, 151, 16), bad = Color.rgb(225, 64, 93),
        )
    }

    @Suppress("DEPRECATION")
    fun applySystemBars(activity: Activity) {
        val p = palette(activity)
        // Chamado antes de setContentView em algumas Activities. Não acesse
        // Window.insetsController aqui: em alguns Android/ROMs o DecorView ainda
        // não existe e PhoneWindow.getInsetsController() pode lançar NPE.
        activity.window.statusBarColor = p.background
        activity.window.navigationBarColor = p.surface
    }

    /**
     * Android 15+ força edge-to-edge para apps recentes. Mantemos o visual leve,
     * mas aplicamos os insets reais da barra de status, navegação e recortes para
     * que nenhum conteúdo fique por baixo de relógio, notificações ou gestos.
     */
    @Suppress("DEPRECATION")
    fun applySafeArea(root: View) {
        val dark = (root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Só ajusta a aparência dos ícones depois que a View já foi criada.
        // Os flags legados continuam sendo uma forma segura de definir contraste
        // sem depender de WindowInsetsController durante Activity.onCreate().
        val iconMask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.systemUiVisibility = (root.systemUiVisibility and iconMask.inv()) or if (dark) 0 else iconMask

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(
                initialLeft + safe.left,
                initialTop + safe.top,
                initialRight + safe.right,
                initialBottom + safe.bottom,
            )
            insets
        }
        root.post { root.requestApplyInsets() }
    }

    fun title(context: Context, text: String, size: Float = 26f): TextView = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(palette(context).ink); setTypeface(typeface, Typeface.BOLD)
    }

    fun sectionTitle(context: Context, text: String): TextView = title(context, text, 20f).apply {
        setPadding(0, dp(context, 6), 0, dp(context, 8))
    }

    fun body(context: Context, text: String, size: Float = 14f): TextView = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(palette(context).muted); setLineSpacing(0f, 1.12f)
    }

    fun card(context: Context, padding: Int = 16): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        background = rounded(context, palette(context).surface, 20, palette(context).line, 1)
        elevation = dp(context, 2).toFloat()
    }

    fun primaryButton(context: Context, text: String, onClick: () -> Unit): TextView = button(context, text, true, onClick)
    fun secondaryButton(context: Context, text: String, onClick: () -> Unit): TextView = button(context, text, false, onClick)

    private fun button(context: Context, label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val p = palette(context)
        return TextView(context).apply {
            text = label; textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12)); minHeight = dp(context, 48)
            setTextColor(if (primary) Color.WHITE else p.ink)
            background = rounded(context, if (primary) p.primaryDark else p.surfaceAlt, 15, if (primary) p.primaryDark else p.line, 1)
            isClickable = true; isFocusable = true; setOnClickListener { onClick() }
        }
    }

    fun pill(context: Context, text: String, tone: String = "neutral"): TextView {
        val p = palette(context)
        val bg = when (tone) { "good" -> p.good; "warn" -> p.warn; "bad" -> p.bad; "primary" -> p.primary; else -> p.surfaceAlt }
        val fg = if (tone == "neutral") p.ink else Color.WHITE
        return TextView(context).apply {
            this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg); setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
            background = rounded(context, bg, 999)
        }
    }

    fun input(context: Context, hint: String, multiline: Boolean = false, numeric: Boolean = false): EditText = EditText(context).apply {
        this.hint = hint; textSize = 15f; setTextColor(palette(context).ink); setHintTextColor(palette(context).muted)
        setPadding(dp(context, 13), dp(context, 11), dp(context, 13), dp(context, 11))
        background = rounded(context, palette(context).surfaceAlt, 14, palette(context).line, 1)
        if (multiline) { minLines = 3; maxLines = 6; gravity = Gravity.TOP } else setSingleLine(true)
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    fun rounded(context: Context, color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat(); setColor(color)
        if (strokeColor != null && strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
    }

    fun margin(view: View, top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0): View {
        view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(view.context, start), dp(view.context, top), dp(view.context, end), dp(view.context, bottom))
        }
        return view
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
