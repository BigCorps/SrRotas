package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class UberDigitizationStore026 private constructor(
    private val app: Context,
) : SQLiteOpenHelper(app.applicationContext, DB_NAME, null, DB_VERSION) {
    companion object {
        private const val DB_NAME = "sr_rotas_uber_digitization.db"
        private const val DB_VERSION = 2
        @Volatile private var instance: UberDigitizationStore026? = null
        fun get(context: Context): UberDigitizationStore026 = instance ?: synchronized(this) {
            instance ?: UberDigitizationStore026(context.applicationContext).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "create table session_imports(" +
                "owner_key text not null,source_key text not null,captured_at text not null," +
                "started_at text,ended_at text,earnings real,completed_trips integer,offered_trips integer," +
                "confidence real not null,journey_id text,observation text,sync_state integer not null default 0," +
                "primary key(owner_key,source_key))",
        )
        db.execSQL(
            "create table ride_imports(" +
                "owner_key text not null,source_key text not null,captured_at text not null,occurred_at text," +
                "fare real not null,service_type text not null,pickup_label text,destination_label text," +
                "confidence real not null,duration_seconds integer,distance_km real,surge_amount real,extra_amount real," +
                "ride_status text not null default 'completed',sync_state integer not null default 0," +
                "primary key(owner_key,source_key))",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            addColumn(db, "session_imports", "journey_id text")
            addColumn(db, "session_imports", "observation text")
            addColumn(db, "ride_imports", "duration_seconds integer")
            addColumn(db, "ride_imports", "distance_km real")
            addColumn(db, "ride_imports", "surge_amount real")
            addColumn(db, "ride_imports", "extra_amount real")
            addColumn(db, "ride_imports", "ride_status text not null default 'completed'")
        }
    }

    @Synchronized
    fun saveSession(v: UberSessionSummary026): Boolean = writableDatabase.insertWithOnConflict(
        "session_imports",
        null,
        ContentValues().apply {
            put("owner_key", ownerKey())
            put("source_key", v.sourceKey)
            put("captured_at", v.capturedAt)
            putNullable("started_at", v.startedAt)
            putNullable("ended_at", v.endedAt)
            putNullable("earnings", v.earnings)
            putNullable("completed_trips", v.completedTrips)
            putNullable("offered_trips", v.offeredTrips)
            put("confidence", v.confidence)
            putNullable("journey_id", v.journeyId)
            putNullable("observation", v.observation)
            put("sync_state", 0)
        },
        // O mesmo resumo pode ter sido salvo numa versão anterior sem vínculo
        // com jornada. REPLACE permite enriquecer esse registro com jornada/
        // observação quando o motorista digitaliza novamente.
        SQLiteDatabase.CONFLICT_REPLACE,
    ) != -1L

    fun sessionForJourney(journeyId: String): UberSessionSummary026? {
        if (journeyId.isBlank()) return null
        val owner = ownerKey()
        return readableDatabase.query(
            "session_imports",
            arrayOf(
                "source_key", "captured_at", "started_at", "ended_at", "earnings",
                "completed_trips", "offered_trips", "confidence", "journey_id", "observation",
            ),
            "owner_key=? and journey_id=?",
            arrayOf(owner, journeyId),
            null,
            null,
            "confidence desc, captured_at desc",
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun string(column: String): String? {
                val index = cursor.getColumnIndexOrThrow(column)
                return if (cursor.isNull(index)) null else cursor.getString(index)
            }
            fun number(column: String): Double? {
                val index = cursor.getColumnIndexOrThrow(column)
                return if (cursor.isNull(index)) null else cursor.getDouble(index)
            }
            fun integer(column: String): Int? {
                val index = cursor.getColumnIndexOrThrow(column)
                return if (cursor.isNull(index)) null else cursor.getInt(index)
            }
            UberSessionSummary026(
                sourceKey = cursor.getString(cursor.getColumnIndexOrThrow("source_key")),
                capturedAt = cursor.getString(cursor.getColumnIndexOrThrow("captured_at")),
                startedAt = string("started_at"),
                endedAt = string("ended_at"),
                earnings = number("earnings"),
                completedTrips = integer("completed_trips"),
                offeredTrips = integer("offered_trips"),
                confidence = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")),
                journeyId = string("journey_id"),
                observation = string("observation"),
            )
        }
    }

    @Synchronized
    fun saveRides(values: List<UberCompletedRide026>): Pair<Int, Int> {
        var saved = 0
        var duplicate = 0
        val owner = ownerKey()
        values.forEach { v ->
            // vc108: a mesma viagem salva por outra leitura (sourceKey diferente) conta como duplicada.
            if (similarSaved(owner, v)) { duplicate++; return@forEach }
            val ok = writableDatabase.insertWithOnConflict(
                "ride_imports",
                null,
                ContentValues().apply {
                    put("owner_key", owner)
                    put("source_key", v.sourceKey)
                    put("captured_at", v.capturedAt)
                    putNullable("occurred_at", v.occurredAt)
                    put("fare", v.fare)
                    put("service_type", v.serviceType)
                    putNullable("pickup_label", v.pickupLabel)
                    putNullable("destination_label", v.destinationLabel)
                    put("confidence", v.confidence)
                    putNullable("duration_seconds", v.durationSeconds)
                    putNullable("distance_km", v.distanceKm)
                    putNullable("surge_amount", v.surgeAmount)
                    putNullable("extra_amount", v.extraAmount)
                    put("ride_status", v.rideStatus)
                    put("sync_state", 0)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            ) != -1L
            if (ok) saved++ else duplicate++
        }
        return saved to duplicate
    }

    private fun similarSaved(owner: String, v: UberCompletedRide026): Boolean =
        readableDatabase.rawQuery(
            "select source_key,captured_at,occurred_at,fare,service_type,confidence,duration_seconds,distance_km,ride_status " +
                "from ride_imports where owner_key=? and abs(fare-?)<=0.011 and source_key<>?",
            arrayOf(owner, v.fare.toString(), v.sourceKey),
        ).use { c ->
            var found = false
            while (!found && c.moveToNext()) {
                val saved = UberCompletedRide026(
                    sourceKey = c.getString(0), capturedAt = c.getString(1), occurredAt = if (c.isNull(2)) null else c.getString(2),
                    fare = c.getDouble(3), serviceType = c.getString(4), pickupLabel = null, destinationLabel = null,
                    confidence = c.getDouble(5), durationSeconds = if (c.isNull(6)) null else c.getInt(6),
                    distanceKm = if (c.isNull(7)) null else c.getDouble(7), rideStatus = c.getString(8),
                )
                found = UberRideIdentity108.sameRide(saved, v)
            }
            found
        }

    /** Viagens importadas com horário no intervalo [from, to) (ISO-8601 UTC). */
    fun ridesBetween(fromIso: String, toIso: String): List<UberCompletedRide026> =
        readableDatabase.rawQuery(
            "select source_key,captured_at,occurred_at,fare,service_type,pickup_label,destination_label,confidence," +
                "duration_seconds,distance_km,surge_amount,extra_amount,ride_status from ride_imports " +
                "where owner_key=? and occurred_at>=? and occurred_at<? order by occurred_at asc",
            arrayOf(ownerKey(), fromIso, toIso),
        ).use { c ->
            val rows = mutableListOf<UberCompletedRide026>()
            fun s(i: Int) = if (c.isNull(i)) null else c.getString(i)
            fun d(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
            while (c.moveToNext()) rows += UberCompletedRide026(
                sourceKey = c.getString(0), capturedAt = c.getString(1), occurredAt = s(2), fare = c.getDouble(3),
                serviceType = c.getString(4), pickupLabel = s(5), destinationLabel = s(6), confidence = c.getDouble(7),
                durationSeconds = if (c.isNull(8)) null else c.getInt(8), distanceKm = d(9), surgeAmount = d(10),
                extraAmount = d(11), rideStatus = c.getString(12),
            )
            rows
        }

    fun summary(): Triple<Int, Int, Double> {
        val owner = ownerKey()
        val sessions = readableDatabase.rawQuery(
            "select count(*) from session_imports where owner_key=?",
            arrayOf(owner),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val rides = readableDatabase.rawQuery(
            "select count(*) from ride_imports where owner_key=?",
            arrayOf(owner),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val earnings = readableDatabase.rawQuery(
            "select coalesce(sum(earnings),0) from session_imports where owner_key=?",
            arrayOf(owner),
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
        return Triple(sessions, rides, earnings)
    }

    @Synchronized
    fun markSessionSynced(key: String) {
        writableDatabase.update(
            "session_imports",
            ContentValues().apply { put("sync_state", 1) },
            "owner_key=? and source_key=?",
            arrayOf(ownerKey(), key),
        )
    }

    @Synchronized
    fun markRideSynced(key: String) {
        writableDatabase.update(
            "ride_imports",
            ContentValues().apply { put("sync_state", 1) },
            "owner_key=? and source_key=?",
            arrayOf(ownerKey(), key),
        )
    }

    private fun ownerKey(): String {
        val token = SettingsRepository(app).load().deviceToken.ifBlank { "anonymous" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private fun addColumn(db: SQLiteDatabase, table: String, definition: String) {
        runCatching { db.execSQL("alter table $table add column $definition") }
    }

    private fun ContentValues.putNullable(key: String, value: Any?) {
        if (value == null) putNull(key) else when (value) {
            is String -> put(key, value)
            is Double -> put(key, value)
            is Int -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}
