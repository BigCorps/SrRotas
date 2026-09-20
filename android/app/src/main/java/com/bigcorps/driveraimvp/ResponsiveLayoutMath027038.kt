package com.srrotas.app

/** Matemática pura usada pela UI responsiva e validada em unit tests. */
object ResponsiveLayoutMath027038 {
    fun contentWidthDp(screenWidthDp: Int, maxDp: Int = 760, horizontalMarginDp: Int = 16): Int {
        val safeScreen = screenWidthDp.coerceAtLeast(1)
        val margin = horizontalMarginDp.coerceAtLeast(0)
        val available = (safeScreen - margin * 2).coerceAtLeast(1)
        return available.coerceAtMost(maxDp.coerceAtLeast(1))
    }

    fun isVeryNarrow(screenWidthDp: Int): Boolean = screenWidthDp < 330
    fun isNarrow(screenWidthDp: Int): Boolean = screenWidthDp < 360
}
