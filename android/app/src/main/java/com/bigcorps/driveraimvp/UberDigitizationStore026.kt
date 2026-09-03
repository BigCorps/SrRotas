package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class UberDigitizationStore026 private constructor(private val app: Context) : SQLiteOpenHelper(app.applicationContext, DB_NAME, null, 1) {
    companion object {
        private const val DB_NAME = "sr_rotas_uber_digitization.db"
        @Volatile private var instance: UberDigitizationStore026? = null
        fun get(context: Context): UberDigitizationStore026 = instance ?: synchronized(this) {
            instance ?: UberDigitizationStore026(context.applicationContext).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("create table session_imports(owner_key text not null,source_key text not null,captured_at text not null,started_at text,ended_at text,earnings real,completed_trips integer,offered_trips integer,confidence real not null,sync_state integer not null default 0,primary key(owner_key,source_key))")
        db.execSQL("create table ride_imports(owner_key text not null,source_key text not null,captured_at text not null,occurred_at text,fare real not null,service_type text not null,pickup_label text,destination_label text,confidence real not null,sync_state integer not null default 0,primary key(owner_key,source_key))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun saveSession(v: UberSessionSummary026): Boolean = writableDatabase.insertWithOnConflict("session_imports", null, ContentValues().apply {
        put("owner_key", ownerKey()); put("source_key", v.sourceKey); put("captured_at", v.capturedAt); putNullable("started_at", v.startedAt); putNullable("ended_at", v.endedAt)
        putNullable("earnings", v.earnings); putNullable("completed_trips", v.completedTrips); putNullable("offered_trips", v.offeredTrips); put("confidence", v.confidence); put("sync_state", 0)
    }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    @Synchronized fun saveRides(values: List<UberCompletedRide026>): Pair<Int,Int> {
        var saved=0; var dup=0; val owner=ownerKey()
        values.forEach { v ->
            val ok = writableDatabase.insertWithOnConflict("ride_imports", null, ContentValues().apply {
                put("owner_key", owner); put("source_key", v.sourceKey); put("captured_at", v.capturedAt); putNullable("occurred_at", v.occurredAt); put("fare", v.fare); put("service_type", v.serviceType)
                putNullable("pickup_label", v.pickupLabel); putNullable("destination_label", v.destinationLabel); put("confidence", v.confidence); put("sync_state", 0)
            }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            if (ok) saved++ else dup++
        }
        return saved to dup
    }

    fun summary(): Triple<Int,Int,Double> {
        val owner=ownerKey()
        val sessions = readableDatabase.rawQuery("select count(*) from session_imports where owner_key=?", arrayOf(owner)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
        val rides = readableDatabase.rawQuery("select count(*) from ride_imports where owner_key=?", arrayOf(owner)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
        val earnings = readableDatabase.rawQuery("select coalesce(sum(earnings),0) from session_imports where owner_key=?", arrayOf(owner)).use { if(it.moveToFirst()) it.getDouble(0) else 0.0 }
        return Triple(sessions, rides, earnings)
    }

    @Synchronized fun markSessionSynced(key: String) { writableDatabase.update("session_imports", ContentValues().apply{put("sync_state",1)}, "owner_key=? and source_key=?", arrayOf(ownerKey(),key)) }
    @Synchronized fun markRideSynced(key: String) { writableDatabase.update("ride_imports", ContentValues().apply{put("sync_state",1)}, "owner_key=? and source_key=?", arrayOf(ownerKey(),key)) }

    private fun ownerKey(): String {
        val token=SettingsRepository(app).load().deviceToken.ifBlank { "anonymous" }
        return MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)}.take(24)
    }
    private fun ContentValues.putNullable(key: String, value: Any?) { if(value==null) putNull(key) else when(value){ is String->put(key,value); is Double->put(key,value); is Int->put(key,value); else->put(key,value.toString()) } }
}
