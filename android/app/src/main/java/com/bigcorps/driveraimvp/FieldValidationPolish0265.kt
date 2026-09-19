package com.srrotas.app

import android.app.Application
import android.content.Context
import android.content.Intent

/**
 * Compatibilidade com chamadas históricas. Nenhum watcher/layout polish permanece.
 * O único método funcional abre explicitamente a tela Agora no shell consolidado.
 */
@Deprecated("Substituído pela arquitetura consolidada RC3.7")
object FieldValidationPolish0265 {
    fun install(@Suppress("UNUSED_PARAMETER") application: Application) = Unit

    fun openNowFromAssistant(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_NOW)
            },
        )
    }
}
