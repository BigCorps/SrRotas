package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * 0.24 — Configuração do HUD.
 *
 * Esta Activity mantém o nome histórico para não quebrar intents/manifest,
 * mas sua responsabilidade agora é exclusivamente HUD + controles associados.
 * Aparência geral do aplicativo permanece fora daqui.
 */
class Strategy021Activity : Activity() {
    private val repo by lazy { SettingsRepository(this) }

    private lateinit var content: LinearLayout
    private lateinit var previewHost: LinearLayout
    private lateinit var presetHost: LinearLayout

    private val metrics = linkedMapOf<String, MetricFields>()
    private val messageEditors = mutableListOf<MessageEditor>()

    private lateinit var pickupKmMax: HudUnitField024
    private lateinit var pickupKmDerived: HudUnitField024
    private lateinit var pickupMinMax: HudUnitField024
    private lateinit var pickupMinDerived: HudUnitField024
    private lateinit var pickupEnabled: CheckBox

    private lateinit var hudTheme: Spinner
    private lateinit var hudSizeHost: LinearLayout
    private lateinit var hudOpacityLabel: TextView
    private lateinit var hudFontLabel: TextView
    private lateinit var hudOpacity: SeekBar
    private lateinit var hudFont: SeekBar
    private lateinit var colorBlind: CheckBox
    private lateinit var dismissOnTap: CheckBox
    private lateinit var dragHud: CheckBox
    private lateinit var showFare: CheckBox
    private lateinit var showDistance: CheckBox
    private lateinit var showTotalTime: CheckBox

    private lateinit var bubbleEnabled: CheckBox
    private lateinit var bubbleOfferCount: Spinner
    private lateinit var bubbleTextSize: Spinner
    private lateinit var bubbleSizeLabel: TextView
    private lateinit var bubbleOpacityLabel: TextView
    private lateinit var bubbleSize: SeekBar
    private lateinit var bubbleOpacity: SeekBar

