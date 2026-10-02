package com.srrotas.app
import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

object RadarContextualTelemetryV1 {
    private val executor=Executors.newSingleThreadExecutor()
    fun track(
        context:Context,type:String,spec:RadarDestinationSpecV1?=null,
        result:RadarContextualResultV1?=null,opportunityId:String?=null,
        metadata:JSONObject=JSONObject()
    ){
        val c=context.applicationContext
        val snap=JourneyCoordinator.snapshot(c)
        val selected=result?.opportunities?.firstOrNull{it.id==opportunityId}
        val body=JSONObject().apply{
            put("client_event_id",UUID.randomUUID().toString())
            put("event_type",type)
            put("occurred_at",Instant.now().toString())
            putOpt("journey_id",snap.journeyId)
            putOpt("ride_local_offer_id",snap.currentRide?.localOfferId ?: spec?.localOfferId)
            putOpt("opportunity_id",opportunityId ?: selected?.id)
            putOpt("poi_id",selected?.poiId)
            putOpt("ranking_score",selected?.rankingScore)
            putOpt("continuity_probability_pct",selected?.continuityProbabilityPct)
            putOpt("baseline_probability_pct",selected?.baselineProbabilityPct ?: result?.baseline?.probabilityPct)
            putOpt("distance_km",selected?.distanceKm)
            put("metadata",metadata)
        }
        executor.execute { runCatching { send(c,body) } }
    }
    private fun send(context:Context,body:JSONObject){
        val s=SettingsRepository(context).load(); if(s.deviceToken.isBlank()) return
        val conn=(URL(s.backendUrl.trimEnd('/')+"/api/v1/radar/contextual/events").openConnection() as HttpURLConnection).apply{
            requestMethod="POST";doOutput=true;connectTimeout=5_000;readTimeout=7_000
            setRequestProperty("Content-Type","application/json")
            setRequestProperty("Authorization","Bearer ${s.deviceToken}")
            setRequestProperty("X-SrRotas-App-Version",BuildConfig.VERSION_NAME)
        }
        OutputStreamWriter(conn.outputStream,Charsets.UTF_8).use{it.write(body.toString())}
        runCatching{conn.inputStream.close()}; conn.disconnect()
    }
}
