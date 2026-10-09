package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Index only new private saves. No Gallery scan, prefix matching or legacy inference. */
object PrivateScreenshotIndexV1 {
    private const val PREFS = "private_screenshot_index_v1"
    @Synchronized fun saved(context: Context, fullOfferId: String, file: File) {
        val prefs = context.getSharedPreferences(PREFS,0)
        val index = runCatching { JSONObject(prefs.getString("index","{}")) }.getOrDefault(JSONObject())
        index.put(fullOfferId,file.name)
        val dir = File(context.filesDir,"private-offer-captures")
        index.keys().asSequence().toList().forEach { key ->
            if(!File(dir,index.optString(key)).isFile) index.remove(key)
        }
        while(index.length() > 30) index.remove(index.keys().next())
        prefs.edit().putString("index",index.toString()).apply()
    }
    @Synchronized fun resolve(context: Context, fullOfferId: String): File? = runCatching {
        val index = JSONObject(context.getSharedPreferences(PREFS,0).getString("index","{}"))
        val name = index.optString(fullOfferId).takeIf { it.isNotBlank() && '/' !in it && '\\' !in it } ?: return null
        val dir = File(context.filesDir,"private-offer-captures").canonicalFile
        File(dir,name).canonicalFile.takeIf { it.parentFile == dir && it.isFile }
    }.getOrNull()
    fun clear(context: Context) { context.getSharedPreferences(PREFS,0).edit().remove("index").apply() }
}
