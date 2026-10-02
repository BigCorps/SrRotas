package com.srrotas.app
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
object AccessResolver10B {
    private const val PREFS="sr_access_resolver_10b"
    private const val KEY_ATTEMPTS="attempts"; private const val KEY_SUCCESS="success"; private const val KEY_FAILURE="failure"; private const val KEY_LAST_ERROR="last_error"; private const val KEY_LAST_SYNC_MS="last_sync_ms"; private const val KEY_IDENTITY_SOURCE="identity_source"; private const val KEY_CLAIM_OK="claim_ok"; private const val KEY_CLAIM_ERROR="claim_error"; private const val KEY_ACCESS_JSON="access_json"
    private val executor=Executors.newSingleThreadExecutor(); private val running=AtomicBoolean(false)
    fun sync(context:Context){
        val app=context.applicationContext; if(!running.compareAndSet(false,true)) return
        executor.execute { try {
            val settings=SettingsRepository(app).load(); if(settings.backendUrl.isBlank()||settings.deviceToken.isBlank()||!ConnectivityState.isOnline(app)) return@execute
            val prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE); prefs.edit().putInt(KEY_ATTEMPTS,prefs.getInt(KEY_ATTEMPTS,0)+1).apply()
            val identity=DeviceIdentity10B.current(app)
            val body=JSONObject().apply{put("device_identity",identity.raw);put("identity_version",identity.version);put("device_name","${Build.MANUFACTURER} ${Build.MODEL}");put("app_version_name",BuildConfig.VERSION_NAME);put("app_version_code",BuildConfig.VERSION_CODE)}
            val response=request("${settings.backendUrl.trimEnd('/')}/api/v1/account/access",settings.deviceToken,body)
            val json=JSONObject(response); val claim=json.optJSONObject("claim")?:JSONObject(); val access=json.optJSONObject("access")?:JSONObject()
            prefs.edit().putInt(KEY_SUCCESS,prefs.getInt(KEY_SUCCESS,0)+1).putString(KEY_LAST_ERROR,"").putLong(KEY_LAST_SYNC_MS,System.currentTimeMillis()).putString(KEY_IDENTITY_SOURCE,identity.version).putBoolean(KEY_CLAIM_OK,claim.optBoolean("ok",false)).putString(KEY_CLAIM_ERROR,claim.optString("error","")).putString(KEY_ACCESS_JSON,access.toString()).apply()
            BetaTelemetry.flushPendingCrash(app)
        } catch(error:Throwable){ val prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE); prefs.edit().putInt(KEY_FAILURE,prefs.getInt(KEY_FAILURE,0)+1).putString(KEY_LAST_ERROR,(error.message?:error.javaClass.simpleName).take(180)).apply(); LocalLog.append(app,"Access Resolver 1.0-B sync falhou: ${error.message}") } finally { running.set(false) } }
    }
    private fun access(context:Context):JSONObject { val raw=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ACCESS_JSON,"{}")?:"{}"; return runCatching{JSONObject(raw)}.getOrElse{JSONObject()} }
    fun state(context:Context):String=access(context).optString("state","UNKNOWN")
    fun commercialTier(context:Context):String=access(context).optString("commercial_tier",if(state(context) in setOf("TRIAL_ACTIVE","PAID_ACTIVE"))"intelligence" else "copilot")
    fun canUseAnalytics(context:Context):Boolean=access(context).optJSONObject("effective")?.optBoolean("can_analytics",false)?:false
    fun canUseAi(context:Context):Boolean=access(context).optJSONObject("effective")?.optBoolean("can_ai",false)?:false
    fun canUseMcp(context:Context):Boolean=access(context).optJSONObject("effective")?.optBoolean("can_mcp",false)?:false
    fun toJson(context:Context):JSONObject{
        val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val access=access(context); val effective=access.optJSONObject("effective")?:JSONObject(); val policy=access.optJSONObject("policy")?:JSONObject()
        return JSONObject().apply{ put("version","1.0-B-commercial");put("attempts",prefs.getInt(KEY_ATTEMPTS,0));put("success",prefs.getInt(KEY_SUCCESS,0));put("failure",prefs.getInt(KEY_FAILURE,0));put("last_error",prefs.getString(KEY_LAST_ERROR,"")?:"");put("last_sync_ms",prefs.getLong(KEY_LAST_SYNC_MS,0L));put("identity_source",prefs.getString(KEY_IDENTITY_SOURCE,"unknown")?:"unknown");put("claim_ok",prefs.getBoolean(KEY_CLAIM_OK,false));put("claim_error",prefs.getString(KEY_CLAIM_ERROR,"")?:"");put("state",access.optString("state","UNKNOWN"));put("reason",access.optString("reason",""));put("commercial_tier",access.optString("commercial_tier","unknown"));put("enforcement_mode",access.optString("enforcement_mode","unknown"));put("device_identity_bound",access.optBoolean("device_identity_bound",false));put("active_identity_devices",access.optInt("active_identity_devices",0));put("max_active_devices",access.optInt("max_active_devices",2));put("policy_can_operate",policy.optBoolean("can_operate",false));put("policy_can_history",policy.optBoolean("can_history",false));put("policy_can_analytics",policy.optBoolean("can_analytics",false));put("policy_can_ai",policy.optBoolean("can_ai",false));put("policy_can_mcp",policy.optBoolean("can_mcp",false));put("effective_can_operate",effective.optBoolean("can_operate",false));put("effective_can_history",effective.optBoolean("can_history",false));put("effective_can_analytics",effective.optBoolean("can_analytics",false));put("effective_can_ai",effective.optBoolean("can_ai",false));put("effective_can_mcp",effective.optBoolean("can_mcp",false));put("exports_raw_device_identity",false);put("exports_device_identity_hash",false);put("uses_imei",false);put("uses_serial",false);put("uses_mac",false);put("uses_installed_app_list",false) }
    }
    private fun request(url:String,token:String,body:JSONObject):String{
        val connection=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=8000;readTimeout=12000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","application/json");setRequestProperty("Authorization","Bearer $token");setRequestProperty("X-SrRotas-App-Version",BuildConfig.VERSION_NAME)}
        connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}; val status=connection.responseCode; val stream=if(status in 200..299)connection.inputStream else connection.errorStream; val text=stream?.use{BufferedReader(InputStreamReader(it)).readText()}?:""; connection.disconnect(); if(status !in 200..299){val message=runCatching{JSONObject(text).optString("error")}.getOrDefault("").ifBlank{"HTTP $status"};error(message)}; return text
    }
}
