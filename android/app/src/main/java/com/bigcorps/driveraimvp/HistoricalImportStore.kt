package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoricalImportStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {

    companion object {
        private const val DB_NAME = "sr_rotas_historical_import.db"
        @Volatile private var instance: HistoricalImportStore? = null

        fun get(context: Context): HistoricalImportStore =
            instance ?: synchronized(this) {
                instance ?: HistoricalImportStore(context.applicationContext).also { instance = it }
            }
    }

    data class Summary(
        val processedFiles: Int,
        val noOfferFiles: Int,
        val failedFiles: Int,
        val importedOffers: Int,
        val duplicateOffers: Int,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table historical_import_files (
              file_sha256 text primary key,
              display_name text not null,
              observed_at text not null,
              time_confidence text not null,
              status text not null,
              parsed_offer_count integer not null default 0,
              imported_offer_count integer not null default 0,
              duplicate_offer_count integer not null default 0,
              last_error text,
              updated_at_ms integer not null
            )
            """.trimIndent(),
        )
        db.execSQL(
            "create index historical_import_status_idx on historical_import_files(status, updated_at_ms desc)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun isAlreadyProcessed(fileSha256: String): Boolean =
        readableDatabase.query(
            "historical_import_files", arrayOf("status"), "file_sha256 = ?",
            arrayOf(fileSha256), null, null, null, "1",
        ).use { c -> c.moveToFirst() && c.getString(0) in setOf("processed", "no_offer") }

    @Synchronized
    fun record(
        fileSha256: String,
        displayName: String,
        observedAt: String,
        timeConfidence: String,
        status: String,
        parsedOfferCount: Int,
        importedOfferCount: Int,
        duplicateOfferCount: Int,
        error: String? = null,
    ) {
        writableDatabase.insertWithOnConflict(
            "historical_import_files",
            null,
            ContentValues().apply {
                put("file_sha256", fileSha256)
                put("display_name", displayName.take(240))
                put("observed_at", observedAt)
                put("time_confidence", timeConfidence.take(40))
                put("status", status.take(30))
                put("parsed_offer_count", parsedOfferCount.coerceAtLeast(0))
                put("imported_offer_count", importedOfferCount.coerceAtLeast(0))
                put("duplicate_offer_count", duplicateOfferCount.coerceAtLeast(0))
                if (error == null) putNull("last_error") else put("last_error", error.take(500))
                put("updated_at_ms", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun summary(): Summary {
        val sql = """
            select
              coalesce(sum(case when status='processed' then 1 else 0 end),0),
              coalesce(sum(case when status='no_offer' then 1 else 0 end),0),
              coalesce(sum(case when status='failed' then 1 else 0 end),0),
              coalesce(sum(imported_offer_count),0),
              coalesce(sum(duplicate_offer_count),0)
            from historical_import_files
        """.trimIndent()

        return readableDatabase.rawQuery(sql, null).use { c ->
            if (!c.moveToFirst()) return@use Summary(0, 0, 0, 0, 0)
            Summary(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4))
        }
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete("historical_import_files", null, null)
    }
}
