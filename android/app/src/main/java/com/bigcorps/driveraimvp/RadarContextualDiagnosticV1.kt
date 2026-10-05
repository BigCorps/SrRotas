package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Observabilidade de homologação do Radar Contextual.
 *
 * Não é fonte de decisão operacional. O runtime continua resolvendo o destino
 * exclusivamente por currentRide.localOfferId -> LocalStore -> RideOffer.context.
 *
 * O JSON deliberadamente NÃO exporta endereço textual nem coordenadas exatas.
 */
object RadarContextualDiagnosticV1 {
    private val lock = Any()

    private var runtimeRunning = false
    private var fetching = false
    private var lastQueryAtMs = 0L
    private var lastQuerySource = ""
    private var lastQueryOfferId: String? = null
    private var backendState = "ainda não consultado"
    private var backendStatus: Int? = null
    private var backendError: String? = null
    private var poiCount = 0
    private var opportunityCount = 0
    private var assistantEligible: Boolean? = null
    private var assistantReason: String? = null
    private var assistantDeliveryReason: String? = null
    private var selectedOpportunityId: String? = null

    data class Snapshot(
        val stage: String,
        val rideActive: Boolean,
        val localOfferId: String?,
        val sourceOfferFound: Boolean,
        val destinationLabel: String?,
        val destinationLatPresent: Boolean,
        val destinationLngPresent: Boolean,
        val etaPresent: Boolean,
        val etaMinutesRemaining: Long?,
        val runtimeRunning: Boolean,
        val fetching: Boolean,
        val lastQueryAtMs: Long,
        val lastQuerySource: String,
        val lastQueryOfferId: String?,
        val queryMatchesCurrentRide: Boolean?,
        val backendState: String,
        val backendStatus: Int?,
        val backendError: String?,
        val poiCount: Int,
        val opportunityCount: Int,
        val assistantEligible: Boolean?,
        val assistantReason: String?,
        val assistantDeliveryReason: String?,
        val selectedOpportunityId: String?,
        val lastAssistantShownAtMs: Long,
        val cooldownRemainingMs: Long,
    )

    fun setRuntimeRunning(value: Boolean) {
        synchronized(lock) { runtimeRunning = value }
    }

    fun setFetching(value: Boolean) {
        synchronized(lock) { fetching = value }
    }

    fun queryStarted(context: Context, source: String) {
        val live = RadarDestinationContextV1.current(context)
        synchronized(lock) {
            fetching = true
            lastQueryAtMs = System.currentTimeMillis()
            lastQuerySource = source
            lastQueryOfferId = live?.localOfferId
            backendState = "consultando"
            backendError = null
        }
    }

    fun httpStatus(status: Int) {
        synchronized(lock) {
            backendStatus = status
            backendState = if (status in 200..299) "sucesso" else "erro"
        }
    }

    fun cacheHit() {
        synchronized(lock) {
            fetching = false
            backendState = "cache"
            backendError = null
        }
    }

    fun acceptResult(result: RadarContextualResultV1, source: String) {
        synchronized(lock) {
            fetching = false
            lastQuerySource = source
            backendState = if (backendState == "cache") "cache" else "sucesso"
            backendError = null
            poiCount = result.opportunities.mapNotNull { it.poiId.takeIf(String::isNotBlank) }.distinct().size
            opportunityCount = result.opportunities.size
            assistantEligible = result.assistant.eligible
            assistantReason = result.assistant.reason
            selectedOpportunityId = result.assistant.opportunityId
        }
    }

    fun failure(error: Throwable) {
        synchronized(lock) {
            fetching = false
            backendState = "erro"
            backendError = (error.message ?: error.javaClass.simpleName).take(180)
            poiCount = 0
            opportunityCount = 0
        }
    }

    fun assistantDecision(
        result: RadarContextualResultV1,
        deliveryReason: String,
    ) {
        synchronized(lock) {
            assistantEligible = result.assistant.eligible
            assistantReason = result.assistant.reason
            assistantDeliveryReason = deliveryReason
            selectedOpportunityId = result.assistant.opportunityId
        }
    }

