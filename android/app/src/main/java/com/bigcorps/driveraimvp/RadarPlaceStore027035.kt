package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object RadarPlaceStore027035 {
    private const val PREFS="sr_radar_places_027035"
    private const val KEY="places"
    data class Place(val id:String=UUID.randomUUID().toString(), val name:String, val address:String, val note:String="", val sharing:String="private")
    fun list(context:Context):List<Place> = runCatching {
        val a=JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]") ?: "[]")
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> Place(o.optString("id"),o.optString("name"),o.optString("address"),o.optString("note"),o.optString("sharing","private")) } }
    }.getOrDefault(emptyList())
    fun add(context:Context,name:String,address:String,note:String,sharing:String) {
        val next=list(context)+Place(name=name.trim().take(60),address=address.trim().take(160),note=note.trim().take(160),sharing=if(sharing=="shareable")"shareable" else "private")
        write(context,next)
    }
    fun delete(context:Context,id:String)=write(context,list(context).filterNot{it.id==id})
    private fun write(context:Context,items:List<Place>) {
        val a=JSONArray(); items.forEach { p -> a.put(JSONObject().apply { put("id",p.id);put("name",p.name);put("address",p.address);put("note",p.note);put("sharing",p.sharing) }) }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply()
    }
}
