package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.WeakHashMap

/**
 * Consolidação visual/funcional 0.27.
 *
 * Deliberadamente não toca MediaProjection/OCR/parser da alpha2, já que a
 * rodada de campo mostrou melhora. Este polish fecha os pontos secundários.
 */
object ReleasePolish0270 {
    private val attached =
        WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) =
                    attach(activity)

                override fun onActivityPaused(activity: Activity) =
                    detach(activity)

                override fun onActivityDestroyed(activity: Activity) =
                    detach(activity)

                override fun onActivityCreated(
                    activity: Activity,
                    state: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit
            },
        )
    }

    private fun attach(activity: Activity) {
        if (attached.containsKey(activity)) return
        val root = activity.window.decorView
        val listener =
            ViewTreeObserver.OnGlobalLayoutListener {
                runCatching {
                    decorate(activity, root)
                }.onFailure {
                    LocalLog.append(
                        activity,
                        "Polish 0.27 ignorou ajuste: ${it.message}",
                    )
                }
            }
        attached[activity] = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.post { runCatching { decorate(activity, root) } }
    }

    private fun detach(activity: Activity) {
        val listener = attached.remove(activity) ?: return
        val root = activity.window?.decorView ?: return
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    private fun decorate(
        activity: Activity,
        root: View,
    ) {
        if (activity !is MainActivity) return

        (findFirst(root) { it is SettingsHub023 } as? SettingsHub023)
            ?.let(::decorateSettings)

        (findFirst(root) { it is HistoryPanel } as? HistoryPanel)
            ?.let(::decorateJourneys)
    }

    private fun decorateSettings(settings: SettingsHub023) {
        val grid =
            getPrivate<LinearLayout>(settings, "grid")
                ?: return

        val existing =
            findFirst(grid) {
                it.contentDescription ==
                    "sr0270_compact_bubble_tile"
            } as? LinearLayout

        if (existing != null) {
            updateCompactTile(existing, settings)
            return
        }

        val p = SrUi023.palette(settings.context)
        val card =
            SrUi023.card(
                settings.context,
                10,
                16,
            ).apply {
                contentDescription = "sr0270_compact_bubble_tile"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true

                addView(
                    SrUi023.iconBox(
                        context,
                        R.drawable.sr23_ic_route,
                        p.teal,
                        38,
                    ),
                )

                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            SrUi023.dp(context, 10),
                            0,
                            0,
                            0,
                        )
                        addView(
                            SrUi023.title(
                                context,
                                "Janela flutuante",
                                13f,
                            ),
                        )
                        addView(
                            SrUi023.body(
                                context,
                                "",
                                9.5f,
                            ).apply {
                                contentDescription =
                                    "sr0270_compact_bubble_status"
                            },
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )

                setOnClickListener {
                    showCompactSetting(settings)
                }
            }

        updateCompactTile(card, settings)

        grid.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(settings.context, 8)
                bottomMargin = SrUi023.dp(settings.context, 4)
            },
        )
    }

    private fun updateCompactTile(
        card: LinearLayout,
        settings: SettingsHub023,
    ) {
        val status =
            findFirst(card) {
                it.contentDescription ==
                    "sr0270_compact_bubble_status"
            } as? TextView ?: return

        status.text =
            if (
                JourneyUiPreferences(settings.context)
                    .compactPanel()
            ) {
                "Compacta · menos espaço sem esconder informações"
            } else {
                "Padrão"
            }
    }

    private fun showCompactSetting(settings: SettingsHub023) {
        val context = settings.context
        val prefs = JourneyUiPreferences(context)
        val values =
            arrayOf(
                "Compacta",
                "Padrão",
            )
        val selected = if (prefs.compactPanel()) 0 else 1

        AlertDialog.Builder(context)
            .setTitle("Janela flutuante")
            .setSingleChoiceItems(
                values,
                selected,
            ) { dialog, which ->
                prefs.setCompactPanel(which == 0)
                JourneyBubbleController.refresh(context)
                dialog.dismiss()
                settings.refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun decorateJourneys(panel: HistoryPanel) {
        val active =
            getPrivate<StatisticsSection026.Section>(
                panel,
                "activeSection",
            ) ?: return
        if (active != StatisticsSection026.Section.JOURNEYS) return

        val data =
            getPrivate<HistoryAnalytics>(panel, "currentData")
                ?: return
        val content =
            getPrivate<LinearLayout>(panel, "content")
                ?: return

        data.journeys.forEach { journey ->
            decorateJourneyCard(
                panel,
                content,
                journey,
            )
        }

        ensureOdometerRegistry(panel, content, data)
    }

    private fun decorateJourneyCard(
        panel: HistoryPanel,
        content: LinearLayout,
        journey: JourneyAnalytics,
    ) {
        val startLabel = shortDateTime(journey.startedAt)
        val startView =
            textViews(content)
                .firstOrNull {
                    it.text?.toString()?.trim() == startLabel
                } ?: return
        val card = startView.parent as? LinearLayout ?: return

        val boundsId =
            "sr0270_journey_bounds_${journey.id}"
        if (
            findFirst(card) {
                it.contentDescription == boundsId
            } == null
        ) {
            val line =
                SrUi023.body(
                    panel.context,
                    buildString {
                        append("Início ${fullDateTime(journey.startedAt)}")
                        append(" · Fim ")
                        append(
                            journey.endedAt
                                ?.let(::fullDateTime)
                                ?: "em andamento",
                        )
                    },
                    10.5f,
                ).apply {
                    contentDescription = boundsId
                    setTextColor(
                        SrUi023.palette(panel.context).ink,
                    )
                }

            val index =
                card.indexOfChild(startView)
                    .coerceAtLeast(0) + 1
            card.addView(
                line,
                index.coerceAtMost(card.childCount),
            )
        }

        // Substitui somente o editor pós-jornada por uma versão de contraste
        // explícito. Persistência continua na mesma JourneyMetricsStore.
        textViews(card)
            .firstOrNull {
                it.text?.toString()?.trim() ==
                    "Completar / corrigir dados"
            }
            ?.setOnClickListener {
                JourneyEditor0270.open(
                    panel.context,
                    journey.id,
                ) {
                    panel.refresh(true)
                }
            }

        if (Appearance021.isDark(panel.context)) {
            improveDarkContrast(card)
        }
    }

    private fun improveDarkContrast(root: View) {
        val context = root.context
        val ink = SrUi023.palette(context).ink
        textViews(root).forEach { view ->
            if (!view.isClickable) {
                view.setTextColor(ink)
            }
        }
    }

    private fun ensureOdometerRegistry(
        panel: HistoryPanel,
        content: LinearLayout,
        data: HistoryAnalytics,
    ) {
        // A faixa 0.26.5 já existe quando há dados. Se ela aparecer depois
        // de a faixa vazia 0.27 ter sido criada, removemos a vazia.
        val legacyPopulated =
            findFirst(content) {
                it.contentDescription ==
                    "sr0265_odometer_registry"
            }
        val existing0270 =
            findFirst(content) {
                it.contentDescription ==
                    "sr0270_odometer_registry"
            }

        if (legacyPopulated != null) {
            if (existing0270 != null) {
                (existing0270.parent as? ViewGroup)
                    ?.removeView(existing0270)
            }
            return
        }

        if (existing0270 != null) return

        val store = JourneyMetricsStore026.get(panel.context)
        val records =
            data.journeys
                .mapNotNull { journey ->
                    store.snapshot(journey.id)
                        .metric
                        ?.let {
                            journey to it
                        }
                }
                .sortedByDescending { it.first.startedAt }

        val card =
            SrUi023.card(
                panel.context,
                12,
                17,
            ).apply {
                contentDescription = "sr0270_odometer_registry"

                addView(
                    SrUi023.title(
                        context,
                        "Odômetros registrados",
                        14f,
                    ),
                )

                if (records.isEmpty()) {
                    addView(
                        SrUi023.body(
                            context,
                            "Nenhum odômetro registrado neste período. Use “Completar / corrigir dados” em uma jornada para informar km inicial e/ou final.",
                            10.5f,
                        ),
                    )
                } else {
                    addView(
                        SrUi023.body(
                            context,
                            "Histórico de km inicial, km final e distância percorrida por jornada.",
                            9.5f,
                        ),
                    )

                    records.take(30).forEachIndexed {
                            index,
                            (journey, metric),
                        ->
                        if (index > 0) {
                            addView(
                                View(context).apply {
                                    setBackgroundColor(
                                        SrUi023.palette(context).outline,
                                    )
                                },
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    SrUi023.dp(context, 1),
                                ).apply {
                                    topMargin =
                                        SrUi023.dp(context, 7)
                                    bottomMargin =
                                        SrUi023.dp(context, 7)
                                },
                            )
                        }

                        addView(
                            SrUi023.body(
                                context,
                                fullDateTime(journey.startedAt),
                                10f,
                            ).apply {
                                setTypeface(typeface, Typeface.BOLD)
                                setTextColor(
                                    SrUi023.palette(context).ink,
                                )
                            },
                        )

                        addView(
                            SrUi023.body(
                                context,
                                buildString {
                                    append(
                                        "Inicial: ${metric.odometerStartKm?.let(::km) ?: "—"}",
                                    )
                                    append(
                                        " · Final: ${metric.odometerEndKm?.let(::km) ?: "—"}",
                                    )
                                    append(
                                        "\nPercorrido: ${metric.distanceKm?.let(::km) ?: "—"}",
                                    )
                                },
                                11.5f,
                            ),
                        )

                        addView(
                            JourneyFlow026.editorButton(
                                panel.context,
                                journey.id,
                            ) {
                                panel.refresh(true)
                            }.apply {
                                setOnClickListener {
                                    JourneyEditor0270.open(
                                        panel.context,
                                        journey.id,
                                    ) {
                                        panel.refresh(true)
                                    }
                                }
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin =
                                    SrUi023.dp(panel.context, 5)
                            },
                        )
                    }
                }
            }

        content.addView(
            card,
            1.coerceAtMost(content.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = SrUi023.dp(panel.context, 8)
                bottomMargin = SrUi023.dp(panel.context, 8)
            },
        )
    }

    private fun shortDateTime(value: String): String =
        runCatching {
            DateTimeFormatter.ofPattern("dd/MM HH:mm")
                .withZone(ZoneId.of("America/Sao_Paulo"))
                .format(Instant.parse(value))
        }.getOrDefault(
            value.take(16).replace('T', ' '),
        )

    private fun fullDateTime(value: String): String =
        runCatching {
            DateTimeFormatter.ofPattern(
                "dd/MM/yyyy HH:mm",
                Locale("pt", "BR"),
            )
                .withZone(ZoneId.of("America/Sao_Paulo"))
                .format(Instant.parse(value))
        }.getOrDefault(
            value.take(16).replace('T', ' '),
        )

    private fun km(value: Double): String =
        String.format(
            Locale("pt", "BR"),
            "%,.1f km",
            value,
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivate(target: Any, name: String): T? =
        runCatching {
            val field =
                target.javaClass
                    .getDeclaredField(name)
                    .apply { isAccessible = true }
            field.get(target) as? T
        }.getOrNull()

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(view: View) {
            if (view is TextView) out += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(root)
        return out
    }

    private fun findFirst(
        root: View,
        predicate: (View) -> Boolean,
    ): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst(
                    root.getChildAt(i),
                    predicate,
                )?.let { return it }
            }
        }
        return null
    }
}
