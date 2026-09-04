package com.srrotas.app

/** Fonte única de cores da UI. */
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

    // 0.26.3: mesma paleta, com os sinais operacionais mais vivos.
    val LIGHT = Palette(
        background = argb("FFFAFCF8"),
        surface = argb("FFFFFFFF"),
        surfaceAlt = argb("FFF0F8F3"),
        surfaceWarm = argb("FFFFFDF8"),
        ink = argb("FF102E38"),
        muted = argb("FF61727B"),
        line = argb("FFDCE8E1"),
        navy = argb("FF08345B"),
        navyDeep = argb("FF052744"),
        now = argb("FF087CFF"),
        nowGlow = argb("FF48A1FF"),
        history = argb("FF00BDB3"),
        ai = argb("FF7C3AED"),
        settings = argb("FFFF7600"),
        user = argb("FF4BCB28"),
        cyan = argb("FF00CFE8"),
        magenta = argb("FFE23BC7"),
        collectiveWarm = argb("FFFFAD24"),
        good = argb("FF00D968"),
        warn = argb("FFFFB800"),
        bad = argb("FFFF3B30"),
    )

    val DARK = Palette(
        background = argb("FF08141B"),
        surface = argb("FF101F27"),
        surfaceAlt = argb("FF182D33"),
        surfaceWarm = argb("FF1A292E"),
        ink = argb("FFF7FBFC"),
        muted = argb("FFC5D2D6"),
        line = argb("FF3C5660"),
        navy = argb("FF104977"),
        navyDeep = argb("FF0A355B"),
        now = argb("FF3395FF"),
        nowGlow = argb("FF6CB4FF"),
        history = argb("FF22DDD0"),
        ai = argb("FF9A75FF"),
        settings = argb("FFFF9500"),
        user = argb("FF70F05A"),
        cyan = argb("FF31DDEC"),
        magenta = argb("FFF06ADD"),
        collectiveWarm = argb("FFFFC057"),
        good = argb("FF70FF86"),
        warn = argb("FFFFD24A"),
        bad = argb("FFFF4D45"),
    )

    fun collectiveGradientStops(dark: Boolean): IntArray {
        val p = palette(dark)
        return intArrayOf(p.now, p.cyan, p.ai, p.magenta, p.collectiveWarm)
    }

    private fun argb(hex: String): Int =
        hex.removePrefix("#").toLong(16).toInt()
}
