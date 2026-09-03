package com.srrotas.app

import android.content.Context

/**
 * Compatibilidade temporária 0.23 -> 0.26.
 *
 * A rota histórica foi preservada internamente para não quebrar intents e
 * atalhos existentes. A partir da 0.26, a cópia visível "Histórico" passa a
 * representar a nova área "Estatísticas".
 */
class SrAppHeader023(
    context: Context,
    titleText: String,
    subtitleText: String,
    @Suppress("UNUSED_PARAMETER") trailingDrawable: Int? = null,
) : SrSectionHeader024(
    context = context,
    titleText = StatisticsSection026.headerTitle(titleText),
    subtitleText = StatisticsSection026.headerSubtitle(titleText, subtitleText),
)
