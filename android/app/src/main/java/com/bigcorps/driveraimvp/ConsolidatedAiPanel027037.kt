package com.srrotas.app

import android.widget.FrameLayout

/**
 * Preserva o shell oficial da aba IA.
 * O avatar discreto e a conversa ficam sob responsabilidade de AiPanel023;
 * não ampliar novamente o mascote nem criar sobreposições artificiais.
 */
class ConsolidatedAiPanel027037(context: android.content.Context) : FrameLayout(context) {
    private val panel = AiPanel023(context)
    init {
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
