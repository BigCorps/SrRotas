package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.Instant

class StrategyActivity : Activity() {
    private lateinit var repo: SettingsRepository

    private lateinit var redKm: EditText
    private lateinit var greenKm: EditText
    private lateinit var redHour: EditText
    private lateinit var greenHour: EditText
    private lateinit var redRating: EditText
    private lateinit var greenRating: EditText
    private lateinit var redMinute: EditText
    private lateinit var greenMinute: EditText
    private lateinit var minFare: EditText
    private lateinit var maxPickup: EditText
    private lateinit var minProfit: EditText
    private lateinit var redProfitHour: EditText
    private lateinit var greenProfitHour: EditText
    private lateinit var redProfitPct: EditText
    private lateinit var greenProfitPct: EditText

    private lateinit var positionSpinner: Spinner
    private lateinit var themeSpinner: Spinner
    private lateinit var sizeSpinner: Spinner

    private lateinit var colorBlind: CheckBox
    private lateinit var opacity: SeekBar
    private lateinit var fontSize: SeekBar
    private lateinit var opacityLabel: TextView
    private lateinit var fontLabel: TextView

    private lateinit var dismissTap: CheckBox
    private lateinit var dragHud: CheckBox

    private lateinit var textNotification: CheckBox
    private lateinit var voiceNotification: CheckBox
    private lateinit var voiceFollowHud: CheckBox
    private lateinit var privateScreenshot: CheckBox
    private lateinit var passengerMessage: EditText

    private lateinit var metricsBox: LinearLayout
    private lateinit var voiceMetricsBox: LinearLayout
    private lateinit var costSummary: TextView

    private val metricLabels =
        linkedMapOf(
            "per_minute" to "R$/min",
            "per_km" to "R$/km",
            "rating" to "Avaliação",
            "per_hour" to "R$/hora",
            "profit_hour" to "Lucro est./hora",
            "profit_percent" to "Margem est. %",
            "profit" to "Lucro est.*",
            "pickup" to "Buscar",
        )

    private val voiceMetricLabels =
        linkedMapOf(
            "per_minute" to "R$/min",
            "per_km" to "R$/km",
            "fare" to "Valor",
            "per_hour" to "R$/hora",
            "total_km" to "Distância",
            "total_minutes" to "Duração",
            "destination" to "Destino",
        )

    private val order =
        mutableListOf<String>()
    private val enabled =
        mutableSetOf<String>()
    private val voiceOrder =
        mutableListOf<String>()
    private val voiceEnabled =
        mutableSetOf<String>()

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        UiKit.applySystemBars(this)