    fun userSelectedOpportunity(id: String) {
        synchronized(lock) { selectedOpportunityId = id }
    }

    fun rideChanged() {
        synchronized(lock) {
            fetching = false
            poiCount = 0
            opportunityCount = 0
            assistantEligible = null
            assistantReason = null
            assistantDeliveryReason = null
            selectedOpportunityId = null
            backendState = "aguardando consulta da corrida atual"
            backendStatus = null
            backendError = null
        }
    }

    fun noActiveRide() {
        synchronized(lock) {
            fetching = false
            poiCount = 0
            opportunityCount = 0
            assistantEligible = null
            assistantReason = null
            assistantDeliveryReason = null
            selectedOpportunityId = null
        }
    }

    fun snapshot(context: Context): Snapshot {
        val journey = JourneyCoordinator.snapshot(context)
        val ride = journey.currentRide
        val offer =
            ride?.let { active ->
                journey.latestOffer?.takeIf { it.localId == active.localOfferId }
                    ?: LocalStore.get(context).recentOffers(100)
                        .firstOrNull { it.localId == active.localOfferId }
            }
        val c = offer?.context
        val eta = c?.estimatedArrivalAt?.takeIf(String::isNotBlank)
        val etaMinutes = eta?.let {
            runCatching {
                ((Instant.parse(it).toEpochMilli() - System.currentTimeMillis() + 59_999L) / 60_000L)
                    .coerceAtLeast(0L)
            }.getOrNull()
        }
        val cooldown = DestinationRadarAssistantBridgeV1.diagnosticState(context)

        return synchronized(lock) {
            Snapshot(
                stage = RadarContextualFlagsV1.stage(context),
                rideActive = journey.isDoingRide,
                localOfferId = ride?.localOfferId,
                sourceOfferFound = offer != null,
                destinationLabel = c?.destinationLabel?.takeIf(String::isNotBlank),
                destinationLatPresent = c?.destinationLat != null,
                destinationLngPresent = c?.destinationLng != null,
                etaPresent = eta != null,
                etaMinutesRemaining = etaMinutes,
                runtimeRunning = runtimeRunning,
                fetching = fetching,
                lastQueryAtMs = lastQueryAtMs,
                lastQuerySource = lastQuerySource,
                lastQueryOfferId = lastQueryOfferId,
                queryMatchesCurrentRide =
                    if (ride == null || lastQueryOfferId.isNullOrBlank()) null
                    else ride.localOfferId == lastQueryOfferId,
                backendState = backendState,
                backendStatus = backendStatus,
                backendError = backendError,
                poiCount = poiCount,
                opportunityCount = opportunityCount,
                assistantEligible = assistantEligible,
                assistantReason = assistantReason,
                assistantDeliveryReason = assistantDeliveryReason,
                selectedOpportunityId = selectedOpportunityId,
                lastAssistantShownAtMs = cooldown.lastShownAtMs,
                cooldownRemainingMs = cooldown.remainingMs,
            )
        }
    }

