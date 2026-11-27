package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import org.json.JSONObject

/** IA nativa do Sr. Rotas. */
class AiPanel023(context: Context) : LinearLayout(context) {
    private val repo = SettingsRepository(context)
    private val period: Spinner = SrUi023.spinner(context, listOf("Hoje", "7 dias", "30 dias", "90 dias")).apply { setSelection(1) }
    private val question: EditText = UiKit.input(context, "Pergunte ao Sr. Rotas…", multiline = true).apply { minLines = 1; maxLines = 4 }
    private val intelligenceStatus = SrUi023.body(context, "Inteligência: consultando...", 9.5f).apply { gravity = Gravity.END }
    private val scroll = ScrollView(context)
    private val introHost = LinearLayout(context)
    private val messagesHost = LinearLayout(context)
    private val newChat = SrUi023.body(context, "Nova conversa", 11f).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        visibility = View.GONE
        minimumHeight = SrUi023.dp(context, 48)
        contentDescription = "Iniciar nova conversa e limpar contexto"
        setTextColor(SrUi023.palette(context).teal)
        setOnClickListener { resetConversation() }
    }
    private val voiceStatus = SrUi023.body(context, "", 10f).apply { visibility = View.GONE }
    private var hasInteracted = false
    private var isAsking = false
    private var conversationSerial = 0
    private var conversationContext: JSONObject? = null
    // Never reuse a previous driver's private context after account/session switch.
    private var conversationSessionToken: String? = null
    private var voiceGeneration = 0
    private var speechRecognizer: SpeechRecognizer? = null

    // Cabeça do logo oficial, recortada em memória. Sem corpo, texto ou asset novo.
    private val officialMascotHead: Bitmap? by lazy {
        runCatching {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.sr0265_header_light,
                BitmapFactory.Options().apply { inSampleSize = 4 })
            val cropWidth = (bitmap.height * 1.08f).toInt().coerceAtMost(bitmap.width)
            Bitmap.createBitmap(bitmap, 0, 0, cropWidth, bitmap.height).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }.getOrNull()
    }

    private fun mascotHead(size: Int): ImageView = ImageView(context).apply {
        officialMascotHead?.let { setImageBitmap(it) }
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Cabeça do mascote Sr. Rotas"
        adjustViewBounds = true
        minimumWidth = SrUi023.dp(context, size)
        minimumHeight = SrUi023.dp(context, size)
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(UiKit.palette(context).background)
        addView(SrAppHeader023(context, "IA do Sr. Rotas", "Converse sobre ofertas, estratégia e jornada."), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.setBackgroundColor(UiKit.palette(context).background)
        val scrollFrame = FrameLayout(context)
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, SrUi023.dp(context, 8), 0, SrUi023.dp(context, 18))
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
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(newChat, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        introHost.orientation = VERTICAL
        introHost.gravity = Gravity.CENTER_HORIZONTAL
        buildIntro(introHost)
        content.addView(introHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        messagesHost.orientation = VERTICAL
        messagesHost.visibility = View.GONE
        content.addView(
            messagesHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = SrUi023.dp(context, 8)
            },
        )

        val composerHost = FrameLayout(context).apply {
            setBackgroundColor(UiKit.palette(context).background)
            setPadding(0, SrUi023.dp(context, 2), 0, SrUi023.dp(context, 5))
        }
        composerHost.addView(
            composer(),
            FrameLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            ),
        )
        addView(composerHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshBilling()
    }

    private fun buildIntro(host: LinearLayout) {
        host.addView(
            mascotHead(84),
            LayoutParams(SrUi023.dp(context, 84), SrUi023.dp(context, 88)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = SrUi023.dp(context, 8)
            },
        )

        host.addView(
            SrUi023.title(context, "Como posso ajudar hoje?", 20f).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        host.addView(
            SrUi023.body(
                context,
                "Pergunte sobre corridas, ofertas e regiões. Respondo com os dados disponíveis.",
                11.5f,
            ).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = SrUi023.dp(context, 3)
            },
        )

        host.addView(
            suggestions(),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = SrUi023.dp(context, 12)
            },
        )
    }

    private fun suggestions(): View {
        val prompts = listOf(
            Triple(
                "Quais horários tiveram melhores ofertas?",
                R.drawable.sr23_ic_clock,
                SrUi023.palette(context).orange,
            ),
            Triple(
                "Como foi meu dia?",
                R.drawable.sr23_ic_chart_down,
                SrUi023.palette(context).blue,
            ),
            Triple(
                "Qual região pagou melhor?",
                R.drawable.sr23_ic_car,
                SrUi023.palette(context).teal,
            ),
            Triple(
                "Quantas corridas fiz?",
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
                            LayoutParams(SrUi023.dp(context, 44), SrUi023.dp(context, 44)),
                        )

                        addView(
                            SrUi023.body(context, text, 10.5f).apply {
                                setTextColor(SrUi023.palette(context).ink)
                                setTypeface(typeface, Typeface.BOLD)
                                gravity = Gravity.CENTER
                                textAlignment = View.TEXT_ALIGNMENT_CENTER
                                maxLines = 4
                            },
                            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
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
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (columnIndex > 0) marginStart = SrUi023.dp(context, 6)
                        },
                    )
                }

                addView(
                    row,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        if (rowIndex > 0) topMargin = SrUi023.dp(context, 6)
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
                    intelligenceStatus,
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
                    ImageButton(context).apply {
                        setImageResource(R.drawable.sr23_ic_mic_minimal)
                        imageTintList = ColorStateList.valueOf(SrUi023.palette(context).ink)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 12),
                            SrUi023.dp(context, 12), SrUi023.dp(context, 12))
                        contentDescription = "Ditar pergunta pelo microfone"
                        isFocusable = true
                        background = SrUi023.rounded(
                            SrUi023.palette(context).surface,
                            12,
                            SrUi023.palette(context).outline,
                            1,
                            context,
                        )
                        setOnClickListener { dictateQuestion() }
                    },
                    LayoutParams(SrUi023.dp(context, 48), SrUi023.dp(context, 48)).apply {
                        marginEnd = SrUi023.dp(context, 6)
                    },
                )

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
            addView(voiceStatus, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = SrUi023.dp(context, 3)
            })
            addView(
                SrUi023.body(
                    context,
                    "Ditado opcional: o serviço de voz do aparelho poderá processar o áudio. Confira o texto antes de enviar.",
                    9f,
                ),
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = SrUi023.dp(context, 4)
                },
            )
        }

    private fun ask() {
        if (isAsking) return
        val text = question.text.toString().trim()
        if (text.length !in 3..800) {
            question.error = "Digite uma pergunta de 3 a 800 caracteres."
            return
        }
        question.error = null
        val sessionToken = repo.load().deviceToken
        if (conversationSessionToken != null && conversationSessionToken != sessionToken) {
            resetConversation()
            question.setText(text)
        }
        conversationSessionToken = sessionToken.takeIf { it.isNotBlank() }
        enterConversation()
        addUserMessage(text)

        if (sessionToken.isBlank()) {
            addAssistantMessage("Conecte sua conta/aparelho antes de perguntar.")
            return // Mantém pergunta para tentar após entrar.
        }

        val days = listOf(1, 7, 30, 90)[period.selectedItemPosition]
        val pending = addAssistantMessage("Analisando seus dados…")
        val generation = conversationSerial
        isAsking = true
        BackendClient.askEnhanced(context, text, days, conversationContext) { result ->
            // "Nova conversa" or a different account invalidates the old result.
            if (generation != conversationSerial) return@askEnhanced
            if (repo.load().deviceToken != sessionToken) {
                resetConversation()
                return@askEnhanced
            }
            isAsking = false
            result.onSuccess { response ->
                conversationContext = response.structuredContext
                pending.text.text = response.answer
                pending.meta.text = evidenceSummary(response)
                pending.meta.visibility = View.VISIBLE
                addEvidenceDetails(pending, response)
                question.setText("")
                refreshBilling()
                scrollToBottom()
            }.onFailure {
                pending.text.text = "Não foi possível consultar o Sr. Rotas: ${friendlyError(it.message)}"
                pending.meta.text = "Confira a conexão e tente novamente. Sua pergunta foi mantida."
                pending.meta.visibility = View.VISIBLE
                refreshBilling()
                scrollToBottom()
            }
        }
    }

    private fun resetConversation() {
        conversationSerial += 1
        conversationContext = null
        conversationSessionToken = null
        isAsking = false
        voiceGeneration += 1
        hasInteracted = false
        messagesHost.removeAllViews()
        messagesHost.visibility = View.GONE
        introHost.visibility = View.VISIBLE
        newChat.visibility = View.GONE
        question.setText("")
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceStatus.visibility = View.GONE
        scrollToBottom()
    }

    private fun evidenceSummary(response: BackendClient.AiAnswer): String {
        val evidence = response.evidence
        val count = evidence?.optInt("sampleCount", -1) ?: -1
        val periodLabel = evidence?.optJSONObject("period")?.optString("label").orEmpty()
        val nature = when (evidence?.optString("semantic")) {
            "OBSERVED_OFFER" -> "Ofertas observadas"
            "REALIZED_RIDE" -> "Corridas realizadas"
            "REALIZED_REVENUE" -> "Faturamento realizado"
            "ESTIMATED_COST" -> "Custo estimado"
            "ACTUAL_COST" -> "Custo realizado"
            "ACTUAL_DISTANCE" -> "Distância percorrida"
            "REGIONAL_EXPOSURE" -> "Observações regionais"
            "MARKET_OBSERVATION" -> "Observações de mercado"
            "MIXED" -> "Dados combinados"
            else -> "Dados consultados"
        }
        return listOfNotNull(
            periodLabel.takeIf { it.isNotBlank() },
            count.takeIf { it >= 0 }?.let { "$it registros" },
            nature,
        ).joinToString(" · ")
    }

    private fun addEvidenceDetails(bubble: AssistantBubble, response: BackendClient.AiAnswer) {
        if (response.evidence == null && response.metrics == null && response.limitations.isEmpty()) return
        val detail = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
        }
        fun line(value: String) {
            detail.addView(SrUi023.body(context, value, 10.5f).apply {
                setTextColor(SrUi023.palette(context).ink)
                setPadding(0, SrUi023.dp(context, 4), 0, SrUi023.dp(context, 4))
            })
        }
        val evidence = response.evidence
        val confidence = when (evidence?.optString("confidence")) {
            "high" -> "Alta"; "medium" -> "Moderada"; "low" -> "Baixa"
            else -> "Dados insuficientes"
        }
        line("Confiança da análise: $confidence")
        val coverage = evidence?.optJSONObject("coverage")?.opt("value")
        val percentage = (coverage as? Number)?.toDouble()
        line("Cobertura: " + if (percentage != null && percentage.isFinite())
            "${(percentage * 100).toInt()}%" else "Não disponível")
        line(if (evidence?.optString("completeness") == "complete")
            "Dados completos para o recorte" else "Dados parciais para o recorte")
        response.metrics?.let { metrics ->
            for (i in 0 until metrics.length()) {
                val value = metrics.optJSONObject(i) ?: continue
                val formatted = value.optString("formatted")
                if (formatted.isNotBlank()) line("${value.optString("label")}: $formatted")
            }
        }
        response.limitations.forEach { line("Limite: $it") }
        if (response.status != "ok") line("Não há dados suficientes para uma conclusão segura.")
        val toggle = SrUi023.body(context, "Entenda o resultado ▾", 11.5f).apply {
            setTextColor(SrUi023.palette(context).teal)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = SrUi023.dp(context, 48)
            isClickable = true
            isFocusable = true
            contentDescription = "Expandir detalhes, evidências e limitações"
            setOnClickListener {
                detail.visibility = if (detail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (detail.visibility == View.VISIBLE)
                    "Ocultar detalhes ▴" else "Entenda o resultado ▾"
            }
        }
        bubble.host.addView(toggle)
        bubble.host.addView(detail)

        val destination = if (response.status == "ok") when (response.intent) {
            "WAIT_OR_MOVE", "DESTINATION_CONTINUITY", "CURRENT_REGION_ADVICE" ->
                "/app/agora" to "Abrir Agora"
            "PERIOD_SUMMARY", "REALIZED_EARNINGS", "ACTUAL_DISTANCE", "OPERATING_COST",
            "PROFITABILITY", "BEST_JOURNEY", "COMPARE_PERIODS", "REGION_PERFORMANCE",
            "COMPARE_REGIONS", "HOUR_PERFORMANCE", "CATEGORY_PERFORMANCE", "OFFER_QUALITY",
            "COMPLETED_RIDES", "MARKET_QUALITY", "MISSED_OPPORTUNITIES" ->
                "/app/estatisticas" to "Abrir Estatísticas"
            else -> null
        } else null
        if (destination != null) {
            bubble.host.addView(SrUi023.body(context, destination.second, 11f).apply {
                setTextColor(SrUi023.palette(context).teal)
                setTypeface(typeface, Typeface.BOLD)
                minimumHeight = SrUi023.dp(context, 48)
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setOnClickListener { WebHandoff021.open(context, destination.first) }
            })
        }
    }

    private fun voiceMessage(value: String) {
        voiceStatus.text = value
        voiceStatus.visibility = View.VISIBLE
    }

    private fun dictateQuestion() {
        if (isAsking) return
        if (speechRecognizer != null) {
            voiceGeneration += 1
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            voiceMessage("Ditado interrompido. Você pode digitar sua pergunta.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceMessage("Ditado indisponível neste aparelho. Digite sua pergunta.")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            val activity = context as? Activity
            if (activity != null) {
                activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1207)
                voiceMessage("Autorize o microfone e toque novamente para ditar.")
            } else voiceMessage("Microfone indisponível. Digite sua pergunta.")
            return
        }
        val thisVoice = ++voiceGeneration
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (thisVoice == voiceGeneration) voiceMessage("Ouvindo… fale sua pergunta.")
                }
                override fun onBeginningOfSpeech() {
                    if (thisVoice == voiceGeneration) voiceMessage("Ouvindo…")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (thisVoice == voiceGeneration) voiceMessage("Reconhecendo o texto…")
                }
                override fun onError(error: Int) {
                    if (thisVoice != voiceGeneration) return
                    voiceGeneration += 1
                    voiceMessage("Não consegui reconhecer a fala. Tente novamente ou digite.")
                    this@AiPanel023.speechRecognizer?.destroy()
                    this@AiPanel023.speechRecognizer = null
                }
                override fun onResults(results: Bundle?) {
                    if (thisVoice != voiceGeneration) return
                    voiceGeneration += 1
                    val recognized = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().trim()
                    if (recognized.isNotBlank()) {
                        question.setText(recognized)
                        voiceMessage("Texto reconhecido. Confira e toque em Enviar.")
                    } else voiceMessage("Nenhum texto reconhecido. Digite sua pergunta.")
                    this@AiPanel023.speechRecognizer?.destroy()
                    this@AiPanel023.speechRecognizer = null
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        }
        voiceMessage("Preparando microfone…")
    }

    override fun onDetachedFromWindow() {
        voiceGeneration += 1
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDetachedFromWindow()
    }

    private fun enterConversation() {
        if (hasInteracted) return
        hasInteracted = true
        introHost.visibility = View.GONE
        messagesHost.visibility = View.VISIBLE
        newChat.visibility = View.VISIBLE
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
        val host: LinearLayout,
    )

    private fun addAssistantMessage(text: String): AssistantBubble {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
        }
        row.addView(mascotHead(34),
            LayoutParams(SrUi023.dp(context, 34), SrUi023.dp(context, 34)).apply {
                marginEnd = SrUi023.dp(context, 7)
            })

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
        return AssistantBubble(body, meta, bubble)
    }

    private fun scrollToBottom() {
        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshBilling() {
        if (repo.load().deviceToken.isBlank()) {
            intelligenceStatus.text = "Conecte sua conta"
            return
        }

        BackendClient.fetchBillingStatus(context) { result ->
            result.onSuccess { billing ->
                val state = AccessResolver10B.state(context)
                intelligenceStatus.text = when {
                    billing.subscriptionActive -> "Inteligência ativa"
                    state == "TRIAL_ACTIVE" -> "Trial da Inteligência"
                    else -> "Copiloto grátis"
                }
            }.onFailure {
                intelligenceStatus.text = "Inteligência indisponível"
            }
        }
    }

    private fun friendlyError(value: String?): String =
        when (value) {
            "openai_not_configured" ->
                "o recurso de Inteligência ainda não está disponível nesta sessão."
            "subscription_required" ->
                "a Inteligência requer trial ou assinatura ativa."
            "ai_credits_required" ->
                "esta sessão ainda usa um contrato antigo; atualize o acesso e tente novamente."
            "unauthorized" ->
                "sessão expirada; conecte o aparelho novamente."
            else ->
                value ?: "erro inesperado"
        }
}