        val root = buildUi()
        setContentView(root)
        UiKit.applySafeArea(root)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (
            this::costSummary.isInitialized
        ) {
            refreshCostSummary()
        }
        CostProfileSync.refreshOrFlush(this)
    }

    private fun buildUi(): View {
        val scroll =
            ScrollView(this).apply {
                setFillViewport(true)
                setBackgroundColor(
                    UiKit.palette(
                        this@StrategyActivity,
                    ).background,
                )
            }

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(32),
                )
            }

        scroll.addView(root)

        root.addView(
            UiKit.title(
                this,
                "Estratégia e HUD",
                27f,
            ),
        )
        root.addView(
            UiKit.body(
                this,
                "Configure suas metas e deixe o card confortável para a sua tela. " +
                    "O motorista precisa bater o olho e entender, não navegar em menus enquanto dirige.",
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Atalhos de estratégia",
                ),
                top = 14,
            ),
        )

        val presets =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        listOf(
            "Popular" to "popular",
            "Conforto" to "comfort",
            "Premium" to "premium",
        ).forEach { (label, key) ->
            presets.addView(
                UiKit.secondaryButton(
                    this,
                    label,
                ) {
                    applyPreset(key)
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    setMargins(
                        dp(2),
                        0,
                        dp(2),
                        0,
                    )
                },
            )
        }

        root.addView(presets)

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Metas da oferta",
                ),
                top = 16,
            ),
        )

        val km =
            thresholdCard(
                "R$/km",
                "Valor por quilômetro considerando embarque + viagem.",
                1.45,
                1.80,
            )
        redKm = km.first
        greenKm = km.second
        root.addView(km.third)

        val min =
            thresholdCard(
                "R$/min",
                "Valor da oferta dividido pelo tempo total estimado.",
                0.48,
                0.60,
            )
        redMinute = min.first
        greenMinute = min.second
        root.addView(min.third)

        val hr =
            thresholdCard(
                "R$/hora",
                "Valor da oferta dividido pelo tempo total estimado.",
                28.0,
                35.0,
            )
        redHour = hr.first
        greenHour = hr.second
        root.addView(hr.third)

        val rat =
            thresholdCard(
                "Avaliação",
                "Usada somente quando a avaliação estiver disponível.",
                4.70,
                4.85,
            )
        redRating = rat.first
        greenRating = rat.second
        root.addView(rat.third)

        val ph =
            thresholdCard(
                "Lucro est./hora",
                "Estimativa após o custo por km configurado, dividida pelo tempo. Use 0/0 para não classificar por esta métrica.",
                0.0,
                0.0,
            )
        redProfitHour = ph.first
        greenProfitHour = ph.second
        root.addView(ph.third)

        val pp =
            thresholdCard(
                "Margem %",
                "Percentual estimado que sobra após o custo por km. Use 0/0 para não classificar.",
                0.0,
                0.0,
            )
        redProfitPct = pp.first
        greenProfitPct = pp.second
        root.addView(pp.third)

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Limites adicionais",
                ),
                top = 12,
            ),
        )

        val limits =
            UiKit.card(this)
        minFare =
            UiKit.input(
                this,
                "Valor mínimo da oferta — 0 desativa",
                numeric = true,
            )
        maxPickup =
            UiKit.input(
                this,
                "Distância máxima para buscar — km",
                numeric = true,
            )
        minProfit =
            UiKit.input(
                this,
                "Lucro est. mínimo — 0 desativa",
                numeric = true,
            )

        listOf(
            minFare,
            maxPickup,
            minProfit,
        ).forEachIndexed { i, v ->
            limits.addView(
                if (i == 0) {
                    v
                } else {
                    UiKit.margin(
                        v,
                        top = 8,
                    )
                },
            )
        }
        root.addView(limits)

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Custos e Lucro est.*",
                ),
                top = 14,
            ),
        )

        root.addView(
            UiKit.card(this).apply {
                costSummary =
                    UiKit.body(
                        this@StrategyActivity,
                        "",
                        13f,
                    )
                addView(costSummary)

                addView(
                    UiKit.margin(
                        UiKit.primaryButton(
                            this@StrategyActivity,
                            "Ajustar meus custos",
                        ) {
                            startActivity(
                                Intent(
                                    this@StrategyActivity,
                                    CostProfileActivity::class.java,
                                ),
                            )
                        },
                        top = 10,
                    ),
                )

                addView(
                    UiKit.margin(
                        UiKit.secondaryButton(
                            this@StrategyActivity,
                            "Ver memória do cálculo",
                        ) {
                            showCostMemory()
                        },
                        top = 7,
                    ),
                )

                addView(
                    UiKit.margin(
                        UiKit.body(
                            this@StrategyActivity,
                            "Lucro est.* é uma estimativa operacional baseada nos custos configurados. " +
                                "Não representa lucro contábil.",
                            11f,
                        ),
                        top = 8,
                    ),
                )
            },
        )

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Painel de Rota",
                ),
                top = 16,
            ),
        )
        root.addView(
            UiKit.body(
                this,
                "Novo HUD próprio do Sr. Rotas: linha de rota lateral, hierarquia visual e três tamanhos independentes da fonte.",
            ),
        )

        val appearance =
            UiKit.card(this)

        appearance.addView(
            label("Tamanho do card"),
        )
        sizeSpinner =
            spinner(
                listOf(
                    "Compacto",
                    "Normal",
                    "Grande",
                ),
            )
        appearance.addView(
            sizeSpinner,
        )

        appearance.addView(
            label("Posição inicial"),
        )
        positionSpinner =
            spinner(
                listOf(
                    "Esquerda",
                    "Centro",
                    "Direita",
                ),
            )
        appearance.addView(
            positionSpinner,
        )

        appearance.addView(
            label("Tema"),
        )
        themeSpinner =
            spinner(
                listOf(
                    "Automático",
                    "Claro",
                    "Escuro",
                ),
            )
        appearance.addView(
            themeSpinner,
        )

        colorBlind =
            CheckBox(this).apply {
                text =
                    "Modo para daltonismo"
                setTextColor(
                    UiKit.palette(
                        this@StrategyActivity,
                    ).ink,
                )
            }
        appearance.addView(
            colorBlind,
        )

        opacityLabel =
            label("Opacidade")
        appearance.addView(
            opacityLabel,
        )
        opacity =
            SeekBar(this).apply {
                max = 70
                setOnSeekBarChangeListener(
                    simpleSeek {
                        opacityLabel.text =
                            "Opacidade: ${it + 30}%"
                    },
                )
            }
        appearance.addView(opacity)

        fontLabel =
            label("Fonte")
        appearance.addView(
            fontLabel,
        )
        fontSize =
            SeekBar(this).apply {
                max = 10
                setOnSeekBarChangeListener(
                    simpleSeek {
                        fontLabel.text =
                            "Fonte: ${it + 14}"
                    },
                )
            }
        appearance.addView(fontSize)

        root.addView(appearance)

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Métricas do card",
                ),
                top = 14,
            ),
        )
        root.addView(
            UiKit.body(
                this,
                "Marque o que aparece e use as setas para ordenar. O modo Compacto prioriza as primeiras métricas.",
            ),
        )

        metricsBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }
        root.addView(
            UiKit.margin(
                metricsBox,
                top = 8,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Gestos para dirigir",
                ),
                top = 14,
            ),
        )

        root.addView(
            UiKit.card(this).apply {
                dismissTap =
                    CheckBox(
                        this@StrategyActivity,
                    ).apply {
                        text =
                            "Toque no card fecha a oferta atual"
                        isChecked = true
                        setTextColor(
                            UiKit.palette(
                                this@StrategyActivity,
                            ).ink,
                        )
                    }
                addView(dismissTap)

                dragHud =
                    CheckBox(
                        this@StrategyActivity,
                    ).apply {
                        text =
                            "Segurar e arrastar reposiciona o card"
                        isChecked = true
                        setTextColor(
                            UiKit.palette(
                                this@StrategyActivity,
                            ).ink,
                        )
                    }
                addView(dragHud)

                addView(
                    UiKit.body(
                        this@StrategyActivity,
                        "A posição arrastada fica salva para as próximas ofertas. " +
                            "Você pode restaurar a posição inicial quando quiser.",
                    ),
                )

                addView(
                    UiKit.margin(
                        UiKit.secondaryButton(
                            this@StrategyActivity,
                            "Restaurar posição inicial",
                        ) {
                            repo.resetHudPosition()
                            toast(
                                "Posição do HUD restaurada.",
                            )
                        },
                        top = 10,
                    ),
                )

                addView(
                    UiKit.margin(
                        UiKit.primaryButton(
                            this@StrategyActivity,
                            "Pré-visualizar HUD",
                        ) {
                            preview()
                        },
                        top = 8,
                    ),
                )
            },
        )

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Voz da oferta",
                ),
                top = 14,
            ),
        )
        root.addView(
            UiKit.body(
                this,
                "Escolha o que o Sr. Rotas fala. Por padrão, as métricas em comum acompanham a ordem visual do HUD.",
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    voiceNotification =
                        CheckBox(
                            this@StrategyActivity,
                        ).apply {
                            text =
                                "Falar ofertas reconhecidas"
                            setTextColor(
                                UiKit.palette(
                                    this@StrategyActivity,
                                ).ink,
                            )
                        }
                    addView(
                        voiceNotification,
                    )

                    voiceFollowHud =
                        CheckBox(
                            this@StrategyActivity,
                        ).apply {
                            text =
                                "Seguir ordem do HUD"
                            setTextColor(
                                UiKit.palette(
                                    this@StrategyActivity,
                                ).ink,
                            )
                        }
                    addView(
                        voiceFollowHud,
                    )

                    addView(
                        UiKit.body(
                            this@StrategyActivity,
                            "Valor, distância e duração mantêm a posição escolhida abaixo. Destino usa o Context Engine.",
                        ),
                    )
                },
                top = 8,
            ),
        )

        voiceMetricsBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        root.addView(
            UiKit.margin(
                voiceMetricsBox,
                top = 8,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.sectionTitle(
                    this,
                    "Avançado",
                ),
                top = 14,
            ),
        )

        root.addView(
            UiKit.card(this).apply {
                textNotification =
                    CheckBox(
                        this@StrategyActivity,
                    ).apply {
                        text =
                            "Notificação textual"
                        setTextColor(
                            UiKit.palette(
                                this@StrategyActivity,
                            ).ink,
                        )
                    }

                privateScreenshot =
                    CheckBox(
                        this@StrategyActivity,
                    ).apply {
                        text =
                            "Salvar captura privada ao reconhecer oferta"
                        setTextColor(
                            UiKit.palette(
                                this@StrategyActivity,
                            ).ink,
                        )
                    }

                addView(
                    textNotification,
                )
                addView(
                    privateScreenshot,
                )

                addView(
                    UiKit.body(
                        this@StrategyActivity,
                        "Capturas privadas ficam no armazenamento interno, não vão para a galeria e não são enviadas automaticamente ao servidor.",
                    ),
                )

                addView(
                    UiKit.margin(
                        UiKit.secondaryButton(
                            this@StrategyActivity,
                            "Apagar capturas privadas",
                        ) {
                            PrivateScreenshotStore.clear(
                                this@StrategyActivity,
                            )
                            toast(
                                "Capturas privadas apagadas.",
                            )
                        },
                        top = 8,
                    ),
                )

                addView(
                    label(
                        "Mensagem padrão para passageiro",
                    ),
                )

                passengerMessage =
                    UiKit.input(
                        this@StrategyActivity,
                        "Mensagem",
                        multiline = true,
                    )
                addView(
                    passengerMessage,
                )

                addView(
                    UiKit.margin(
                        UiKit.secondaryButton(
                            this@StrategyActivity,
                            "Copiar mensagem",
                        ) {
                            copyPassengerMessage()
                        },
                        top = 8,
                    ),
                )
            },
        )

        root.addView(
            UiKit.margin(
                UiKit.primaryButton(
                    this,
                    "Salvar tudo",
                ) {
                    save()
                    BackendClient.syncPreferences(
                        this,
                    )
                    toast(
                        "Estratégia e HUD salvos.",
                    )
                },
                top = 16,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.secondaryButton(
                    this,
                    "Voltar",
                ) {
                    finish()
                },
                top = 8,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.body(
                    this,
                    "Sr. Rotas — desenvolvido pela BigCorps • contato@bigcorps.com.br",
                    12f,
                ),
                top = 16,
            ),
        )

        return scroll
    }

    private fun thresholdCard(
        name: String,
        help: String,
        redDefault: Double,
        greenDefault: Double,
    ): Triple<EditText, EditText, View> {
        val red =
            UiKit.input(
                this,
                "Abaixo de $redDefault",
                numeric = true,
            )
        val green =
            UiKit.input(
                this,
                "A partir de $greenDefault",
                numeric = true,
            )

        val card =
            UiKit.card(this).apply {
                val head =
                    LinearLayout(
                        this@StrategyActivity,
                    ).apply {
                        orientation =
                            LinearLayout.HORIZONTAL
                        gravity =
                            Gravity.CENTER_VERTICAL
                    }

                head.addView(
                    UiKit.title(
                        this@StrategyActivity,
                        name,
                        18f,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )

                head.addView(
                    UiKit.pill(
                        this@StrategyActivity,
                        "?",
                        "primary",
                    ).apply {
                        setOnClickListener {
                            AlertDialog.Builder(
                                this@StrategyActivity,
                            )
                                .setTitle(name)
                                .setMessage(help)
                                .setPositiveButton(
                                    "OK",
                                    null,
                                )
                                .show()
                        }
                    },
                )

                addView(head)
                addView(
                    UiKit.margin(
                        red,
                        top = 8,
                    ),
                )
                addView(
                    UiKit.margin(
                        green,
                        top = 8,
                    ),
                )
            }

        return Triple(
            red,
            green,
            UiKit.margin(
                card,
                bottom = 9,
            ),
        )
    }

    private fun renderMetrics() {
        metricsBox.removeAllViews()

        order.forEachIndexed { index, key ->
            val row =
                UiKit.card(
                    this,
                    10,
                ).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.CENTER_VERTICAL
                }

            val cb =
                CheckBox(this).apply {
                    text =
                        metricLabels[key]
                            ?: key
                    isChecked =
                        key in enabled
                    setTextColor(
                        UiKit.palette(
                            this@StrategyActivity,
                        ).ink,
                    )
                    setOnCheckedChangeListener {
                            _,
                            checked,
                        ->
                        if (checked) {
                            enabled += key
                        } else {
                            enabled -= key
                        }
                    }
                }

            row.addView(
                cb,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            row.addView(
                UiKit.pill(
                    this,
                    "↑",
                ).apply {
                    isEnabled =
                        index > 0
                    alpha =
                        if (isEnabled) {
                            1f
                        } else {
                            .35f
                        }
                    setOnClickListener {
                        if (index > 0) {
                            val k =
                                order.removeAt(
                                    index,
                                )
                            order.add(
                                index - 1,
                                k,
                            )
                            renderMetrics()
                        }
                    }
                },
            )

            row.addView(
                UiKit.pill(
                    this,
                    "↓",
                ).apply {
                    isEnabled =
                        index <
                        order.lastIndex
                    alpha =
                        if (isEnabled) {
                            1f
                        } else {
                            .35f
                        }
                    setOnClickListener {
                        if (
                            index <
                            order.lastIndex
                        ) {
                            val k =
                                order.removeAt(
                                    index,
                                )
                            order.add(
                                index + 1,
                                k,
                            )
                            renderMetrics()
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart =
                        dp(6)
                },
            )

            metricsBox.addView(
                UiKit.margin(
                    row,
                    bottom = 6,
                ),
            )
        }
    }

    private fun renderVoiceMetrics() {
        voiceMetricsBox.removeAllViews()

        voiceOrder.forEachIndexed {
                index,
                key,
            ->
            val row =
                UiKit.card(
                    this,
                    10,
                ).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.CENTER_VERTICAL
                }

            val cb =
                CheckBox(this).apply {
                    text =
                        voiceMetricLabels[key]
                            ?: key
                    isChecked =
                        key in voiceEnabled
                    setTextColor(
                        UiKit.palette(
                            this@StrategyActivity,
                        ).ink,
                    )
                    setOnCheckedChangeListener {
                            _,
                            checked,
                        ->
                        if (checked) {
                            voiceEnabled += key
                        } else {
                            voiceEnabled -= key
                        }
                    }
                }

            row.addView(
                cb,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            row.addView(
                UiKit.pill(
                    this,
                    "↑",
                ).apply {
                    isEnabled =
                        index > 0
                    alpha =
                        if (isEnabled) {
                            1f
                        } else {
                            .35f
                        }
                    setOnClickListener {
                        if (index > 0) {
                            val k =
                                voiceOrder.removeAt(
                                    index,
                                )
                            voiceOrder.add(
                                index - 1,
                                k,
                            )
                            renderVoiceMetrics()
                        }
                    }
                },
            )

            row.addView(
                UiKit.pill(
                    this,
                    "↓",
                ).apply {
                    isEnabled =
                        index <
                        voiceOrder.lastIndex
                    alpha =
                        if (isEnabled) {
                            1f
                        } else {
                            .35f
                        }
                    setOnClickListener {
                        if (
                            index <
                            voiceOrder.lastIndex
                        ) {
                            val k =
                                voiceOrder.removeAt(
                                    index,
                                )
                            voiceOrder.add(
                                index + 1,
                                k,
                            )
                            renderVoiceMetrics()
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart =
                        dp(6)
                },
            )

            voiceMetricsBox.addView(
                UiKit.margin(
                    row,
                    bottom = 6,
                ),
            )
        }
    }

    private fun load() {
        val s =
            repo.load()

        redKm.setText(
            pt(s.redPerKmBelow),
        )
        greenKm.setText(
            pt(s.minPerKm),
        )
        redHour.setText(
            pt(s.redPerHourBelow),
        )
        greenHour.setText(
            pt(s.minPerHour),
        )
        redRating.setText(
            pt(s.redRatingBelow),
        )
        greenRating.setText(
            pt(s.goodRatingFrom),
        )
        redMinute.setText(
            pt(s.redPerMinuteBelow),
        )
        greenMinute.setText(
            pt(s.minPerMinute),
        )
        redProfitHour.setText(
            pt(s.redProfitPerHourBelow),
        )
        greenProfitHour.setText(
            pt(s.minProfitPerHour),
        )
        redProfitPct.setText(
            pt(s.redProfitPercentBelow),
        )
        greenProfitPct.setText(
            pt(s.minProfitPercent),
        )
        minFare.setText(
            pt(s.minFare),
        )
        maxPickup.setText(
            pt(s.maxPickupKm),
        )
        minProfit.setText(
            pt(s.minProfit),
        )

        order.clear()
        order +=
            s.hudMetricOrder
                .split(',')
                .filter(
                    metricLabels::containsKey,
                )
        metricLabels.keys
            .filterNot(
                order::contains,
            )
            .forEach(
                order::add,
            )

        enabled.clear()
        enabled +=
            s.hudEnabledMetrics
                .split(',')
                .filter(
                    metricLabels::containsKey,
                )
        renderMetrics()

        voiceOrder.clear()
        voiceOrder +=
            HudPresentation
                .normalizedVoiceOrder(
                    s.voiceMetricOrder,
                )
                .filter(
                    voiceMetricLabels::containsKey,
                )

        voiceMetricLabels.keys
            .filterNot(
                voiceOrder::contains,
            )
            .forEach(
                voiceOrder::add,
            )

        voiceEnabled.clear()
        voiceEnabled +=
            s.voiceEnabledMetrics
                .split(',')
                .filter(
                    voiceMetricLabels::containsKey,
                )
        renderVoiceMetrics()

        sizeSpinner.setSelection(
            when (
                s.hudCardSize
            ) {
                "compact" -> 0
                "large" -> 2
                else -> 1
            },
        )

        positionSpinner.setSelection(
            when (
                s.hudPosition
            ) {
                "center" -> 1
                "right" -> 2
                else -> 0
            },
        )

        themeSpinner.setSelection(
            when (
                s.hudTheme
            ) {
                "light" -> 1
                "dark" -> 2
                else -> 0
            },
        )

        colorBlind.isChecked =
            s.colorBlindMode
        opacity.progress =
            s.hudOpacity - 30
        opacityLabel.text =
            "Opacidade: ${s.hudOpacity}%"
        fontSize.progress =
            (
                s.hudFontSize -
                14
                ).coerceIn(
                0,
                10,
            )
        fontLabel.text =
            "Fonte: ${s.hudFontSize}"
        dismissTap.isChecked =
            s.hudDismissOnTap
        dragHud.isChecked =
            s.hudDragEnabled

        textNotification.isChecked =
            s.textNotificationEnabled
        voiceNotification.isChecked =
            s.voiceNotificationEnabled
        voiceFollowHud.isChecked =
            s.voiceFollowHudOrder
        privateScreenshot.isChecked =
            s.privateScreenshotEnabled
        passengerMessage.setText(
            s.defaultPassengerMessage,
        )

        refreshCostSummary()
    }

    private fun save() {
        val old =
            repo.load()

        repo.save(
            old.copy(
                minPerKm =
                    num(
                        greenKm,
                        old.minPerKm,
                    ),
                redPerKmBelow =
                    num(
                        redKm,
                        old.redPerKmBelow,
                    ),
                minPerHour =
                    num(
                        greenHour,
                        old.minPerHour,
                    ),
                redPerHourBelow =
                    num(
                        redHour,
                        old.redPerHourBelow,
                    ),
                goodRatingFrom =
                    num(
                        greenRating,
                        old.goodRatingFrom,
                    ),
                redRatingBelow =
                    num(
                        redRating,
                        old.redRatingBelow,
                    ),
                minPerMinute =
                    num(
                        greenMinute,
                        old.minPerMinute,
                    ),
                redPerMinuteBelow =
                    num(
                        redMinute,
                        old.redPerMinuteBelow,
                    ),
                minFare =
                    num(
                        minFare,
                        old.minFare,
                    ),
                maxPickupKm =
                    num(
                        maxPickup,
                        old.maxPickupKm,
                    ),
                minProfit =
                    num(
                        minProfit,
                        old.minProfit,
                    ),
                minProfitPerHour =
                    num(
                        greenProfitHour,
                        old.minProfitPerHour,
                    ),
                redProfitPerHourBelow =
                    num(
                        redProfitHour,
                        old.redProfitPerHourBelow,
                    ),
                minProfitPercent =
                    num(
                        greenProfitPct,
                        old.minProfitPercent,
                    ),
                redProfitPercentBelow =
                    num(
                        redProfitPct,
                        old.redProfitPercentBelow,
                    ),

                // costPerKm é controlado exclusivamente
                // pelo CostProfileActivity na 0.18.
                costPerKm =
                    old.costPerKm,

                hudMetricOrder =
                    order.joinToString(","),
                hudEnabledMetrics =
                    order
                        .filter(
                            enabled::contains,
                        )
                        .joinToString(","),
                hudPosition =
                    listOf(
                        "left",
                        "center",
                        "right",
                    )[
                        positionSpinner
                            .selectedItemPosition
                    ],
                hudTheme =
                    listOf(
                        "auto",
                        "light",
                        "dark",
                    )[
                        themeSpinner
                            .selectedItemPosition
                    ],
                hudCardSize =
                    listOf(
                        "compact",
                        "normal",
                        "large",
                    )[
                        sizeSpinner
                            .selectedItemPosition
                    ],
                hudDismissOnTap =
                    dismissTap.isChecked,
                hudDragEnabled =
                    dragHud.isChecked,
                colorBlindMode =
                    colorBlind.isChecked,
                hudOpacity =
                    opacity.progress + 30,
                hudFontSize =
                    fontSize.progress + 14,
                textNotificationEnabled =
                    textNotification.isChecked,
                voiceNotificationEnabled =
                    voiceNotification.isChecked,
                voiceFollowHudOrder =
                    voiceFollowHud.isChecked,
                voiceMetricOrder =
                    voiceOrder
                        .joinToString(","),
                voiceEnabledMetrics =
                    voiceOrder
                        .filter(
                            voiceEnabled::contains,
                        )
                        .joinToString(","),
                privateScreenshotEnabled =
                    privateScreenshot.isChecked,
                defaultPassengerMessage =
                    passengerMessage.text
                        .toString()
                        .trim()
                        .take(600),
            ),
        )
    }

    private fun refreshCostSummary() {
        val snapshot =
            repo.costSnapshot()
        val profile =
            CostProfileStore
                .get(this)
                .load()

        if (profile == null) {
            costSummary.text =
                "Custo atual: R$ ${pt(snapshot.costPerKm)}/km\n" +
                    "Fonte: configuração anterior à 0.18. " +
                    "Abra “Ajustar meus custos” para separar combustível/energia, custos mensais e base de rateio."
            return
        }

        val calc =
            CostCalculator
                .calculate(profile)

        val allocationLabel =
            if (
                calc.allocationSource ==
                CostProfileValues.SOURCE_USER
            ) {
                "km/mês informado"
            } else {
                "km/mês estimado"
            }

        costSummary.text =
            "Custo operacional estimado: R$ ${pt4(calc.effectiveCostPerKm)}/km\n" +
                "Variável R$ ${pt4(calc.variableCostPerKm)}/km · " +
                "fixos rateados R$ ${pt4(calc.fixedCostPerKm)}/km\n" +
                "Base: $allocationLabel · " +
                if (
                    calc.completeness ==
                    "complete"
                ) {
                    "perfil completo"
                } else {
                    "perfil parcial"
                }
    }

    private fun showCostMemory() {
        val profile =
            CostProfileStore
                .get(this)
                .load()

        if (profile == null) {
            AlertDialog.Builder(this)
                .setTitle(
                    "Memória do cálculo",
                )
                .setMessage(
                    "Ainda não existe um perfil 0.18. " +
                        "O custo atual é uma configuração legada de R$ ${pt(repo.costSnapshot().costPerKm)}/km.",
                )
                .setPositiveButton(
                    "Ajustar meus custos",
                ) { _, _ ->
                    startActivity(
                        Intent(
                            this,
                            CostProfileActivity::class.java,
                        ),
                    )
                }
                .setNegativeButton(
                    "Fechar",
                    null,
                )
                .show()
            return
        }

        val calc =
            CostCalculator
                .calculate(profile)

        AlertDialog.Builder(this)
            .setTitle(
                "Memória do cálculo",
            )
            .setMessage(
                calc.memoryText(),
            )
            .setPositiveButton(
                "OK",
                null,
            )
            .show()
    }

    private fun applyPreset(
        kind: String,
    ) {
        when (kind) {
            "comfort" -> {
                redKm.setText("1,50")
                greenKm.setText("1,80")
                redHour.setText("30")
                greenHour.setText("39")
                redMinute.setText("0,50")
                greenMinute.setText("0,65")
            }

            "premium" -> {
                redKm.setText("1,80")
                greenKm.setText("2,20")
                redHour.setText("39")
                greenHour.setText("51")
                redMinute.setText("0,65")
                greenMinute.setText("0,85")
            }

            else -> {
                redKm.setText("1,20")
                greenKm.setText("1,50")
                redHour.setText("24")
                greenHour.setText("30")
                redMinute.setText("0,40")
                greenMinute.setText("0,50")
            }
        }

        Strategy021Store.savePreset(this, kind)
        toast(
            "Perfil aplicado. Limites de busca foram preservados; revise e salve.",
        )
    }

    private fun preview() {
        save()

        if (
            !Settings.canDrawOverlays(
                this,
            )
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:$packageName",
                    ),
                ),
            )
            toast(
                "Autorize o HUD e tente novamente.",
            )
            return
        }

        val cost =
            repo.costSnapshot()
                .costPerKm

        val sampleKm = 8.5
        val sampleFare = 28.75
        val sampleCost =
            sampleKm * cost
        val sampleProfit =
            sampleFare - sampleCost

        val sample =
            RideOffer(
                observedAt =
                    Instant.now()
                        .toString(),
                sourcePackage =
                    "preview",
                captureMethod =
                    "preview",
                rawText =
                    "preview",
                fare =
                    sampleFare,
                pickupKm =
                    1.2,
                tripKm =
                    7.3,
                totalKm =
                    sampleKm,
                pickupMinutes =
                    5,
                tripMinutes =
                    20,
                totalMinutes =
                    25,
                perKm =
                    3.38,
                perHour =
                    69.0,
                perMinute =
                    1.15,
                estimatedCost =
                    sampleCost,
                estimatedProfit =
                    sampleProfit,
                profitPerHour =
                    sampleProfit /
                        (25.0 / 60.0),
                profitPercent =
                    if (
                        sampleFare >
                        0.0
                    ) {
                        sampleProfit /
                            sampleFare *
                            100.0
                    } else {
                        null
                    },
                passengerRating =
                    4.95,
                advertisedPerKm =
                    3.38,
                serviceType =
                    "uberx",
                verdict =
                    "boa",
                confidence =
                    .99,
                offerType =
                    "exclusive",
                costPerKmUsed =
                    cost,
                costSource =
                    repo.costSnapshot()
                        .source,
                costProfileVersion =
                    repo.costSnapshot()
                        .version,
                costProfileUpdatedAt =
                    repo.costSnapshot()
                        .profileUpdatedAt,
                dedupeKey =
                    "preview",
            )

        OverlayController(this)
            .show(
                sample,
                15000,
            )
    }

    private fun copyPassengerMessage() {
        val text =
            passengerMessage.text
                .toString()
                .trim()

        if (text.isBlank()) {
            toast(
                "Digite uma mensagem primeiro.",
            )
            return
        }

        (
            getSystemService(
                CLIPBOARD_SERVICE,
            ) as ClipboardManager
            ).setPrimaryClip(
            ClipData.newPlainText(
                "Mensagem Sr. Rotas",
                text,
            ),
        )

        toast(
            "Mensagem copiada. O Sr. Rotas não envia automaticamente.",
        )
    }

    private fun label(
        t: String,
    ) =
        UiKit.body(
            this,
            t,
            13f,
        ).apply {
            setPadding(
                0,
                dp(10),
                0,
                dp(5),
            )
        }

    private fun spinner(
        items: List<String>,
    ) =
        Spinner(this).apply {
            adapter =
                ArrayAdapter(
                    this@StrategyActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    items,
                )
        }

    private fun simpleSeek(
        onProgress:
            (Int) -> Unit,
    ) =
        object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
                onProgress(progress)
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?,
            ) = Unit

            override fun onStopTrackingTouch(
                seekBar: SeekBar?,
            ) = Unit
        }

    private fun num(
        e: EditText,
        fallback: Double,
    ) =
        e.text
            .toString()
            .trim()
            .replace(
                ',',
                '.',
            )
            .toDoubleOrNull()
            ?: fallback

    private fun pt(
        v: Double,
    ) =
        String.format(
            java.util.Locale(
                "pt",
                "BR",
            ),
            "%.2f",
            v,
        )

    private fun pt4(
        v: Double,
    ) =
        String.format(
            java.util.Locale(
                "pt",
                "BR",
            ),
            "%.4f",
            v,
        )

    private fun dp(
        v: Int,
    ) =
        UiKit.dp(
            this,
            v,
        )

    private fun toast(
        t: String,
    ) =
        Toast.makeText(
            this,
            t,
            Toast.LENGTH_SHORT,
        ).show()
}
