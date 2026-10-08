package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
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
    private var assistantKind: String? = null // sinal da decisão atual; null = nenhuma entrega proposta
    private var lastRenderedAssistantKind: String? = null
    private var regionGenerated = false
    private var regionRendered = false
    private var regionClicked = false
    private var selectedOpportunityId: String? = null

    private var lastResolvedOfferId: String? = null
    private var launchState = "nenhuma tentativa"
    private var lastLaunchKind = ""
    private var mapState = "não criado"
    private var mapActiveSinceMs = 0L
    private val recentTransitions = ArrayDeque<String>()

    data class SurfaceState(
        val source:String="",val kind:String="",val state:String="idle",val attempts:Int=0,
        val routeSelected:Boolean=false,val panelAttached:Boolean=false,val panelShown:Boolean=false,
        val stageWidth:Int=0,val stageHeight:Int=0,val panelWidth:Int=0,val panelHeight:Int=0,
        val deferred:Boolean=false,val started:Boolean=false,val completed:Boolean=false,val blockReason:String?=null,
    )
    private var surface=SurfaceState()
    private var demoOpened=false
    private var demoRendered=false
    private var mapCreatedCount=0
    private var mapReadyCount=0
    private var mapReleaseCount=0
    private var lastMapError:String?=null
    private var lastMapReleaseReason:String?=null
    data class MapRenderDiagnostic(
        val styleLoaded:Boolean=false, val loadingFinished:Boolean=false, val firstFrame:Boolean=false,
        val fullyRendered:Boolean=false, val timeout:Boolean=false, val renderError:Boolean=false, val loadError:Boolean=false,
        val loadTimeout:Boolean=false, val lateRecovered:Boolean=false,
        val styleCount:Int=0, val firstFrameCount:Int=0, val fullyCount:Int=0,
        val timeoutCount:Int=0, val renderErrorCount:Int=0, val lastRenderError:String?=null,
    )
    private var mapRender=MapRenderDiagnostic()

    fun mapEvent(event:String, error:String?=null) { synchronized(lock) {
        // Categorias fixas: nunca exportar mensagens nativas que possam conter URLs/coordenadas.
        val safeError=error?.let {
            when {
                it.contains("network",true) || it.contains("http",true) -> "network_error"
                it.contains("style",true) -> "style_error"
                it.contains("tile",true) -> "tile_error"
                else -> "renderer_or_load_error"
            }
        }
        mapRender=when(event) {
            "map_style_loaded" -> mapRender.copy(styleLoaded=true,styleCount=mapRender.styleCount+1)
            "map_loading_finished" -> mapRender.copy(loadingFinished=true)
            "map_first_frame" -> mapRender.copy(firstFrame=true,firstFrameCount=mapRender.firstFrameCount+1)
            "map_fully_rendered" -> mapRender.copy(fullyRendered=true,fullyCount=mapRender.fullyCount+1)
            "map_render_timeout" -> mapRender.copy(timeout=true,timeoutCount=mapRender.timeoutCount+1)
            "map_load_timeout" -> mapRender.copy(loadTimeout=true)
            "map_late_render_recovered" -> mapRender.copy(lateRecovered=true)
            "map_render_error" -> mapRender.copy(renderError=true,renderErrorCount=mapRender.renderErrorCount+1,lastRenderError=safeError)
            "map_load_error" -> mapRender.copy(loadError=true,lastRenderError=safeError)
            else -> return
        }
        mapState=when(event) {
            "map_style_loaded" -> if (mapState == "error") mapState else "style_loaded"
            "map_fully_rendered", "map_late_render_recovered" -> "rendered"
            "map_render_timeout" -> "render_timeout"
            "map_load_timeout" -> "load_timeout"
            "map_render_error", "map_load_error" -> "error"
            else -> mapState
        }
        if (safeError!=null) lastMapError=safeError
        pushLocked(event)
    } }

    fun surfaceRequested(source:String,kind:String) { synchronized(lock) {
        surface=SurfaceState(source=source,kind=kind,state="pending")
        pushLocked("surface_request:$source:$kind")
    } }
    internal fun surfaceMeasured(attempt:Int,routeSelected:Boolean,m:RadarSurfaceCoordinatorV1.Metrics) { synchronized(lock) {
        surface=surface.copy(state=if(m.ready) "ready" else "waiting_layout",attempts=attempt,routeSelected=routeSelected,
            panelAttached=m.panelAttached,panelShown=m.shown,stageWidth=m.stageWidth,stageHeight=m.stageHeight,
            panelWidth=m.panelWidth,panelHeight=m.panelHeight)
    } }
    fun surfaceBlocked(reason:String) { synchronized(lock) {
        surface=surface.copy(state="blocked",blockReason=reason)
        pushLocked("surface_blocked:$reason")
    } }
    fun surfaceRenderDeferred() { synchronized(lock) { surface=surface.copy(state="deferred",deferred=true) } }
    fun surfaceRenderStarted() { synchronized(lock) { surface=surface.copy(state="rendering",started=true,blockReason=null) } }
    fun surfaceRenderCompleted() { synchronized(lock) { surface=surface.copy(state="rendered",completed=true) } }

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
        val etaDeltaSeconds:Long?,
        val surface:SurfaceState,
        val demoOpened:Boolean,
        val demoRendered:Boolean,
        val mapCreatedCount:Int,
        val mapReadyCount:Int,
        val mapRender:MapRenderDiagnostic,
        val mapReleaseCount:Int,
        val lastMapError:String?,
        val lastMapReleaseReason:String?,
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
        val fieldR4MigrationApplied: Boolean,
        val assistantKind: String?,
        val lastRenderedAssistantKind: String?,
        val regionGenerated: Boolean,
        val regionRendered: Boolean,
        val regionClicked: Boolean,
        val selectedOpportunityId: String?,
        val lastAssistantShownAtMs: Long,
        val cooldownRemainingMs: Long,
        val regionLastRideMatchesCurrent:Boolean,
        val launchState: String,
        val lastLaunchKind: String,
        val mapState: String,
        val mapActive: Boolean,
        val mapActiveMsCurrent: Long,
        val recentTransitions: List<String>,
    )

    private fun short(id: String?): String = id?.take(8) ?: "—"

    private fun pushLocked(value: String) {
        if (recentTransitions.size >= 18) recentTransitions.removeFirst()
        recentTransitions.addLast("${System.currentTimeMillis()}|$value")
    }

    fun setRuntimeRunning(value: Boolean) {
        synchronized(lock) { runtimeRunning = value }
    }

    fun setFetching(value: Boolean) {
        synchronized(lock) { fetching = value }
    }

    fun rideMarkRequested(localOfferId: String) {
        synchronized(lock) {
            pushLocked("ride_mark_requested:${short(localOfferId)}")
        }
    }

    fun rideMarkSucceeded(localOfferId: String) {
        synchronized(lock) {
            pushLocked("ride_mark_success:${short(localOfferId)}")
        }
    }

    fun rideMarkFailed(localOfferId: String) {
        synchronized(lock) {
            pushLocked("ride_mark_failed:${short(localOfferId)}")
        }
    }

    fun specResolved(localOfferId: String) {
        synchronized(lock) {
            if (lastResolvedOfferId != localOfferId) {
                lastResolvedOfferId = localOfferId
                pushLocked("spec_resolved:${short(localOfferId)}")
            }
        }
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
            pushLocked("backend_query:$source:${short(lastQueryOfferId)}")
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
            pushLocked("backend_cache")
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
            result.assistant.opportunityId?.let { selectedOpportunityId = it }
            pushLocked("backend_success:$source:pois=$poiCount:ops=$opportunityCount")
        }
    }

    fun failure(error: Throwable) {
        synchronized(lock) {
            fetching = false
            backendState = "erro"
            backendError = (error.message ?: error.javaClass.simpleName).take(180)
            poiCount = 0
            opportunityCount = 0
            pushLocked("backend_error:${error.javaClass.simpleName}")
        }
    }

    fun assistantDecision(
        result: RadarContextualResultV1,
        deliveryReason: String,
        signal: DestinationRadarAssistantBridgeV1.Signal? = null,
    ) {
        synchronized(lock) {
            assistantEligible = result.assistant.eligible
            assistantReason = result.assistant.reason
            assistantDeliveryReason = deliveryReason
            assistantKind = signal?.kind?.name?.lowercase(Locale.ROOT)
            signal?.opportunityId?.let { selectedOpportunityId = it }
            if(signal?.kind == DestinationRadarAssistantBridgeV1.Kind.REGION) {
                regionGenerated = true
                pushLocked("region_signal_generated")
            }
            pushLocked("assistant_decision:$deliveryReason")
        }
    }

    fun assistantRenderBlocked(reason: String) {
        synchronized(lock) {
            assistantDeliveryReason = reason
            pushLocked("assistant_blocked:$reason")
        }
    }

    fun assistantRendered(kind: DestinationRadarAssistantBridgeV1.Kind) {
        synchronized(lock) {
            assistantDeliveryReason = "exibido"
            lastRenderedAssistantKind = kind.name.lowercase(Locale.ROOT)
            if(kind == DestinationRadarAssistantBridgeV1.Kind.REGION) {
                regionRendered = true
                pushLocked("region_signal_rendered")
            }
            pushLocked("assistant_shown")
        }
    }

    fun fieldR4MigrationApplied() { synchronized(lock) { pushLocked("field_r4_migration_applied") } }
    fun regionViewClicked() { synchronized(lock) {
        regionClicked = true
        pushLocked("region_view_clicked")
    } }

    fun userSelectedOpportunity(id: String) {
        synchronized(lock) { selectedOpportunityId = id }
    }

    fun launchAttempt(kind: String) {
        synchronized(lock) {
            lastLaunchKind = kind
            launchState = "tentativa"
            pushLocked("open_attempt:$kind")
        }
    }

    fun launchSent(kind: String) {
        synchronized(lock) {
            lastLaunchKind = kind
            launchState = "pending_intent_enviado"
            pushLocked("open_sent:$kind")
        }
    }

    fun launchFailed(kind: String, error: Throwable) {
        synchronized(lock) {
            lastLaunchKind = kind
            launchState = "erro:${(error.message ?: error.javaClass.simpleName).take(100)}"
            pushLocked("open_failed:$kind:${error.javaClass.simpleName}")
        }
    }

    fun launchIntentReceived(kind: String, specAvailable: Boolean) {
        synchronized(lock) {
            lastLaunchKind = kind
            launchState = if (specAvailable) "intent_recebido" else "intent_recebido_sem_spec"
            pushLocked("intent_received:$kind:spec=$specAvailable")
        }
    }

    fun hudCurrentRideSourceMissing() { synchronized(lock) { pushLocked("hud_current_ride_source_missing") } }

    fun demoPreviewOpened() { synchronized(lock) { demoOpened=true; demoRendered=false; pushLocked("demo_preview_opened") } }
    fun demoPreviewRendered() { synchronized(lock) { demoRendered=true; pushLocked("demo_preview_rendered") } }

    fun mapCreated() {
        synchronized(lock) {
            mapCreatedCount++
            mapState = "loading"
            mapRender=MapRenderDiagnostic(styleCount=mapRender.styleCount,firstFrameCount=mapRender.firstFrameCount,
                fullyCount=mapRender.fullyCount,timeoutCount=mapRender.timeoutCount,renderErrorCount=mapRender.renderErrorCount,
                lastRenderError=mapRender.lastRenderError)
            mapActiveSinceMs = System.currentTimeMillis()
            pushLocked("map_created")
        }
    }

    fun mapReady() {
        synchronized(lock) {
            mapReadyCount++
            // Native ready não significa rendered.
            if (mapActiveSinceMs <= 0L) mapActiveSinceMs = System.currentTimeMillis()
            pushLocked("map_ready")
        }
    }

    fun mapFailed(message: String) = mapEvent("map_load_error",message)

    fun mapReleased(reason:String="released") {
        synchronized(lock) {
            mapReleaseCount++
            lastMapReleaseReason=reason
            if (mapState != "released") pushLocked("map_released")
            mapState = "released"
            mapActiveSinceMs = 0L
        }
    }

    fun rideChanged() {
        synchronized(lock) {
            fetching = false
            poiCount = 0
            opportunityCount = 0
            assistantEligible = null
            assistantReason = null
            assistantDeliveryReason = null
            assistantKind = null
            lastRenderedAssistantKind = null
            regionGenerated = false
            regionRendered = false
            regionClicked = false
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
            assistantKind = null
            lastRenderedAssistantKind = null
            regionGenerated = false
            regionRendered = false
            regionClicked = false
            selectedOpportunityId = null
            lastResolvedOfferId = null
            lastQueryOfferId = null
            lastQuerySource = ""
            backendState = "sem corrida ativa"
            backendStatus = null
            backendError = null
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
            val mapActive = mapActiveSinceMs > 0L
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
                etaDeltaSeconds=eta?.let { runCatching { (Instant.parse(it).toEpochMilli()-System.currentTimeMillis())/1000L }.getOrNull() },
                surface=surface,demoOpened=demoOpened,demoRendered=demoRendered,
                mapCreatedCount=mapCreatedCount,mapReadyCount=mapReadyCount,mapRender=mapRender,mapReleaseCount=mapReleaseCount,
                lastMapError=lastMapError,lastMapReleaseReason=lastMapReleaseReason,
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
                fieldR4MigrationApplied = RadarContextualFlagsV1.fieldR4MigrationApplied(context),
                assistantKind = assistantKind,
                lastRenderedAssistantKind = lastRenderedAssistantKind,
                regionGenerated = regionGenerated,
                regionRendered = regionRendered,
                regionClicked = regionClicked,
                selectedOpportunityId = selectedOpportunityId,
                lastAssistantShownAtMs = cooldown.lastShownAtMs,
                cooldownRemainingMs = cooldown.remainingMs,
                regionLastRideMatchesCurrent=cooldown.regionLastRideMatchesCurrent,
                launchState = launchState,
                lastLaunchKind = lastLaunchKind,
                mapState = mapState,
                mapActive = mapActive,
                mapActiveMsCurrent = if (mapActive) System.currentTimeMillis() - mapActiveSinceMs else 0L,
                recentTransitions = recentTransitions.toList(),
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
        val lastTransition = s.recentTransitions.lastOrNull()?.substringAfter('|') ?: "—"

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
            append("surface: ").append(s.surface.source).append("/").append(s.surface.kind).append("/").append(s.surface.state)
                .append(" · ").append(s.surface.stageWidth).append("x").append(s.surface.stageHeight)
                .append(" · ").append(s.surface.blockReason ?: "—").append('\n')
            append("eta_delta_seconds: ").append(s.etaDeltaSeconds ?: "—").append('\n')
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
            append("assistant_kind (decisão atual): ").append(s.assistantKind ?: "sem signal").append('\n')
            append("último assistente renderizado: ").append(s.lastRenderedAssistantKind ?: "—").append('\n')
            append("field_r4_migration_applied: ").append(s.fieldR4MigrationApplied).append('\n')
            append("REGION generated/rendered/clicked: ").append(s.regionGenerated).append("/")
                .append(s.regionRendered).append("/").append(s.regionClicked).append('\n')
            append("opportunityId selecionada: ").append(s.selectedOpportunityId ?: "—").append('\n')
            append("último disparo/cooldown por oportunidade: ").append(cooldown).append('\n')
            append("REGION já exibido na corrida atual: ").append(s.regionLastRideMatchesCurrent).append('\n')
            append("abertura Radar: ").append(s.launchState)
            if (s.lastLaunchKind.isNotBlank()) append(" · ").append(s.lastLaunchKind)
            append('\n')
            append("mapa: ").append(s.mapState)
            if (s.mapActive) append(" · ativo ").append(s.mapActiveMsCurrent).append(" ms")
            append('\n')
            append("última transição: ").append(lastTransition)
        }
    }

    fun toJson(context: Context): JSONObject {
        val s = snapshot(context)
        return JSONObject().apply {
            put("schema", "sr-radar-contextual-diagnostic-v2")
            put("last_map_episode", RadarMapLastEpisodeV1.read(context))
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
            putOpt("eta_delta_seconds",s.etaDeltaSeconds)
            put("surface_open_source",s.surface.source)
            put("surface_open_kind",s.surface.kind)
            put("surface_open_state",s.surface.state)
            put("surface_open_attempts",s.surface.attempts)
            put("surface_route_selected",s.surface.routeSelected)
            put("surface_panel_attached",s.surface.panelAttached)
            put("surface_panel_shown",s.surface.panelShown)
            put("surface_stage_width",s.surface.stageWidth)
            put("surface_stage_height",s.surface.stageHeight)
            put("surface_panel_width",s.surface.panelWidth)
            put("surface_panel_height",s.surface.panelHeight)
            put("surface_render_deferred",s.surface.deferred)
            put("surface_render_started",s.surface.started)
            put("surface_render_completed",s.surface.completed)
            putOpt("surface_block_reason",s.surface.blockReason)
            put("demo_preview_opened",s.demoOpened)
            put("demo_preview_rendered",s.demoRendered)
            put("map_created_count",s.mapCreatedCount)
            put("map_ready_count",s.mapReadyCount)
            put("map_style_loaded",s.mapRender.styleLoaded)
            put("map_loading_finished",s.mapRender.loadingFinished)
            put("map_first_frame",s.mapRender.firstFrame)
            put("map_fully_rendered",s.mapRender.fullyRendered)
            put("map_render_timeout",s.mapRender.timeout)
            put("map_load_timeout",s.mapRender.loadTimeout)
            put("map_late_render_recovered",s.mapRender.lateRecovered)
            put("map_render_error",s.mapRender.renderError)
            put("map_load_error",s.mapRender.loadError)
            put("map_style_loaded_count",s.mapRender.styleCount)
            put("map_first_frame_count",s.mapRender.firstFrameCount)
            put("map_fully_rendered_count",s.mapRender.fullyCount)
            put("map_render_timeout_count",s.mapRender.timeoutCount)
            put("map_render_error_count",s.mapRender.renderErrorCount)
            put("last_map_render_error",s.mapRender.lastRenderError ?: JSONObject.NULL)
            put("map_release_count",s.mapReleaseCount)
            putOpt("last_map_error",s.lastMapError)
            putOpt("last_map_release_reason",s.lastMapReleaseReason)
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
            put("field_r4_migration_applied", s.fieldR4MigrationApplied)
            put("assistant_kind", s.assistantKind ?: JSONObject.NULL)
            putOpt("last_rendered_assistant_kind", s.lastRenderedAssistantKind)
            put("region_signal_generated", s.regionGenerated)
            put("region_signal_rendered", s.regionRendered)
            put("region_view_clicked", s.regionClicked)
            putOpt("selected_opportunity_id", s.selectedOpportunityId)
            putOpt(
                "last_assistant_shown_at",
                s.lastAssistantShownAtMs.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() },
            )
            put("assistant_cooldown_remaining_seconds", s.cooldownRemainingMs / 1000L)
            put("region_last_ride_matches_current",s.regionLastRideMatchesCurrent)
            put("launch_state", s.launchState)
            put("last_launch_kind", s.lastLaunchKind)
            put("map_state", s.mapState)
            put("map_active", s.mapActive)
            put("map_active_ms_current", s.mapActiveMsCurrent)
            put("recent_transitions", JSONArray(s.recentTransitions))
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
