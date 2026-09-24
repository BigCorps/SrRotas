package com.srrotas.app

/** Texto compacto do controle de jornada em Develop Mode. */
internal object DevelopStatus033 {
    fun readerLabel(mode: String): String = when (mode) {
        ReaderLab027036.MODE_M2 -> "M2"
        ReaderLab027036.MODE_COMPARE -> "M1/M2/2.0"
        else -> "M1/2.0"
    }

    fun label(mode: String, allOk: Boolean, recoveryNeeded: Boolean): String {
        val reader = readerLabel(mode)
        return when {
            recoveryNeeded -> "⚠ Retomar — $reader"
            allOk -> "OK ✓ — $reader"
            else -> "⚠ Ajustes — $reader"
        }
    }
}
