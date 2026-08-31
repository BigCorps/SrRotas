package com.srrotas.app

/**
 * Regra pura do semáforo de prontidão.
 *
 * Verde: nenhuma pendência.
 * Amarelo: uma pendência isolada.
 * Vermelho: duas ou mais pendências ou combinação crítica.
 */
object SettingsStatusRules024 {
    enum class Level { GREEN, YELLOW, RED }

    data class Input(
        val overlayOk: Boolean,
        val locationOk: Boolean,
        val captureOk: Boolean,
        val ocrEnabled: Boolean,
        val onboardingCompleted: Boolean,
        val journeyActive: Boolean,
    )

    data class Result(
        val level: Level,
        val title: String,
        val missing: List<String>,
    )

    fun evaluate(input: Input): Result {
        val missing = buildList {
            if (!input.overlayOk) add("Permissão para exibir o HUD sobre outros apps")
            if (!input.locationOk) add("Localização aproximada")
            if (input.journeyActive && !input.captureOk) add("Captura de tela da jornada")
            if (!input.ocrEnabled) add("Leitura OCR")
            if (!input.onboardingCompleted) add("Configuração guiada")
        }

        val criticalPair =
            (!input.overlayOk && !input.locationOk) ||
                (input.journeyActive && !input.captureOk && !input.ocrEnabled)

        val level = when {
            missing.isEmpty() -> Level.GREEN
            criticalPair || missing.size >= 2 -> Level.RED
            else -> Level.YELLOW
        }

        val title = when (level) {
            Level.GREEN -> "Sr. Rotas está pronto"
            Level.YELLOW -> "Sr. Rotas precisa de um ajuste"
            Level.RED -> "Sr. Rotas precisa de atenção"
        }
        return Result(level, title, missing)
    }
}
