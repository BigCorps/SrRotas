package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

/** Configurações locais do aparelho; conta/assinatura permanecem na Web. */
class SettingsHub023(
    context: Context,
    private val actions: Actions = Actions(),
) : ScrollView(context) {
    data class Actions(
        val journey: () -> Unit = {},
        val strategy: () -> Unit = {},
        val appearance: () -> Unit = {},
        val notifications: () -> Unit = {},
        val sync: () -> Unit = {},
        val privacy: () -> Unit = {},
        val demo: () -> Unit = {},
    )

    private val root = LinearLayout(context)
    private val statusHost = LinearLayout(context)
    private val grid = LinearLayout(context)

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
                "Configurações",
                "Ajustes do aparelho, jornada, HUD e aparência.",
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
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(
            0,
            SrUi023.dp(context, 8),
            0,
            SrUi023.dp(context, 28),
        )
        root.addView(
            grid,
            LinearLayout.LayoutParams(
                SrUi023.maxContentWidthPx(context),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        refresh()
    }

    fun refresh() {
        renderStatus()
        renderGrid()
    }

    private fun renderStatus() {
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

        val state = SettingsStatusRules024.evaluate(
            SettingsStatusRules024.Input(
                overlayOk = overlayOk,
                locationOk = locationOk,
                captureOk = captureOk,
                ocrEnabled = s.ocrEnabled,
                onboardingCompleted = s.onboardingCompleted,
                journeyActive = active,
            ),
        )

        val p = SrUi023.palette(context)
        val tone = when (state.level) {
            SettingsStatusRules024.Level.GREEN -> p.teal
            SettingsStatusRules024.Level.YELLOW -> p.orange
            SettingsStatusRules024.Level.RED -> p.red
        }

        val statusCard = StatusVisual0242.card(
            context,
            when (state.level) {
                SettingsStatusRules024.Level.GREEN -> "good"
                SettingsStatusRules024.Level.YELLOW -> "warn"
                SettingsStatusRules024.Level.RED -> "bad"
            },
            14,
        ).apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val mascot = ImageView(context).apply {
                setImageResource(R.drawable.sr23_mascot_ai_transparent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = state.title
            }
            row.addView(
                mascot,
                LinearLayout.LayoutParams(
                    SrUi023.dp(context, 54),
                    SrUi023.dp(context, 54),
                ),
            )

            row.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        SrUi023.dp(context, 10),
                        0,
                        SrUi023.dp(context, 6),
                        0,
                    )
                    addView(
                        SrUi023.title(
                            context,
                            state.title,
                            16f,
                        ),
                    )
                    addView(
                        SrUi023.body(
                            context,
                            if (state.missing.isEmpty()) {
                                "${if (active) "Jornada ativa" else "Jornada parada"} · permissões e OCR OK"
                            } else {
                                "${if (active) "Jornada ativa" else "Jornada parada"} · ${state.missing.size} ajuste(s)"
                            },
                            10.5f,
                        ),
                    )
                    addView(
                        trafficLights(state.level),
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = SrUi023.dp(context, 4)
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            row.addView(
                TextView(context).apply {
                    text = "?"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(tone)
                    minWidth = SrUi023.dp(context, 44)
                    minHeight = SrUi023.dp(context, 44)
                    background = SrUi023.rounded(
                        p.surfaceMuted,
                        999,
                        tone,
                        1,
                        context,
                    )
                    contentDescription = "Entender status do Sr. Rotas"
                    setOnClickListener {
                        showStatusHelp(state)
                    }
                },
            )

            addView(row)
            addView(
                themeSelector(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 12)
                },
            )
        }

        statusHost.addView(
            statusCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun trafficLights(
        level: SettingsStatusRules024.Level,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(
                SettingsStatusRules024.Level.GREEN to SrUi023.palette(context).teal,
                SettingsStatusRules024.Level.YELLOW to SrUi023.palette(context).orange,
                SettingsStatusRules024.Level.RED to SrUi023.palette(context).red,
            ).forEach { (item, color) ->
                addView(
                    TextView(context).apply {
                        text = "●"
                        textSize = 15f
                        setTextColor(
                            if (item == level) color
                            else SrUi023.palette(context).outline
                        )
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = SrUi023.dp(context, 4)
                    },
                )
            }
        }

    private fun showStatusHelp(
        state: SettingsStatusRules024.Result,
    ) {
        val message =
            if (state.missing.isEmpty()) {
                "Tudo pronto para usar o Sr. Rotas. O semáforo fica verde quando permissões, OCR e configuração essencial estão disponíveis."
            } else {
                buildString {
                    append("Revise:\n\n")
                    state.missing.forEach { append("• $it\n") }
                    append("\nAmarelo indica um ajuste isolado. Vermelho indica duas ou mais pendências ou uma combinação crítica.")
                }
            }
        AlertDialog.Builder(context)
            .setTitle("Status do aplicativo")
            .setMessage(message)
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun themeSelector(): View {
        val active = Strategy021Store.load(context).appTheme
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = SrUi023.rounded(
                SrUi023.palette(context).surfaceMuted,
                13,
                SrUi023.palette(context).outline,
                1,
                context,
            )
            listOf(
                "auto" to "Automático",
                "light" to "Claro",
                "dark" to "Escuro",
            ).forEach { (key, label) ->
                addView(
                    SrUi023.segment(
                        context,
                        label,
                        active == key,
                    ) {
                        Strategy021Store.saveAppTheme(context, key)
                        if (
                            Strategy021Store.load(context).hudThemeMode ==
                            "follow_app"
                        ) {
                            val repo = SettingsRepository(context)
                            repo.save(repo.load().copy(hudTheme = key))
                        }
                        Preference021Sync.sync(context)
                        (context as? Activity)?.recreate()
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            }
        }
    }

    private fun renderGrid() {
        grid.removeAllViews()
        val p = SrUi023.palette(context)
        val sync = SyncCoordinator.pending(context)
        val columns = SrUi023.preferredColumns(context)
        val tiles = listOf(
            Tile(
                "Jornada e permissões",
                "Status, acessos e última jornada",
                R.drawable.sr23_ic_route,
                p.teal,
                null,
                actions.journey,
            ),
            Tile(
                "Configuração do HUD",
                "Métricas, prévia, janela e mensagens rápidas",
                R.drawable.sr23_ic_sliders,
                p.blue,
                null,
                actions.strategy,
            ),
            Tile(
                "Aparência",
                "Tema Claro, Escuro ou Automático",
                R.drawable.sr23_ic_sun_moon,
                p.orange,
                null,
                actions.appearance,
            ),
            Tile(
                "Notificações",
                "Alertas, resumos e avisos",
                R.drawable.sr23_ic_bell,
                p.red,
                null,
                actions.notifications,
            ),
            Tile(
                "Dados e sincronização",
                "Backup e sincronização",
                R.drawable.sr23_ic_cloud_sync,
                p.teal,
                if (sync.total == 0) {
                    "Sincronizado"
                } else {
                    "${sync.total} pendente(s)"
                },
                actions.sync,
            ),
            Tile(
                "Privacidade e suporte",
                "Privacidade, termos e ajuda",
                R.drawable.sr23_ic_shield_help,
                p.navy,
                null,
                actions.privacy,
            ),
            Tile(
                "Modo Demonstração",
                "Dados fictícios para screenshots e regressão visual",
                R.drawable.sr23_ic_info,
                p.blue,
                "LOCAL",
                actions.demo,
            ),
        )

        tiles.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            rowTiles.forEachIndexed { columnIndex, tile ->
                row.addView(
                    settingsTile(tile),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        if (columnIndex > 0) {
                            marginStart = SrUi023.dp(context, 8)
                        }
                    },
                )
            }

            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) {
                        topMargin = SrUi023.dp(context, 8)
                    }
                },
            )
        }
    }

    private fun settingsTile(tile: Tile): View =
        SrUi023.card(context, 14, 18).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = SrUi023.dp(context, 150)
            isClickable = true
            isFocusable = true
            setOnClickListener { tile.action() }

            addView(
                SrUi023.iconBox(
                    context,
                    tile.icon,
                    tile.tone,
                    52,
                ),
                LinearLayout.LayoutParams(
                    SrUi023.dp(context, 52),
                    SrUi023.dp(context, 52),
                ),
            )
            addView(
                SrUi023.title(
                    context,
                    tile.title,
                    14.5f,
                ).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 9)
                },
            )
            addView(
                SrUi023.body(
                    context,
                    tile.subtitle,
                    10.5f,
                ).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = SrUi023.dp(context, 4)
                },
            )
            tile.badge?.let { badge ->
                addView(
                    SrUi023.body(
                        context,
                        badge,
                        9.5f,
                    ).apply {
                        gravity = Gravity.CENTER
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setTextColor(tile.tone)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = SrUi023.dp(context, 5)
                    },
                )
            }
        }

    private data class Tile(
        val title: String,
        val subtitle: String,
        val icon: Int,
        val tone: Int,
        val badge: String?,
        val action: () -> Unit,
    )
}
