package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HistoricalImportActivity : Activity() {
    companion object { private const val REQ_IMAGES = 17017 }

    private lateinit var status: TextView
    private lateinit var chooseButton: TextView
    private lateinit var localSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)

        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(UiKit.palette(this@HistoricalImportActivity).background)
        }
        scroll.addView(root)
        setContentView(scroll)
        UiKit.applySafeArea(scroll)

        root.addView(UiKit.title(this, "Importar histórico", 28f))
        root.addView(
            UiKit.margin(
                UiKit.body(
                    this,
                    "Selecione screenshots antigos de ofertas do Uber. O OCR roda no aparelho; as imagens não são enviadas. Só dados estruturados de ofertas válidas podem sincronizar.",
                    14f,
                ),
                top = 8,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@HistoricalImportActivity, "Regras da importação"))
                    addView(
                        UiKit.body(
                            this@HistoricalImportActivity,
                            "• SHA-256 evita reprocessar a mesma imagem.\n" +
                                "• O mesmo Offer Engine e Context Engine são reutilizados.\n" +
                                "• A data tenta metadado, nome do arquivo e data de modificação.\n" +
                                "• Data incerta é marcada como incerta.\n" +
                                "• Screenshot é evento positivo: nunca cria tempo de espera fictício.",
                            13f,
                        ),
                    )
                },
                top = 14,
            ),
        )

        chooseButton = UiKit.primaryButton(this, "Selecionar screenshots") {
            val repo = SettingsRepository(this)
            if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) {
                status.text = "Encerre a jornada antes de importar. Isso evita competir com o OCR ao vivo."
            } else {
                chooseImages()
            }
        }
        root.addView(UiKit.margin(chooseButton, top = 14))

        status = UiKit.body(this, "Nenhuma importação em andamento.", 13f)
        root.addView(UiKit.margin(status, top = 10))

        localSummary = UiKit.body(this, "", 13f)
        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@HistoricalImportActivity, "Resumo local"))
                    addView(localSummary)
                },
                top = 14,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.secondaryButton(this, "Voltar") { finish() },
                top = 14,
            ),
        )

        refreshLocalSummary()
    }

    @Suppress("DEPRECATION")
    private fun chooseImages() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            },
            REQ_IMAGES,
        )
    }

    @Deprecated("Compatibilidade sem dependência AndroidX Activity Result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMAGES || resultCode != RESULT_OK || data == null) return

        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        }
        data.data?.let { uris += it }

        val selected = uris.distinctBy(Uri::toString)
        if (selected.isEmpty()) {
            status.text = "Nenhuma imagem selecionada."
            return
        }

        chooseButton.isEnabled = false
        chooseButton.alpha = .5f
        status.text = "Preparando ${selected.size} imagem(ns)..."

        HistoricalScreenshotImporter.import(
            this,
            selected,
            onProgress = { progress ->
                status.text =
                    "${progress.current}/${progress.total} · ${progress.displayName.take(70)}\n" +
                        "Ofertas novas ${progress.importedOffers} · duplicadas ${progress.duplicateOffers}"
            },
            onResult = { result ->
                chooseButton.isEnabled = true
                chooseButton.alpha = 1f
                result.onSuccess { r ->
                    status.text =
                        "Importação concluída.\n" +
                            "Processados ${r.processedFiles} · já importados ${r.skippedFiles} · " +
                            "sem oferta ${r.noOfferFiles} · falhas ${r.failedFiles}\n" +
                            "Ofertas novas ${r.importedOffers} · duplicadas ${r.duplicateOffers}"
                    refreshLocalSummary()
                }.onFailure {
                    status.text = "Importação interrompida: ${it.message}"
                }
            },
        )
    }

    private fun refreshLocalSummary() {
        val s = HistoricalImportStore.get(this).summary()
        localSummary.text =
            "Arquivos processados ${s.processedFiles}\n" +
                "Sem oferta ${s.noOfferFiles} · falhas ${s.failedFiles}\n" +
                "Ofertas importadas ${s.importedOffers} · duplicadas evitadas ${s.duplicateOffers}"
    }

    private fun dp(value: Int) = UiKit.dp(this, value)
}
