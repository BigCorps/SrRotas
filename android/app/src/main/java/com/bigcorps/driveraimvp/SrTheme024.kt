package com.srrotas.app

/**
 * Fonte única de cores da UI.
 *
 * 0.26 clareia o tema principal para um creme quase branco com leve leitura
 * verde e aumenta a saturação dos acentos. A hierarquia e o tema escuro são
 * preservados para evitar alterações de layout ou de comportamento.
 */
object SrTheme024 {
    enum class Section {
        HISTORY,
        AI,
        NOW,
        SETTINGS,
        USER,
    }

    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceAlt: Int,
        val surfaceWarm: Int,
        val ink: Int,
        val muted: Int,
        val line: Int,
        val navy: Int,
        val navyDeep: Int,
        val now: Int,
        val nowGlow: Int,
        val history: Int,
        val ai: Int,
        val settings: Int,
        val user: Int,
        val cyan: Int,
        val magenta: Int,
        val collectiveWarm: Int,
        val good: Int,
        val warn: Int,
        val bad: Int,
    ) {
        fun accent(section: Section): Int = when (section) {
            Section.HISTORY -> history
            Section.AI -> ai
            Section.NOW -> now
            Section.SETTINGS -> settings
            Section.USER -> user
        }

        val primary: Int get() = history
        val primaryDark: Int get() = navy
        val orange: Int get() = settings
    }

    fun palette(dark: Boolean): Palette = if (dark) DARK else LIGHT

    val LIGHT = Palette(
        // Creme quase branco, com um toque verde muito discreto.
        background = argb("FFFAFCF8"),
        surface = argb("FFFFFFFF"),
        surfaceAlt = argb("FFF1F7F3"),
        surfaceWarm = argb("FFFFFDF8"),
        ink = argb("FF102E38"),
        muted = argb("FF61727B"),
        line = argb("FFDCE8E1"),
        navy = argb("FF08345B"),
        navyDeep = argb("FF052744"),
        now = argb("FF087CFF"),
        nowGlow = argb("FF48A1FF"),
        history = argb("FF00AFA8"),
        ai = argb("FF7C3AED"),
        settings = argb("FFFF8700"),
        user = argb("FF58AE2B"),
        cyan = argb("FF00C4DE"),
        magenta = argb("FFE23BC7"),
        collectiveWarm = argb("FFFFAD24"),
        good = argb("FF16B364"),
        warn = argb("FFF2B500"),
        bad = argb("FFEF4444"),
    )

    val DARK = Palette(
        background = argb("FF08141B"),
        surface = argb("FF101F27"),
        surfaceAlt = argb("FF182A31"),
        surfaceWarm = argb("FF1A292E"),
        ink = argb("FFF7FBFC"),
        muted = argb("FFA6B7BD"),
        line = argb("FF294048"),
        navy = argb("FF104977"),
        navyDeep = argb("FF0A355B"),
        now = argb("FF3395FF"),
        nowGlow = argb("FF6CB4FF"),
        history = argb("FF22D0C5"),
        ai = argb("FF9A75FF"),
        settings = argb("FFFFA73D"),
        user = argb("FF8DD85D"),
        cyan = argb("FF31D4E8"),
        magenta = argb("FFF06ADD"),
        collectiveWarm = argb("FFFFC057"),
        good = argb("FF43D68B"),
        warn = argb("FFFFD052"),
        bad = argb("FFFF716B"),
    )

    fun collectiveGradientStops(dark: Boolean): IntArray {
        val p = palette(dark)
        return intArrayOf(p.now, p.cyan, p.ai, p.magenta, p.collectiveWarm)
    }

    private fun argb(hex: String): Int =
        hex.removePrefix("#").toLong(16).toInt()
}
