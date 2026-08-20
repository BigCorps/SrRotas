package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class CostProfileStore private constructor(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    1,
) {
    companion object {
        private const val DB_NAME =
            "sr_rotas_cost_profile.db"

        @Volatile
        private var instance:
            CostProfileStore? = null

        fun get(
            context: Context,
        ): CostProfileStore =
            instance ?: synchronized(this) {
                instance ?:
                    CostProfileStore(
                        context.applicationContext,
                    ).also {
                        instance = it
                    }
            }
    }

    data class Stored(
        val profile: CostProfile,
        val syncState: Int,
    )

    override fun onCreate(
        db: SQLiteDatabase,
    ) {
        db.execSQL(
            """
            create table local_cost_profile (
              id integer primary key check (id = 1),
              profile_json text not null,
              sync_state integer not null default 0,
              updated_at_ms integer not null
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    @Synchronized
    fun save(
        profile: CostProfile,
        syncState: Int = 0,
    ) {
        writableDatabase.insertWithOnConflict(
            "local_cost_profile",
            null,
            ContentValues().apply {
                put("id", 1)
                put(
                    "profile_json",
                    profile.toJson().toString(),
                )
                put(
                    "sync_state",
                    syncState.coerceIn(0, 1),
                )
                put(
                    "updated_at_ms",
                    System.currentTimeMillis(),
                )
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadStored(): Stored? =
        readableDatabase.query(
            "local_cost_profile",
            arrayOf(
                "profile_json",
                "sync_state",
            ),
            "id = 1",
            null,
            null,
            null,
            null,
            "1",
        ).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                val profile =
                    runCatching {
                        CostProfile.fromJson(
                            JSONObject(
                                c.getString(0),
                            ),
                        )
                    }.getOrNull()
                        ?: return@use null

                Stored(
                    profile = profile,
                    syncState = c.getInt(1),
                )
            }
        }

    fun load(): CostProfile? =
        loadStored()?.profile

    fun pending(): CostProfile? =
        loadStored()
            ?.takeIf {
                it.syncState == 0
            }
            ?.profile

    @Synchronized
    fun markSynced(
        expectedUpdatedAt: String,
    ) {
        val current =
            loadStored() ?: return

        if (
            current.profile.updatedAt !=
            expectedUpdatedAt
        ) {
            return
        }

        writableDatabase.update(
            "local_cost_profile",
            ContentValues().apply {
                put("sync_state", 1)
                put(
                    "updated_at_ms",
                    System.currentTimeMillis(),
                )
            },
            "id = 1",
            null,
        )
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete(
            "local_cost_profile",
            null,
            null,
        )
    }
}
