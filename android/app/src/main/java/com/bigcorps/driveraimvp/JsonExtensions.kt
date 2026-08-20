package com.srrotas.app

import org.json.JSONObject

/**
 * Utilitários JSON compartilhados pelas estruturas 0.18.
 *
 * CostProfileModels.kt usa putNullable(...) ao serializar campos opcionais.
 * A primeira entrega da 0.18 chamou essa extensão sem tê-la declarado no
 * pacote, fazendo :app:compileDebugKotlin falhar no GitHub Actions.
 */
internal fun JSONObject.putNullable(
    key: String,
    value: Any?,
) {
    if (value == null) {
        put(key, JSONObject.NULL)
    } else {
        put(key, value)
    }
}
