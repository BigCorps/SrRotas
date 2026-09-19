package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Configurações consolidadas: cada função aparece uma única vez. */
class SettingsPanel027037(context: Context) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val readerStatus = SrUi023.body(context, "", 10f)

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(SrAppHeader023(context, "Configurações", "Ajustes do Sr. Rotas"))
        body.setPadding(SrUi023.dp(context, 14), SrUi023.dp(context, 9), SrUi023.dp(context, 14), SrUi023.dp(context, 28))
        root.addView(body, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refresh()
    }

    fun refresh() {
        body.removeAllViews()
        val p = SrUi023.palette(context)
        val lab = ReaderLab027036.snapshot(context)
        body.addView(SrUi023.card(context, 13, 15).apply {
            addView(SrUi023.title(context, "Leitor de ofertas", 14f))
            readerStatus.text = "${ReaderLab027036.modeLabel(lab.mode)} · Acessibilidade ${if (lab.accessibilityEnabled) "ativa" else "desativada"}"
            addView(readerStatus)
            addView(
                UiKit.margin(
                    SrUi023.body(
                        context,
                        "M1 ${lab.m1Seen} (${lab.m1Complete} core completo) · M2 ${lab.m2Seen} (${lab.m2Complete} core completo) · pareadas ${lab.matched}",
                        9.5f,
                    ),
                    top = 5,
                ),
            )
            addView(UiKit.margin(UiKit.primaryButton(context, "Escolher M1 / Comparativo / M2") { showReaderMode() }, top = 8))
        })

        addTile("Jornada e permissões", "Acessos, captura e configuração inicial", R.drawable.sr23_ic_route, p.teal) {
            (context as? MainActivity)?.openJourneyAndPermissions()
        }
        addTile("Janela flutuante", "Tamanho, opacidade, tema, assistente e mensagens", R.drawable.sr23_ic_sliders, p.blue) {
            context.startActivity(Intent(context, FloatingWindowSettingsActivity027034::class.java))
        }
        addTile("Configuração do HUD", "Métricas, limites, prévia e perfis por veículo", R.drawable.sr23_ic_sliders, p.purple) {
            context.startActivity(Intent(context, Strategy021Activity::class.java))
        }
        addTile("Probabilidade de novas corridas", "Sinal histórico de continuidade exibido no HUD", R.drawable.sr23_ic_route, p.purple) {
            showDestinationContinuity()
        }
        addTile("Plataformas", "Uber homologado para o teste atual", R.drawable.sr23_ic_route, p.teal) { showPlatforms() }
        addTile("Digitalização", "Ferramentas manuais de captura e histórico Uber", R.drawable.sr23_ic_camera, p.blue) {
            context.startActivity(Intent(context, UberDigitizationActivity026::class.java))
        }
        addTile("Aparência", "Claro, Escuro ou Automático", R.drawable.sr23_ic_sun_moon, p.orange) {
            (context as? MainActivity)?.showAppearanceOnly()
        }
        addTile("Notificações", "Alertas, resumos e avisos", R.drawable.sr23_ic_bell, p.red) {
            (context as? MainActivity)?.showNotifications()
        }
        addTile("Screenshots das ofertas", "Backup visual local das ofertas reconhecidas", R.drawable.sr23_ic_camera, p.blue) {
            showScreenshotSetting()
        }
        addTile("Dados e sincronização", "Sincronizar preferências e dados pendentes", R.drawable.sr23_ic_cloud_sync, p.teal) {
            (context as? MainActivity)?.requestFullSync(true)
        }
        addTile("Diagnóstico de leitura", "Exporta M1, M2, watchdog e saúde da captura em um único arquivo", R.drawable.sr23_ic_info, p.orange) {
            ReaderLabCombinedDiagnostic0270361.share(context)
        }
        addTile("Modo Demonstração", "Dados fictícios para regressão visual", R.drawable.sr23_ic_info, p.blue) {
            (context as? MainActivity)?.showDemoMode()
        }
        addTile("Privacidade e suporte", "Política, termos e ajuda", R.drawable.sr23_ic_shield_help, p.navy) {
            (context as? MainActivity)?.openWeb("https://srrotas.com/privacidade")
        }
    }

    private fun addTile(title: String, subtitle: String, icon: Int, tone: Int, action: () -> Unit) {
        body.addView(UiKit.margin(SrUi023.card(context, 12, 14).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(SrUi023.iconBox(context, icon, tone, 44))
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(SrUi023.title(context, title, 13f))
                addView(SrUi023.body(context, subtitle, 9.5f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 9) })
            row.addView(TextView(context).apply {
                text = "›"
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(tone)
                gravity = Gravity.CENTER
            })
            addView(row)
        }, top = 7))
    }

    private fun showReaderMode() {
        val repo = SettingsRepository(context)
        if (repo.currentJourneyId().isNotBlank()) {
            AlertDialog.Builder(context)
                .setTitle("Troca de leitor bloqueada durante a jornada")
                .setMessage("Para evitar que M1 e M2 mudem de comportamento no meio da coleta, encerre a jornada atual e escolha o método antes de iniciar a próxima.")
                .setPositiveButton("Entendi", null)
                .show()
            return
        }

        val labels = arrayOf(
            "M1 — MediaProjection (produção)",
            "Comparativo — M1 oficial + M2 árvore em shadow",
            "M2 — Acessibilidade isolada (teste)",
        )
        val keys = arrayOf(ReaderLab027036.MODE_M1, ReaderLab027036.MODE_COMPARE, ReaderLab027036.MODE_M2)
        var choice = keys.indexOf(ReaderLab027036.mode(context)).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle("Método de leitura")
            .setMessage("M1 é o padrão seguro. No Comparativo o M2 usa somente a árvore de Acessibilidade, sem segundo OCR concorrente. Em M2 isolado, o MediaProjection não inicia e os resultados não entram na base oficial.")
            .setSingleChoiceItems(labels, choice) { _, which -> choice = which }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val next = keys[choice]
                ReaderLab027036.setMode(context, next)
                if (next != ReaderLab027036.MODE_M1 && !ReaderLab027036.isAccessibilityEnabled(context)) {
                    (context as? MainActivity)?.let(ReaderLab027036::showDisclosureAndOpenSettings)
                }
                refresh()
            }
            .show()
    }


    private fun showDestinationContinuity() {
        val enabled = DestinationContinuityHud025.enabled(context)
        val current = when {
            !enabled -> 0
            DestinationContinuityHud025.position(context) == DestinationContinuityHud025.POSITION_BOTTOM -> 2
            else -> 1
        }
        AlertDialog.Builder(context)
            .setTitle("Probabilidade de novas corridas")
            .setSingleChoiceItems(
                arrayOf("Desativado", "Ativo no topo", "Ativo abaixo"),
                current,
            ) { dialog, which ->
                when (which) {
                    0 -> DestinationContinuityHud025.setEnabled(context, false)
                    1 -> {
                        DestinationContinuityHud025.setEnabled(context, true)
                        DestinationContinuityHud025.setPosition(context, DestinationContinuityHud025.POSITION_TOP)
                    }
                    else -> {
                        DestinationContinuityHud025.setEnabled(context, true)
                        DestinationContinuityHud025.setPosition(context, DestinationContinuityHud025.POSITION_BOTTOM)
                    }
                }
                JourneyBubbleController.refresh(context)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showScreenshotSetting() {
        val repo = SettingsRepository(context)
        val enabled = repo.load().privateScreenshotEnabled
        AlertDialog.Builder(context)
            .setTitle("Screenshots das ofertas")
            .setMessage("Quando ativado, o Sr. Rotas salva no próprio aparelho uma imagem de cada oferta válida consolidada. Nada é enviado à Base Coletiva.")
            .setSingleChoiceItems(
                arrayOf("Salvar screenshots", "Não salvar screenshots"),
                if (enabled) 0 else 1,
            ) { dialog, which ->
                val current = repo.load()
                repo.save(current.copy(privateScreenshotEnabled = which == 0))
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPlatforms() {
        AlertDialog.Builder(context)
            .setTitle("Plataformas")
            .setMessage("Uber é a plataforma homologada para o teste atual. O suporte às demais plataformas permanece separado e não deve alterar o leitor Uber estabilizado.")
            .setPositiveButton("Entendi", null)
            .show()
    }
}
