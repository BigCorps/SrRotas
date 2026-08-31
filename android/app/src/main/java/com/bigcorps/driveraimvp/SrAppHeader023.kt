package com.srrotas.app

import android.content.Context

/**
 * Compatibilidade temporária 0.23 -> 0.24.
 *
 * O relatório 0.24 remove o cabeçalho institucional repetido. Mantemos o nome
 * da classe para não forçar uma alteração de dezenas de call-sites na mesma
 * etapa. `trailingDrawable` é aceito apenas por compatibilidade e não é exibido.
 */
class SrAppHeader023(
    context: Context,
    titleText: String,
    subtitleText: String,
    @Suppress("UNUSED_PARAMETER") trailingDrawable: Int? = null,
) : SrSectionHeader024(
    context = context,
    titleText = titleText,
    subtitleText = subtitleText,
)
