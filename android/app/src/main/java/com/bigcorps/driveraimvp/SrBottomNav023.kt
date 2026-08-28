package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Navegação principal.
 *
 * O centro é um botão de verdade, visualmente dominante e sempre presente:
 * Histórico · IA · [Agora] · Configurações · Usuário.
 */
class SrBottomNav023(
    context: Context,
    selected: Route,
    onNavigate: (Route) -> Unit,
) : LinearLayout(context) {
    enum class Route { HISTORY, AI, NOW, SETTINGS, USER }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
        setPadding(
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 8),
        )
        minimumHeight = SrUi023.dp(context, 78)
        background = SrUi023.rounded(
            SrUi023.palette(context).surface,
            26,
            SrUi023.palette(context).outline,
            1,
            context,
        )
        elevation = SrUi023.dp(context, 7).toFloat()

        val p = SrUi023.palette(context)
        val items = listOf(
            Item(Route.HISTORY, "Histórico", R.drawable.sr23_ic_history, p.orange),
            Item(Route.AI, "IA", R.drawable.sr23_ic_ai, p.purple),
            Item(Route.NOW, "Agora", R.drawable.sr23_ic_now_button, p.blue),
            Item(Route.SETTINGS, "Configurações", R.drawable.sr23_ic_settings, p.tealDark),
            Item(Route.USER, "Usuário", R.drawable.sr23_ic_user, p.red),
        )

        items.forEach { entry ->
            addView(
                buildItem(entry, selected == entry.route) { onNavigate(entry.route) },
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
            )
        }
    }

    private data class Item(
        val route: Route,
        val label: String,
        val icon: Int,
        val accent: Int,
    )

    private fun buildItem(
        item: Item,
        active: Boolean,
        click: () -> Unit,
    ) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumHeight = SrUi023.dp(context, 68)
        isClickable = true
        isFocusable = true
        contentDescription = item.label
        setOnClickListener { click() }

        val isNow = item.route == Route.NOW
        val boxDp = if (isNow) 58 else 40
        val iconDp = if (isNow) 29 else 23

        val iconBox = FrameLayout(context).apply {
            val fill = when {
                isNow && active -> item.accent
                isNow -> UiKit.brandHeaderColor()
                active -> item.accent
                else -> Color.TRANSPARENT
            }
            background = SrUi023.rounded(
                fill,
                if (isNow) 999 else 13,
                null,
                0,
                context,
            )

            addView(
                ImageView(context).apply {
                    setImageResource(item.icon)
                    if (!isNow) {
                        setColorFilter(if (active) Color.WHITE else item.accent)
                    }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    SrUi023.dp(context, iconDp),
                    SrUi023.dp(context, iconDp),
                    Gravity.CENTER,
                ),
            )
        }

        addView(
            iconBox,
            LayoutParams(
                SrUi023.dp(context, boxDp),
                SrUi023.dp(context, boxDp),
            ).apply {
                topMargin = SrUi023.dp(context, if (isNow) 0 else 8)
            },
        )

        addView(
            TextView(context).apply {
                text = item.label
                textSize = if (item.label == "Configurações") 8.4f else 9.4f
                gravity = Gravity.CENTER
                setTextColor(
                    when {
                        active -> item.accent
                        isNow -> SrUi023.palette(context).ink
                        else -> SrUi023.palette(context).muted
                    },
                )
                if (active || isNow) setTypeface(typeface, Typeface.BOLD)
                setSingleLine(true)
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, if (isNow) 0 else 2)
            },
        )
    }
}
