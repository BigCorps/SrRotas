package com.srrotas.app

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 0.20:
 * A importação histórica continua disponível internamente/admin, mas deixa de
 * aparecer na UI normal do motorista.
 *
 * Esta política permite retirar as entradas da interface sem apagar a
 * infraestrutura local criada na 0.17.
 */
object UiVisibilityPolicy {
    private val hiddenTerms = listOf(
        "importar screenshots",
        "importe screenshots",
        "importação deduplica screenshots",
        "importação histórica",
        "oferta(s) importada(s)",
        "importadas ",
        "duplicadas evitadas",
        "arquivos processados",
    )

    fun hideHistoricalImportUi(root: View) {
        walk(root)
    }

    private fun walk(view: View) {
        if (view is TextView) {
            val normalized = view.text?.toString()?.trim()?.lowercase().orEmpty()
            if (hiddenTerms.any { normalized.contains(it) }) {
                val parent = view.parent as? ViewGroup
                if (
                    parent != null &&
                    parent.childCount <= 2 &&
                    parent.parent != null
                ) {
                    // Ex.: card exclusivo do checklist da importação.
                    parent.visibility = View.GONE
                } else {
                    view.visibility = View.GONE
                }
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index))
            }
        }
    }
}
