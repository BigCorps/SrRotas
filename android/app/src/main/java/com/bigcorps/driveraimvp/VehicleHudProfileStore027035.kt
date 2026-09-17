package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Perfis locais por veículo. Nunca salva token/conta/backend. */
object VehicleHudProfileStore027035 {
    private const val PREFS="sr_vehicle_hud_profiles_027035"
    private const val KEY="profiles"

    data class Profile(
        val name:String,
        val minPerKm:Double,
        val redPerKmBelow:Double,
        val minPerHour:Double,
        val redPerHourBelow:Double,
        val minPerMinute:Double,
        val redPerMinuteBelow:Double,
        val minFare:Double,
        val maxPickupKm:Double,
        val maxPickupMinutes:Int,
        val costPerKm:Double,
        val hudMetricOrder:String,
        val hudEnabledMetrics:String,
        val hudPosition:String,
        val hudTheme:String,
        val hudCardSize:String,
        val hudOpacity:Int,
        val hudFontSize:Int,
        val colorBlindMode:Boolean,
        val hudDismissOnTap:Boolean,
        val hudDragEnabled:Boolean,
    )

    fun list(context:Context):List<Profile> = runCatching {
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]") ?: "[]"
        val a=JSONArray(raw)
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::decode) }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun saveCurrent(context:Context, name:String) {
        val clean=name.trim().take(32).ifBlank { return }
        val s=SettingsRepository(context).load()
        val p=Profile(clean,s.minPerKm,s.redPerKmBelow,s.minPerHour,s.redPerHourBelow,s.minPerMinute,s.redPerMinuteBelow,
            s.minFare,s.maxPickupKm,Strategy021Store.load(context).maxPickupMinutes,s.costPerKm,s.hudMetricOrder,s.hudEnabledMetrics,
            s.hudPosition,s.hudTheme,s.hudCardSize,s.hudOpacity,s.hudFontSize,s.colorBlindMode,s.hudDismissOnTap,s.hudDragEnabled)
        val next=list(context).filterNot { it.name.equals(clean,true) } + p
        write(context,next)
    }

    fun apply(context:Context, profile:Profile) {
        val repo=SettingsRepository(context); val s=repo.load()
        repo.save(s.copy(
            minPerKm=profile.minPerKm, redPerKmBelow=profile.redPerKmBelow,
            minPerHour=profile.minPerHour, redPerHourBelow=profile.redPerHourBelow,
            minPerMinute=profile.minPerMinute, redPerMinuteBelow=profile.redPerMinuteBelow,
            minFare=profile.minFare, maxPickupKm=profile.maxPickupKm, costPerKm=profile.costPerKm,
            hudMetricOrder=profile.hudMetricOrder, hudEnabledMetrics=profile.hudEnabledMetrics,
            hudPosition=profile.hudPosition, hudTheme=profile.hudTheme, hudCardSize=profile.hudCardSize,
            hudOpacity=profile.hudOpacity, hudFontSize=profile.hudFontSize,
            colorBlindMode=profile.colorBlindMode, hudDismissOnTap=profile.hudDismissOnTap, hudDragEnabled=profile.hudDragEnabled,
        ))
        Strategy021Store.saveMaxPickupMinutes(context,profile.maxPickupMinutes)
        Strategy021Store.savePreset(context,"custom")
        Preference021Sync.sync(context)
        JourneyBubbleController.refresh(context)
    }

    fun delete(context:Context,name:String)=write(context,list(context).filterNot { it.name==name })

    private fun write(context:Context, profiles:List<Profile>) {
        val a=JSONArray(); profiles.forEach { a.put(encode(it)) }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply()
    }
    private fun encode(p:Profile)=JSONObject().apply {
        put("name",p.name); put("minPerKm",p.minPerKm); put("redPerKmBelow",p.redPerKmBelow); put("minPerHour",p.minPerHour); put("redPerHourBelow",p.redPerHourBelow)
        put("minPerMinute",p.minPerMinute); put("redPerMinuteBelow",p.redPerMinuteBelow); put("minFare",p.minFare); put("maxPickupKm",p.maxPickupKm); put("maxPickupMinutes",p.maxPickupMinutes)
        put("costPerKm",p.costPerKm); put("hudMetricOrder",p.hudMetricOrder); put("hudEnabledMetrics",p.hudEnabledMetrics); put("hudPosition",p.hudPosition); put("hudTheme",p.hudTheme)
        put("hudCardSize",p.hudCardSize); put("hudOpacity",p.hudOpacity); put("hudFontSize",p.hudFontSize); put("colorBlindMode",p.colorBlindMode); put("hudDismissOnTap",p.hudDismissOnTap); put("hudDragEnabled",p.hudDragEnabled)
    }
    private fun decode(o:JSONObject)=Profile(
        name=o.optString("name"), minPerKm=o.optDouble("minPerKm",1.8), redPerKmBelow=o.optDouble("redPerKmBelow",1.45),
        minPerHour=o.optDouble("minPerHour",35.0), redPerHourBelow=o.optDouble("redPerHourBelow",28.0), minPerMinute=o.optDouble("minPerMinute",0.60), redPerMinuteBelow=o.optDouble("redPerMinuteBelow",0.48),
        minFare=o.optDouble("minFare",0.0), maxPickupKm=o.optDouble("maxPickupKm",5.0), maxPickupMinutes=o.optInt("maxPickupMinutes",8), costPerKm=o.optDouble("costPerKm",0.85),
        hudMetricOrder=o.optString("hudMetricOrder"), hudEnabledMetrics=o.optString("hudEnabledMetrics"), hudPosition=o.optString("hudPosition","left"), hudTheme=o.optString("hudTheme","auto"),
        hudCardSize=o.optString("hudCardSize","normal"), hudOpacity=o.optInt("hudOpacity",90), hudFontSize=o.optInt("hudFontSize",16), colorBlindMode=o.optBoolean("colorBlindMode",false),
        hudDismissOnTap=o.optBoolean("hudDismissOnTap",true), hudDragEnabled=o.optBoolean("hudDragEnabled",true),
    )
}
