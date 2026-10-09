package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object RadarContextualClientV1 {
    private const val CACHE_TTL_MS=3*60_000L
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private data class Cached(val value:RadarContextualResultV1,val at:Long)
    private data class HttpResponse(val status:Int,val text:String)
    private class HttpFailure(val status:Int, message:String): IllegalStateException(message)
    private val cache=ConcurrentHashMap<String,Cached>()

    fun fetch(
        context:Context,
        destinationLat:Double,
        destinationLng:Double,
        eta:String,
        destinationLabel:String?=null,
        radiusKm:Double=4.0,
        force:Boolean=false,
        source:String="ui",
        traceOfferId:String?=null,
        callback:(Result<RadarContextualResultV1>)->Unit,
    ) {
        FieldPipelineTraceV1.event(context,traceOfferId,"RADAR_FETCH_STARTED",source)
        RadarContextualDiagnosticV1.queryStarted(context,source)
        val key="${"%.4f".format(java.util.Locale.US,destinationLat)}|${"%.4f".format(java.util.Locale.US,destinationLng)}|$eta"
        val now=android.os.SystemClock.elapsedRealtime()
        cache[key]?.takeIf { !force && now-it.at in 0 until CACHE_TTL_MS }?.let {
            RadarContextualDiagnosticV1.cacheHit()
            FieldPipelineTraceV1.result(context,traceOfferId,it.value,"cache")
            main.post { callback(Result.success(it.value)) }; return
        }
        val app=context.applicationContext
        executor.execute {
            val result=runCatching {
                val settings=SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) { "Conecte sua conta." }
                val endpoint=buildString {
                    append(settings.backendUrl.trimEnd('/'))
                    append("/api/v1/radar/contextual?lat=");append(enc(destinationLat.toString()))
                    append("&lng=");append(enc(destinationLng.toString()))
                    append("&eta=");append(enc(eta))
                    append("&radius_km=");append(radiusKm.coerceIn(.5,15.0))
                    if(!destinationLabel.isNullOrBlank()){append("&label=");append(enc(destinationLabel))}
                }
                val response=request(endpoint,settings.deviceToken)
                FieldPipelineTraceV1.event(app,traceOfferId,"RADAR_HTTP_STATUS","http_success",response.status.toLong())
                parse(JSONObject(response.text)).also {
                    cache[key]=Cached(it,android.os.SystemClock.elapsedRealtime())
                }
            }
            result.onSuccess { FieldPipelineTraceV1.result(app,traceOfferId,it,"http_success") }
                .onFailure {
                    val reason = when(it) {
                        is HttpFailure -> {
                            FieldPipelineTraceV1.event(app,traceOfferId,"RADAR_HTTP_STATUS","http_error",it.status.toLong())
                            if(it.status == 401 || it.status == 403) "http_unauthorized" else "http_error"
                        }
                        is java.io.IOException -> "network_failed"
                        is org.json.JSONException -> "payload_invalid"
                        is IllegalArgumentException -> "request_precondition_failed"
                        else -> "fetch_failed"
                    }
                    FieldPipelineTraceV1.event(app,traceOfferId,"RADAR_FETCH_FAILED",reason)
                }
            result.exceptionOrNull()?.let(RadarContextualDiagnosticV1::failure)
            main.post { callback(result) }
        }
    }

    private fun parse(root:JSONObject):RadarContextualResultV1 {
        require(root.optString("schema_version")=="srrotas-radar-contextual-v1") { "Contrato Radar incompatível." }
        val d=root.getJSONObject("destination")
        val b=root.getJSONObject("baseline")
        val a=root.getJSONObject("assistant")
        val arr=root.optJSONArray("opportunities")
        val opportunities=(0 until (arr?.length()?:0)).mapNotNull { i ->
            val o=arr?.optJSONObject(i)?:return@mapNotNull null
            val ctx=o.optJSONObject("context")?.let {
                RadarContextualContextV1(
                    id=it.optString("id"),name=it.optString("name"),type=it.optString("type"),
                    egressStartAt=it.optString("egress_start_at"),egressEndAt=it.optString("egress_end_at"),
                    confidence=it.optDouble("confidence",0.0)
                )
            }
            val ev=o.optJSONArray("evidence")
            val evidence=(0 until (ev?.length()?:0)).mapNotNull { e ->
                ev?.optJSONObject(e)?.let {
                    RadarContextualEvidenceV1(
                        kind=it.optString("kind"),label=it.optString("label"),
                        value=it.optDouble("value",Double.NaN).takeIf(Double::isFinite),
                        samples=if(it.has("samples")&&!it.isNull("samples"))it.optInt("samples") else null,
                        reliability=it.optString("reliability").takeIf(String::isNotBlank)
                    )
                }
            }
            val win=o.optJSONObject("optimal_window")
            RadarContextualOpportunityV1(
                id=o.optString("id"),poiId=o.optString("poi_id"),title=o.optString("title"),
                subtitle=o.optString("subtitle").takeIf(String::isNotBlank),poiType=o.optString("poi_type"),
                lat=o.optDouble("lat"),lng=o.optDouble("lng"),distanceKm=o.optDouble("distance_km"),
                eta=o.optString("eta"),
                continuityProbabilityPct=o.optDouble("continuity_probability_pct",Double.NaN).takeIf(Double::isFinite),
                baselineProbabilityPct=o.optDouble("baseline_probability_pct",Double.NaN).takeIf(Double::isFinite),
                deltaProbabilityPct=o.optDouble("delta_probability_pct",Double.NaN).takeIf(Double::isFinite),
                confidence=o.optDouble("confidence",0.0),rankingScore=o.optDouble("ranking_score",0.0),
                potential=o.optString("potential","insufficient"),reasonHeadline=o.optString("reason_headline"),
                windowStart=win?.optString("start")?.takeIf(String::isNotBlank),
                windowEnd=win?.optString("end")?.takeIf(String::isNotBlank),
                context=ctx,evidence=evidence
            )
        }
        return RadarContextualResultV1(
            generatedAt=root.optString("generated_at"),
            destinationLat=d.optDouble("lat"),destinationLng=d.optDouble("lng"),
            destinationEta=d.optString("eta"),destinationLabel=d.optString("label").takeIf(String::isNotBlank),
            destinationCell=d.optString("geo_cell"),
            baseline=RadarContextualBaselineV1(
                probabilityPct=b.optDouble("probability_pct",Double.NaN).takeIf(Double::isFinite),
                samples=b.optInt("samples"),reliability=b.optString("reliability"),source=b.optString("source")
            ),
            opportunities=opportunities,
            assistant=RadarContextualAssistantV1(
                eligible=a.optBoolean("eligible"),reason=a.optString("reason"),
                headline=a.optString("headline").takeIf(String::isNotBlank),
                actionLabel=a.optString("action_label").takeIf(String::isNotBlank),
                opportunityId=a.optString("opportunity_id").takeIf(String::isNotBlank),
                minEtaMinutes=a.optInt("min_eta_minutes",4),maxEtaMinutes=a.optInt("max_eta_minutes",18)
            )
        )
    }

    private fun request(url:String,token:String):HttpResponse {
        val c=(URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod="GET";connectTimeout=8_000;readTimeout=12_000
            setRequestProperty("Accept","application/json")
            setRequestProperty("Authorization","Bearer $token")
            setRequestProperty("X-SrRotas-App-Version",BuildConfig.VERSION_NAME)
        }
        val status=c.responseCode
        RadarContextualDiagnosticV1.httpStatus(status)
        val text=(if(status in 200..299)c.inputStream else c.errorStream)?.use {
            BufferedReader(InputStreamReader(it)).readText()
        }.orEmpty()
        c.disconnect()
        if(status !in 200..299) throw HttpFailure(status,runCatching { JSONObject(text).optString("error").ifBlank{"HTTP $status"} }.getOrDefault("HTTP $status"))
        return HttpResponse(status,text)
    }
    private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
}
