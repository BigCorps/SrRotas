package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Barra inferior final: Histórico · IA · Agora · Configurações · Usuário. */
class SrBottomNav023(
    context: Context,
    selected: Route,
    onNavigate: (Route) -> Unit,
) : LinearLayout(context) {
    enum class Route { HISTORY, AI, NOW, SETTINGS, USER }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
        setPadding(SrUi023.dp(context, 7), SrUi023.dp(context, 7), SrUi023.dp(context, 7), SrUi023.dp(context, 9))
        background = SrUi023.rounded(SrUi023.palette(context).surface, 26, SrUi023.palette(context).outline, 1, context)
        elevation = SrUi023.dp(context, 7).toFloat()

        val items = listOf(
            Item(Route.HISTORY, "Histórico", R.drawable.sr23_ic_history, SrUi023.palette(context).orange),
            Item(Route.AI, "IA", R.drawable.sr23_ic_ai, SrUi023.palette(context).purple),
            Item(Route.NOW, "Agora", R.drawable.sr23_ic_now, SrUi023.palette(context).blue),
            Item(Route.SETTINGS, "Configurações", R.drawable.sr23_ic_settings, SrUi023.palette(context).tealDark),
            Item(Route.USER, "Usuário", R.drawable.sr23_ic_user, SrUi023.palette(context).red),
        )
        items.forEach { entry -> addView(buildItem(entry, selected == entry.route) { onNavigate(entry.route) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private data class Item(val route: Route, val label: String, val icon: Int, val accent: Int)

    private fun buildItem(item: Item, active: Boolean, click: () -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val isNow = item.route == Route.NOW
        val iconSize = if (isNow && active) 58 else 42
        val iconBox = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = SrUi023.rounded(
                if (active) item.accent else Color.TRANSPARENT,
                if (isNow) 18 else 14,
                null,
                0,
                context,
            )
            addView(ImageView(context).apply {
                setImageResource(item.icon)
                setColorFilter(if (active) Color.WHITE else item.accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = item.label
            }, LayoutParams(SrUi023.dp(context, 25), SrUi023.dp(context, 25)))
        }
        addView(iconBox, LayoutParams(SrUi023.dp(context, iconSize), SrUi023.dp(context, iconSize)))
        addView(TextView(context).apply {
            text = item.label
            textSize = if (item.label == "Configurações") 8.7f else 9.7f
            gravity = Gravity.CENTER
            setTextColor(if (active) item.accent else SrUi023.palette(context).muted)
            if (active) setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
        })
        minHeight = SrUi023.dp(context, 66)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }
}
