package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

/** vc108 — revisão pós-jornada: pendente / confirmada / rejeitada / duplicada, com desfazer. */
object RideRecoveryDialog108 {
    fun summaryLine(report: RideRecovery108.Report): String = buildString {
        append(report.uberCompleted?.let { "Uber $it concluídas · " } ?: "Sem resumo Uber · ")
        append("${report.confirmed} confirmadas no Sr. Rotas")
        append(" · ${report.pending} para revisar")
        report.unresolved?.let { append(" · $it sem evidência individual") }
    }

    fun show(context: Context, journeyId: String, onChanged: () -> Unit = {}) {
        val report = runCatching { RideRecovery108.report(context, journeyId) }.getOrNull()
        if (report == null) {
            AlertDialog.Builder(context).setTitle("Recuperar corridas")
                .setMessage("Jornada não encontrada neste aparelho.").setPositiveButton("Fechar", null).show()
            return
        }
        lateinit var dialog: AlertDialog
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiKit.dp(context, 18)
            setPadding(pad, UiKit.dp(context, 18), pad, UiKit.dp(context, 6))
        }
        body.addView(UiKit.title(context, "Recuperar corridas da jornada", 20f))
        body.addView(UiKit.margin(UiKit.body(context, summaryLine(report), 12f), top = 6))
        report.uberEarnings?.let {
            body.addView(UiKit.margin(UiKit.body(context,
                "Faturamento no resumo Uber: ${money(it)} (referência; não é somado às tarifas das corridas).", 10.5f), top = 4))
        }
        body.addView(UiKit.margin(UiKit.body(context,
            "Somente corridas com oferta observada nesta jornada podem ser confirmadas. Nada é alterado sem sua decisão; toda confirmação pode ser desfeita.",
            10.5f), top = 6))
        val visible = report.items.sortedBy { order(it) }
        if (visible.isEmpty()) {
            body.addView(UiKit.margin(UiKit.body(context,
                "Nenhum candidato. Digitalize o resumo e o histórico Uber desta jornada para comparar.", 12f), top = 10))
        }
        visible.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = UiKit.rounded(context, UiKit.palette(context).surfaceAlt, 12, UiKit.palette(context).line, 1)
                val p = UiKit.dp(context, 9)
                setPadding(p, p, p, p)
            }
            row.addView(UiKit.body(context, "${status(item)} · ${item.fare?.let(::money) ?: "—"}${time(item)}", 12.5f))
            row.addView(UiKit.body(context, item.evidence, 10.5f))
            val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            fun add(label: String, primary: Boolean, action: () -> Unit) {
                val b = if (primary) UiKit.primaryButton(context, label) { action(); dialog.dismiss(); onChanged(); show(context, journeyId, onChanged) }
                else UiKit.secondaryButton(context, label) { action(); dialog.dismiss(); onChanged(); show(context, journeyId, onChanged) }
                buttons.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (buttons.childCount > 0) marginStart = UiKit.dp(context, 6)
                })
            }
            when {
                item.actionable && item.decision == RideRecovery108.Decision.PENDING -> {
                    add("Confirmar realizada", true) { RideRecovery108.confirm(context, report, item) }
                    add("Não foi realizada", false) { RideRecovery108.reject(context, report, item) }
                }
                item.decision == RideRecovery108.Decision.CONFIRMED || (item.actionable && item.decision == RideRecovery108.Decision.REJECTED) ->
                    add("Desfazer", false) { RideRecovery108.undo(context, report, item) }
            }
            if (buttons.childCount > 0) row.addView(UiKit.margin(buttons, top = 6))
            body.addView(UiKit.margin(row, top = 8))
        }
        dialog = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton("Fechar", null)
            .create()
        // Fundo pela paleta do app (tema Claro/Escuro/Automático do Sr. Rotas), como os cards.
        val palette = UiKit.palette(context)
        dialog.window?.setBackgroundDrawable(UiKit.rounded(context, palette.surface, 20, palette.line, 1))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply { setTextColor(palette.primary); isAllCaps = false }
    }

    private fun order(item: RideRecovery108.Item) = when {
        item.actionable && item.decision == RideRecovery108.Decision.PENDING -> 0
        item.decision == RideRecovery108.Decision.CONFIRMED -> 1
        item.kind == RideRecovery108.Kind.NO_OBSERVED_OFFER || item.kind == RideRecovery108.Kind.AMBIGUOUS -> 2
        else -> 3
    }

    private fun status(item: RideRecovery108.Item): String = when {
        item.decision == RideRecovery108.Decision.CONFIRMED -> "Confirmada"
        item.decision == RideRecovery108.Decision.DUPLICATE -> "Duplicada (já confirmada)"
        item.kind == RideRecovery108.Kind.CANCELLED_IN_HISTORY -> "Cancelada no histórico"
        item.decision == RideRecovery108.Decision.REJECTED -> "Rejeitada"
        item.kind == RideRecovery108.Kind.NO_OBSERVED_OFFER -> "Não resolvida · sem oferta observada"
        item.kind == RideRecovery108.Kind.AMBIGUOUS -> "Não resolvida · ambígua"
        else -> "Pendente"
    }

    private fun time(item: RideRecovery108.Item): String = item.imported?.occurredAt?.let { iso ->
        runCatching {
            " · " + java.time.Instant.parse(iso).atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toLocalTime().toString().take(5)
        }.getOrDefault("")
    }.orEmpty()

    private fun money(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
}
