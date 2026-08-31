package com.srrotas.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

/**
 * Agora 0.24: Base Pessoal e Base Coletiva ficam visualmente inequívocas.
 *
 * A fonte continua sendo resolvida por RegionalClient/RegionalSourceRules024.
 * Esta classe só apresenta a fonte que realmente foi selecionada pelo motor.
 */
class NowPanel023(context: Context) : ScrollView(context) {
    private var mode = "now"
    private var source = "personal"
    private var profileKey =
        when (Strategy021Store.load(context).strategyPreset) {
            "popular" -> "popular"
            "comfort" -> "comfort"
            "premium" -> "premium"
            else -> "all"
        }

    private val root = LinearLayout(context)
    private val statusHost = LinearLayout(context)
    private val modeBox = LinearLayout(context)
    private val sourceBox = LinearLayout(context)
    private val profileBox = LinearLayout(context)
    private val results = LinearLayout(context)
    private val status = SrUi023.body(context, "", 11f)
    private val region: EditText =
        UiKit.input(context, "Bairro ou região")

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
                "Agora",
                "Inteligência de região e controle da sua jornada.",
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(
            statusHost,
            LinearLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 12)
            },
        )
        refreshJourneyState()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                0,
                0,
                0,
                SrUi023.dp(context, 28),
            )
        }
        root.addView(
            content,
            LinearLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        modeBox.orientation = LinearLayout.HORIZONTAL
        modeBox.background = SrUi023.rounded(
            SrUi023.palette(context).surface,
            13,
            SrUi023.palette(context).outline,
            1,
            context,
        )
        content.addView(
            modeBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )

        sourceBox.orientation = LinearLayout.HORIZONTAL
        sourceBox.background = SrUi023.rounded(
            SrUi023.palette(context).surface,
            13,
            SrUi023.palette(context).outline,
            1,
            context,
        )
        content.addView(
            sourceBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 8)
            },
        )
        renderSegments()

        val search = SrUi023.card(context, 14, 17).apply {
            addView(SrUi023.title(context, "Pesquisa", 14f))
            addView(
                region,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                },
            )
            addView(
                SrUi023.body(
                    context,
                    "Perfil de serviço",
                    10f,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                },
            )
            profileBox.orientation = LinearLayout.VERTICAL
            addView(
                profileBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 4)
                },
            )
            renderProfileChips()

            addView(
                SrUi023.primaryButton(
                    context,
                    "Consultar",
                    R.drawable.sr23_ic_search,
                ) { refresh() },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                },
            )
        }
        content.addView(
            search,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 11)
            },
        )
        content.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(context, 10)
            },
        )
        results.orientation = LinearLayout.VERTICAL
        content.addView(results)
        refresh()
    }

    fun refreshJourneyState() {
        statusHost.removeAllViews()
        val repo = SettingsRepository(context)
        val s = repo.load()
        val active = repo.currentJourneyId().isNotBlank()
        val overlayOk = Settings.canDrawOverlays(context)
        val locationOk =
            context.checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val captureOk = !active || repo.isProjectionActive()
        val allOk =
            overlayOk &&
                locationOk &&
                captureOk &&
                s.ocrEnabled &&
                s.onboardingCompleted
        val pending = listOf(
            overlayOk,
            locationOk,
            captureOk,
            s.ocrEnabled,
            s.onboardingCompleted,
        ).count { !it }

        val card = SrUi023.card(context, 14, 18).apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                SrUi023.iconBox(
                    context,
                    if (allOk) {
                        R.drawable.sr23_ic_check_square
                    } else {
                        R.drawable.sr23_ic_alert
                    },
                    if (allOk) {
                        SrUi023.palette(context).teal
                    } else {
                        SrUi023.palette(context).orange
                    },
                    48,
                ),
            )
            row.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        SrUi023.dp(context, 10),
                        0,
                        SrUi023.dp(context, 8),
                        0,
                    )
                    addView(
                        SrUi023.title(
                            context,
                            if (allOk) "Tudo pronto" else "Ação necessária",
                            16f,
                        ),
                    )
                    addView(
                        SrUi023.body(
                            context,
                            buildString {
                                append("HUD ${if (overlayOk) "OK" else "pendente"} · ")
                                append("Localização ${if (locationOk) "OK" else "pendente"} · ")
                                append(
                                    "Captura/OCR ${
                                        if (captureOk && s.ocrEnabled) "OK"
                                        else "pendente"
                                    }",
                                )
                            },
                            10f,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            row.addView(
                SrUi023.pill(
                    context,
                    if (allOk) "OK" else "$pending pend.",
                    if (allOk) "good" else "warn",
                ),
            )
            addView(row)
            addView(
                SrUi023.primaryButton(
                    context,
                    if (active) "Encerrar jornada" else "Iniciar jornada",
                    if (active) {
                        R.drawable.sr23_float_stop
                    } else {
                        R.drawable.sr23_float_play
                    },
                ) {
                    (context as? MainActivity)?.toggleJourneyFromNow()
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 10)
                },
            )
        }
        statusHost.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun refresh() {
        refreshJourneyState()
        status.text = "Consultando inteligência regional…"
        RegionalClient.fetch(
            context,
            mode,
            source,
            region.text.toString(),
            profileKey,
        ) {
            it.onSuccess(::render)
                .onFailure { error ->
                    status.text =
                        "Não foi possível consultar: ${error.message}"
                    results.removeAllViews()
                }
        }
    }

    private fun renderSegments() {
        modeBox.removeAllViews()
        listOf(
            "now" to "Momento",
            "today" to "Hoje",
            "week" to "Semana",
            "search" to "Pesquisa",
        ).forEach { (key, label) ->
            modeBox.addView(
                SrUi023.segment(
                    context,
                    label,
                    mode == key,
                ) {
                    mode = key
                    renderSegments()
                    refresh()
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
        }

        sourceBox.removeAllViews()
        listOf(
            "personal" to "Base pessoal",
            "collective" to "Base coletiva",
        ).forEach { (key, label) ->
            sourceBox.addView(
                SrUi023.segment(
                    context,
                    label,
                    source == key,
                ) {
                    source = key
                    renderSegments()
                    refresh()
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
        }
    }

    private fun renderProfileChips() {
        profileBox.removeAllViews()
        val options = listOf(
            "all" to "Todas",
            "popular" to "Popular",
            "comfort" to "Conforto",
            "premium" to "Premium",
        )
        options.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background =
                    if (rowIndex == 0) {
                        SrUi023.rounded(
                            SrUi023.palette(context).surfaceMuted,
                            12,
                            SrUi023.palette(context).outline,
                            1,
                            context,
                        )
                    } else {
                        null
                    }
            }
            pair.forEachIndexed { index, (key, label) ->
                row.addView(
                    SrUi023.segment(
                        context,
                        label,
                        profileKey == key,
                    ) {
                        profileKey = key
                        renderProfileChips()
                        refresh()
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        if (index > 0) {
                            marginStart = SrUi023.dp(context, 4)
                        }
                    },
                )
            }
            profileBox.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) {
                        topMargin = SrUi023.dp(context, 4)
                    }
                },
            )
        }
    }

    private fun render(found: RegionalClient.Result) {
        results.removeAllViews()
        val collectiveSelected = source == "collective"
        val locked =
            collectiveSelected &&
                (
                    !found.collectiveOptIn ||
                        found.resolvedSource == "collective_locked_preview"
                    )

        status.text = when {
            locked ->
                "Prévia da Base Coletiva · participe para liberar os dados completos."
            collectiveSelected && found.tips.isEmpty() ->
                "Base Coletiva ativa, mas ainda sem amostras suficientes para esta combinação."
            found.tips.isEmpty() ->
                "Dados insuficientes para esta combinação."
            else ->
                "${found.tips.size} regiões em destaque · ${
                    sourceLabel(found.resolvedSource)
                }"
        }

        if (locked) {
            results.addView(
                collectiveExplanationCard(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 10)
                },
            )
        }

        found.tips.take(if (locked) 3 else 12).forEach { tip ->
            results.addView(
                regionCard(
                    tip,
                    collective = collectiveSelected,
                    locked = locked,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 10)
                },
            )
        }

        if (found.tips.isNotEmpty() && !locked) {
            results.addView(
                SrUi023.softCard(context, "neutral", 12).apply {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                    }
                    row.addView(
                        SrUi023.icon(
                            context,
                            R.drawable.sr23_ic_info,
                            if (collectiveSelected) {
                                SrUi023.palette(context).purple
                            } else {
                                SrUi023.palette(context).teal
                            },
                            18,
                        ),
                    )
                    row.addView(
                        SrUi023.body(
                            context,
                            found.note,
                            10.5f,
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            marginStart = SrUi023.dp(context, 8)
                        },
                    )
                    addView(row)
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

    private fun collectiveExplanationCard(): View =
        collectiveFrame(
            SrUi023.card(context, 14, 16).apply {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    SrUi023.pill(
                        context,
                        "BASE COLETIVA",
                        "good",
                    ),
                )
                row.addView(
                    SrUi023.body(
                        context,
                        "  Prévia protegida",
                        10f,
                    ),
                )
                addView(row)
                addView(
                    SrUi023.body(
                        context,
                        "A Base Coletiva reúne amostras agregadas de participantes para ampliar a leitura de região, horário e desempenho. OCR bruto, screenshots e endereços textuais não são exibidos como dados coletivos.",
                        10.5f,
                    ),
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
                        "Como funciona",
                    ) {
                        showCollectiveInfo()
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 8)
                    },
                )
            },
        )

    private fun showCollectiveInfo() {
        AlertDialog.Builder(context)
            .setTitle("Como funciona a Base Coletiva")
            .setMessage(
                "Ao participar, suas estatísticas elegíveis entram de forma agregada na inteligência coletiva e você passa a consultar uma base mais ampla do Sr. Rotas. A Base Coletiva engloba também seus próprios dados elegíveis; quando ela está selecionada, os cards do Agora usam essa fonte mais ampla em vez de misturar uma segunda Base Pessoal isolada. A participação pode ser desativada e, no modelo atual, não cria uma cobrança adicional.",
            )
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun regionCard(
        t: RegionalClient.Tip,
        collective: Boolean,
        locked: Boolean,
    ): View {
        val card = SrUi023.card(context, 14, 18).apply {
            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                SrUi023.iconBox(
                    context,
                    R.drawable.sr23_ic_location,
                    if (collective) {
                        SrUi023.palette(context).purple
                    } else {
                        SrUi023.palette(context).teal
                    },
                    42,
                ),
            )
            top.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        SrUi023.title(
                            context,
                            t.region,
                            16f,
                        ),
                    )
                    addView(
                        SrUi023.body(
                            context,
                            if (locked) {
                                "${profileName(t.profile)} · ••• amostras"
                            } else {
                                "${profileName(t.profile)} · ${t.samples} amostras"
                            },
                            9.5f,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = SrUi023.dp(context, 8)
                },
            )
            if (collective) {
                top.addView(
                    SrUi023.pill(
                        context,
                        if (locked) {
                            "PRÉVIA · BASE COLETIVA"
                        } else {
                            "BASE COLETIVA"
                        },
                        if (locked) "warn" else "good",
                    ),
                )
            } else {
                t.distanceKm?.let {
                    top.addView(
                        SrUi023.pill(
                            context,
                            "${fmt(it)} km",
                            "good",
                        ),
                    )
                }
            }
            addView(top)

            addView(
                SrUi023.body(
                    context,
                    if (locked) {
                        "Dados completos disponíveis ao participar da Base Coletiva."
                    } else {
                        t.wording
                    },
                    10.5f,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                },
            )

            val metrics = LinearLayout(context).apply {
                orientation =
                    if (context.resources.configuration.screenWidthDp < 360) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
            }
            val metricItems =
                if (locked) {
                    listOf(
                        metric("R$/km", "•••", "•••–•••", "neutral"),
                        metric("R$/h", "•••", "•••–•••", "neutral"),
                        metric("Busca", "•••", "•••", "neutral"),
                    )
                } else {
                    listOf(
                        metric(
                            "R$/km",
                            t.medianPerKm?.let(::fmt) ?: "—",
                            range(t.p25PerKm, t.p75PerKm),
                            "good",
                        ),
                        metric(
                            "R$/h",
                            t.medianPerHour?.let(::fmt) ?: "—",
                            range(t.p25PerHour, t.p75PerHour),
                            "warn",
                        ),
                        metric(
                            "Busca",
                            t.pickupMinutes?.let { "${fmt(it)} min" } ?: "—",
                            t.pickupKm?.let { "${fmt(it)} km" } ?: "histórico",
                            "bad",
                        ),
                    )
                }

            metricItems.forEachIndexed { index, item ->
                val lp =
                    if (metrics.orientation == LinearLayout.HORIZONTAL) {
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    } else {
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }
                if (index > 0) {
                    if (metrics.orientation == LinearLayout.HORIZONTAL) {
                        lp.marginStart = SrUi023.dp(context, 6)
                    } else {
                        lp.topMargin = SrUi023.dp(context, 6)
                    }
                }
                metrics.addView(item, lp)
            }
            addView(
                metrics,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 9)
                },
            )

            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            footer.addView(
                SrUi023.icon(
                    context,
                    R.drawable.sr23_ic_database,
                    SrUi023.palette(context).muted,
                    15,
                ),
            )
            footer.addView(
                SrUi023.body(
                    context,
                    when {
                        locked -> "Base Coletiva: dados protegidos"
                        collective -> "Base Coletiva: ${t.samples} rotas"
                        else -> "Base pessoal: ${t.samples} rotas"
                    },
                    9f,
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = SrUi023.dp(context, 5)
                },
            )
            footer.addView(
                SrUi023.icon(
                    context,
                    R.drawable.sr23_ic_clock,
                    SrUi023.palette(context).muted,
                    15,
                ),
            )
            footer.addView(
                SrUi023.body(
                    context,
                    if (locked) "•••" else TimeWindow0212.label(t.hourBucket),
                    9f,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = SrUi023.dp(context, 4)
                },
            )
            addView(
                footer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 8)
                },
            )
        }

        return if (collective) {
            collectiveFrame(card)
        } else {
            card
        }
    }

    private fun collectiveFrame(
        child: View,
    ): View =
        FrameLayout(context).apply {
            val dark = Appearance021.isDark(context)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                SrTheme024.collectiveGradientStops(dark),
            ).apply {
                cornerRadius = SrUi023.dp(context, 17).toFloat()
            }
            setPadding(
                SrUi023.dp(context, 2),
                SrUi023.dp(context, 2),
                SrUi023.dp(context, 2),
                SrUi023.dp(context, 2),
            )
            addView(
                child,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

    private fun metric(
        label: String,
        value: String,
        help: String,
        tone: String,
    ) =
        SrUi023.softCard(context, tone, 9).apply {
            addView(SrUi023.body(context, label, 9f))
            addView(SrUi023.title(context, value, 17f))
            addView(SrUi023.body(context, help, 8f))
        }

    private fun range(
        a: Double?,
        b: Double?,
    ) =
        if (a != null && b != null) {
            "${fmt(a)}–${fmt(b)}"
        } else {
            "mediana"
        }

    private fun fmt(v: Double) =
        String.format(Locale("pt", "BR"), "%.2f", v)

    private fun profileName(v: String) =
        when (v) {
            "popular" -> "Popular"
            "comfort" -> "Conforto"
            "premium" -> "Premium"
            else -> "Todas"
        }

    private fun sourceLabel(v: String) =
        when {
            v == "personal" -> "Base pessoal"
            v == "collective" -> "Base Coletiva"
            v == "collective_locked_preview" -> "prévia da Base Coletiva"
            else -> "Base Sr. Rotas"
        }
}
