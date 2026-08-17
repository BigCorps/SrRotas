package com.srrotas.app

/**
 * Gate textual conservador antes do parser financeiro.
 * MediaProjection enxerga a tela inteira e não informa qual app está em primeiro plano.
 */
object UberScreenGate {
    enum class Kind { OFFER_CANDIDATE, OWN_APP, FOREIGN_UI, IDLE_OR_HOME, UNKNOWN }

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

        // Tem prioridade: o diagnóstico do próprio Sr. Rotas pode conter textos antigos de oferta.
        if (ownApp) return Kind.OWN_APP

        // Evita interpretar print/card antigo dentro de Recentes, WhatsApp ou galeria.
        // Não basta uma palavra genérica: usamos marcadores fortes/combinações.
        val recentsUi = listOf("close all", "fechar tudo", "limpar tudo").any(text::contains)
        val notificationSummary = (text.contains("resumir") && (text.contains("não lida") || text.contains("nao lida")))
        val whatsappUi = text.contains("whatsapp") && listOf("mensagem", "conversas", "responder").any(text::contains)
        val galleryUi = listOf("galeria", "google fotos", "photos").any(text::contains) &&
            listOf("editar", "compartilhar", "excluir").any(text::contains)
        if (recentsUi || notificationSummary || whatsappUi || galleryUi) return Kind.FOREIGN_UI

        val explicitCardAnchor = listOf(
            "radar de viagens",
            "exclusivo",
            "aceitar",
            "selecionar",
            "uberx",
            "comfort",
            "priority",
            "electric",
            "black",
            "uber moto",
            "ubermoto",
        ).any(text::contains)

        val hasMoney = Regex("r\\$|\\$\\s*[0-9]", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasAdvertisedPerKm = Regex("(?:r\\$|\\$)\\s*[0-9osil.,]+\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasTimeDistance = Regex("[0-9osil]+\\s*(?:min|minuto|minutos)\\s*\\([^)]*km", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val strongGeometryCard = hasMoney && hasAdvertisedPerKm && hasTimeDistance

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
