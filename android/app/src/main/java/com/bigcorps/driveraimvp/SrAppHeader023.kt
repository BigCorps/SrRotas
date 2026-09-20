package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Cabeçalho único e estável do Sr. Rotas, adaptável à largura e à escala de fonte. */
class SrAppHeader023(
    context: Context,
    titleText: String,
    @Suppress("UNUSED_PARAMETER") subtitleText: String,
    @Suppress("UNUSED_PARAMETER") trailingDrawable: Int? = null,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val widthDp = ResponsiveUi027038.screenWidthDp(context)
        val veryNarrow = widthDp < 330
        val compact = widthDp < 380
        val backReserve = ConfigurationBackOverlay0212.backSlotWidthPx(context)

        setPadding(
            SrUi023.dp(context, if (veryNarrow) 3 else 4),
            SrUi023.dp(context, 7),
            SrUi023.dp(context, 4) + backReserve,
            SrUi023.dp(context, 7),
        )
        setBackgroundColor(SrTheme024.palette(Appearance021.isDark(context)).background)

        val logoWidth = when {
            veryNarrow -> 100
            compact -> 118
            widthDp < 430 -> 136
            else -> 156
        }
        val logoHeight = when {
            veryNarrow -> 30
            compact -> 35
            widthDp < 430 -> 39
            else -> 43
        }
        val logoRes = if (Appearance021.isDark(context)) R.drawable.sr0265_header_dark else R.drawable.sr0265_header_light

        addView(ImageView(context).apply {
            setImageResource(logoRes)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            contentDescription = "Sr. Rotas"
        }, LayoutParams(SrUi023.dp(context, logoWidth), SrUi023.dp(context, logoHeight)))

        addView(
            SrUi023.title(context, sectionName(titleText), if (compact) 15f else 17f).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                ellipsize = TextUtils.TruncateAt.END
                ResponsiveUi027038.autoSizeSingleLine(this, if (veryNarrow) 10 else 11, if (compact) 15 else 17)
                val russoOneId = resources.getIdentifier("russo_one_regular", "font", context.packageName)
                typeface = if (russoOneId != 0) runCatching { resources.getFont(russoOneId) }.getOrElse { Typeface.DEFAULT_BOLD } else Typeface.DEFAULT_BOLD
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, if (veryNarrow) 3 else 5) },
        )

        (context as? MainActivity)?.let { activity ->
            val p = SrUi023.palette(context)
            val actions = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                background = SrUi023.rounded(p.surface, 999, p.outline, 1, context)
                setPadding(SrUi023.dp(context, 3), SrUi023.dp(context, 3), SrUi023.dp(context, 3), SrUi023.dp(context, 3))
            }
            val size = SrUi023.dp(context, when { veryNarrow -> 28; compact -> 30; else -> 34 })
            val iconPadding = when { veryNarrow -> 6; compact -> 6; else -> 7 }
            fun action(icon: Int, label: String, tint: Int, click: () -> Unit) = ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(tint)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = label
                setPadding(SrUi023.dp(context, iconPadding), SrUi023.dp(context, iconPadding), SrUi023.dp(context, iconPadding), SrUi023.dp(context, iconPadding))
                setOnClickListener { click() }
            }
            actions.addView(action(R.drawable.sr23_ic_settings, "Configurações", p.teal) { activity.openSettingsFromPanel() }, LayoutParams(size, size))
            actions.addView(View(context).apply { setBackgroundColor(p.outline) }, LayoutParams(SrUi023.dp(context, 1), SrUi023.dp(context, 22)).apply { gravity = Gravity.CENTER_VERTICAL })
            actions.addView(action(R.drawable.sr23_ic_user, "Usuário", p.orange) { activity.openUserFromHeader() }, LayoutParams(size, size))
            addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = SrUi023.dp(context, if (veryNarrow) 3 else 6) })
        }
    }

    private fun sectionName(original: String): String = when (original.trim()) {
        "IA do Sr. Rotas" -> "IA"
        else -> original.trim()
    }
}
