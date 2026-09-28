package com.srrotas.app
import android.content.Context
import android.provider.Settings
import java.util.UUID
object DeviceIdentity10B {
    data class Identity(val raw:String,val version:String)
    private const val PREFS="sr_device_identity_10b"
    private const val FALLBACK_KEY="fallback_install_id"
    fun current(context:Context):Identity {
        val androidId=runCatching { Settings.Secure.getString(context.contentResolver,Settings.Secure.ANDROID_ID) }.getOrNull()?.trim().orEmpty()
        if(androidId.length>=8 && !androidId.equals("9774d56d682e549c",ignoreCase=true)) return Identity("android_ssaid_v1:$androidId","android_ssaid_v1")
        val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var fallback=prefs.getString(FALLBACK_KEY,"")?.trim().orEmpty()
        if(fallback.isBlank()){fallback=UUID.randomUUID().toString();prefs.edit().putString(FALLBACK_KEY,fallback).apply()}
        return Identity("install_random_v1:$fallback","install_random_v1")
    }
}
