package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class FieldValidationActivity : Activity() {
    private lateinit var summaryText: TextView
    private lateinit var checksBox: LinearLayout
    private lateinit var performanceText: TextView
    private lateinit var manualProgress: TextView
    private lateinit var startPerformanceButton: TextView
    private lateinit var finishPerformanceButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val root = buildUi()
        setContentView(root)
        UiKit.applySafeArea(root)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::summaryText.isInitialized) refresh()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(UiKit.palette(this@FieldValidationActivity).background)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(root)

        root.addView(UiKit.pill(this, "FIELD TEST · ${BuildConfig.VERSION_NAME}", "primary"))
        root.addView(UiKit.margin(UiKit.title(this, "Validação de campo 0.19", 27f), top = 8))
        root.addView(
            UiKit.body(
                this,
                "Esta tela separa evidência automática de itens que ainda precisam ser conferidos na rua. " +
                    "Use os controles somente com o veículo parado.",
                13f,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@FieldValidationActivity, "Estado automático"))
                    summaryText = UiKit.body(this@FieldValidationActivity, "", 13f)
                    addView(summaryText)
                    addView(
                        UiKit.margin(
                            UiKit.secondaryButton(this@FieldValidationActivity, "Atualizar diagnóstico") {
                                refresh()
                            },
                            top = 9,
                        ),
                    )
                    addView(
                        UiKit.margin(
                            UiKit.secondaryButton(this@FieldValidationActivity, "Sincronizar agora") {
                                syncNow()
                            },
                            top = 7,
                        ),
                    )
                    addView(
                        UiKit.margin(
                            UiKit.primaryButton(this@FieldValidationActivity, "Compartilhar relatório 0.19") {
                                FieldValidationReporter.share(this@FieldValidationActivity)
                            },
                            top = 7,
                        ),
                    )
                },
                top = 14,
            ),
        )

        checksBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(UiKit.margin(checksBox, top = 10))

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@FieldValidationActivity, "Bateria e desempenho"))
                    performanceText = UiKit.body(this@FieldValidationActivity, "", 12f)
                    addView(performanceText)

                    startPerformanceButton = UiKit.primaryButton(
                        this@FieldValidationActivity,
                        "Iniciar medição de desempenho",
                    ) {
                        val sample = FieldValidationSession.start(this@FieldValidationActivity)
                        toast("Medição iniciada. Pode iniciar a jornada normalmente.")
                        performanceText.text = performanceLabel(sample)
                        refreshPerformanceButtons()
                    }
                    addView(UiKit.margin(startPerformanceButton, top = 9))

                    finishPerformanceButton = UiKit.secondaryButton(
                        this@FieldValidationActivity,
                        "Encerrar medição",
                    ) {
                        val sample = FieldValidationSession.finish(this@FieldValidationActivity)
                        performanceText.text = sample?.let(::performanceLabel)
                            ?: "Nenhuma medição disponível."
                        refreshPerformanceButtons()
                    }
                    addView(UiKit.margin(finishPerformanceButton, top = 7))

                    addView(
                        UiKit.margin(
                            UiKit.body(
                                this@FieldValidationActivity,
                                "A 0.19 registra duração, CPU do processo, bateria, memória PSS e estado térmico. " +
                                    "Não há um limite artificial de aprovação: vamos comparar os aparelhos e os relatos reais da rodada.",
                                11f,
                            ),
                            top = 8,
                        ),
                    )
                },
                top = 10,
            ),
        )

        root.addView(UiKit.margin(UiKit.sectionTitle(this, "Roteiro manual"), top = 16))
        root.addView(
            UiKit.body(
                this,
                "O checklist é opcional e fica somente neste aparelho. Ele existe para não esquecermos nenhum critério da 0.19.",
                12f,
            ),
        )
        manualProgress = UiKit.body(this, "", 13f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(UiKit.margin(manualProgress, top = 8))

        FieldValidationManualChecklist.items.forEach { item ->
            root.addView(
                UiKit.margin(
                    UiKit.card(this, 10).apply {
                        val box = CheckBox(this@FieldValidationActivity).apply {
                            text = item.label
                            setTextColor(UiKit.palette(this@FieldValidationActivity).ink)
                            isChecked = FieldValidationManualStore.isChecked(
                                this@FieldValidationActivity,
                                item.id,
                            )
                            setOnCheckedChangeListener { _, checked ->
                                FieldValidationManualStore.setChecked(
                                    this@FieldValidationActivity,
                                    item.id,
                                    checked,
                                )
                                refreshManualProgress()
                            }
                        }
                        addView(box)
                        addView(UiKit.body(this@FieldValidationActivity, item.help, 11f))
                    },
                    top = 6,
                ),
            )
        }

        root.addView(
            UiKit.margin(
                UiKit.secondaryButton(this, "Zerar checklist manual") {
                    AlertDialog.Builder(this)
                        .setTitle("Zerar checklist 0.19?")
                        .setMessage("Isso remove somente as marcações locais desta rodada.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Zerar") { _, _ ->
                            FieldValidationManualStore.reset(this)
                            recreate()
                        }
                        .show()
                },
                top = 10,
            ),
        )

        root.addView(UiKit.margin(UiKit.sectionTitle(this, "Atalhos da rodada"), top = 16))
        root.addView(
            UiKit.card(this).apply {
                addView(UiKit.secondaryButton(this@FieldValidationActivity, "Histórico e corridas") {
                    startActivity(Intent(this@FieldValidationActivity, HistoryQuickActivity::class.java))
                })
                addView(UiKit.margin(UiKit.secondaryButton(this@FieldValidationActivity, "Importar screenshots") {
                    startActivity(Intent(this@FieldValidationActivity, HistoricalImportActivity::class.java))
                }, top = 7))
                addView(UiKit.margin(UiKit.secondaryButton(this@FieldValidationActivity, "Meus custos") {
                    startActivity(Intent(this@FieldValidationActivity, CostProfileActivity::class.java))
                }, top = 7))
                addView(UiKit.margin(UiKit.secondaryButton(this@FieldValidationActivity, "Estratégia e HUD") {
                    startActivity(Intent(this@FieldValidationActivity, StrategyActivity::class.java))
                }, top = 7))
            },
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@FieldValidationActivity, "Critério de encerramento"))
                    addView(
                        UiKit.body(
                            this@FieldValidationActivity,
                            "Para fechar a 0.19 precisamos: zero P0/P1 conhecidos, Offer Engine sem regressão, " +
                                "Context Engine sem mistura no Radar, dedupe de importação, denominador de exposição, " +
                                "guardrail estatístico íntegro, desempenho aceitável e sync confiável. " +
                                "Um PASS automático não substitui o teste manual correspondente.",
                            12f,
                        ),
                    )
                },
                top = 12,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.secondaryButton(this, "Voltar") { finish() },
                top = 12,
            ),
        )

        return scroll
    }

    private fun refresh() {
        val facts = FieldValidationCollector.collect(this)
        val checks = FieldValidationAssessment.evaluate(facts)
        val pass = checks.count { it.status == FieldValidationStatus.PASS }
        val warn = checks.count { it.status == FieldValidationStatus.WARN }
        val fail = checks.count { it.status == FieldValidationStatus.FAIL }
        val manual = checks.count { it.status == FieldValidationStatus.MANUAL }

        summaryText.text = buildString {
            append("$pass PASS · $warn atenção · $fail falha · $manual manual")
            append("\n${facts.offers} ofertas locais · ${facts.closedExposures} exposições · ${facts.pendingTotal} item(ns) na fila")
            append("\n${FieldValidationManualStore.completed(this@FieldValidationActivity)} de ${FieldValidationManualChecklist.items.size} itens manuais concluídos")
        }

        checksBox.removeAllViews()
        checks.forEach { check ->
            checksBox.addView(
                UiKit.margin(
                    UiKit.card(this, 10).apply {
                        val top = LinearLayout(this@FieldValidationActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        top.addView(
                            UiKit.title(this@FieldValidationActivity, check.title, 15f),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        top.addView(
                            UiKit.pill(
                                this@FieldValidationActivity,
                                statusLabel(check.status),
                                statusTone(check.status),
                            ),
                        )
                        addView(top)
                        addView(UiKit.margin(UiKit.body(this@FieldValidationActivity, check.detail, 11f), top = 5))
                    },
                    bottom = 7,
                ),
            )
        }

        val perf = FieldValidationSession.current(this)
            ?: FieldValidationSession.last(this)
        performanceText.text = perf?.let(::performanceLabel)
            ?: "Nenhuma sessão medida ainda. Para a rodada final, inicie a medição antes de uma jornada de pelo menos 30 minutos."

        refreshPerformanceButtons()
        refreshManualProgress()
    }

    private fun syncNow() {
        BackendClient.syncPreferences(this)
        BackendClient.flushPendingOffers(this)
        JourneySyncClient.flush(this)
        CostProfileSync.refreshOrFlush(this)
        toast("Sincronização solicitada. Atualizando em alguns segundos.")
        summaryText.postDelayed({ refresh() }, 2500L)
    }

    private fun refreshPerformanceButtons() {
        val running = FieldValidationSession.isRunning(this)
        startPerformanceButton.isEnabled = !running
        startPerformanceButton.alpha = if (running) .45f else 1f
        finishPerformanceButton.isEnabled = running
        finishPerformanceButton.alpha = if (running) 1f else .45f
    }

    private fun refreshManualProgress() {
        val completed = FieldValidationManualStore.completed(this)
        manualProgress.text = "$completed de ${FieldValidationManualChecklist.items.size} itens manuais concluídos."
    }

    private fun performanceLabel(sample: FieldPerformanceSample): String = buildString {
        append(if (sample.running) "Medição em andamento" else "Última medição")
        append(" · ${fmt(sample.elapsedMinutes)} min")
        append("\nCPU do processo ${fmt(sample.processCpuMinutes)} min · relação CPU/tempo ${fmt(sample.cpuToElapsedPct)}%")
        if (!sample.processContinuous) {
            append(" · processo reiniciou; CPU desta sessão não é comparável")
        }
        append("\nMemória PSS ${fmt(sample.startPssMb)} → ${fmt(sample.currentPssMb)} MB")
        if (sample.startBatteryPct != null && sample.currentBatteryPct != null) {
            append("\nBateria ${sample.startBatteryPct}% → ${sample.currentBatteryPct}%")
            sample.batteryDropPerHour?.let {
                append(" · queda observada ${fmt(it)} p.p./h")
            }
        }
        sample.batteryTemperatureC?.let {
            append("\nBateria ${fmt(it)} °C")
        }
        append(" · térmico ${thermalLabel(sample.thermalStatus)}")
    }

    private fun statusLabel(status: FieldValidationStatus) = when (status) {
        FieldValidationStatus.PASS -> "PASS"
        FieldValidationStatus.WARN -> "ATENÇÃO"
        FieldValidationStatus.FAIL -> "FALHA"
        FieldValidationStatus.MANUAL -> "MANUAL"
    }

    private fun statusTone(status: FieldValidationStatus) = when (status) {
        FieldValidationStatus.PASS -> "good"
        FieldValidationStatus.WARN -> "warn"
        FieldValidationStatus.FAIL -> "bad"
        FieldValidationStatus.MANUAL -> "primary"
    }

    private fun thermalLabel(value: String) = when (value) {
        "none" -> "normal"
        "light" -> "leve"
        "moderate" -> "moderado"
        "severe" -> "severo"
        "critical" -> "crítico"
        "emergency" -> "emergência"
        "shutdown" -> "desligamento"
        "unavailable" -> "não disponível"
        else -> value
    }

    private fun fmt(value: Double) = String.format(Locale("pt", "BR"), "%.2f", value)
    private fun dp(value: Int) = UiKit.dp(this, value)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
