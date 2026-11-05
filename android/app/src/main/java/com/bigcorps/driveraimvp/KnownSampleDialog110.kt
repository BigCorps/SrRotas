package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import java.util.Locale

/** vc110 — iniciar/encerrar o teste de amostra conhecida (Configurações). */
object KnownSampleDialog110 {
    fun show(context: Context) {
        if (!KnownSample110.running(context)) {
            AlertDialog.Builder(context)
                .setTitle("Teste de leitura")
                .setMessage("Inicie, opere com a jornada ativa e conte quantas ofertas a Uber exibiu. Ao encerrar, informe essa quantidade: o Sr. Rotas compara com as ofertas que registrou e com a cobertura de busca e destino. Nada da tela é gravado.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Iniciar teste") { _, _ -> KnownSample110.start(context) }
                .show()
            return
        }
        val input = UiKit.input(context, "Ofertas exibidas pela Uber").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(context)
            .setTitle("Encerrar teste de leitura")
            .setView(UiKit.margin(input, top = 8, start = 18, end = 18))
            .setNegativeButton("Continuar testando", null)
            .setPositiveButton("Encerrar") { _, _ ->
                val shown = input.text.toString().trim().toIntOrNull() ?: 0
                val r = KnownSample110.stop(context, shown) ?: return@setPositiveButton
                val pct = r.detectionRate?.let { String.format(Locale("pt", "BR"), "%.0f%%", it * 100) } ?: "—"
                AlertDialog.Builder(context)
                    .setTitle("Resultado do teste")
                    .setMessage("Ofertas exibidas: ${r.shown}\nRegistradas pelo Reader: ${r.persisted} ($pct)\nCom busca: ${r.withPickup} · com destino: ${r.withDestination} · ambos: ${r.withBoth}\nTelas candidatas: ${r.candidateFrames} · leituras OCR: ${r.ocr}\n\nO resultado entra no Diagnóstico de leitura (known_sample_110).")
                    .setPositiveButton("OK", null).show()
            }
            .show()
    }
}
