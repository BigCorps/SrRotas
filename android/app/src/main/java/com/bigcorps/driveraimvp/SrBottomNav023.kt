package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Estatísticas · IA · Agora · Histórico · Radar. */
class SrBottomNav023(
    context: Context,
    selected: Route,
    onNavigate: (Route) -> Unit,
) : SrSoftShadowCard023(
    context = context,
    fillColor = SrUi023.palette(context).surface,
    strokeColor = SrUi023.palette(context).outline,
    radiusDp = 26,
    shadowEnabled = true,
    shadowHorizontalDp = 5,
    shadowTopDp = 3,
    shadowBottomDp = 6,
    blurRadiusDp = 6f,
    shadowOffsetYDp = 2f,
) {
    enum class Route { HISTORY, AI, NOW, SETTINGS, USER }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
        setInnerPadding(6, 4, 6, 5)
        minimumHeight = SrUi023.dp(context, 84)
        val p = SrUi023.palette(context)
        val nowPink = if (Appearance021.isDark(context)) 0xFFFF2AA6.toInt() else 0xFFFF0A8A.toInt()
        listOf(
            Item(Route.HISTORY, "Estatísticas", R.drawable.sr36_ic_chart, p.blue),
            Item(Route.AI, "IA", R.drawable.sr23_ic_ai, p.purple),
            Item(Route.NOW, "Agora", R.drawable.sr23_ic_now_button, nowPink),
            Item(Route.SETTINGS, "Histórico", R.drawable.sr23_ic_history, p.orange),
            Item(Route.USER, "Radar", R.drawable.sr36_ic_radar, p.userGreen),
        ).forEach { entry ->
            addView(buildItem(entry, selected == entry.route) { onNavigate(entry.route) }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private data class Item(val route: Route, val label: String, val icon: Int, val accent: Int)

    private fun buildItem(item: Item, active: Boolean, click: () -> Unit) = LinearLayout(context).apply {
        val p = SrUi023.palette(context)
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumHeight = SrUi023.dp(context, 68)
        isClickable = true
        isFocusable = true
        contentDescription = item.label
        setOnClickListener { click() }
        val isNow = item.route == Route.NOW
        val boxDp = if (isNow && active) 62 else if (isNow) 58 else 40
        val iconDp = if (isNow) 30 else 23
        val iconBox = FrameLayout(context).apply {
            val fill = when {
                isNow && active -> item.accent
                isNow -> blend(item.accent, Color.WHITE, .18f)
                active -> item.accent
                else -> Color.TRANSPARENT
            }
            background = SrUi023.rounded(fill, if (isNow) 999 else 13, if (isNow) item.accent else null, if (isNow) 3 else 0, context)
            addView(ImageView(context).apply {
                setImageResource(item.icon)
                if (!isNow) setColorFilter(if (active) Color.WHITE else item.accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(SrUi023.dp(context, iconDp), SrUi023.dp(context, iconDp), Gravity.CENTER))
        }
        addView(iconBox, LayoutParams(SrUi023.dp(context, boxDp), SrUi023.dp(context, boxDp)).apply { topMargin = SrUi023.dp(context, if (isNow) 0 else 8) })
        addView(TextView(context).apply {
            text = item.label
            textSize = if (item.label == "Estatísticas") 8.2f else 9.2f
            gravity = Gravity.CENTER
            setTextColor(if (active || isNow) item.accent else p.muted)
            if (active || isNow) setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, if (isNow) 0 else 2) })
    }

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun c(x: Int, y: Int) = (x + (y - x) * r).toInt().coerceIn(0, 255)
        return Color.rgb(c(Color.red(a), Color.red(b)), c(Color.green(a), Color.green(b)), c(Color.blue(a), Color.blue(b)))
    }
}
