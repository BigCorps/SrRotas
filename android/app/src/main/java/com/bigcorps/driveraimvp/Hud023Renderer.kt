package com.srrotas.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Renderer visual do HUD 0.23 (UI Freeze).
 *
 * Regras:
 * - compacto, normal e grande recebem exatamente os mesmos dados;
 * - não recalcula oferta/veredito;
 * - cabeçalho sempre Aplicativo • Categoria à esquerda e avaliação à direita;
 * - cada métrica usa sua própria cor sem alterar o veredito geral;
 * - compacto: fundo transparente + cápsulas sólidas em uma coluna;
 * - normal: card creme/escuro + grade 2 colunas, rótulo e valor na mesma linha;
 * - grande: mesma grade, rótulo em cima e valor maior embaixo;
 * - Lucro est.* ocupa a largura inteira quando habilitado;
 * - valor, distância e tempo totais são opcionais;
 * - nenhum endereço/detalhe de rota é repetido dentro do HUD.
 */
object Hud023Renderer {
    private data class Palette(
        val surface: Int,
        val surfaceAlt: Int,
        val ink: Int,
        val muted: Int,
        val line: Int,
    )

    private data class MetricItem(
        val key: String,
        val label: String,
        val value: String,
        val grade: Int,
    )

    fun build(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
        layout: Hud023LayoutPrefs.State = Hud023LayoutPrefs.load(context),
    ): View {
        val size = Hud023Spec.normalizeSize(settings.hudCardSize)
        val dark = isDark(context, settings.hudTheme)
        val palette = palette(dark)
        val maxPickupMinutes = Strategy021Store.load(context).maxPickupMinutes
        val overall = gradeColor(overallGrade(offer), settings.colorBlindMode)
        val metrics = orderedMetrics(context, offer, settings, maxPickupMinutes)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = when (size) {
                Hud023Spec.SIZE_COMPACT -> 5
                Hud023Spec.SIZE_LARGE -> 10
                else -> 8
            }
            setPadding(dp(context, pad), dp(context, pad), dp(context, pad), dp(context, pad))
            alpha = settings.hudOpacity.coerceIn(30, 100) / 100f
            background = when (size) {
                Hud023Spec.SIZE_COMPACT -> rounded(
                    context,
                    Color.TRANSPARENT,
                    14,
                    overall,
                    1,
                )
                else -> rounded(
                    context,
                    palette.surface,
                    if (size == Hud023Spec.SIZE_LARGE) 18 else 16,
                    overall,
                    2,
                )
            }
            if (size != Hud023Spec.SIZE_COMPACT) {
                elevation = dp(context, 4).toFloat()
            }
        }

        root.addView(
            header(
                context = context,
                offer = offer,
                size = size,
                palette = palette,
                overall = overall,
                colorBlind = settings.colorBlindMode,
                baseFont = settings.hudFontSize,
            ),
        )

