package com.srrotas.app

/**
 * Matemática pura para manter a janela flutuante dentro da área visível.
 * Não depende de Android para poder ser testada em unit test JVM.
 */
object OverlayBounds024 {
    data class Position(val x: Int, val y: Int)

    fun clamp(
        x: Int,
        y: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        margin: Int = 0,
    ): Position {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return Position(x.coerceAtLeast(0), y.coerceAtLeast(0))
        }

        val safeMargin = margin.coerceAtLeast(0)
        val usableRight = (viewportWidth - contentWidth - safeMargin)
            .coerceAtLeast(safeMargin)
        val usableBottom = (viewportHeight - contentHeight - safeMargin)
            .coerceAtLeast(safeMargin)

        return Position(
            x = x.coerceIn(safeMargin, usableRight),
            y = y.coerceIn(safeMargin, usableBottom),
        )
    }

    fun isVisible(
        x: Int,
        y: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
    ): Boolean =
        x >= 0 &&
            y >= 0 &&
            x + contentWidth <= viewportWidth &&
            y + contentHeight <= viewportHeight
}
