package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class BetaFeedbackView(context: Context) : LinearLayout(context) {
    private val progress = UiKit.body(context, "", 13f)
    private val status = UiKit.body(context, "", 12f)
    private val category = Spinner(context).apply {
        adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "Geral",
                "OCR",
                "HUD",
                "Contexto/Maps",
                "Jornada",
                "Histórico",
                "Importação",
                "Estatística",
                "Custos",
                "Sync",
                "Conta/Login",
                "IA",
                "MCP",
                "Plano/Pix",
                "Notificações",
                "Bateria/Desempenho",
            ),
        )
    }
    private val severity = Spinner(context).apply {
        adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "Sugestão",
                "Problema leve",
                "Problema importante",
                "Bloqueador",
            ),
        )
    }
    private val message = UiKit.input(
        context,
        "Conte o que aconteceu, o que esperava e em qual tela...",
        multiline = true,
    )

    init {
        orientation = VERTICAL
        addView(UiKit.pill(context, "CLOSED BETA · ${BuildConfig.VERSION_NAME}", "primary"))
        addView(UiKit.margin(UiKit.title(context, "Central do testador", 21f), top = 8))
        addView(
            UiKit.body(
                context,
                "A Central é opcional. O relato por mensagem continua válido; esta área só organiza diagnóstico e feedback quando for conveniente, sempre com o veículo parado.",
                12f,
            ),
        )

        addView(
            UiKit.margin(
                UiKit.body(context, "Rodada 0.19", 13f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                },
                top = 10,
            ),
        )
        addView(progress)
        addView(
            UiKit.margin(
                UiKit.primaryButton(context, "Abrir validação de campo 0.19") {
                    context.startActivity(Intent(context, FieldValidationActivity::class.java))
                },
                top = 8,
            ),
        )

        addView(
            UiKit.margin(
                UiKit.body(context, "Enviar feedback", 13f).apply {
                    setTypeface(typeface, Typeface.BOLD)
                },
                top = 12,
            ),
        )
        addView(caption("Área"))
        addView(category)
        addView(caption("Impacto"))
        addView(severity)
        addView(UiKit.margin(message, top = 8))
        addView(
            UiKit.margin(
                UiKit.primaryButton(context, "Enviar feedback do beta") {
                    submit()
                },
                top = 9,
            ),
        )
        addView(UiKit.margin(status, top = 7))
        addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "O feedback automático não envia OCR bruto, capturas de tela, senha, token ou chave MCP. " +
                        "O relatório 0.19 também não inclui endereço textual nem coordenadas exatas.",
                    11f,
                ),
                top = 8,
            ),
        )

        refresh()
    }

    fun refresh() {
        val completed = FieldValidationManualStore.completed(context)
        progress.text = "$completed de ${FieldValidationManualChecklist.items.size} itens manuais da 0.19 concluídos."

        val pending = BetaTelemetry.hasPendingCrash(context)
        status.text = if (pending) {
            "Há um crash técnico pendente; ele será enviado automaticamente quando a conta estiver conectada."
        } else if (status.text.isBlank()) {
            "Use este formulário sempre que encontrar algo estranho."
        } else {
            status.text
        }

        BetaTelemetry.flushPendingCrash(context)
    }

    private fun submit() {
        val completed = FieldValidationManualStore.completed(context)
        status.text = "Enviando..."

        BetaClient.sendFeedback(
            context,
            BetaClient.FeedbackPayload(
                category = category.selectedItem.toString(),
                severity = severity.selectedItem.toString(),
                message = message.text.toString(),
                checklistCompleted = completed,
                checklistTotal = FieldValidationManualChecklist.items.size,
            ),
        ) { result ->
            result.onSuccess {
                message.setText("")
                status.text = "Feedback enviado. Obrigado por ajudar no teste."
            }.onFailure {
                status.text = "Não foi possível enviar: ${it.message}"
            }
        }
    }

    private fun caption(text: String): TextView = UiKit.body(context, text, 12f).apply {
        setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 3))
    }
}
