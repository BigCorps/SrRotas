package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** IA operacional permanece nativa; apenas a conta fica na Web. */
class AiPanel023(context: Context) : ScrollView(context) {
    private val repo = SettingsRepository(context)
    private val period: Spinner = SrUi023.spinner(context, listOf("Hoje", "7 dias", "30 dias", "90 dias")).apply { setSelection(1) }
    private val question: EditText = UiKit.input(context, "Pergunte ao Sr. Rotas…", multiline = true).apply { minLines = 1; maxLines = 4 }
    private val answer = SrUi023.body(context, "Escolha uma pergunta ou escreva a sua para analisar seus dados.", 13f)
    private val answerMeta = SrUi023.body(context, "", 9.5f)
    private val creditStatus = SrUi023.body(context, "Créditos: consultando...", 10.5f)

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(
            SrAppHeader023(context, "Converse com o Sr. Rotas", "Pergunte sobre ofertas, estratégia e jornada.", R.drawable.sr23_mascot_ai_header),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, SrUi023.dp(context, 12), 0, SrUi023.dp(context, 28))
        }
        root.addView(content, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        content.addView(
            suggestions(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(responseCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
        content.addView(composer(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
        refreshBilling()
    }

    private fun suggestions(): View {
        val prompts = listOf(
            Triple("Quais horários tiveram as melhores ofertas?", R.drawable.sr23_ic_clock, SrUi023.palette(context).orange),
            Triple("O que mais está derrubando minha estratégia?", R.drawable.sr23_ic_chart_down, SrUi023.palette(context).blue),
            Triple("Quais categorias estão pagando melhor por km ou minuto?", R.drawable.sr23_ic_car, SrUi023.palette(context).teal),
            Triple("Resuma meu período e diga o que observar no próximo turno.", R.drawable.sr23_ic_file_text, SrUi023.palette(context).purple),
        )
        val columns = SrUi023.preferredColumns(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            prompts.chunked(columns).forEachIndexed { rowIndex, rowPrompts ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

                rowPrompts.forEachIndexed { columnIndex, (text, icon, tone) ->
                    val card = SrUi023.card(context, 12, 16).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            SrUi023.iconBox(context, icon, tone, 42),
                            LinearLayout.LayoutParams(
                                SrUi023.dp(context, 42),
                                SrUi023.dp(context, 42),
                            ),
                        )
                        addView(
                            SrUi023.body(context, text, 11f).apply {
                                setTextColor(SrUi023.palette(context).ink)
                            },
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f,
                            ).apply {
                                marginStart = SrUi023.dp(context, 9)
                            },
                        )
                        addView(
                            SrUi023.icon(
                                context,
                                R.drawable.sr23_ic_chevron_right,
                                SrUi023.palette(context).muted,
                                16,
                            ),
                        )
                        setOnClickListener {
                            question.setText(text)
                            ask()
                        }
                    }

                    row.addView(
                        card,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            if (columnIndex > 0) {
                                marginStart = SrUi023.dp(context, 6)
                            }
                        },
                    )
                }

                addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (rowIndex > 0) {
                            topMargin = SrUi023.dp(context, 6)
                        }
                    },
                )
            }
        }
    }

    private fun responseCard(): View = SrUi023.card(context, 15, 18).apply {
        addView(SrUi023.title(context, "Resposta do Sr. Rotas", 17f))
        addView(answer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 9) })
        addView(answerMeta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 7) })
        addView(creditStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
        addView(period, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 8) })
    }

    private fun composer(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(SrUi023.dp(context, 7), SrUi023.dp(context, 6), SrUi023.dp(context, 6), SrUi023.dp(context, 6))
        background = SrUi023.rounded(SrUi023.palette(context).surface, 20, SrUi023.palette(context).outline, 1, context)
        question.background = null
        addView(question, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.sr23_ic_send)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = SrUi023.rounded(SrUi023.palette(context).navy, 999, null, 0, context)
            contentDescription = "Enviar pergunta"
            setPadding(SrUi023.dp(context, 13), SrUi023.dp(context, 13), SrUi023.dp(context, 13), SrUi023.dp(context, 13))
            setOnClickListener { ask() }
        }, LinearLayout.LayoutParams(SrUi023.dp(context, 48), SrUi023.dp(context, 48)))
    }

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.length < 3) { answer.text = "Digite uma pergunta com pelo menos 3 caracteres."; return }
        if (repo.load().deviceToken.isBlank()) { answer.text = "Conecte sua conta/aparelho antes de usar a IA do Sr. Rotas."; return }
        val days = listOf(1, 7, 30, 90)[period.selectedItemPosition]
        answer.text = "Analisando seus dados…"
        answerMeta.text = ""
        BackendClient.askEnhanced(context, text, days) { result ->
            result.onSuccess { response ->
                answer.text = response.answer
                answerMeta.text = buildString {
                    append("${response.offerCount} oferta(s) analisada(s) · ${response.model}")
                    if (response.totalTokens != null) append(" · ${response.totalTokens} tokens")
                }
                refreshBilling()
            }.onFailure {
                answer.text = "Não foi possível consultar a IA: ${friendlyError(it.message)}"
                refreshBilling()
            }
        }
    }

    private fun refreshBilling() {
        if (repo.load().deviceToken.isBlank()) { creditStatus.text = "Créditos: conecte sua conta para consultar."; return }
        BackendClient.fetchBillingStatus(context) { result ->
            result.onSuccess { b ->
                creditStatus.text = buildString {
                    append("Créditos de IA: ${b.creditBalance}")
                    if (!b.subscriptionActive && !b.billingEnforcement) append(" · testes liberados")
                    else if (!b.subscriptionActive) append(" · assinatura necessária")
                }
            }.onFailure { creditStatus.text = "Créditos: indisponível" }
        }
    }

    private fun friendlyError(value: String?): String = when (value) {
        "openai_not_configured" -> "IA do Sr. Rotas ainda não configurada no servidor."
        "subscription_required" -> "é necessário ter uma assinatura ativa."
        "ai_credits_required" -> "seus créditos de IA acabaram."
        "unauthorized" -> "sessão expirada; conecte o aparelho novamente."
        else -> value ?: "erro inesperado"
    }
}
