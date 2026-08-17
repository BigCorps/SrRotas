package com.srrotas.app

/**
 * Gate textual conservador antes do parser financeiro.
 * MediaProjection enxerga a tela inteira e não informa qual app está em primeiro plano,
 * então o próprio Sr. Rotas e telas genéricas precisam ser descartados por contexto.
 */
object UberScreenGate {
    enum class Kind { OFFER_CANDIDATE, OWN_APP, IDLE_OR_HOME, UNKNOWN }

    fun classify(rawText: String): Kind {
        val text = rawText.replace('\u00A0', ' ').lowercase()
        if (text.isBlank()) return Kind.UNKNOWN

        val ownApp = listOf(
            "sr. rotas 2.0 alpha",
            "estratégia e hud",
            "estrategia e hud",
            "diagnóstico da leitura",
            "diagnostico da leitura",
            "versão instalada:",
            "versao instalada:",
            "compartilhar diagnóstico",
            "compartilhar diagnostico",
        ).any(text::contains)

        val explicitCardAnchor = listOf(
            "radar de viagens",
            "exclusivo",
            "aceitar",
            "selecionar",
            "uberx",
            "comfort",
            "priority",
            "black",
            "uber moto",
            "ubermoto",
        ).any(text::contains)

        val hasMoney = Regex("r\\$|\\$\\s*[0-9]", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasAdvertisedPerKm = Regex("(?:r\\$|\\$)\\s*[0-9os.,]+\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasTimeDistance = Regex("[0-9os]+\\s*(?:min|minuto|minutos)\\s*\\([^)]*km", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val strongGeometryCard = hasMoney && hasAdvertisedPerKm && hasTimeDistance

        // A tela de diagnóstico pode conter cópias de logs antigos com "Radar", "Black" e R$.
        // Se reconhecemos o próprio Sr. Rotas, isso tem prioridade sobre qualquer texto embutido.
        if (ownApp) return Kind.OWN_APP
        if (explicitCardAnchor && hasMoney) return Kind.OFFER_CANDIDATE
        if (strongGeometryCard) return Kind.OFFER_CANDIDATE

        val idle = listOf(
            "registro de viagens",
            "tendências de ganhos",
            "tendencias de ganhos",
            "você está online",
            "voce esta online",
            "você está offline",
            "voce esta offline",
            "ficar offline",
            "voltar a ficar online",
            "uber pro",
            "próxima viagem:",
            "proxima viagem:",
        ).any(text::contains)
        if (idle) return Kind.IDLE_OR_HOME

        return Kind.UNKNOWN
    }
}