        if (layout.hasOfferDetails) {
            optionalDetails(context, offer, layout, size, palette)?.let { details ->
                root.addView(
                    details,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(context, if (size == Hud023Spec.SIZE_COMPACT) 4 else 6)
                    },
                )
            }
        }

        when (size) {
            Hud023Spec.SIZE_COMPACT -> renderCompact(
                context,
                root,
                metrics,
                settings.hudFontSize,
                settings.colorBlindMode,
            )
            Hud023Spec.SIZE_LARGE -> renderGrid(
                context,
                root,
                metrics,
                palette,
                settings.hudFontSize,
                settings.colorBlindMode,
                large = true,
            )
            else -> renderGrid(
                context,
                root,
                metrics,
                palette,
                settings.hudFontSize,
                settings.colorBlindMode,
                large = false,
            )
        }

        return root
    }

    fun preferredWidthDp(size: String?): Int = when (Hud023Spec.normalizeSize(size)) {
        Hud023Spec.SIZE_COMPACT -> 220
        Hud023Spec.SIZE_LARGE -> 300
        else -> 260
    }

    private fun header(
        context: Context,
        offer: RideOffer,
        size: String,
        palette: Palette,
        overall: Int,
        colorBlind: Boolean,
        baseFont: Int,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val service = TextView(context).apply {
            text = serviceLabel(offer)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.ink)
            textSize = when (size) {
                Hud023Spec.SIZE_COMPACT -> (baseFont - 4).coerceIn(10, 13).toFloat()
                Hud023Spec.SIZE_LARGE -> (baseFont + 1).coerceIn(15, 21).toFloat()
                else -> baseFont.coerceIn(13, 18).toFloat()
            }
            maxLines = 1
            setPadding(
                dp(context, if (size == Hud023Spec.SIZE_COMPACT) 7 else 2),
                dp(context, if (size == Hud023Spec.SIZE_COMPACT) 4 else 1),
                dp(context, if (size == Hud023Spec.SIZE_COMPACT) 7 else 2),
                dp(context, if (size == Hud023Spec.SIZE_COMPACT) 4 else 1),
            )
            if (size == Hud023Spec.SIZE_COMPACT) {
                background = rounded(context, palette.surface, 9, palette.line, 1)
            }
        }
        row.addView(
            service,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val verdict = TextView(context).apply {
            text = verdictLabel(offer.verdict)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(semanticText(overall, colorBlind))
            textSize = when (size) {
                Hud023Spec.SIZE_COMPACT -> 10f
                Hud023Spec.SIZE_LARGE -> 14f
                else -> 11f
            }
            gravity = Gravity.CENTER
            setPadding(
                dp(context, if (size == Hud023Spec.SIZE_LARGE) 12 else 9),
                dp(context, if (size == Hud023Spec.SIZE_LARGE) 5 else 4),
                dp(context, if (size == Hud023Spec.SIZE_LARGE) 12 else 9),
                dp(context, if (size == Hud023Spec.SIZE_LARGE) 5 else 4),
            )
            background = rounded(context, overall, 999)
        }
        row.addView(
            verdict,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(context, 5) },
        )

        return row
    }

    private fun optionalDetails(
        context: Context,
        offer: RideOffer,
        layout: Hud023LayoutPrefs.State,
        size: String,
        palette: Palette,
    ): View? {
        val parts = mutableListOf<String>()
        if (layout.showFare) parts += "Valor R$ ${fmt(offer.fare)}"
        if (layout.showDistance) offer.totalKm?.let { parts += "Distância ${fmt(it)} km" }
        if (layout.showTotalTime) offer.totalMinutes?.let { parts += "Tempo $it min" }
        if (parts.isEmpty()) return null

        return TextView(context).apply {
            text = parts.joinToString("  ·  ")
            setTextColor(palette.muted)
            textSize = if (size == Hud023Spec.SIZE_LARGE) 11f else 9.5f
            maxLines = 2
            setPadding(dp(context, 7), dp(context, 4), dp(context, 7), dp(context, 4))
            background = rounded(context, palette.surfaceAlt, 9, palette.line, 1)
        }
    }

    private fun renderCompact(
        context: Context,
        root: LinearLayout,
        metrics: List<MetricItem>,
        baseFont: Int,
        colorBlind: Boolean,
    ) {
        metrics.forEach { item ->
            root.addView(
                compactMetric(context, item, baseFont, colorBlind),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 4) },
            )
        }
    }

    private fun compactMetric(
        context: Context,
        item: MetricItem,
        baseFont: Int,
        colorBlind: Boolean,
    ): View {
        val color = gradeColor(item.grade, colorBlind)
        val ink = semanticText(color, colorBlind)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5))
            background = rounded(context, color, 8)

            addView(TextView(context).apply {
                text = item.label
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
                textSize = (baseFont - 4).coerceIn(9, 13).toFloat()
                maxLines = 1
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(context).apply {
                text = item.value
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
                textSize = (baseFont - 2).coerceIn(11, 16).toFloat()
                gravity = Gravity.END
                maxLines = 1
            })
        }
    }

    private fun renderGrid(
        context: Context,
        root: LinearLayout,
        metrics: List<MetricItem>,
        palette: Palette,
        baseFont: Int,
        colorBlind: Boolean,
        large: Boolean,
    ) {
        var index = 0
        while (index < metrics.size) {
            val first = metrics[index]

            if (Hud023Spec.fullWidthMetric(first.key)) {
                root.addView(
                    gridMetric(context, first, palette, baseFont, colorBlind, large),
                    fullWidthParams(context),
                )
                index += 1
                continue
            }

            val second = metrics.getOrNull(index + 1)
            if (second == null || Hud023Spec.fullWidthMetric(second.key)) {
                root.addView(
                    gridMetric(context, first, palette, baseFont, colorBlind, large),
                    fullWidthParams(context),
                )
                index += 1
                continue
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(
                gridMetric(context, first, palette, baseFont, colorBlind, large),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(
                gridMetric(context, second, palette, baseFont, colorBlind, large),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, 6)
                },
            )
            root.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 6) },
            )
            index += 2
        }
    }

    private fun fullWidthParams(context: Context) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(context, 6) }

    private fun gridMetric(
        context: Context,
        item: MetricItem,
        palette: Palette,
        baseFont: Int,
        colorBlind: Boolean,
        large: Boolean,
    ): View {
        val color = gradeColor(item.grade, colorBlind)
        val tinted = blend(palette.surface, color, if (large) 0.13f else 0.10f)

        return LinearLayout(context).apply {
            orientation = if (large) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (large) Gravity.CENTER else Gravity.CENTER_VERTICAL
            setPadding(
                dp(context, if (large) 9 else 8),
                dp(context, if (large) 8 else 6),
                dp(context, if (large) 9 else 8),
                dp(context, if (large) 8 else 6),
            )
            background = rounded(context, tinted, 10, color, 2)

            val label = TextView(context).apply {
                text = item.label
                setTextColor(palette.ink)
                textSize = if (large) (baseFont - 2).coerceIn(11, 16).toFloat()
                else (baseFont - 4).coerceIn(9, 13).toFloat()
                maxLines = 1
                if (large) gravity = Gravity.CENTER
            }
            addView(
                label,
                if (large) {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                } else {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )

            addView(TextView(context).apply {
                text = item.value
                setTextColor(palette.ink)
                setTypeface(typeface, Typeface.BOLD)
                textSize = if (large) (baseFont + 3).coerceIn(16, 24).toFloat()
                else (baseFont - 1).coerceIn(12, 18).toFloat()
                gravity = if (large) Gravity.CENTER else Gravity.END
                maxLines = 1
            }, if (large) {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 2) }
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            })
        }
    }

    private fun orderedMetrics(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
        maxPickupMinutes: Int,
    ): List<MetricItem> {
        val values = linkedMapOf<String, Pair<String, String>?>()
        values["per_minute"] = offer.perMinute?.let { "R$/min" to fmt(it) }
        values["pickup"] = pickupValue(context, offer, settings)?.let { "Busca" to it }
        values["per_km"] = offer.perKm?.let { "R$/km" to fmt(it) }
        values["per_hour"] = offer.perHour?.let { "R$/h" to fmt(it) }
        values["rating"] = offer.passengerRating?.let { "Avaliação" to fmt(it) }
        values["profit_hour"] = offer.profitPerHour?.let { "Lucro por hora" to "R$ ${fmt(it)}/h" }
        values["profit_percent"] = offer.profitPercent?.let { "Margem da corrida" to "${fmt(it)}%" }
        values["profit"] = offer.estimatedProfit?.let { "Lucro da corrida" to "R$ ${fmt(it)}" }

        val available = values.filterValues { it != null }.keys
        val keys = Hud023Spec.visibleMetricKeys(
            settings.hudMetricOrder,
            settings.hudEnabledMetrics,
            available,
        )

        return keys.mapNotNull { key ->
            val pair = values[key] ?: return@mapNotNull null
            val grade = HudMetricEvaluation0221.grade(
                key,
                offer,
                settings,
                maxPickupMinutes,
            ) ?: 1
            MetricItem(key, pair.first, pair.second, grade)
        }
    }

    private fun pickupValue(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
    ): String? {
        if (offer.pickupKm == null && offer.pickupMinutes == null) return null
        val grade = PickupPresentation0211.grade(
            offer.pickupKm,
            offer.pickupMinutes,
            settings.maxPickupKm,
            Strategy021Store.load(context).maxPickupMinutes,
        )
        return buildString {
            append(grade.label)
            when {
                offer.pickupMinutes != null -> append(" · ${offer.pickupMinutes} min")
                offer.pickupKm != null -> append(" · ${fmt(offer.pickupKm)} km")
            }
        }
    }

    private fun serviceLabel(offer: RideOffer): String {
        val platform = when (offer.platform.lowercase(Locale.ROOT)) {
            "99" -> "99"
            "uber" -> "Uber"
            "indrive" -> "inDrive"
            "maxim" -> "Maxim"
            else -> offer.platform.ifBlank { "Motorista" }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
        }
        val service = serviceName(offer.serviceType)
        return if (service == null || service.equals(platform, ignoreCase = true)) {
            platform
        } else {
            "$platform • $service"
        }
    }

    private fun serviceName(raw: String): String? {
        val v = raw.trim().lowercase(Locale.ROOT)
        if (v.isBlank() || v == "unknown") return null
        return when (v.replace(" ", "").replace("_", "")) {
            "uberx" -> "UberX"
            "comfort" -> "Comfort"
            "black", "uberblack" -> "Black"
            "priority" -> "Priority"
            "ubermoto" -> "Uber Moto"
            "99pop" -> "99Pop"
            "99plus" -> "99Plus"
            "99moto" -> "99Moto"
            "99taxi", "99táxi" -> "99Táxi"
            else -> raw.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString()
            }
        }
    }

    private fun verdictLabel(verdict: String): String = when (verdict) {
        "boa" -> "BOA"
        "ruim" -> "RUIM"
        else -> "REGULAR"
    }

    private fun overallGrade(offer: RideOffer): Int = when (offer.verdict) {
        "boa" -> 2
        "ruim" -> 0
        else -> 1
    }

    private fun palette(dark: Boolean): Palette = if (dark) {
        Palette(
            surface = Color.rgb(7, 55, 70),
            surfaceAlt = Color.rgb(11, 72, 84),
            ink = Color.rgb(248, 244, 223),
            muted = Color.rgb(169, 200, 199),
            line = Color.rgb(49, 83, 93),
        )
    } else {
        Palette(
            surface = Color.rgb(255, 253, 246),
            surfaceAlt = Color.rgb(241, 237, 216),
            ink = Color.rgb(7, 55, 70),
            muted = Color.rgb(96, 119, 122),
            line = Color.rgb(218, 220, 199),
        )
    }

    private fun isDark(context: Context, theme: String): Boolean = when (theme) {
        "dark" -> true
        "light" -> false
        else ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun gradeColor(grade: Int, colorBlind: Boolean): Int = if (colorBlind) {
        when (grade) {
            2 -> Color.rgb(0, 114, 178)
            0 -> Color.rgb(213, 94, 0)
            else -> Color.rgb(230, 159, 0)
        }
    } else {
        when (grade) {
            2 -> Color.rgb(16, 168, 134)
            0 -> Color.rgb(217, 92, 82)
            else -> Color.rgb(230, 182, 49)
        }
    }

    private fun semanticText(color: Int, colorBlind: Boolean): Int {
        val warning = if (colorBlind) Color.rgb(230, 159, 0) else Color.rgb(230, 182, 49)
        return if (color == warning) Color.rgb(7, 55, 70) else Color.WHITE
    }

    private fun blend(base: Int, overlay: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun c(a: Int, b: Int): Int = (a + (b - a) * r).toInt().coerceIn(0, 255)
        return Color.rgb(
            c(Color.red(base), Color.red(overlay)),
            c(Color.green(base), Color.green(overlay)),
            c(Color.blue(base), Color.blue(overlay)),
        )
    }

    private fun rounded(
        context: Context,
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 0,
    ): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null && strokeDp > 0) {
            setStroke(dp(context, strokeDp), strokeColor)
        }
    }

    private fun fmt(value: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", value)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
