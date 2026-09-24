package com.srrotas.app

/** Política pura de largura do 0.33, testável sem runtime Android. */
internal object ResponsivePolicy033 {
    fun contentWidthDp(screenDp: Int, maxDp: Int = 1440, horizontalMarginDp: Int = 12): Int =
        (screenDp - horizontalMarginDp * 2).coerceAtMost(maxDp).coerceAtLeast(280)
}