    private var hudSize = Hud023Spec.SIZE_NORMAL
    private var selectedPreset = "custom"
    private var loadingForm = false
    private var messageUseSynced = false
    private lateinit var initialSettings: DriverSettings
    private var initialPickupMinutes = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)

        initialSettings = repo.load()
        initialPickupMinutes = Strategy021Store.load(this).maxPickupMinutes
        selectedPreset = Strategy021Store.load(this).strategyPreset
        hudSize = Hud023Spec.normalizeSize(initialSettings.hudCardSize)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.palette(this@Strategy021Activity).background)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(UiKit.palette(this@Strategy021Activity).background)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(
            page,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT,
            ),
        )
        outer.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        page.addView(
            SrAppHeader023(
                this,
                "Configuração do HUD",
                "Defina limites, métricas e como o Painel de Rota aparece durante a jornada.",
            ),
        )

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiKit.dp(this@Strategy021Activity, 14),
                UiKit.dp(this@Strategy021Activity, 10),
                UiKit.dp(this@Strategy021Activity, 14),
                UiKit.dp(this@Strategy021Activity, 24),
            )
        }
        page.addView(content)

        buildPreview()
        buildPresets()
        buildMainMetrics()
        buildOtherMetrics()
        buildCosts()
        buildHudDisplay()
        buildFloatingWindow()
        buildQuickMessages()

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiKit.dp(this@Strategy021Activity, 14),
                UiKit.dp(this@Strategy021Activity, 8),
                UiKit.dp(this@Strategy021Activity, 14),
                UiKit.dp(this@Strategy021Activity, 10),
            )
            setBackgroundColor(UiKit.palette(this@Strategy021Activity).surface)
            addView(
                UiKit.primaryButton(
                    this@Strategy021Activity,
                    "Salvar configuração",
                ) { save() },
            )
        }
        outer.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        setContentView(outer)
        UiKit.applySafeArea(outer)

        loadForm(initialSettings, initialPickupMinutes)
        renderPresetButtons()
        refreshPreview()
    }

    private fun buildPreview() {
        content.addView(
            UiKit.sectionTitle(this, "Prévia em tempo real"),
        )
        previewHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(
            previewHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(this@Strategy021Activity, 7)
            },
        )
    }

    private fun buildPresets() {
        presetHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@Strategy021Activity,
                            "Estratégia",
                        ),
                    )
                    addView(
                        UiKit.body(
                            this@Strategy021Activity,
                            "Escolha um perfil pronto ou mantenha suas próprias metas em Meu perfil.",
                            10.5f,
                        ),
                    )
                    addView(
                        presetHost,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = UiKit.dp(this@Strategy021Activity, 8)
                        },
                    )
                },
                top = 12,
            ),
        )
    }

    private fun buildMainMetrics() {
        val card = UiKit.card(this).apply {
            addView(
                UiKit.sectionTitle(
                    this@Strategy021Activity,
                    "Ganhos e eficiência",
                ),
            )
            addView(
                UiKit.body(
                    this@Strategy021Activity,
                    "Mínimo é o limite aceitável; Máximo/meta inicia a faixa verde.",
                    10.5f,
                ),
            )
        }

        addBenefitMetric(
            card,
            key = "per_km",
            title = "Valor por quilômetro",
            prefix = "R$",
            suffix = "/km",
            minimum = initialSettings.redPerKmBelow,
            target = initialSettings.minPerKm,
        )
        addBenefitMetric(
            card,
            key = "per_minute",
            title = "Valor por minuto",
            prefix = "R$",
            suffix = "/min",
            minimum = initialSettings.redPerMinuteBelow,
            target = initialSettings.minPerMinute,
        )
        addBenefitMetric(
            card,
            key = "per_hour",
            title = "Valor por hora",
            prefix = "R$",
            suffix = "/h",
            minimum = initialSettings.redPerHourBelow,
            target = initialSettings.minPerHour,
        )
        addBenefitMetric(
            card,
            key = "rating",
            title = "Avaliação do passageiro",
            prefix = "",
            suffix = "★",
            minimum = initialSettings.redRatingBelow,
            target = initialSettings.goodRatingFrom,
        )

        content.addView(UiKit.margin(card, top = 12))
    }

    private fun buildOtherMetrics() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addBenefitMetric(
            body,
            key = "profit_hour",
            title = "Lucro estimado por hora",
            prefix = "R$",
            suffix = "/h",
            minimum = initialSettings.redProfitPerHourBelow,
            target = initialSettings.minProfitPerHour,
        )
        addBenefitMetric(
            body,
            key = "profit_percent",
            title = "Margem estimada",
            prefix = "",
            suffix = "%",
            minimum = initialSettings.redProfitPercentBelow,
            target = initialSettings.minProfitPercent,
        )

        body.addView(
            UiKit.margin(
                UiKit.sectionTitle(this, "Busca"),
                top = 10,
            ),
        )
        pickupEnabled = metricToggle("pickup", "Distância e tempo até o embarque")
        body.addView(pickupEnabled)

        pickupKmDerived = HudUnitField024(
            this,
            "Mínimo",
            suffix = "km",
        ).apply {
            setEditable(false)
        }
        pickupKmMax = HudUnitField024(
            this,
            "Máximo",
            suffix = "km",
        )
        body.addView(
            pairFields(pickupKmDerived, pickupKmMax),
        )

        pickupMinDerived = HudUnitField024(
            this,
            "Mínimo",
            suffix = "min",
            integer = true,
        ).apply {
            setEditable(false)
        }
        pickupMinMax = HudUnitField024(
            this,
            "Máximo",
            suffix = "min",
            integer = true,
        )
        body.addView(
            pairFields(pickupMinDerived, pickupMinMax),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(this@Strategy021Activity, 7)
            },
        )
        body.addView(
            UiKit.margin(
                UiKit.body(
                    this,
                    "Na Busca, menor é melhor. Para preservar a regra já validada do motor, o Mínimo/faixa boa é calculado em 75% do limite Máximo.",
                    9.5f,
                ),
                top = 6,
            ),
        )

        addFormWatcher(pickupKmMax)
        addFormWatcher(pickupMinMax)
        pickupEnabled.setOnCheckedChangeListener { _, _ ->
            if (!loadingForm) markCustomAndPreview()
        }

        content.addView(
            UiKit.margin(
                expandableCard(
                    "Outras métricas",
                    "Lucro, margem e Busca",
                    body,
                    initiallyOpen = false,
                ),
                top = 12,
            ),
        )
    }

    private fun buildCosts() {
        val cost = repo.costSnapshot()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                UiKit.body(
                    this@Strategy021Activity,
                    "Custo atual estimado: R$ ${HudConfigRules024.format(cost.costPerKm)} /km",
                    12f,
                ),
            )
            addView(
                UiKit.margin(
                    UiKit.body(
                        this@Strategy021Activity,
                        "Fonte: ${cost.source} · versão ${cost.version}. O cálculo continua centralizado no perfil de custos existente.",
                        9.5f,
                    ),
                    top = 4,
                ),
            )
            addView(
                UiKit.margin(
                    UiKit.secondaryButton(
                        this@Strategy021Activity,
                        "Editar combustível, energia e custos",
                    ) {
                        startActivity(
                            Intent(
                                this@Strategy021Activity,
                                CostProfileActivity::class.java,
                            ),
                        )
                    },
                    top = 8,
                ),
            )
        }

        content.addView(
            UiKit.margin(
                expandableCard(
                    "Custos e cálculo de lucro",
                    "Combustível/energia, custos mensais e memória",
                    body,
                    initiallyOpen = false,
                ),
                top = 12,
            ),
        )
    }

    private fun buildHudDisplay() {
        val s = initialSettings
        val x = Strategy021Store.load(this)
        val layout = Hud023LayoutPrefs.load(this)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(UiKit.body(this, "Tamanho do HUD", 10.5f))
        hudSizeHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        body.addView(
            hudSizeHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(this@Strategy021Activity, 5)
            },
        )

        body.addView(
            UiKit.margin(
                UiKit.body(this, "Tema do HUD", 10.5f),
                top = 10,
            ),
        )
        hudTheme = SrUi023.spinner(
            this,
            listOf("Seguir aplicativo", "Claro", "Escuro"),
        ).apply {
            setSelection(
                when (x.hudThemeMode) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                },
            )
            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        if (!loadingForm) refreshPreview()
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?,
                    ) = Unit
                }
        }
        body.addView(hudTheme)

        hudOpacityLabel = UiKit.body(this, "", 10.5f)
        body.addView(UiKit.margin(hudOpacityLabel, top = 10))
        hudOpacity = SeekBar(this).apply {
            max = 70
            progress = (s.hudOpacity - 30).coerceIn(0, 70)
            setOnSeekBarChangeListener(seekListener {
                hudOpacityLabel.text = "Opacidade do HUD: ${it + 30}%"
                if (!loadingForm) refreshPreview()
            })
        }
        body.addView(hudOpacity)

        hudFontLabel = UiKit.body(this, "", 10.5f)
        body.addView(UiKit.margin(hudFontLabel, top = 7))
        hudFont = SeekBar(this).apply {
            max = 10
            progress = (s.hudFontSize - 14).coerceIn(0, 10)
            setOnSeekBarChangeListener(seekListener {
                hudFontLabel.text = "Texto do HUD: ${it + 14} sp"
                if (!loadingForm) refreshPreview()
            })
        }
        body.addView(hudFont)

        colorBlind = hudCheck(
            "Modo de cores acessíveis",
            s.colorBlindMode,
        )
        dismissOnTap = hudCheck(
            "Ocultar HUD ao tocar",
            s.hudDismissOnTap,
        )
        dragHud = hudCheck(
            "Permitir arrastar HUD",
            s.hudDragEnabled,
        )
        showFare = hudCheck(
            "Mostrar valor integral da oferta",
            layout.showFare,
        )
        showDistance = hudCheck(
            "Mostrar distância total",
            layout.showDistance,
        )
        showTotalTime = hudCheck(
            "Mostrar tempo total",
            layout.showTotalTime,
        )
        listOf(
            colorBlind,
            dismissOnTap,
            dragHud,
            showFare,
            showDistance,
            showTotalTime,
        ).forEach(body::addView)

        content.addView(
            UiKit.margin(
                expandableCard(
                    "Exibição do HUD",
                    "Tamanho, tema, opacidade e detalhes",
                    body,
                    initiallyOpen = false,
                ),
                top = 12,
            ),
        )

        renderHudSizeButtons()
        hudOpacityLabel.text = "Opacidade do HUD: ${s.hudOpacity}%"
        hudFontLabel.text = "Texto do HUD: ${s.hudFontSize} sp"
    }

    private fun buildFloatingWindow() {
        val prefs = JourneyUiPreferences(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        bubbleEnabled = hudCheck(
            "Exibir janela flutuante durante a jornada",
            prefs.enabled(),
        )
        body.addView(bubbleEnabled)
        body.addView(
            UiKit.margin(
                UiKit.body(
                    this,
                    "Desativar a janela não encerra a jornada nem interrompe o OCR.",
                    9.5f,
                ),
                top = 2,
            ),
        )

        body.addView(
            UiKit.margin(
                UiKit.body(this, "Quantidade de ofertas no painel", 10.5f),
                top = 9,
            ),
        )
        bubbleOfferCount = SrUi023.spinner(
            this,
            listOf("1 oferta", "2 ofertas", "3 ofertas", "4 ofertas", "5 ofertas"),
        ).apply {
            setSelection((prefs.offerCount() - 1).coerceIn(0, 4))
        }
        body.addView(bubbleOfferCount)

        body.addView(
            UiKit.margin(
                UiKit.body(this, "Tamanho do texto da janela", 10.5f),
                top = 8,
            ),
        )
        bubbleTextSize = SrUi023.spinner(
            this,
            listOf("Pequeno", "Padrão", "Grande"),
        ).apply {
            setSelection(
                when (prefs.textSize()) {
                    "small" -> 0
                    "large" -> 2
                    else -> 1
                },
            )
        }
        body.addView(bubbleTextSize)

        bubbleSizeLabel = UiKit.body(this, "", 10.5f)
        body.addView(UiKit.margin(bubbleSizeLabel, top = 9))
        bubbleSize = SeekBar(this).apply {
            max = 30
            progress = (prefs.sizeDp() - 46).coerceIn(0, 30)
            setOnSeekBarChangeListener(seekListener {
                bubbleSizeLabel.text = "Botão flutuante: ${it + 46} dp"
            })
        }
        body.addView(bubbleSize)

        bubbleOpacityLabel = UiKit.body(this, "", 10.5f)
        body.addView(UiKit.margin(bubbleOpacityLabel, top = 7))
        bubbleOpacity = SeekBar(this).apply {
            max = 40
            progress = (prefs.opacityPercent() - 60).coerceIn(0, 40)
            setOnSeekBarChangeListener(seekListener {
                bubbleOpacityLabel.text = "Opacidade da janela: ${it + 60}%"
            })
        }
        body.addView(bubbleOpacity)

        body.addView(
            UiKit.margin(
                UiKit.secondaryButton(
                    this,
                    "Restaurar posição da janela",
                ) {
                    JourneyBubbleController.restorePosition(this)
                    Toast.makeText(
                        this,
                        "Posição da janela restaurada.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                top = 8,
            ),
        )
        body.addView(
            UiKit.margin(
                UiKit.body(
                    this,
                    "A posição é validada novamente em rotação e tela dividida para evitar que o botão fique fora da área visível.",
                    9.5f,
                ),
                top = 6,
            ),
        )

        content.addView(
            UiKit.margin(
                expandableCard(
                    "Janela flutuante",
                    "Ativação, ofertas, texto, tamanho, opacidade e posição",
                    body,
                    initiallyOpen = false,
                ),
                top = 12,
            ),
        )

        bubbleSizeLabel.text = "Botão flutuante: ${prefs.sizeDp()} dp"
        bubbleOpacityLabel.text =
            "Opacidade da janela: ${prefs.opacityPercent()}%"
    }

    private fun buildQuickMessages() {
        val current = MessagePresetStore023.load(this)
        val base = MessagePresetStore023.base(this)
        messageUseSynced = !MessagePresetStore023.hasLocalOverride(this)
        val start = MessagePresetEditorRules024.sixSlots(
            if (current.isNotEmpty()) current else base,
            initialSettings.defaultPassengerMessage,
        )

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(
            UiKit.body(
                this,
                "Edite os 6 atalhos usados no trilho da janela. O Sr. Rotas apenas copia a mensagem para a área de transferência; não envia automaticamente no Uber/99.",
                10.5f,
            ),
        )

        start.forEachIndexed { index, item ->
            val enabled = CheckBox(this).apply {
                text = "${index + 1}"
                isChecked = item.enabled
                setTextColor(UiKit.palette(this@Strategy021Activity).ink)
                setOnCheckedChangeListener { _, _ ->
                    if (!loadingForm) messageUseSynced = false
                }
            }
            val input = EditText(this).apply {
                setText(item.text)
                textSize = 12f
                setTextColor(UiKit.palette(this@Strategy021Activity).ink)
                setHintTextColor(UiKit.palette(this@Strategy021Activity).muted)
                minLines = 2
                maxLines = 4
                background = UiKit.rounded(
                    this@Strategy021Activity,
                    UiKit.palette(this@Strategy021Activity).surfaceAlt,
                    12,
                    UiKit.palette(this@Strategy021Activity).line,
                    1,
                )
                setPadding(
                    UiKit.dp(this@Strategy021Activity, 9),
                    UiKit.dp(this@Strategy021Activity, 8),
                    UiKit.dp(this@Strategy021Activity, 9),
                    UiKit.dp(this@Strategy021Activity, 8),
                )
                addTextChangedListener(
                    simpleWatcher {
                        if (!loadingForm) messageUseSynced = false
                    },
                )
            }
            messageEditors += MessageEditor(item, enabled, input)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                addView(
                    enabled,
                    LinearLayout.LayoutParams(
                        UiKit.dp(this@Strategy021Activity, 48),
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            }
            body.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = UiKit.dp(this@Strategy021Activity, 7)
                },
            )
        }

        body.addView(
            UiKit.margin(
                UiKit.secondaryButton(
                    this,
                    "Usar mensagens sincronizadas",
                ) {
                    val remote = MessagePresetStore023.base(this)
                    val slots = MessagePresetEditorRules024.sixSlots(
                        remote,
                        repo.load().defaultPassengerMessage,
                    )
                    loadMessageEditors(slots)
                    messageUseSynced = true
                    Toast.makeText(
                        this,
                        if (remote.isEmpty()) {
                            "Sem mensagens remotas: carregados os padrões locais."
                        } else {
                            "Mensagens sincronizadas carregadas."
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                top = 9,
            ),
        )

        content.addView(
            UiKit.margin(
                expandableCard(
                    "Mensagens rápidas",
                    "6 atalhos editáveis dentro do APK",
                    body,
                    initiallyOpen = false,
                ),
                top = 12,
            ),
        )
    }

    private fun addBenefitMetric(
        parent: LinearLayout,
        key: String,
        title: String,
        prefix: String,
        suffix: String,
        minimum: Double,
        target: Double,
    ) {
        val enabled = metricToggle(key, title)
        val min = HudUnitField024(
            this,
            "Mínimo",
            prefix = prefix,
            suffix = suffix,
        ).apply { setValue(minimum) }
        val max = HudUnitField024(
            this,
            "Máximo/meta",
            prefix = prefix,
            suffix = suffix,
        ).apply { setValue(target) }

        metrics[key] = MetricFields(
            key = key,
            title = title,
            enabled = enabled,
            minimum = min,
            target = max,
        )

        parent.addView(
            UiKit.margin(enabled, top = 10),
        )
        parent.addView(pairFields(min, max))

        addFormWatcher(min)
        addFormWatcher(max)
        enabled.setOnCheckedChangeListener { _, _ ->
            if (!loadingForm) markCustomAndPreview()
        }
    }

    private fun metricToggle(
        key: String,
        title: String,
    ): CheckBox =
        CheckBox(this).apply {
            text = title
            setTextColor(UiKit.palette(this@Strategy021Activity).ink)
            tag = key
        }

    private fun pairFields(
        left: View,
        right: View,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                left,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(
                right,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = UiKit.dp(this@Strategy021Activity, 7)
                },
            )
        }

    private fun expandableCard(
        title: String,
        subtitle: String,
        body: View,
        initiallyOpen: Boolean,
    ): LinearLayout {
        var open = initiallyOpen
        return UiKit.card(this).apply {
            val header = LinearLayout(this@Strategy021Activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
            }
            val labels = LinearLayout(this@Strategy021Activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    UiKit.sectionTitle(
                        this@Strategy021Activity,
                        title,
                    ),
                )
                addView(
                    UiKit.body(
                        this@Strategy021Activity,
                        subtitle,
                        9.5f,
                    ),
                )
            }
            val arrow = UiKit.title(
                this@Strategy021Activity,
                if (open) "−" else "+",
                20f,
            ).apply {
                gravity = Gravity.CENTER
            }

            header.addView(
                labels,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            header.addView(
                arrow,
                LinearLayout.LayoutParams(
                    UiKit.dp(this@Strategy021Activity, 42),
                    UiKit.dp(this@Strategy021Activity, 42),
                ),
            )
            addView(header)
            body.visibility = if (open) View.VISIBLE else View.GONE
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = UiKit.dp(this@Strategy021Activity, 7)
                },
            )

            header.setOnClickListener {
                open = !open
                body.visibility = if (open) View.VISIBLE else View.GONE
                arrow.text = if (open) "−" else "+"
            }
        }
    }

    private fun renderPresetButtons() {
        presetHost.removeAllViews()
        val options = listOf(
            "popular" to "Popular",
            "comfort" to "Conforto",
            "premium" to "Premium",
            "custom" to "Meu perfil",
        )
        options.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            pair.forEachIndexed { index, (key, label) ->
                val button =
                    if (selectedPreset == key) {
                        UiKit.primaryButton(this, "✓ $label") {
                            selectPreset(key)
                        }
                    } else {
                        UiKit.secondaryButton(this, label) {
                            selectPreset(key)
                        }
                    }
                row.addView(
                    button,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        if (index > 0) {
                            marginStart = UiKit.dp(this@Strategy021Activity, 7)
                        }
                    },
                )
            }
            presetHost.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) {
                        topMargin = UiKit.dp(this@Strategy021Activity, 7)
                    }
                },
            )
        }
    }

    private fun selectPreset(key: String) {
        if (key == "custom") {
            selectedPreset = "custom"
            loadThresholds(initialSettings, initialPickupMinutes)
        } else {
            val preset = HudConfigRules024.preset(key) ?: return
            loadingForm = true
            metrics["per_km"]?.set(
                preset.perKm.minimum,
                preset.perKm.target,
            )
            metrics["per_minute"]?.set(
                preset.perMinute.minimum,
                preset.perMinute.target,
            )
            metrics["per_hour"]?.set(
                preset.perHour.minimum,
                preset.perHour.target,
            )
            selectedPreset = key
            loadingForm = false
        }
        renderPresetButtons()
        refreshPreview()
    }

    private fun loadForm(
        settings: DriverSettings,
        pickupMinutes: Int,
    ) {
        loadingForm = true
        loadThresholds(settings, pickupMinutes)

        val enabled = settings.hudEnabledMetrics
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        metrics.values.forEach {
            it.enabled.isChecked = it.key in enabled
        }
        pickupEnabled.isChecked = "pickup" in enabled

        hudSize = Hud023Spec.normalizeSize(settings.hudCardSize)
        renderHudSizeButtons()

        hudOpacity.progress = (settings.hudOpacity - 30).coerceIn(0, 70)
        hudFont.progress = (settings.hudFontSize - 14).coerceIn(0, 10)
        colorBlind.isChecked = settings.colorBlindMode
        dismissOnTap.isChecked = settings.hudDismissOnTap
        dragHud.isChecked = settings.hudDragEnabled

        loadingForm = false
        refreshPickupDerived()
    }

    private fun loadThresholds(
        settings: DriverSettings,
        pickupMinutes: Int,
    ) {
        loadingForm = true
        metrics["per_km"]?.set(
            settings.redPerKmBelow,
            settings.minPerKm,
        )
        metrics["per_minute"]?.set(
            settings.redPerMinuteBelow,
            settings.minPerMinute,
        )
        metrics["per_hour"]?.set(
            settings.redPerHourBelow,
            settings.minPerHour,
        )
        metrics["rating"]?.set(
            settings.redRatingBelow,
            settings.goodRatingFrom,
        )
        metrics["profit_hour"]?.set(
            settings.redProfitPerHourBelow,
            settings.minProfitPerHour,
        )
        metrics["profit_percent"]?.set(
            settings.redProfitPercentBelow,
            settings.minProfitPercent,
        )
        pickupKmMax.setValue(settings.maxPickupKm)
        pickupMinMax.setIntegerValue(pickupMinutes)
        loadingForm = false
        refreshPickupDerived()
    }

    private fun refreshPickupDerived() {
        val km = pickupKmMax.valueOrNull() ?: 0.0
        val min = pickupMinMax.intValueOrNull() ?: 0
        pickupKmDerived.setValue(
            HudConfigRules024.pickupGoodBoundary(km),
        )
        pickupMinDerived.setIntegerValue(
            HudConfigRules024.pickupGoodBoundary(
                min.toDouble(),
            ).toInt(),
        )
    }

    private fun renderHudSizeButtons() {
        if (!::hudSizeHost.isInitialized) return
        hudSizeHost.removeAllViews()
        listOf(
            Hud023Spec.SIZE_COMPACT to "Compacto",
            Hud023Spec.SIZE_NORMAL to "Normal",
            Hud023Spec.SIZE_LARGE to "Grande",
        ).forEachIndexed { index, (key, label) ->
            val button =
                if (hudSize == key) {
                    UiKit.primaryButton(this, label) {
                        hudSize = key
                        renderHudSizeButtons()
                        refreshPreview()
                    }
                } else {
                    UiKit.secondaryButton(this, label) {
                        hudSize = key
                        renderHudSizeButtons()
                        refreshPreview()
                    }
                }
            hudSizeHost.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index > 0) {
                        marginStart = UiKit.dp(this@Strategy021Activity, 5)
                    }
                },
            )
        }
    }

    private fun candidateSettings(): DriverSettings {
        val s = repo.load()
        val enabled = enabledMetrics()
        return s.copy(
            redPerKmBelow =
                metrics["per_km"]?.minimum?.valueOrNull()
                    ?: s.redPerKmBelow,
            minPerKm =
                metrics["per_km"]?.target?.valueOrNull()
                    ?: s.minPerKm,
            redPerMinuteBelow =
                metrics["per_minute"]?.minimum?.valueOrNull()
                    ?: s.redPerMinuteBelow,
            minPerMinute =
                metrics["per_minute"]?.target?.valueOrNull()
                    ?: s.minPerMinute,
            redPerHourBelow =
                metrics["per_hour"]?.minimum?.valueOrNull()
                    ?: s.redPerHourBelow,
            minPerHour =
                metrics["per_hour"]?.target?.valueOrNull()
                    ?: s.minPerHour,
            redRatingBelow =
                metrics["rating"]?.minimum?.valueOrNull()
                    ?: s.redRatingBelow,
            goodRatingFrom =
                metrics["rating"]?.target?.valueOrNull()
                    ?: s.goodRatingFrom,
            redProfitPerHourBelow =
                metrics["profit_hour"]?.minimum?.valueOrNull()
                    ?: s.redProfitPerHourBelow,
            minProfitPerHour =
                metrics["profit_hour"]?.target?.valueOrNull()
                    ?: s.minProfitPerHour,
            redProfitPercentBelow =
                metrics["profit_percent"]?.minimum?.valueOrNull()
                    ?: s.redProfitPercentBelow,
            minProfitPercent =
                metrics["profit_percent"]?.target?.valueOrNull()
                    ?: s.minProfitPercent,
            maxPickupKm = pickupKmMax.valueOrNull() ?: s.maxPickupKm,
            hudEnabledMetrics = enabled.joinToString(","),
            hudMetricOrder =
                HudConfigRules024.ensureMetricOrder(
                    s.hudMetricOrder,
                    enabled,
                ),
            hudCardSize = hudSize,
            hudOpacity = hudOpacity.progress + 30,
            hudFontSize = hudFont.progress + 14,
            colorBlindMode = colorBlind.isChecked,
        )
    }

    private fun refreshPreview() {
        if (!::previewHost.isInitialized || loadingForm) return
        refreshPickupDerived()
        previewHost.removeAllViews()
        previewHost.addView(
            HudConfigPreview024.build(
                this,
                HudConfigPreview024.Model(
                    settings = candidateSettings(),
                    maxPickupMinutes =
                        pickupMinMax.intValueOrNull()
                            ?: initialPickupMinutes,
                    size = hudSize,
                ),
            ),
        )
    }

    private fun enabledMetrics(): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        metrics.values.forEach {
            if (it.enabled.isChecked) set += it.key
        }
        if (pickupEnabled.isChecked) set += "pickup"
        return set
    }

    private fun validateForm(): String? {
        val enabled = enabledMetrics()
        HudConfigRules024.validateEnabled(enabled)
            .takeIf { !it.valid }
            ?.let { return it.message }

        metrics.values.forEach { metric ->
            val validation = HudConfigRules024.validateBenefitPair(
                metric.title,
                metric.minimum.valueOrNull(),
                metric.target.valueOrNull(),
                if (metric.key == "rating") 5.0 else 100000.0,
            )
            if (!validation.valid) return validation.message
        }

        HudConfigRules024.validatePickupLimit(
            "Distância para buscar",
            pickupKmMax.valueOrNull(),
            100.0,
        ).takeIf { !it.valid }?.let { return it.message }

        HudConfigRules024.validatePickupLimit(
            "Tempo para buscar",
            pickupMinMax.intValueOrNull()?.toDouble(),
            120.0,
        ).takeIf { !it.valid }?.let { return it.message }

        return null
    }

    private fun save() {
        val error = validateForm()
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        val current = repo.load()
        val candidate = candidateSettings()
        val themeMode = when (hudTheme.selectedItemPosition) {
            1 -> "light"
            2 -> "dark"
            else -> "follow_app"
        }
        val resolvedHudTheme = when (themeMode) {
            "light" -> "light"
            "dark" -> "dark"
            else -> Strategy021Store.load(this).appTheme
        }

        repo.save(
            candidate.copy(
                hudTheme = resolvedHudTheme,
                hudDismissOnTap = dismissOnTap.isChecked,
                hudDragEnabled = dragHud.isChecked,
                collectiveStatsOptIn = current.collectiveStatsOptIn,
            ),
        )
        Strategy021Store.saveMaxPickupMinutes(
            this,
            pickupMinMax.intValueOrNull() ?: initialPickupMinutes,
        )
        Strategy021Store.saveHudThemeMode(this, themeMode)
        Strategy021Store.savePreset(this, selectedPreset)

        Hud023LayoutPrefs.save(
            this,
            Hud023LayoutPrefs.State(
                showFare = showFare.isChecked,
                showDistance = showDistance.isChecked,
                showTotalTime = showTotalTime.isChecked,
            ),
        )

        JourneyUiPreferences(this).apply {
            setEnabled(bubbleEnabled.isChecked)
            setOfferCount(bubbleOfferCount.selectedItemPosition + 1)
            setTextSize(
                when (bubbleTextSize.selectedItemPosition) {
                    0 -> "small"
                    2 -> "large"
                    else -> "standard"
                },
            )
            setSizeDp(bubbleSize.progress + 46)
            setOpacityPercent(bubbleOpacity.progress + 60)
        }

        val messageItems = messageEditors.mapIndexed { index, editor ->
            editor.original.copy(
                order = index,
                shortLabel = (index + 1).toString(),
                accessibilityLabel =
                    "Mensagem rápida ${index + 1}",
                text = editor.input.text.toString().trim().take(500),
                colorToken = MessageShortcutRules023.colorFor(index),
                enabled = editor.enabled.isChecked,
            )
        }
        if (messageUseSynced) {
            MessagePresetStore023.clearLocal(this)
        } else {
            MessagePresetStore023.saveLocal(this, messageItems)
        }

        BackendClient.syncPreferences(this)
        Preference021Sync.sync(this)
        JourneyBubbleController.refresh(this)

        initialSettings = repo.load()
        initialPickupMinutes =
            Strategy021Store.load(this).maxPickupMinutes

        Toast.makeText(
            this,
            "Configuração do HUD salva.",
            Toast.LENGTH_SHORT,
        ).show()
        refreshPreview()
    }

    private fun loadMessageEditors(items: List<MessageShortcut023>) {
        loadingForm = true
        items.take(messageEditors.size).forEachIndexed { index, item ->
            messageEditors[index].enabled.isChecked = item.enabled
            messageEditors[index].input.setText(item.text)
            messageEditors[index] =
                messageEditors[index].copy(original = item)
        }
        loadingForm = false
    }

    private fun addFormWatcher(field: HudUnitField024) {
        field.addWatcher(
            simpleWatcher {
                if (!loadingForm) {
                    markCustomAndPreview()
                }
            },
        )
    }

    private fun markCustomAndPreview() {
        if (loadingForm) return
        if (selectedPreset != "custom") {
            selectedPreset = "custom"
            renderPresetButtons()
        }
        refreshPreview()
    }

    private fun simpleWatcher(action: () -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = action()

            override fun afterTextChanged(s: Editable?) = Unit
        }

    private fun seekListener(
        onProgress: (Int) -> Unit,
    ): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) = onProgress(progress)

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun hudCheck(
        label: String,
        checked: Boolean,
    ): CheckBox =
        CheckBox(this).apply {
            text = label
            isChecked = checked
            setTextColor(UiKit.palette(this@Strategy021Activity).ink)
            setOnCheckedChangeListener { _, _ ->
                if (!loadingForm) refreshPreview()
            }
        }

    private data class MetricFields(
        val key: String,
        val title: String,
        val enabled: CheckBox,
        val minimum: HudUnitField024,
        val target: HudUnitField024,
    ) {
        fun set(min: Double, max: Double) {
            minimum.setValue(min)
            target.setValue(max)
        }
    }

    private data class MessageEditor(
        val original: MessageShortcut023,
        val enabled: CheckBox,
        val input: EditText,
    )
}
