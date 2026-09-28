package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import org.json.JSONObject
import java.time.Instant

/**
 * 0.33.8 — saneamento conservador da fila de exposições.
 *
 * Só quarentena payloads que o backend jamais aceitaria:
 * - id/journey/cell obrigatórios;
 * - cell no contrato g2:x:y;
 * - janela ISO válida com ended_at >= started_at.
 *
 * Estado 2 preserva o registro localmente e o retira apenas da fila de rede.
 * Nada é apagado.
 */
object ExposureQueueRepair0338 {
    private const val PREFS = "sr_exposure_queue_repair_0338"
    private const val KEY_RUNS = "runs"
    private const val KEY_SCANNED = "scanned"
    private const val KEY_QUARANTINED = "quarantined"
    private const val KEY_INVALID_FIELDS = "invalid_fields"
    private const val KEY_INVALID_WINDOW = "invalid_window"
    private const val KEY_LAST_RUN_MS = "last_run_ms"
    private const val MIN_INTERVAL_MS = 60_000L
    private val validCell = Regex("^g2:-?\\d+:-?\\d+$")

    @Volatile
    private var lastProcessRunMs = 0L

    data class Result(
        val scanned: Int,
        val quarantined: Int,
        val invalidFields: Int,
        val invalidWindow: Int,
        val skippedByThrottle: Boolean = false,
    )

    @Synchronized
    fun run(
        context: Context,
        force: Boolean = false,
    ): Result {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastProcessRunMs < MIN_INTERVAL_MS) {
            return Result(0, 0, 0, 0, skippedByThrottle = true)
        }
        lastProcessRunMs = now

        val db = LocalStore.get(app).writableDatabase
        val invalid = mutableListOf<Pair<String, String>>()
        var scanned = 0

        db.rawQuery(
            """
            select id, journey_id, cell, started_at, ended_at
              from local_zone_exposure
             where sync_state = 0
               and ended_at is not null
             order by created_at_ms asc
             limit 250
            """.trimIndent(),
            null,
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow("id")
            val journeyCol = cursor.getColumnIndexOrThrow("journey_id")
            val cellCol = cursor.getColumnIndexOrThrow("cell")
            val startedCol = cursor.getColumnIndexOrThrow("started_at")
            val endedCol = cursor.getColumnIndexOrThrow("ended_at")

            while (cursor.moveToNext()) {
                scanned += 1
                val id = cursor.getString(idCol) ?: ""
                val journeyId = cursor.getString(journeyCol) ?: ""
                val cell = cursor.getString(cellCol) ?: ""
                val startedAt = cursor.getString(startedCol) ?: ""
                val endedAt = cursor.getString(endedCol) ?: ""

                val fieldsValid =
                    id.isNotBlank() &&
                        journeyId.isNotBlank() &&
                        validCell.matches(cell)

                if (!fieldsValid) {
                    invalid += id to "invalid_exposure_fields"
                    continue
                }

                val windowValid =
                    runCatching {
                        val start = Instant.parse(startedAt)
                        val end = Instant.parse(endedAt)
                        !end.isBefore(start)
                    }.getOrDefault(false)

                if (!windowValid) {
                    invalid += id to "invalid_exposure_window"
                }
            }
        }

        var quarantined = 0
        var invalidFields = 0
        var invalidWindow = 0

        if (invalid.isNotEmpty()) {
            db.beginTransaction()
            try {
                invalid.forEach { (id, reason) ->
                    val updated =
                        db.update(
                            "local_zone_exposure",
                            ContentValues().apply {
                                put("sync_state", 2)
                            },
                            "id = ? and sync_state = 0",
                            arrayOf(id),
                        )
                    if (updated > 0) {
                        quarantined += updated
                        if (reason == "invalid_exposure_fields") {
                            invalidFields += updated
                        } else if (reason == "invalid_exposure_window") {
                            invalidWindow += updated
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_RUNS, prefs.getInt(KEY_RUNS, 0) + 1)
            .putInt(KEY_SCANNED, prefs.getInt(KEY_SCANNED, 0) + scanned)
            .putInt(
                KEY_QUARANTINED,
                prefs.getInt(KEY_QUARANTINED, 0) + quarantined,
            )
            .putInt(
                KEY_INVALID_FIELDS,
                prefs.getInt(KEY_INVALID_FIELDS, 0) + invalidFields,
            )
            .putInt(
                KEY_INVALID_WINDOW,
                prefs.getInt(KEY_INVALID_WINDOW, 0) + invalidWindow,
            )
            .putLong(KEY_LAST_RUN_MS, now)
            .apply()

        if (quarantined > 0) {
            LocalLog.append(
                app,
                "SYNC 0.33.8: $quarantined exposição(ões) legada(s) inválida(s) " +
                    "preservada(s) localmente · fields=$invalidFields window=$invalidWindow",
            )
        }

        return Result(
            scanned = scanned,
            quarantined = quarantined,
            invalidFields = invalidFields,
            invalidWindow = invalidWindow,
        )
    }

    fun toJson(context: Context): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = SyncCoordinator.pending(app)
        val quarantined = SyncCoordinator.quarantined(app)

        return JSONObject().apply {
            put("schema", "sr-exposure-queue-repair-0338-v1")
            put("runs", prefs.getInt(KEY_RUNS, 0))
            put("scanned_total", prefs.getInt(KEY_SCANNED, 0))
            put(
                "quarantined_total",
                prefs.getInt(KEY_QUARANTINED, 0),
            )
            put(
                "invalid_fields_total",
                prefs.getInt(KEY_INVALID_FIELDS, 0),
            )
            put(
                "invalid_window_total",
                prefs.getInt(KEY_INVALID_WINDOW, 0),
            )
            put("last_run_at_ms", prefs.getLong(KEY_LAST_RUN_MS, 0L))
            put("pending_exposures_now", pending.exposures)
            put("quarantined_exposures_now", quarantined.exposures)
            put("deletes_local_rows", false)
            put("exports_exposure_ids", false)
            put("exports_cells", false)
            put("exports_coordinates", false)
        }
    }
}
