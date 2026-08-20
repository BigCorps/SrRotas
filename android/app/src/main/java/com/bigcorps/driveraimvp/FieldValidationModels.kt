package com.srrotas.app

enum class FieldValidationStatus {
    PASS,
    WARN,
    FAIL,
    MANUAL,
}

data class FieldValidationCheck(
    val id: String,
    val title: String,
    val status: FieldValidationStatus,
    val detail: String,
)

data class FieldValidationFacts(
    val offers: Int = 0,
    val exclusiveOffers: Int = 0,
    val radarOffers: Int = 0,
    val offersWithContext: Int = 0,
    val offersWithDestinationCell: Int = 0,
    val resolvedContexts: Int = 0,
    val closedExposures: Int = 0,
    val journeyEvents: Int = 0,
    val rideOutcomes: Int = 0,
    val completedRides: Int = 0,
    val importedOffers: Int = 0,
    val duplicateImports: Int = 0,
    val failedImports: Int = 0,
    val costConfigured: Boolean = false,
    val offersWithCostSnapshot: Int = 0,
    val probabilityReadyCells: Int = 0,
    val probabilityGuardrailViolations: Int = 0,
    val pendingOffers: Int = 0,
    val pendingContexts: Int = 0,
    val pendingJourneyEvents: Int = 0,
    val pendingRideOutcomes: Int = 0,
    val pendingExposures: Int = 0,
    val online: Boolean = false,
    val paired: Boolean = false,
    val overlayAllowed: Boolean = false,
    val coarseLocationAllowed: Boolean = false,
    val pendingCrash: Boolean = false,
) {
    val pendingTotal: Int
        get() = pendingOffers +
            pendingContexts +
            pendingJourneyEvents +
            pendingRideOutcomes +
            pendingExposures
}

data class ManualFieldValidationItem(
    val id: String,
    val label: String,
    val help: String,
)

object FieldValidationManualChecklist {
    val items = listOf(
        ManualFieldValidationItem(
            "exclusive_context",
            "Exclusive: retirada e destino corretos",
            "Conferir pelo menos 5 ofertas Exclusive e comparar o texto do card com Retirada/Destino do Sr. Rotas.",
        ),
        ManualFieldValidationItem(
            "radar_context",
            "Radar: nenhum contexto misturado entre cards",
            "Em Radar com vários cards, conferir valor, retirada e destino de cada oportunidade. Qualquer cruzamento entre cards é P1.",
        ),
        ManualFieldValidationItem(
            "maps_pickup",
            "Maps abre a retirada correta",
            "Testar pela notificação e pelo mascote com o veículo parado.",
        ),
        ManualFieldValidationItem(
            "maps_destination_eta",
            "Destino + ETA coerentes",
            "Abrir o destino no Maps e conferir se a ETA local faz sentido com pickup + viagem mostrados na oferta.",
        ),
        ManualFieldValidationItem(
            "hud_stability",
            "HUD estável e sem perder chamadas",
            "Comparar com o comportamento conhecido da 0.16: sem piscar, sem travar e sem redução perceptível das leituras.",
        ),
        ManualFieldValidationItem(
            "journey_pause_resume",
            "Pausar e retomar jornada funciona",
            "Pausado não deve criar novas exposições/ofertas operacionais; ao retomar, o fluxo volta normalmente.",
        ),
        ManualFieldValidationItem(
            "ride_outcomes",
            "Estou fazendo / realizada / não realizada",
            "Testar iniciar corrida, finalizar, cancelar e corrigir posteriormente pelo Histórico.",
        ),
        ManualFieldValidationItem(
            "regional_exposure",
            "Exposição regional acompanha tempo disponível",
            "Ficar alguns minutos disponível sem corrida e depois receber uma oferta; o relatório automático deve registrar exposição fechada.",
        ),
        ManualFieldValidationItem(
            "historical_import",
            "Importação deduplica screenshots",
            "Importar o mesmo arquivo duas vezes; a segunda passagem não pode criar uma nova oferta.",
        ),
        ManualFieldValidationItem(
            "cost_memory",
            "Custos e memória do Lucro est.* conferem",
            "Configurar um exemplo conhecido e conferir manualmente custo/km, custo da oferta e memória do cálculo.",
        ),
        ManualFieldValidationItem(
            "statistics_guardrail",
            "Dados insuficientes não viram percentual",
            "Em região com amostra pequena, a interface precisa continuar mostrando Dados insuficientes em vez de percentual inventado.",
        ),
        ManualFieldValidationItem(
            "performance_session",
            "Sessão longa sem degradação perceptível",
            "Rodar pelo menos 30 minutos com a medição 0.19 ligada e observar bateria, aquecimento, lentidão e perda de chamadas.",
        ),
        ManualFieldValidationItem(
            "offline_sync",
            "Fila offline volta a zero após reconectar",
            "Gerar dados offline, reconectar e usar Sincronizar agora; as filas devem esvaziar sem duplicar ofertas.",
        ),
    )
}

