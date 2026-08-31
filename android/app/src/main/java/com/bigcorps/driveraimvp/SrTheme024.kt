package com.srrotas.app

/**
 * Fonte única de cores da UI 0.24.
 *
 * Os relatórios de 30/08 e 31/08/2026 definem papéis visuais, mas não fornecem
 * HEX exatos. Estes valores são a tradução de implementação dos mockups e
 * passam a ser a fonte de verdade compartilhada por APK/Web.
 *
 * Light/Dark preservam a mesma hierarquia; muda apenas a paleta.
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

        /** Compatibilidade com componentes 0.23 enquanto são migrados. */
        val primary: Int get() = history
        val primaryDark: Int get() = navy
        val orange: Int get() = settings
    }

    fun palette(dark: Boolean): Palette = if (dark) DARK else LIGHT

    val LIGHT = Palette(
        background = argb("FFF6F3EB"),
        surface = argb("FFFFFFFF"),
        surfaceAlt = argb("FFF2EEE3"),
        surfaceWarm = argb("FFFBF7ED"),
        ink = argb("FF0A2747"),
        muted = argb("FF657589"),
        line = argb("FFD9E0E7"),
        navy = argb("FF082A56"),
        navyDeep = argb("FF061F42"),
        now = argb("FF1677FF"),
        nowGlow = argb("FF59A3FF"),
        history = argb("FF0A9B9A"),
        ai = argb("FF744DFF"),
        settings = argb("FFFF8A18"),
        user = argb("FF6C9F25"),
        cyan = argb("FF10BBD4"),
        magenta = argb("FFDF4FD0"),
        collectiveWarm = argb("FFFFB14A"),
        good = argb("FF18A957"),
        warn = argb("FFF2B729"),
        bad = argb("FFE25555"),
    )

    val DARK = Palette(
        background = argb("FF0A1420"),
        surface = argb("FF111F2D"),
        surfaceAlt = argb("FF182838"),
        surfaceWarm = argb("FF1C2832"),
        ink = argb("FFF5F8FC"),
        muted = argb("FF9EADBE"),
        line = argb("FF2B3C4E"),
        navy = argb("FF123D70"),
        navyDeep = argb("FF0B2B50"),
        now = argb("FF3D8DFF"),
        nowGlow = argb("FF6CABFF"),
        history = argb("FF31C5C0"),
        ai = argb("FF9B7CFF"),
        settings = argb("FFFFAD4D"),
        user = argb("FF95C953"),
        cyan = argb("FF35D1E6"),
        magenta = argb("FFF078DD"),
        collectiveWarm = argb("FFFFC05C"),
        good = argb("FF43D083"),
        warn = argb("FFFFD15A"),
        bad = argb("FFFF7770"),
    )

    /**
     * Gradiente Base Coletiva, na ordem definida pela referência:
     * azul -> ciano -> violeta -> magenta -> acento quente.
     */
    fun collectiveGradientStops(dark: Boolean): IntArray {
        val p = palette(dark)
        return intArrayOf(p.now, p.cyan, p.ai, p.magenta, p.collectiveWarm)
    }

    private fun argb(hex: String): Int =
        hex.removePrefix("#").toLong(16).toInt()
}
