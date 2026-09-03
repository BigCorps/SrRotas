package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Injeta o Sr. Rotas Radar na tela Agora sem alterar a composição legada dela. */
object RadarInline026 {
    fun wrapPrimaryButton(
        context: Context,
        label: String,
        iconRes: Int?,
        onClick: () -> Unit,
    ): View? {
        if (label != "Consultar") return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                standardButton(context, label, iconRes, onClick),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            val radar = radarCard(context)
            addView(
                radar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 10) },
            )
        }
    }

    private fun radarCard(context: Context): View =
        SrUi023.softCard(context, "neutral", 12).apply {
            val title = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title.addView(
                SrUi023.icon(
                    context,
                    R.drawable.sr23_ic_location,
                    SrUi023.palette(context).purple,
                    18,
                ),
            )
            title.addView(
                SrUi023.title(context, "Sr. Rotas Radar", 14f),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = SrUi023.dp(context, 7)
                },
            )
            title.addView(SrUi023.pill(context, "EVENTOS", "purple"))
            addView(title)

            val status = SrUi023.body(
                context,
                "Buscando shows, esportes, teatros, feiras e outros eventos próximos…",
                10f,
            )
            addView(
                status,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 6) },
            )

            val host = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(
                host,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 7) },
            )

            fun refresh(force: Boolean = false) {
                status.text = if (force) "Atualizando oportunidades próximas…" else "Consultando Radar…"
                host.removeAllViews()
                EventRadarClient026.fetchNearby(context, force = force) { result ->
                    result.onSuccess { found ->
                        if (found.opportunities.isEmpty()) {
                            status.text = when (found.sourceStatus) {
                                "source_not_configured" -> "Radar preparado; fonte automática ainda não configurada."
                                else -> "Nenhuma saída de evento relevante nas próximas horas."
                            }
                        } else {
                            status.text = "${found.opportunities.size} oportunidades próximas · tendência de saída, não garantia de corrida."
                            found.opportunities.take(3).forEach { opportunity ->
                                host.addView(
                                    opportunityRow(context, opportunity),
                                    LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                    ).apply { topMargin = SrUi023.dp(context, 6) },
                                )
                            }
                        }
                    }.onFailure {
                        status.text = "Radar indisponível agora: ${it.message ?: "tente novamente"}"
                    }
                }
            }

            addView(
                TextView(context).apply {
                    text = "Atualizar Radar"
                    textSize = 10.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(SrUi023.palette(context).blue)
                    gravity = Gravity.CENTER
                    minHeight = SrUi023.dp(context, 36)
                    background = SrUi023.rounded(
                        Color.TRANSPARENT,
                        10,
                        SrUi023.palette(context).blue,
                        1,
                        context,
                    )
                    setOnClickListener { refresh(force = true) }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 7) },
            )

            post { refresh(force = false) }
        }

    private fun opportunityRow(
        context: Context,
        item: EventRadarOpportunity026,
    ): View =
        SrUi023.card(context, 10, 12).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        SrUi023.title(context, item.name, 12.5f),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        SrUi023.pill(
                            context,
                            "${String.format(Locale("pt", "BR"), "%.1f", item.distanceKm)} km",
                            "good",
                        ),
                    )
                },
            )
            val place = listOfNotNull(item.venueName, item.address).joinToString(" · ")
            if (place.isNotBlank()) {
                addView(SrUi023.body(context, place, 9.5f))
            }
            val urgency = EventRadarRules026.urgencyLabel(Instant.now(), item)
            addView(
                SrUi023.body(
                    context,
                    "${EventRadarRules026.typeLabel(item.type)} · $urgency · confiança ${EventRadarRules026.confidenceLabel(item.confidence)}",
                    9.5f,
                ),
            )
            addView(
                SrUi023.body(
                    context,
                    "Fim estimado ${formatTime(item.expectedEndAt)} · janela ${formatTime(item.egressStartAt)}–${formatTime(item.egressEndAt)}",
                    9f,
                ),
            )
            item.sourceUrl?.let { url ->
                addView(
                    TextView(context).apply {
                        text = "Ver fonte"
                        textSize = 9.5f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(SrUi023.palette(context).blue)
                        setPadding(0, SrUi023.dp(context, 4), 0, 0)
                        setOnClickListener {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

    private fun standardButton(
        context: Context,
        label: String,
        iconRes: Int?,
        onClick: () -> Unit,
    ): View = TextView(context).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = SrUi023.dp(context, 48)
        setPadding(
            SrUi023.dp(context, 14),
            SrUi023.dp(context, 11),
            SrUi023.dp(context, 14),
            SrUi023.dp(context, 11),
        )
        setTextColor(Color.WHITE)
        background = SrUi023.rounded(
            SrUi023.palette(context).blue,
            14,
            SrUi023.palette(context).blue,
            1,
            context,
        )
        iconRes?.let {
            setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
            compoundDrawablePadding = SrUi023.dp(context, 8)
        }
        setOnClickListener { onClick() }
    }

    private fun formatTime(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value))
    }.getOrDefault("—")
}
