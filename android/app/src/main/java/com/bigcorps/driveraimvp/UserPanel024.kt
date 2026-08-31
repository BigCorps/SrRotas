package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Resumo nativo de Usuário.
 *
 * Não abre navegador automaticamente. A Central Web continua disponível como
 * ação explícita para dados avançados, pagamento e segurança.
 */
class UserPanel024(context: Context) : ScrollView(context) {
    private val root = LinearLayout(context)
    private val body = LinearLayout(context)

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        addView(
            root,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            SrAppHeader023(
                context,
                "Usuário",
                "Sua conta, plano, créditos e acessos.",
            ),
        )
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(
            SrUi023.dp(context, 14),
            SrUi023.dp(context, 10),
            SrUi023.dp(context, 14),
            SrUi023.dp(context, 28),
        )
        root.addView(
            body,
            LinearLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        refresh()
    }

    fun refresh() {
        body.removeAllViews()
        val settings = SettingsRepository(context).load()
        val connected = settings.deviceToken.isNotBlank()
        val p = SrUi023.palette(context)

        body.addView(
            SrUi023.card(context, 14, 18).apply {
                val top = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                top.addView(
                    SrUi023.iconBox(
                        context,
                        R.drawable.sr23_ic_user,
                        p.userGreen,
                        54,
                    ),
                )
                top.addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(SrUi023.dp(context, 11), 0, 0, 0)
                        addView(
                            SrUi023.title(
                                context,
                                settings.driverDisplayName.ifBlank { "Motorista" },
                                17f,
                            ),
                        )
                        addView(
                            SrUi023.body(
                                context,
                                settings.accountEmail.ifBlank {
                                    if (connected) "Conta conectada" else "Conta ainda não conectada"
                                },
                                10.5f,
                            ),
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
                top.addView(
                    SrUi023.pill(
                        context,
                        if (connected) "CONECTADO" else "LOCAL",
                        if (connected) "good" else "warn",
                    ),
                )
                addView(top)
            },
        )

        val billing = BillingStatusView(context)
        body.addView(
            billing,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )
        billing.refresh()

        body.addView(
            SrUi023.card(context, 14, 18).apply {
                addView(UiKit.sectionTitle(context, "Conta e assinatura"))
                addView(
                    SrUi023.body(
                        context,
                        "O APK mostra o essencial. Alterações de cobrança, segurança e dados completos permanecem na Central Web protegida.",
                        10.5f,
                    ),
                )
                addView(
                    SrUi023.primaryButton(
                        context,
                        "Abrir Central Web",
                        R.drawable.sr23_ic_link,
                    ) {
                        SrUserWeb023.open(context)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 9)
                    },
                )
                addView(
                    UiKit.secondaryButton(
                        context,
                        "Plano e pagamentos",
                    ) {
                        WebHandoff021.open(context, "/app/plano")
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 7)
                    },
                )
                addView(
                    UiKit.secondaryButton(
                        context,
                        "Segurança e MCP",
                    ) {
                        WebHandoff021.open(context, "/app/mcp")
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 7)
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )

        body.addView(
            SrUi023.softCard(context, "neutral", 12).apply {
                addView(
                    SrUi023.body(
                        context,
                        "Este aparelho usa as configurações locais de jornada/HUD e sincroniza os dados permitidos quando a conta está conectada.",
                        10f,
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