    fun renderField(context: Context): String {
        val s = snapshot(context)
        fun yesNo(value: Boolean) = if (value) "SIM" else "NÃO"
        val queryTime = if (s.lastQueryAtMs > 0L) formatTime(s.lastQueryAtMs) else "—"
        val backend = buildString {
            append(s.backendState.uppercase(Locale("pt", "BR")))
            s.backendStatus?.let { append(" · HTTP ").append(it) }
            if (!s.backendError.isNullOrBlank()) append(" · ").append(s.backendError)
        }
        val cooldown = when {
            s.lastAssistantShownAtMs <= 0L -> "nenhum disparo"
            s.cooldownRemainingMs > 0L ->
                "${formatTime(s.lastAssistantShownAtMs)} · ${s.cooldownRemainingMs / 1000L}s restantes"
            else -> "${formatTime(s.lastAssistantShownAtMs)} · liberado"
        }

        return buildString {
            append("fonte: currentRide.localOfferId → LocalStore → RideOffer.context\n")
            append("corrida ativa: ").append(yesNo(s.rideActive)).append('\n')
            append("localOfferId: ").append(s.localOfferId ?: "—").append('\n')
            append("oferta da corrida encontrada: ").append(yesNo(s.sourceOfferFound)).append('\n')
            append("destino real: ").append(s.destinationLabel ?: "—").append('\n')
            append("destinationLat/Lng presentes: ")
                .append(yesNo(s.destinationLatPresent)).append("/")
                .append(yesNo(s.destinationLngPresent)).append('\n')
            append("ETA: ").append(yesNo(s.etaPresent))
            s.etaMinutesRemaining?.let { append(" · ").append(it).append(" min restantes") }
            append('\n')
            append("runtime: ").append(if (s.runtimeRunning) "RODANDO" else "PARADO")
            if (s.fetching) append(" · consultando")
            append('\n')
            append("última consulta Radar: ").append(queryTime)
            if (s.lastQuerySource.isNotBlank()) append(" · ").append(s.lastQuerySource)
            append('\n')
            append("consulta usa corrida atual: ")
                .append(s.queryMatchesCurrentRide?.let(::yesNo) ?: "—").append('\n')
            append("backend: ").append(backend).append('\n')
            append("POIs recebidos: ").append(s.poiCount).append('\n')
            append("oportunidades: ").append(s.opportunityCount).append('\n')
            append("assistant.eligible: ").append(s.assistantEligible?.toString() ?: "—").append('\n')
            append("assistant.reason: ").append(s.assistantReason ?: "—").append('\n')
            append("entrega assistente: ").append(s.assistantDeliveryReason ?: "—").append('\n')
            append("opportunityId selecionada: ").append(s.selectedOpportunityId ?: "—").append('\n')
            append("último disparo/cooldown: ").append(cooldown)
        }
    }

    fun toJson(context: Context): JSONObject {
        val s = snapshot(context)
        return JSONObject().apply {
            put("schema", "sr-radar-contextual-diagnostic-v1")
            put("stage", s.stage)
            put("source_chain", "currentRide.localOfferId -> LocalStore -> RideOffer.context")
            put("ride_active", s.rideActive)
            putOpt("current_local_offer_id", s.localOfferId)
            put("source_offer_found", s.sourceOfferFound)
            put("destination_label_present", !s.destinationLabel.isNullOrBlank())
            put("destination_lat_present", s.destinationLatPresent)
            put("destination_lng_present", s.destinationLngPresent)
            put("eta_present", s.etaPresent)
            putOpt("eta_minutes_remaining", s.etaMinutesRemaining)
            put("runtime_running", s.runtimeRunning)
            put("fetching", s.fetching)
            putOpt("last_query_at", s.lastQueryAtMs.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() })
            put("last_query_source", s.lastQuerySource)
            putOpt("last_query_local_offer_id", s.lastQueryOfferId)
            putOpt("query_matches_current_ride", s.queryMatchesCurrentRide)
            put("backend_state", s.backendState)
            putOpt("backend_status", s.backendStatus)
            putOpt("backend_error", s.backendError)
            put("pois_received", s.poiCount)
            put("opportunities", s.opportunityCount)
            putOpt("assistant_eligible", s.assistantEligible)
            putOpt("assistant_reason", s.assistantReason)
            putOpt("assistant_delivery_reason", s.assistantDeliveryReason)
            putOpt("selected_opportunity_id", s.selectedOpportunityId)
            putOpt(
                "last_assistant_shown_at",
                s.lastAssistantShownAtMs.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() },
            )
            put("assistant_cooldown_remaining_seconds", s.cooldownRemainingMs / 1000L)
            put("demo_data_included", false)
            put("exports_destination_label", false)
            put("exports_coordinates", false)
        }
    }

    private fun formatTime(ms: Long): String =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale("pt", "BR"))
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(ms))
}
