package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** vc109 — estados observáveis da releitura da galeria. */
enum class RescanState109(val label: String) {
    AGUARDANDO("Aguardando imagem"),
    PREPARANDO("Preparando imagem…"),
    PRONTA("Imagem pronta para revisar"),
    PROCESSANDO("Processando a leitura…"),
    CONCLUIDA("Leitura concluída"),
    SEM_RESULTADO("Concluída sem resultado"),
    BLOQUEADA("Bloqueada por segurança"),
    FALHA("Falha na leitura"),
}

/**
 * vc109 — trilha persistente da releitura, escrita no processo `:rescan` e lida pelo diagnóstico
 * do processo principal (arquivo privado compartilhado). Só estados, códigos e durações: sem imagem,
 * texto do OCR ou endereços. Permite ver no aparelho onde o fluxo parou.
 */
object RescanTrace109 {
    private const val FILE = "rescan_trace_109.json"
    private const val KEEP = 40

    @Synchronized
    fun record(context: Context, state: String, code: String = "") {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE)
            val rows = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
            rows.put(JSONObject().put("at", System.currentTimeMillis()).put("state", state).put("code", code.take(80))
                .put("process", processTag()))
            val trimmed = JSONArray((maxOf(0, rows.length() - KEEP) until rows.length()).map { rows.get(it) })
            file.writeText(trimmed.toString())
        }
    }

    fun toJson(context: Context): JSONObject {
        val rows = runCatching { JSONArray(File(context.applicationContext.filesDir, FILE).readText()) }.getOrDefault(JSONArray())
        val last = if (rows.length() > 0) rows.optJSONObject(rows.length() - 1) else null
        return JSONObject().put("schema", "sr-rescan-trace-109").put("events", rows)
            .put("last_state", last?.optString("state") ?: JSONObject.NULL).put("last_code", last?.optString("code") ?: JSONObject.NULL)
    }

    private fun processTag(): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName().substringAfterLast(':', "main") else "unknown"
    }.getOrDefault("unknown")
}
