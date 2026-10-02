package com.srrotas.app
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

object DestinationRadarRuntimeV1 {
    interface Listener {
        fun onRadarUpdated(result:RadarContextualResultV1) {}
        fun onAssistantSignal(result:RadarContextualResultV1, signal:DestinationRadarAssistantBridgeV1.Signal) {}
        fun onRadarUnavailable(reason:String) {}
    }
    private const val LOOP_MS=30_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val fetching=AtomicBoolean(false)
    @Volatile private var running=false
    @Volatile private var app:Context?=null
    @Volatile private var listener:Listener?=null
    @Volatile private var activeOfferId:String?=null
    @Volatile private var lastFetchAt=0L
    @Volatile private var latest:RadarContextualResultV1?=null

    fun start(context:Context, listener:Listener?=null) {
        app=context.applicationContext; this.listener=listener
        if(running) return
        running=true; main.removeCallbacks(loop); main.post(loop)
    }
    fun stop() { running=false; fetching.set(false); activeOfferId=null; lastFetchAt=0L; latest=null; main.removeCallbacks(loop) }
    fun latest():RadarContextualResultV1?=latest
    fun refreshNow(){ if(running){ main.removeCallbacks(loop); main.post(loop) } }

    private val loop=object:Runnable {
        override fun run() {
            if(!running) return
            val c=app ?: return
            val spec=RadarDestinationContextV1.current(c)
            if(spec==null){
                activeOfferId=null; lastFetchAt=0L; latest=null
                main.postDelayed(this,LOOP_MS); return
            }
            if(activeOfferId!=spec.localOfferId){
                activeOfferId=spec.localOfferId; lastFetchAt=0L; latest=null
                RadarContextualTelemetryV1.track(c,"ride_radar_armed",spec)
            }
            val now=System.currentTimeMillis()
            if((lastFetchAt==0L || now-lastFetchAt>=refreshDelayMs(spec.eta,now)) && fetching.compareAndSet(false,true)){
                lastFetchAt=now
                RadarContextualClientV1.fetch(c,spec.lat,spec.lng,spec.eta,spec.label,force=true){ r ->
                    fetching.set(false)
                    r.onSuccess { radar ->
                        val live=RadarDestinationContextV1.current(c)
                        if(live?.localOfferId!=spec.localOfferId) return@onSuccess
                        latest=radar
                        listener?.onRadarUpdated(radar)
                        DestinationRadarAssistantBridgeV1.signal(c,radar)?.let { signal ->
                            RadarContextualTelemetryV1.track(c,"assistant_eligible",spec,radar,signal.opportunityId)
                            listener?.onAssistantSignal(radar,signal)
                        }
                    }.onFailure { listener?.onRadarUnavailable(it.message ?: "radar_failed") }
                }
            }
            main.postDelayed(this,LOOP_MS)
        }
    }
    internal fun refreshDelayMs(eta:String,nowMs:Long):Long {
        val etaMs=runCatching{Instant.parse(eta).toEpochMilli()}.getOrDefault(nowMs)
        val min=((etaMs-nowMs)/60_000.0).coerceAtLeast(0.0)
        return when { min>30->300_000L; min>18->180_000L; min>8->90_000L; else->60_000L }
    }
}
