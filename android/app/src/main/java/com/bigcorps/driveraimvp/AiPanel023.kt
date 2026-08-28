package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/**
 * IA nativa do Sr. Rotas.
 *
 * Estado inicial: personagem amigável + quatro sugestões.
 * Após a primeira pergunta, o personagem e as sugestões desaparecem e a tela
 * passa a funcionar como uma conversa, sem alterar BackendClient.askEnhanced.
 */
class AiPanel023(context: Context) : LinearLayout(context) {
    private val repo = SettingsRepository(context)
    private val period: Spinner =
        SrUi023.spinner(context, listOf("Hoje", "7 dias", "30 dias", "90 dias")).apply {
            setSelection(1)
        }
    private val question: EditText =
        UiKit.input(context, "Pergunte ao Sr. Rotas…", multiline = true).apply {
            minLines = 1
            maxLines = 4
        }
    private val creditStatus =
        SrUi023.body(context, "Créditos: consultando...", 9.5f).apply {
            gravity = Gravity.END
        }

    private val scroll = ScrollView(context)
    private val introHost = LinearLayout(context)
    private val messagesHost = LinearLayout(context)
    private var hasInteracted = false

    init {
        orientation = VERTICAL
        setBackgroundColor(UiKit.palette(context).background)

        addView(
            SrAppHeader023(
                context,
                "IA do Sr. Rotas",
                "Converse sobre ofertas, estratégia e jornada.",
            ),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.setBackgroundColor(UiKit.palette(context).background)

        val scrollFrame = FrameLayout(context)
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                0,
                SrUi023.dp(context, 8),
                0,
                SrUi023.dp(context, 18),
            )
        }
        scrollFrame.addView(
            content,
            FrameLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )
        scroll.addView(
            scrollFrame,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            scroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        introHost.orientation = VERTICAL
        introHost.gravity = Gravity.CENTER_HORIZONTAL
        buildIntro(introHost)
        content.addView(
            introHost,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )

        messagesHost.orientation = VERTICAL
        messagesHost.visibility = View.GONE
        content.addView(
            messagesHost,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 8)
            },
        )

        val composerHost = FrameLayout(context).apply {
            setBackgroundColor(UiKit.palette(context).background)
            setPadding(
                0,
                SrUi023.dp(context, 2),
                0,
                SrUi023.dp(context, 5),
            )
        }
        composerHost.addView(
            composer(),
            FrameLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            ),
        )
        addView(
            composerHost,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )

        refreshBilling()
    }

    private fun buildIntro(host: LinearLayout) {
        val mascotWidth = if (resources.configuration.screenWidthDp < 340) 218 else 248
        val mascotHeight = (mascotWidth * 0.85f).toInt()

        host.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.sr23_mascot_ai_transparent)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = "Sr. Rotas aguardando sua pergunta"
            },
            LayoutParams(
                SrUi023.dp(context, mascotWidth),
                SrUi023.dp(context, mascotHeight),
            ).apply {
                topMargin = SrUi023.dp(context, 2)
            },
        )

        host.addView(
            SrUi023.title(context, "Como posso ajudar?", 20f).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )

        host.addView(
            SrUi023.body(
                context,
                "Escolha uma sugestão ou escreva sua própria pergunta abaixo.",
                11.5f,
            ).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 3)
            },
        )

        host.addView(
            suggestions(),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 12)
            },
        )
    }

    private fun suggestions(): View {
        val prompts = listOf(
            Triple(
                "Quais horários tiveram as melhores ofertas?",
                R.drawable.sr23_ic_clock,
                SrUi023.palette(context).orange,
            ),
            Triple(
                "O que mais está derrubando minha estratégia?",
                R.drawable.sr23_ic_chart_down,
                SrUi023.palette(context).blue,
            ),
            Triple(
                "Quais categorias estão pagando melhor por km ou minuto?",
                R.drawable.sr23_ic_car,
                SrUi023.palette(context).teal,
            ),
            Triple(
                "Resuma meu período e diga o que observar no próximo turno.",
                R.drawable.sr23_ic_file_text,
                SrUi023.palette(context).purple,
            ),
        )

        val columns = if (resources.configuration.screenWidthDp >= 340) 2 else 1

        return LinearLayout(context).apply {
            orientation = VERTICAL

            prompts.chunked(columns).forEachIndexed { rowIndex, rowPrompts ->
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.TOP
                }

                rowPrompts.forEachIndexed { columnIndex, (text, icon, tone) ->
                    val card = SrUi023.card(context, 12, 17).apply {
                        orientation = VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        minimumHeight = SrUi023.dp(context, 142)
                        isClickable = true
                        isFocusable = true

                        addView(
                            SrUi023.iconBox(context, icon, tone, 44),
                            LayoutParams(
                                SrUi023.dp(context, 44),
                                SrUi023.dp(context, 44),
                            ),
                        )

                        addView(
                            SrUi023.body(context, text, 10.5f).apply {
                                setTextColor(SrUi023.palette(context).ink)
                                setTypeface(typeface, Typeface.BOLD)
                                gravity = Gravity.CENTER
                                textAlignment = View.TEXT_ALIGNMENT_CENTER
                                maxLines = 4
                            },
                            LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = SrUi023.dp(context, 9)
                            },
                        )

                        setOnClickListener {
                            question.setText(text)
                            ask()
                        }
                    }

                    row.addView(
                        card,
                        LayoutParams(
                            0,
                            LayoutParams.WRAP_CONTENT,
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
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (rowIndex > 0) {
                            topMargin = SrUi023.dp(context, 6)
                        }
                    },
                )
            }
        }
    }

    private fun composer(): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 8),
                SrUi023.dp(context, 8),
            )
            background = SrUi023.rounded(
                SrUi023.palette(context).surface,
                20,
                SrUi023.palette(context).outline,
                1,
                context,
            )

            val meta = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(
                    SrUi023.body(context, "Período", 9.5f).apply {
                        setTypeface(typeface, Typeface.BOLD)
                    },
                )

                addView(
                    period,
                    LayoutParams(
                        SrUi023.dp(context, 112),
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = SrUi023.dp(context, 7)
                    },
                )

                addView(
                    creditStatus,
                    LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        marginStart = SrUi023.dp(context, 8)
                    },
                )
            }
            addView(
                meta,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                ),
            )

            val inputRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                question.background = null
                addView(
                    question,
                    LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )

                addView(
                    ImageButton(context).apply {
                        setImageResource(R.drawable.sr23_ic_send)
                        imageTintList = ColorStateList.valueOf(Color.WHITE)
                        background = SrUi023.rounded(
                            SrUi023.palette(context).navy,
                            999,
                            null,
                            0,
                            context,
                        )
                        contentDescription = "Enviar pergunta"
                        setPadding(
                            SrUi023.dp(context, 13),
                            SrUi023.dp(context, 13),
                            SrUi023.dp(context, 13),
                            SrUi023.dp(context, 13),
                        )
                        setOnClickListener { ask() }
                    },
                    LayoutParams(
                        SrUi023.dp(context, 48),
                        SrUi023.dp(context, 48),
                    ),
                )
            }
            addView(
                inputRow,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 5)
                },
            )
        }

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.length < 3) {
            question.error = "Digite uma pergunta com pelo menos 3 caracteres."
            return
        }
        question.error = null

        enterConversation()
        addUserMessage(text)
        question.setText("")

        if (repo.load().deviceToken.isBlank()) {
            addAssistantMessage(
                "Conecte sua conta/aparelho antes de usar a IA do Sr. Rotas.",
            )
            return
        }

        val days = listOf(1, 7, 30, 90)[period.selectedItemPosition]
        val pending = addAssistantMessage("Analisando seus dados…")

        BackendClient.askEnhanced(context, text, days) { result ->
            result.onSuccess { response ->
                pending.text.text = response.answer
                pending.meta.text = buildString {
                    append("${response.offerCount} oferta(s) analisada(s) · ${response.model}")
                    if (response.totalTokens != null) {
                        append(" · ${response.totalTokens} tokens")
                    }
                }
                pending.meta.visibility = View.VISIBLE
                refreshBilling()
                scrollToBottom()
            }.onFailure {
                pending.text.text =
                    "Não foi possível consultar a IA: ${friendlyError(it.message)}"
                pending.meta.text = ""
                pending.meta.visibility = View.GONE
                refreshBilling()
                scrollToBottom()
            }
        }
    }

    private fun enterConversation() {
        if (hasInteracted) return
        hasInteracted = true
        introHost.visibility = View.GONE
        messagesHost.visibility = View.VISIBLE
        scrollToBottom()
    }

    private fun addUserMessage(text: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
        }
        val bubble = TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.12f)
            setPadding(
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 10),
            )
            background = SrUi023.rounded(
                SrUi023.palette(context).navy,
                18,
                null,
                0,
                context,
            )
            maxWidth = (SrUi023.maxContentWidthPx(context) * 0.82f).toInt()
        }
        row.addView(
            bubble,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        messagesHost.addView(
            row,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 8)
            },
        )
        scrollToBottom()
    }

    private data class AssistantBubble(
        val text: TextView,
        val meta: TextView,
    )

    private fun addAssistantMessage(text: String): AssistantBubble {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
        }

        val bubble = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 11),
                SrUi023.dp(context, 14),
                SrUi023.dp(context, 11),
            )
            background = SrUi023.rounded(
                SrUi023.palette(context).surface,
                18,
                SrUi023.palette(context).outline,
                1,
                context,
            )
        }

        bubble.addView(
            SrUi023.body(context, "Sr. Rotas", 9.5f).apply {
                setTextColor(SrUi023.palette(context).teal)
                setTypeface(typeface, Typeface.BOLD)
            },
        )

        val body = SrUi023.body(context, text, 13f).apply {
            setTextColor(SrUi023.palette(context).ink)
        }
        bubble.addView(
            body,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 4)
            },
        )

        val meta = SrUi023.body(context, "", 9f).apply {
            visibility = View.GONE
        }
        bubble.addView(
            meta,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 6)
            },
        )

        row.addView(
            bubble,
            LayoutParams(
                (SrUi023.maxContentWidthPx(context) * 0.90f).toInt(),
                LayoutParams.WRAP_CONTENT,
            ),
        )

        messagesHost.addView(
            row,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 8)
            },
        )

        scrollToBottom()
        return AssistantBubble(body, meta)
    }

    private fun scrollToBottom() {
        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshBilling() {
        if (repo.load().deviceToken.isBlank()) {
            creditStatus.text = "Conecte sua conta"
            return
        }
        BackendClient.fetchBillingStatus(context) { result ->
            result.onSuccess { billing ->
                creditStatus.text = buildString {
                    append("${billing.creditBalance} crédito(s)")
                    if (!billing.subscriptionActive && !billing.billingEnforcement) {
                        append(" · testes")
                    } else if (!billing.subscriptionActive) {
                        append(" · assine")
                    }
                }
            }.onFailure {
                creditStatus.text = "Créditos indisponíveis"
            }
        }
    }

    private fun friendlyError(value: String?): String =
        when (value) {
            "openai_not_configured" ->
                "IA do Sr. Rotas ainda não configurada no servidor."
            "subscription_required" ->
                "é necessário ter uma assinatura ativa."
            "ai_credits_required" ->
                "seus créditos de IA acabaram."
            "unauthorized" ->
                "sessão expirada; conecte o aparelho novamente."
            else ->
                value ?: "erro inesperado"
        }
}
