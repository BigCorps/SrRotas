package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class BetaFeedbackView(context: Context) : LinearLayout(context) {
    companion object {
        private const val PREFS =
            "sr_rotas_beta_checklist"

        private val CHECKS =
            listOf(
                "Conta e onboarding",
                "Permissão do HUD",
                "Iniciar jornada / MediaProjection",
                "Ler pelo menos 10 ofertas",
                "HUD financeiro estável",
                "Menu flutuante com 3 ofertas",
                "Embarque e Destino no Maps",
                "Pausar e retomar jornada",
                "Histórico e filtros",
                "Fila sincroniza até zero",
                "Pergunta para a IA do Sr. Rotas",
                "Gerar e revogar uma chave MCP",
                "Ativar e testar notificações",
                "Encerrar jornada e conferir resumo",
            )
    }

    private val prefs =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )

    private val progress =
        UiKit.body(
            context,
            "",
            13f,
        )
    private val status =
        UiKit.body(
            context,
            "",
            12f,
        )

    private val category =
        Spinner(context).apply {
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout
                        .simple_spinner_dropdown_item,
                    listOf(
                        "Geral",
                        "OCR",
                        "HUD",
                        "Menu flutuante",
                        "Sincronização",
                        "Conta/Login",
                        "Histórico",
                        "IA",
                        "MCP",
                        "Plano/Pix",
                        "Notificações",
                        "Bateria/Desempenho",
                    ),
                )
        }

    private val severity =
        Spinner(context).apply {
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout
                        .simple_spinner_dropdown_item,
                    listOf(
                        "Sugestão",
                        "Problema leve",
                        "Problema importante",
                        "Bloqueador",
                    ),
                )
        }

    private val message =
        UiKit.input(
            context,
            "Conte o que aconteceu, o que esperava e em qual tela...",
            multiline = true,
        )

    private val boxes =
        mutableListOf<CheckBox>()

    init {
        orientation = VERTICAL

        addView(
            UiKit.pill(
                context,
                "CLOSED BETA · ${BuildConfig.VERSION_NAME}",
                "primary",
            ),
        )
        addView(
            UiKit.margin(
                UiKit.title(
                    context,
                    "Central do testador",
                    21f,
                ),
                top = 8,
            ),
        )
        addView(
            UiKit.body(
                context,
                "Faça configuração e feedback com o veículo parado. Durante a rua, priorize direção e segurança.",
                12f,
            ),
        )

        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Checklist do teste",
                    13f,
                ).apply {
                    setTypeface(
                        typeface,
                        Typeface.BOLD,
                    )
                },
                top = 10,
            ),
        )
        addView(progress)

        CHECKS.forEachIndexed { index, label ->
            val box =
                CheckBox(context).apply {
                    text = label
                    setTextColor(
                        UiKit.palette(
                            context,
                        ).ink,
                    )
                    isChecked =
                        prefs.getBoolean(
                            "check_$index",
                            false,
                        )
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit()
                            .putBoolean(
                                "check_$index",
                                checked,
                            )
                            .apply()
                        refreshProgress()
                    }
                }

            boxes += box
            addView(box)
        }

        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Enviar feedback",
                    13f,
                ).apply {
                    setTypeface(
                        typeface,
                        Typeface.BOLD,
                    )
                },
                top = 10,
            ),
        )

        addView(caption("Área"))
        addView(category)
        addView(caption("Impacto"))
        addView(severity)
        addView(
            UiKit.margin(
                message,
                top = 8,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.primaryButton(
                    context,
                    "Enviar feedback do beta",
                ) {
                    submit()
                },
                top = 9,
            ),
        )
        addView(
            UiKit.margin(
                status,
                top = 7,
            ),
        )
        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "O relatório automático não envia OCR bruto, capturas de tela, senha, token ou chave MCP. Em caso de crash, guardamos apenas dados técnicos mínimos e o stack trace para enviar na próxima abertura.",
                    11f,
                ),
                top = 8,
            ),
        )

        addView(
            UiKit.margin(
                UiKit.secondaryButton(
                    context,
                    "Abrir validação de campo ${BuildConfig.VERSION_NAME}",
                ) {
                    context.startActivity(
                        android.content.Intent(
                            context,
                            FieldValidationActivity::class.java,
                        ),
                    )
                },
                top = 9,
            ),
        )

        refreshProgress()
        refresh()
    }

    fun refresh() {
        val pending =
            BetaTelemetry.hasPendingCrash(
                context,
            )

        if (pending) {
            status.text =
                "Há um crash técnico pendente; ele será enviado automaticamente quando a conta estiver conectada."
        } else if (
            status.text.isBlank()
        ) {
            status.text =
                "Use este formulário sempre que encontrar algo estranho."
        }

        BetaTelemetry.flushPendingCrash(
            context,
        )
    }

    private fun submit() {
        val completed =
            boxes.count {
                it.isChecked
            }

        status.text = "Enviando..."

        BetaClient.sendFeedback(
            context,
            BetaClient.FeedbackPayload(
                category =
                    category.selectedItem
                        .toString(),
                severity =
                    severity.selectedItem
                        .toString(),
                message =
                    message.text.toString(),
                checklistCompleted =
                    completed,
                checklistTotal =
                    CHECKS.size,
            ),
        ) { result ->
            result
                .onSuccess {
                    message.setText("")
                    status.text =
                        "Feedback enviado. Obrigado por ajudar no teste."
                }
                .onFailure {
                    status.text =
                        "Não foi possível enviar: ${it.message}"
                }
        }
    }

    private fun refreshProgress() {
        val completed =
            boxes.count {
                it.isChecked
            }
        progress.text =
            "$completed de ${CHECKS.size} itens concluídos."
    }

    private fun caption(
        text: String,
    ): TextView =
        UiKit.body(
            context,
            text,
            12f,
        ).apply {
            setPadding(
                0,
                UiKit.dp(
                    context,
                    8,
                ),
                0,
                UiKit.dp(
                    context,
                    3,
                ),
            )
        }
}
