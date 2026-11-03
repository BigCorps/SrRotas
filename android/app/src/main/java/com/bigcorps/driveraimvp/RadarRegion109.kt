package com.srrotas.app

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * vc109 — Radar sem corrida: motivo explícito, mensagens que distinguem ausência de dados,
 * ausência de destino, recurso desativado e falha da API, e o texto dos eventos verificados
 * (origem, horário, janela de saída, confiança e validade). Nada é inventado: só exibe o que
 * o backend devolveu.
 */
object RadarRegion109 {
    /** Por que o Radar não está cruzando um destino agora. */
    fun idleReason(context: Context): String {
        val snapshot = runCatching { JourneyCoordinator.snapshot(context) }.getOrNull()
        if (snapshot == null || snapshot.journeyId.isNullOrBlank()) return "Sem jornada ativa: consulte sua região ou inicie uma jornada para cruzar com o destino."
        if (!snapshot.isDoingRide || snapshot.currentRide == null) return "Nenhuma corrida marcada em andamento (\"Estou nessa corrida\"): mostrando sua região."
        val offer = LocalStore.get(context).offerByLocalId(snapshot.currentRide.localOfferId)
        return when (FieldPipelineFactsV1.specBlock(offer)) {
            "source_offer_missing" -> "A oferta da corrida atual não foi encontrada neste aparelho."
            "context_missing" -> "A corrida atual não tem endereços lidos: destino indisponível."
            "destination_coordinates_missing_or_invalid" -> "O destino da corrida ainda não foi localizado no mapa (geocodificação pendente ou indisponível)."
            "eta_missing_or_invalid" -> "A corrida atual não tem horário estimado de chegada."
            else -> "Destino disponível: toque em Consultar destino atual."
        }
    }

    /** Mensagem amigável por tipo de falha (sem expor detalhes internos). */
    fun failure(message: String?): String {
        val m = message.orEmpty()
        return when {
            m.contains("Conecte sua conta") -> "Conecte sua conta para consultar o Radar."
            m.contains("access_restricted") || m.contains("HTTP 402") || m.contains("HTTP 403") || m.contains("blocked") ->
                "Radar indisponível no seu acesso atual (plano/assinatura)."
            m.contains("Localização") -> "Localização ainda indisponível. Ative a localização e tente de novo."
            m.contains("HTTP 5") -> "O serviço do Radar falhou agora (erro do servidor). Tente novamente em instantes."
            m.contains("Contrato") -> "Versão do Radar incompatível; atualize o aplicativo."
            m.contains("Unable to resolve") || m.contains("timeout", true) || m.contains("failed to connect", true) ->
                "Sem conexão com o Radar agora."
            m.isBlank() -> "O Radar não respondeu. Tente novamente."
            else -> "O Radar não respondeu (${m.take(40)})."
        }
    }

    /** Ausência de dados é diferente de falha: explica o que o backend informou. */
    fun emptyEvents(sourceStatus: String): String = when (sourceStatus) {
        "source_not_configured" -> "Nenhum evento verificado: a fonte de eventos ainda não está configurada no servidor."
        "configured_no_refresh" -> "Nenhum evento verificado: a fonte de eventos ainda não foi atualizada."
        "error" -> "Nenhum evento verificado: a última atualização da fonte falhou."
        else -> "Nenhum evento verificado num raio de 15 km nas próximas 8 h."
    }

    fun emptyOpportunities(): String = "Nenhuma oportunidade verificada num raio de 4 km agora (o catálogo de lugares verificados ainda é pequeno)."

    /** Linhas de um evento: horário, saída, origem, confiança e validade. */
    fun eventLines(e: EventRadarOpportunity026, nowMs: Long = System.currentTimeMillis()): List<String> {
        val place = listOfNotNull(e.venueName, e.address).joinToString(" · ")
        val conf = (e.confidence * 100).toInt().coerceIn(0, 100)
        val validUntil = runCatching { Instant.parse(e.egressEndAt).toEpochMilli() }.getOrNull()
        val status = when {
            validUntil == null -> "validade não informada"
            validUntil < nowMs -> "encerrado"
            else -> "válido até ${hm(e.egressEndAt)}"
        }
        return listOfNotNull(
            place.takeIf(String::isNotBlank),
            "${EventRadarRules026.typeLabel(e.type)} · ${hm(e.startsAt)}–${hm(e.expectedEndAt)} · ${String.format(Locale("pt", "BR"), "%.1f", e.distanceKm)} km",
            "Saída estimada ${hm(e.egressStartAt)}–${hm(e.egressEndAt)} · $status",
            "Fonte: ${e.source} · confiança $conf%",
        )
    }

    fun hm(iso: String): String = runCatching {
        DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR")).withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(iso))
    }.getOrDefault("—")

    /** ETA usado para consultar a região "agora" (minuto cheio, mantém o cache do cliente estável). */
    fun regionEta(nowMs: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(nowMs + 10 * 60_000L).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()
}