object FieldValidationAssessment {
    fun evaluate(
        facts: FieldValidationFacts,
    ): List<FieldValidationCheck> {
        val result = mutableListOf<FieldValidationCheck>()

        result += FieldValidationCheck(
            id = "offer_engine_volume",
            title = "Offer Engine numérico",
            status = if (facts.offers >= 10) {
                FieldValidationStatus.PASS
            } else {
                FieldValidationStatus.WARN
            },
            detail = "${facts.offers} ofertas locais · ${facts.exclusiveOffers} Exclusive · ${facts.radarOffers} Radar. " +
                if (facts.offers >= 10) {
                    "Há base local para a rodada; a exatidão numérica continua sendo conferência manual de campo."
                } else {
                    "Ainda há poucas ofertas nesta instalação para concluir a rodada."
                },
        )

        result += FieldValidationCheck(
            id = "context_persistence",
            title = "Context Engine persistindo",
            status = when {
                facts.offers == 0 -> FieldValidationStatus.WARN
                facts.offersWithContext == 0 -> FieldValidationStatus.WARN
                facts.offersWithDestinationCell == 0 -> FieldValidationStatus.WARN
                else -> FieldValidationStatus.PASS
            },
            detail = "${facts.offersWithContext} oferta(s) com contexto · ${facts.offersWithDestinationCell} com célula de destino · ${facts.resolvedContexts} contexto(s) geocodificado(s).",
        )

        result += FieldValidationCheck(
            id = "radar_card_mix",
            title = "Radar não mistura cards",
            status = if (facts.radarOffers > 0) {
                FieldValidationStatus.MANUAL
            } else {
                FieldValidationStatus.WARN
            },
            detail = if (facts.radarOffers > 0) {
                "Há ${facts.radarOffers} oferta(s) Radar na base. A ausência de mistura entre cards precisa ser confirmada visualmente."
            } else {
                "Nenhuma oferta Radar nesta instalação; este item ainda não foi exercitado."
            },
        )

        result += FieldValidationCheck(
            id = "maps_eta",
            title = "Maps e ETA",
            status = FieldValidationStatus.MANUAL,
            detail = "Abertura correta do Maps e coerência visual da ETA exigem comparação com a oferta real.",
        )

        result += FieldValidationCheck(
            id = "journey_state",
            title = "Jornada e estado de corrida",
            status = if (
                facts.journeyEvents > 0 &&
                facts.rideOutcomes > 0
            ) {
                FieldValidationStatus.PASS
            } else {
                FieldValidationStatus.WARN
            },
            detail = "${facts.journeyEvents} evento(s) de jornada · ${facts.rideOutcomes} outcome(s) · ${facts.completedRides} corrida(s) concluída(s).",
        )

        result += FieldValidationCheck(
            id = "regional_exposure",
            title = "Denominador de exposição",
            status = if (facts.closedExposures > 0) {
                FieldValidationStatus.PASS
            } else {
                FieldValidationStatus.WARN
            },
            detail = "${facts.closedExposures} exposição(ões) regional(is) fechada(s). " +
                if (facts.closedExposures > 0) {
                    "O Motor Estatístico possui denominador observado."
                } else {
                    "Ainda não há denominador local para probabilidade."
                },
        )

        result += FieldValidationCheck(
            id = "historical_import",
            title = "Importação histórica",
            status = when {
                facts.importedOffers == 0 -> FieldValidationStatus.MANUAL
                facts.failedImports > 0 -> FieldValidationStatus.WARN
                else -> FieldValidationStatus.PASS
            },
            detail = "${facts.importedOffers} oferta(s) importada(s) · ${facts.duplicateImports} duplicata(s) evitada(s) · ${facts.failedImports} falha(s).",
        )

        result += FieldValidationCheck(
            id = "probability_guardrail",
            title = "Guardrail estatístico",
            status = when {
                facts.probabilityGuardrailViolations > 0 -> FieldValidationStatus.FAIL
                facts.closedExposures > 0 -> FieldValidationStatus.PASS
                else -> FieldValidationStatus.WARN
            },
            detail = if (facts.probabilityGuardrailViolations > 0) {
                "ERRO: ${facts.probabilityGuardrailViolations} resultado(s) publicaram percentual abaixo da amostra mínima."
            } else {
                "Nenhuma violação detectada · ${facts.probabilityReadyCells} região(ões) com amostra suficiente para P10."
            },
        )

        result += FieldValidationCheck(
            id = "cost_profile",
            title = "Custos e memória do Lucro est.*",
            status = if (facts.costConfigured) {
                FieldValidationStatus.PASS
            } else {
                FieldValidationStatus.MANUAL
            },
            detail = if (facts.costConfigured) {
                "Perfil 0.18 configurado · ${facts.offersWithCostSnapshot} oferta(s) local(is) com snapshot de custo."
            } else {
                "Perfil de custos ainda não configurado nesta instalação. ${facts.offersWithCostSnapshot} oferta(s) já possuem snapshot/reconstrução de custo."
            },
        )

        result += FieldValidationCheck(
            id = "sync_queue",
            title = "Sincronização",
            status = when {
                !facts.paired -> FieldValidationStatus.WARN
                !facts.online -> FieldValidationStatus.MANUAL
                facts.pendingTotal == 0 -> FieldValidationStatus.PASS
                else -> FieldValidationStatus.WARN
            },
            detail = "Fila total ${facts.pendingTotal}: ofertas ${facts.pendingOffers}, contextos ${facts.pendingContexts}, jornada ${facts.pendingJourneyEvents}, outcomes ${facts.pendingRideOutcomes}, exposições ${facts.pendingExposures}. " +
                when {
                    !facts.paired -> "Aparelho sem sessão de nuvem."
                    !facts.online -> "Offline: validar novamente depois de reconectar."
                    facts.pendingTotal == 0 -> "Fila local vazia."
                    else -> "Há itens aguardando envio; usar Sincronizar agora e conferir se a fila volta a zero."
                },
        )

        result += FieldValidationCheck(
            id = "permissions",
            title = "Permissões operacionais",
            status = if (
                facts.overlayAllowed &&
                facts.coarseLocationAllowed
            ) {
                FieldValidationStatus.PASS
            } else {
                FieldValidationStatus.WARN
            },
            detail = "HUD ${if (facts.overlayAllowed) "autorizado" else "sem permissão"} · localização aproximada ${if (facts.coarseLocationAllowed) "autorizada" else "não autorizada"}.",
        )

        result += FieldValidationCheck(
            id = "crash",
            title = "Crash técnico pendente",
            status = if (facts.pendingCrash) {
                FieldValidationStatus.WARN
            } else {
                FieldValidationStatus.PASS
            },
            detail = if (facts.pendingCrash) {
                "Existe crash pendente de envio/triagem nesta instalação."
            } else {
                "Nenhum crash pendente registrado pelo beta telemetry."
            },
        )

        result += FieldValidationCheck(
            id = "performance",
            title = "Bateria / CPU / aquecimento",
            status = FieldValidationStatus.MANUAL,
            detail = "A 0.19 mede duração, CPU do processo, bateria, PSS e estado térmico, mas não inventa um limite de aprovação antes da rodada de campo.",
        )

        return result
    }
}
