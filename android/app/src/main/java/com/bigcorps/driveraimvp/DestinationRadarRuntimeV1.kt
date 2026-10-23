package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DestinationRadarRuntimeV1 {
    interface Listener {
        fun onRadarUpdated(result:RadarContextualResultV1) {}
        fun onAssistantSignal(result:RadarContextualResultV1, signal:DestinationRadarAssistantBridgeV1.Signal) {}
        fun onRadarUnavailable(reason:String) {}
    }

    private const val LOOP_MS=60_000L

    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val fetching=AtomicBoolean(false)
    private val requestSequence=AtomicLong(0L)
    @Volatile private var running=false
    @Volatile private var app:Context?=null
    @Volatile private var listener:Listener?=null
    @Volatile private var activeOfferId:String?=null
    @Volatile private var activeSpec:RadarDestinationSpecV1?=null
    @Volatile private var lastFetchAt=0L
    @Volatile private var latest:RadarContextualResultV1?=null
    // Record each blocked ETA/spec revision once, not on every capture broadcast.
    private var lastTemporalBlockKey:String?=null

    fun start(context:Context, listener:Listener?=null) {
        app=context.applicationContext
        this.listener=listener
        RadarContextualDiagnosticV1.setRuntimeRunning(true)
        if(running) return
        running=true
        main.removeCallbacks(loop)
        main.post(loop)
    }

    /** Invalidates previous HTTP callbacks without relying on cancellation. */
    fun invalidatePending() {
        requestSequence.incrementAndGet()
        fetching.set(false)
        activeOfferId=null
        activeSpec=null
        lastFetchAt=0L
        latest=null
        lastTemporalBlockKey=null
    }

    fun stop() {
        running=false
        invalidatePending()
        RadarContextualDiagnosticV1.setRuntimeRunning(false)
        RadarContextualDiagnosticV1.setFetching(false)
        activeOfferId=null
        lastFetchAt=0L
        latest=null
        main.removeCallbacks(loop)
    }

    fun latest():RadarContextualResultV1?=latest
    fun latestFor(localOfferId:String):RadarContextualResultV1? =
        latest.takeIf { activeOfferId==localOfferId }
    /** Compare resolved destination revision in addition to the operational ride ID. */
    fun latestForSpec(spec:RadarDestinationSpecV1):RadarContextualResultV1? =
        latest.takeIf { activeOfferId==spec.localOfferId && activeSpec==spec }
    fun applicationContext():Context?=app

    fun refreshNow(){
        if(running){
            main.removeCallbacks(loop)
            main.post(loop)
        }
    }

    private val loop=object:Runnable {
        override fun run() {
            if(!running) return
            val c=app ?: return
            val spec=RadarDestinationContextV1.current(c)
            if(spec==null){
                if(activeSpec!=null || activeOfferId!=null || fetching.get()){
                    invalidatePending()
                    RadarContextualDiagnosticV1.setFetching(false)
                    DestinationRadarAssistantRendererV1.hide()
                }
                RadarContextualDiagnosticV1.noActiveRide()
                main.postDelayed(this,LOOP_MS)
                return
            }
            RadarContextualDiagnosticV1.specResolved(spec.localOfferId)
            if(activeOfferId!=spec.localOfferId || activeSpec!=spec){
                requestSequence.incrementAndGet()
                fetching.set(false)
                activeOfferId=spec.localOfferId
                activeSpec=spec
                lastFetchAt=0L
                latest=null
                lastTemporalBlockKey=null
                // An updated ETA/coordinate on the same ride supersedes prior suggestions.
                DestinationRadarAssistantRendererV1.hide()
                RadarContextualDiagnosticV1.rideChanged()
                RadarContextualTelemetryV1.track(c,"ride_radar_armed",spec)
            }
            val now=System.currentTimeMillis()
            // Expired/invalid arrival estimates cannot schedule automated radar work.
            // Manual Radar remains independent and the ride is never auto-completed.
            val etaFresh = DestinationRadarFreshnessV1.blockReason(spec.eta,now)==null
            val temporalBlock = if(etaFresh) null else DestinationRadarFreshnessV1.blockReason(spec.eta,now)
            if (!etaFresh && RadarContextualIntegrationV1.assistantAllowed(c)) {
                val blockKey = "${spec.localOfferId}|${spec.eta}|$temporalBlock"
                if (blockKey!=lastTemporalBlockKey) {
                    lastTemporalBlockKey=blockKey
                    FieldPipelineTraceV1.event(c,spec.localOfferId,"ASSISTANT_BLOCKED",temporalBlock!!)
                    RadarContextualDiagnosticV1.assistantRenderBlocked(temporalBlock)
                }
            } else if (etaFresh) lastTemporalBlockKey=null
            if(etaFresh && (lastFetchAt==0L || now-lastFetchAt>=refreshDelayMs(spec.eta,now)) &&
                fetching.compareAndSet(false,true)
            ){
                val requestId=requestSequence.incrementAndGet()
                lastFetchAt=now
                RadarContextualDiagnosticV1.setFetching(true)
                RadarContextualClientV1.fetch(
                    context=c,
                    destinationLat=spec.lat,
                    destinationLng=spec.lng,
                    eta=spec.eta,
                    destinationLabel=spec.label,
                    force=true,
                    source="runtime",
                    traceOfferId=spec.localOfferId,
                ){ r ->
                    // An old response cannot clear or paint a later in-flight request.
                    if(requestSequence.get()!=requestId) return@fetch
                    fetching.set(false)
                    RadarContextualDiagnosticV1.setFetching(false)
                    r.onSuccess { radar ->
                        val live=RadarDestinationContextV1.current(c)
                        if(!running || live!=spec || activeSpec!=spec) return@onSuccess
                        latest=radar
                        RadarContextualDiagnosticV1.acceptResult(radar,"runtime")
                        listener?.onRadarUpdated(radar)
                        DestinationRadarAssistantBridgeV1.signal(c,radar)?.let { signal ->
                            RadarContextualTelemetryV1.track(
                                c,"assistant_eligible",spec,radar,signal.opportunityId
                            )
                            listener?.onAssistantSignal(radar,signal)
                        }
                    }.onFailure {
                        val live=RadarDestinationContextV1.current(c)
                        if(!running || live!=spec || activeSpec!=spec) return@onFailure
                        RadarContextualDiagnosticV1.failure(it)
                        listener?.onRadarUnavailable(it.message ?: "radar_failed")
                    }
                }
            }
            main.postDelayed(this,LOOP_MS)
        }
    }

    internal fun refreshDelayMs(eta:String,nowMs:Long):Long {
        val etaMs=runCatching { Instant.parse(eta).toEpochMilli() }.getOrDefault(nowMs)
        val min=((etaMs-nowMs)/60_000.0).coerceAtLeast(0.0)
        return when {
            min>30 -> 300_000L
            min>18 -> 180_000L
            min>8 -> 90_000L
            else -> 60_000L
        }
    }
}
