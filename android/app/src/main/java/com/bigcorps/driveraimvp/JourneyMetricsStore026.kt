package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.util.UUID

/**
 * Banco local separado para hodômetro/abastecimentos da 0.26.
 * Mantém o LocalStore de ofertas congelado e reduz o risco de regressão.
 */
class JourneyMetricsStore026 private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    1,
) {
    data class Metric(
        val journeyId: String,
        val odometerStartKm: Double?,
        val odometerEndKm: Double?,
        val updatedAt: String,
        val syncState: Int,
    ) {
        val distanceKm: Double?
            get() = JourneyMetricsRules026.distanceKm(odometerStartKm, odometerEndKm)
    }

    data class EnergyEntry(
        val id: String,
        val journeyId: String,
        val kind: String,
        val amountPaid: Double?,
        val quantity: Double?,
        val unit: String,
        val fuelType: String?,
        val recordedAt: String,
        val syncState: Int,
    )

    data class Snapshot(
        val metric: Metric?,
        val energyEntries: List<EnergyEntry>,
    )

    companion object {
        private const val DB_NAME = "sr_rotas_journey_metrics_026.db"
        @Volatile private var instance: JourneyMetricsStore026? = null

        fun get(context: Context): JourneyMetricsStore026 =
            instance ?: synchronized(this) {
                instance ?: JourneyMetricsStore026(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table journey_metrics (
              journey_id text primary key,
              odometer_start_km real,
              odometer_end_km real,
              updated_at text not null,
              sync_state integer not null default 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            create table journey_energy_entries (
              id text primary key,
              journey_id text not null,
              kind text not null,
              amount_paid real,
              quantity real,
              unit text not null,
              fuel_type text,
              recorded_at text not null,
              sync_state integer not null default 0
            )
            """.trimIndent(),
        )
        db.execSQL("create index journey_energy_journey_idx on journey_energy_entries(journey_id, recorded_at)")
        db.execSQL("create index journey_metrics_sync_idx on journey_metrics(sync_state, updated_at)")
        db.execSQL("create index journey_energy_sync_idx on journey_energy_entries(sync_state, recorded_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun saveOdometer(
        journeyId: String,
        startKm: Double? = null,
        endKm: Double? = null,
    ): Metric? {
        if (journeyId.isBlank()) return null
        val existing = metric(journeyId)
        val normalizedStart = JourneyMetricsRules026.normalizedOdometer(startKm) ?: existing?.odometerStartKm
        val normalizedEnd = JourneyMetricsRules026.normalizedOdometer(endKm) ?: existing?.odometerEndKm
        if (normalizedStart == null && normalizedEnd == null) return existing
        if (normalizedStart != null && normalizedEnd != null && normalizedEnd < normalizedStart) return null
        val now = Instant.now().toString()
        writableDatabase.insertWithOnConflict(
            "journey_metrics",
            null,
            ContentValues().apply {
                put("journey_id", journeyId)
                putNullable("odometer_start_km", normalizedStart)
                putNullable("odometer_end_km", normalizedEnd)
                put("updated_at", now)
                put("sync_state", 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return metric(journeyId)
    }

    @Synchronized
    fun addEnergy(
        journeyId: String,
        kind: String,
        amountPaid: Double?,
        quantity: Double?,
        unit: String,
        fuelType: String? = null,
        recordedAt: String = Instant.now().toString(),
        id: String = UUID.randomUUID().toString(),
    ): EnergyEntry? {
        if (journeyId.isBlank()) return null
        if (!JourneyMetricsRules026.validEnergyEntry(kind, amountPaid, quantity, unit)) return null
        val entry = EnergyEntry(
            id = id,
            journeyId = journeyId,
            kind = kind.trim().lowercase(),
            amountPaid = amountPaid,
            quantity = quantity,
            unit = unit.trim().lowercase(),
            fuelType = fuelType?.trim()?.take(40)?.takeIf(String::isNotBlank),
            recordedAt = recordedAt,
            syncState = 0,
        )
        writableDatabase.insertWithOnConflict(
            "journey_energy_entries",
            null,
            entry.values(syncState = 0),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return entry
    }

    fun metric(journeyId: String): Metric? =
        readableDatabase.query(
            "journey_metrics",
            arrayOf("journey_id", "odometer_start_km", "odometer_end_km", "updated_at", "sync_state"),
            "journey_id = ?",
            arrayOf(journeyId),
            null,
            null,
            null,
            "1",
        ).use { if (it.moveToFirst()) it.toMetric() else null }

    fun snapshot(journeyId: String): Snapshot = Snapshot(metric(journeyId), energyEntries(journeyId))

    fun energyEntries(journeyId: String): List<EnergyEntry> =
        readableDatabase.query(
            "journey_energy_entries",
            arrayOf("id", "journey_id", "kind", "amount_paid", "quantity", "unit", "fuel_type", "recorded_at", "sync_state"),
            "journey_id = ?",
            arrayOf(journeyId),
            null,
            null,
            "recorded_at asc",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toEnergy()) } }

    fun pendingMetrics(limit: Int = 30): List<Metric> =
        readableDatabase.query(
            "journey_metrics",
            arrayOf("journey_id", "odometer_start_km", "odometer_end_km", "updated_at", "sync_state"),
            "sync_state = 0",
            null,
            null,
            null,
            "updated_at asc",
            limit.coerceIn(1, 100).toString(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMetric()) } }

    fun pendingEnergy(limit: Int = 50): List<EnergyEntry> =
        readableDatabase.query(
            "journey_energy_entries",
            arrayOf("id", "journey_id", "kind", "amount_paid", "quantity", "unit", "fuel_type", "recorded_at", "sync_state"),
            "sync_state = 0",
            null,
            null,
            null,
            "recorded_at asc",
            limit.coerceIn(1, 200).toString(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toEnergy()) } }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete("journey_energy_entries", null, null)
        writableDatabase.delete("journey_metrics", null, null)
    }

    @Synchronized
    fun markMetricSynced(journeyId: String) {
        writableDatabase.update("journey_metrics", ContentValues().apply { put("sync_state", 1) }, "journey_id = ?", arrayOf(journeyId))
    }

    @Synchronized
    fun markEnergySynced(id: String) {
        writableDatabase.update("journey_energy_entries", ContentValues().apply { put("sync_state", 1) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun importMetric(metric: Metric) {
        // Nunca deixa uma leitura antiga da nuvem sobrescrever edição local ainda pendente.
        if (this.metric(metric.journeyId)?.syncState == 0) return
        writableDatabase.insertWithOnConflict(
            "journey_metrics",
            null,
            ContentValues().apply {
                put("journey_id", metric.journeyId)
                putNullable("odometer_start_km", metric.odometerStartKm)
                putNullable("odometer_end_km", metric.odometerEndKm)
                put("updated_at", metric.updatedAt)
                put("sync_state", 1)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun importEnergy(entry: EnergyEntry) {
        if (energyEntry(entry.id)?.syncState == 0) return
        writableDatabase.insertWithOnConflict(
            "journey_energy_entries",
            null,
            entry.values(syncState = 1),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun energyEntry(id: String): EnergyEntry? =
        readableDatabase.query(
            "journey_energy_entries",
            arrayOf("id", "journey_id", "kind", "amount_paid", "quantity", "unit", "fuel_type", "recorded_at", "sync_state"),
            "id = ?",
            arrayOf(id),
            null, null, null, "1",
        ).use { if (it.moveToFirst()) it.toEnergy() else null }

    private fun EnergyEntry.values(syncState: Int) = ContentValues().apply {
        put("id", id)
        put("journey_id", journeyId)
        put("kind", kind)
        putNullable("amount_paid", amountPaid)
        putNullable("quantity", quantity)
        put("unit", unit)
        putNullable("fuel_type", fuelType)
        put("recorded_at", recordedAt)
        put("sync_state", syncState)
    }

    private fun Cursor.toMetric() = Metric(
        journeyId = getString(getColumnIndexOrThrow("journey_id")),
        odometerStartKm = nullableDouble("odometer_start_km"),
        odometerEndKm = nullableDouble("odometer_end_km"),
        updatedAt = getString(getColumnIndexOrThrow("updated_at")),
        syncState = getInt(getColumnIndexOrThrow("sync_state")),
    )

    private fun Cursor.toEnergy() = EnergyEntry(
        id = getString(getColumnIndexOrThrow("id")),
        journeyId = getString(getColumnIndexOrThrow("journey_id")),
        kind = getString(getColumnIndexOrThrow("kind")),
        amountPaid = nullableDouble("amount_paid"),
        quantity = nullableDouble("quantity"),
        unit = getString(getColumnIndexOrThrow("unit")),
        fuelType = nullableString("fuel_type"),
        recordedAt = getString(getColumnIndexOrThrow("recorded_at")),
        syncState = getInt(getColumnIndexOrThrow("sync_state")),
    )

    private fun Cursor.nullableDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun ContentValues.putNullable(key: String, value: Any?) {
        when (value) {
            null -> putNull(key)
            is Double -> put(key, value)
            is String -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}
