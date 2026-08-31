package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Ambiente local de demonstração/captação.
 *
 * Usa apenas dados fictícios em memória. Não inicia OCR, jornada, rede ou sync.
 */
class DemoCapturePanel024(context: Context) : ScrollView(context) {
    init {
        isFillViewport = true
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                SrUi023.dp(context, 12),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 12),
                SrUi023.dp(context, 18),
            )
        }
        addView(root)

        root.addView(
            SrUi023.softCard(context, "warn", 12).apply {
                addView(
                    SrUi023.title(
                        context,
                        "MODO DEMONSTRAÇÃO",
                        13f,
                    ),
                )
                addView(
                    SrUi023.body(
                        context,
                        "Dados fictícios · nada é capturado, salvo ou sincronizado.",
                        10f,
                    ),
                )
            },
        )

        root.addView(
            CheckBox(context).apply {
                text = "Captação: preparar tela limpa para screenshots"
                isChecked = true
                setTextColor(SrUi023.palette(context).ink)
            },
        )

        root.addView(
            SrUi023.card(context, 14, 18).apply {
                addView(UiKit.sectionTitle(context, "Agora — exemplo"))
                addView(
                    SrUi023.pill(
                        context,
                        "BASE COLETIVA",
                        "good",
                    ),
                )
                addView(
                    SrUi023.title(
                        context,
                        "Centro · alta chance de boas ofertas",
                        15f,
                    ),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 7)
                    },
                )
                addView(
                    SrUi023.body(
                        context,
                        "R$ 2,42/km · R$ 49,80/h · Busca 4 min",
                        11f,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )

        val s = SettingsRepository(context).load()
        root.addView(
            HudConfigPreview024.build(
                context,
                HudConfigPreview024.Model(
                    settings = s,
                    maxPickupMinutes = Strategy021Store.load(context).maxPickupMinutes,
                    size = s.hudCardSize,
                ),
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )

        root.addView(
            SrUi023.card(context, 14, 18).apply {
                addView(UiKit.sectionTitle(context, "Histórico — exemplo"))
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    SrUi023.title(context, "R$ 28,90", 20f),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
                row.addView(SrUi023.pill(context, "REALIZADA", "good"))
                addView(row)
                addView(SrUi023.body(context, "Hoje · 14:35", 9f))
                addView(
                    SrUi023.body(
                        context,
                        "Embarque: Centro\nDestino: Bairro Jardim",
                        10.5f,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )
    }
}
