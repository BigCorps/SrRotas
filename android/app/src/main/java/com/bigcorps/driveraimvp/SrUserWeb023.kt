package com.srrotas.app

import android.content.Context

/** A aba Usuário é deliberadamente Web: conta, aparelhos, plano e pagamento. */
object SrUserWeb023 {
    fun open(context: Context) = WebHandoff021.open(context, "/app/perfil")
}
