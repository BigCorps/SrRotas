package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Duration
import java.time.Instant

class LocalStore private constructor(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {
    companion object {
        private const val DB_NAME = "sr_rotas.db"
        private const val DB_VERSION = 5

        @Volatile
        private var instance: LocalStore? = null

        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(
                    context.applicationContext,
                ).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table local_journeys (
              id text primary key,
              platform text not null,
              started_at text not null,
              ended_at text,
              end_reason text,
              start_synced integer not null default 0,
              end_synced integer not null default 0,
              created_at_ms integer not null
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            create table local_offers (
              local_id text primary key,
              journey_id text,
              platform text not null,
              observed_at text not null,
              source_package text not null,
              capture_method text not null,
              raw_text text not null,
              fare real not null,
              pickup_km real,
              trip_km real,
              total_km real,
              pickup_minutes integer,
              trip_minutes integer,
              total_minutes integer,
              per_km real,
              per_hour real,
              per_minute real,
              estimated_cost real,
              estimated_profit real,
              profit_per_hour real,
              profit_percent real,
              passenger_rating real,
              advertised_per_km real,
              service_type text not null default 'unknown',
              verdict text not null,
              confidence real not null,
              offer_type text not null,
              cost_per_km_used real,
              cost_source text,
              cost_profile_version text,
              cost_profile_updated_at text,
              parser_version text not null,
              dedupe_key text not null,
              sync_state integer not null default 0,
              created_at_ms integer not null
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            create table local_offer_context (
              local_offer_id text primary key,
              pickup_label text,
              destination_label text,
              pickup_lat real,
              pickup_lng real,
              destination_lat real,
              destination_lng real,
              pickup_cell text,
              destination_cell text,
              estimated_arrival_at text,
              context_confidence real not null default 0,
              geocode_status text not null default 'unresolved',
              geocode_source text,
              context_version text not null default 'sr-context-v0.14.0',
              source_type text not null default 'live_ocr',
              time_source text not null default 'system_observed_at',
              sync_state integer not null default 0,
              updated_at_ms integer not null
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            create table local_geocode_cache (
              normalized_query text primary key,
              original_label text not null,
              lat real not null,
              lng real not null,
              cell text,
              updated_at_ms integer not null
            )
            """.trimIndent(),
        )

        createV15Tables(db)
        createIndexes(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            listOf(
                "alter table local_offers add column per_minute real",
                "alter table local_offers add column profit_per_hour real",
                "alter table local_offers add column profit_percent real",
                "alter table local_offers add column passenger_rating real",
                "alter table local_offers add column advertised_per_km real",
                "alter table local_offers add column service_type text not null default 'unknown'",
            ).forEach { sql ->
                runCatching { db.execSQL(sql) }
            }
        }

        if (oldVersion < 3) {
            db.execSQL(
                """
                create table if not exists local_offer_context (
                  local_offer_id text primary key,
                  pickup_label text,
                  destination_label text,
                  pickup_lat real,
                  pickup_lng real,
                  destination_lat real,
                  destination_lng real,
                  pickup_cell text,
                  destination_cell text,
                  estimated_arrival_at text,
                  context_confidence real not null default 0,
                  geocode_status text not null default 'unresolved',
                  geocode_source text,
                  context_version text not null default 'sr-context-v0.14.0',
                  source_type text not null default 'live_ocr',
                  time_source text not null default 'system_observed_at',
                  sync_state integer not null default 0,
                  updated_at_ms integer not null
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                create table if not exists local_geocode_cache (
                  normalized_query text primary key,
                  original_label text not null,
                  lat real not null,
                  lng real not null,
                  cell text,
                  updated_at_ms integer not null
                )
                """.trimIndent(),
            )
        }

        if (oldVersion < 4) {
            createV15Tables(db)
        }

        if (oldVersion < 5) {
            listOf(
                "alter table local_offers add column cost_per_km_used real",
                "alter table local_offers add column cost_source text",
                "alter table local_offers add column cost_profile_version text",
                "alter table local_offers add column cost_profile_updated_at text",
            ).forEach { sql ->
                runCatching { db.execSQL(sql) }
            }

            // Reconstrói o que é possível dos registros anteriores.
            db.execSQL(
                """
                update local_offers
                set cost_per_km_used =
                    case
                      when estimated_cost is not null
                       and total_km is not null
                       and total_km > 0
                      then round(estimated_cost / total_km, 4)
                      else null
                    end,
                    cost_source =
                    case
                      when estimated_cost is not null
                       and total_km is not null
                       and total_km > 0
                      then 'legacy_reconstructed'
                      else 'legacy_unknown'
                    end,
                    cost_profile_version = 'legacy_pre_018'
                where cost_source is null
                """.trimIndent(),
            )
        }

        createIndexes(db)
    }

    private fun createV15Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table if not exists local_journey_events (
              id text primary key,
              journey_id text not null,
              event_type text not null,
              state text not null,
              occurred_at text not null,
              sync_state integer not null default 0,
              created_at_ms integer not null
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            create table if not exists local_ride_outcomes (
              local_offer_id text primary key,
              journey_id text not null,
              status text not null,
              started_at text,
              completed_at text,
              cancelled_at text,
              corrected_at text,
              source text not null,
              revision integer not null default 1,
              sync_state integer not null default 0,
              updated_at_ms integer not null
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            create table if not exists local_zone_exposure (
              id text primary key,
              journey_id text not null,
              cell text not null,
              started_at text not null,
              ended_at text,
              duration_seconds integer,
              close_reason text,
              next_offer_local_id text,
              location_accuracy_m real,
              sync_state integer not null default 0,
              created_at_ms integer not null
            )
            """.trimIndent(),
        )
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "create index if not exists local_offers_journey_idx on local_offers(journey_id, observed_at desc)",
        )
        db.execSQL(
            "create index if not exists local_offers_sync_idx on local_offers(sync_state, created_at_ms)",
        )
        db.execSQL(
            "create unique index if not exists local_offers_dedupe_idx on local_offers(dedupe_key)",
        )
        db.execSQL(
            "create index if not exists local_offers_observed_idx on local_offers(observed_at desc)",
        )
        db.execSQL(
            "create index if not exists local_journeys_started_idx on local_journeys(started_at desc)",
        )
        db.execSQL(
            "create index if not exists local_offer_context_sync_idx on local_offer_context(sync_state, updated_at_ms)",
        )
        db.execSQL(
            "create index if not exists local_offer_context_destination_idx on local_offer_context(destination_cell)",
        )
        db.execSQL(
            "create index if not exists local_geocode_cache_updated_idx on local_geocode_cache(updated_at_ms)",
        )
        db.execSQL(
            "create index if not exists local_journey_events_journey_idx on local_journey_events(journey_id, occurred_at desc)",
        )
        db.execSQL(
            "create index if not exists local_journey_events_sync_idx on local_journey_events(sync_state, created_at_ms)",
        )
        db.execSQL(
            "create index if not exists local_ride_outcomes_journey_idx on local_ride_outcomes(journey_id, status, updated_at_ms desc)",
        )
        db.execSQL(
            "create index if not exists local_ride_outcomes_sync_idx on local_ride_outcomes(sync_state, updated_at_ms)",
        )
        db.execSQL(
            "create index if not exists local_zone_exposure_journey_idx on local_zone_exposure(journey_id, started_at desc)",
        )
        db.execSQL(
            "create index if not exists local_zone_exposure_cell_idx on local_zone_exposure(cell, started_at desc)",
        )
        db.execSQL(
            "create index if not exists local_zone_exposure_sync_idx on local_zone_exposure(sync_state, created_at_ms)",
        )
    }

    @Synchronized
    fun startJourney(
        platform: String = "uber",
    ): JourneyRecord {
        val record =
            JourneyRecord(
                platform = platform,
                startedAt = Instant.now().toString(),
            )

        writableDatabase.insertOrThrow(
            "local_journeys",
            null,
            ContentValues().apply {
                put("id", record.id)
                put("platform", record.platform)
                put("started_at", record.startedAt)
                put("created_at_ms", System.currentTimeMillis())
            },
        )

        return record
    }

    @Synchronized
    fun endJourney(
        id: String,
        reason: String,
    ): JourneySummary? {
        val current =
            journey(id) ?: return null

        val endedAt =
            current.endedAt ?: Instant.now().toString()

        writableDatabase.update(
            "local_journeys",
            ContentValues().apply {
                put("ended_at", endedAt)
                put("end_reason", reason.take(120))
            },
            "id = ?",
            arrayOf(id),
        )

        return journeySummary(id)
    }

    fun journey(id: String): JourneyRecord? =
        readableDatabase.query(
            "local_journeys",
            arrayOf(
                "id",
                "platform",
                "started_at",
                "ended_at",
                "end_reason",
            ),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toJourney()
        }

    fun latestJourney(): JourneyRecord? =
        readableDatabase.query(
            "local_journeys",
            arrayOf(
                "id",
                "platform",
                "started_at",
                "ended_at",
                "end_reason",
            ),
            null,
            null,
            null,
            null,
            "created_at_ms desc",
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toJourney()
        }

    @Synchronized
    fun saveOffer(o: RideOffer): Boolean {
        val reconstructedCost =
            o.costPerKmUsed
                ?: if (
                    o.estimatedCost != null &&
                    o.totalKm != null &&
                    o.totalKm > 0.0
                ) {
                    kotlin.math.round(
                        (o.estimatedCost / o.totalKm) * 10000.0,
                    ) / 10000.0
                } else {
                    null
                }

        val reconstructedSource =
            o.costSource
                ?: when {
                    reconstructedCost == null ->
                        "legacy_unknown"
                    o.captureMethod.startsWith("historical-import/") ->
                        "historical_revaluation"
                    else ->
                        "legacy_reconstructed"
                }

        val reconstructedVersion =
            o.costProfileVersion
                ?: if (
                    o.captureMethod.startsWith("historical-import/")
                ) {
                    "historical_revaluation"
                } else {
                    "legacy_pre_018"
                }

        val result =
            writableDatabase.insertWithOnConflict(
                "local_offers",
                null,
                ContentValues().apply {
                    put("local_id", o.localId)
                    putNullable("journey_id", o.journeyId)
                    put("platform", o.platform)
                    put("observed_at", o.observedAt)
                    put("source_package", o.sourcePackage)
                    put("capture_method", o.captureMethod)
                    put("raw_text", o.rawText.take(12000))
                    put("fare", o.fare)
                    putNullable("pickup_km", o.pickupKm)
                    putNullable("trip_km", o.tripKm)
                    putNullable("total_km", o.totalKm)
                    putNullable("pickup_minutes", o.pickupMinutes)
                    putNullable("trip_minutes", o.tripMinutes)
                    putNullable("total_minutes", o.totalMinutes)
                    putNullable("per_km", o.perKm)
                    putNullable("per_hour", o.perHour)
                    putNullable("per_minute", o.perMinute)
                    putNullable("estimated_cost", o.estimatedCost)
                    putNullable("estimated_profit", o.estimatedProfit)
                    putNullable("profit_per_hour", o.profitPerHour)
                    putNullable("profit_percent", o.profitPercent)
                    putNullable("passenger_rating", o.passengerRating)
                    putNullable("advertised_per_km", o.advertisedPerKm)
                    put("service_type", o.serviceType)
                    put("verdict", o.verdict)
                    put("confidence", o.confidence)
                    put("offer_type", o.offerType)
                    putNullable("cost_per_km_used", reconstructedCost)
                    putNullable("cost_source", reconstructedSource)
                    putNullable("cost_profile_version", reconstructedVersion)
                    putNullable("cost_profile_updated_at", o.costProfileUpdatedAt)
                    put("parser_version", o.parserVersion)
                    put("dedupe_key", o.dedupeKey)
                    put("sync_state", 0)
                    put("created_at_ms", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )

        if (result != -1L) {
            o.context?.let {
                saveOrUpdateContext(
                    o.localId,
                    it,
                    syncState = 0,
                )
            }

            writableDatabase.execSQL(
                "delete from local_offers where sync_state = 1 and local_id in (select local_id from local_offers where sync_state = 1 order by created_at_ms desc limit -1 offset 1500)",
            )
            writableDatabase.execSQL(
                "delete from local_offer_context where local_offer_id not in (select local_id from local_offers)",
            )
        }

        return result != -1L
    }

    @Synchronized
    fun markOfferSynced(localId: String) {
        writableDatabase.update(
            "local_offers",
            ContentValues().apply { put("sync_state", 1) },
            "local_id = ?",
            arrayOf(localId),
        )
        writableDatabase.update(
            "local_offer_context",
            ContentValues().apply { put("sync_state", 1) },
            "local_offer_id = ?",
            arrayOf(localId),
        )
    }

    @Synchronized
    fun markJourneyStartSynced(id: String) {
        writableDatabase.update(
            "local_journeys",
            ContentValues().apply { put("start_synced", 1) },
            "id = ?",
            arrayOf(id),
        )
    }

    @Synchronized
    fun markJourneyEndSynced(id: String) {
        writableDatabase.update(
            "local_journeys",
            ContentValues().apply { put("end_synced", 1) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun pendingJourneyStarts(limit: Int = 20) =
        queryJourneys(
            "start_synced = 0",
            null,
            "created_at_ms asc",
            limit,
        )

    fun pendingJourneyEnds(limit: Int = 20) =
        queryJourneys(
            "ended_at is not null and end_synced = 0",
            null,
            "created_at_ms asc",
            limit,
        )

    fun pendingOffers(limit: Int = 50) =
        queryOffers(
            "sync_state = 0",
            null,
            "created_at_ms asc",
            limit,
        )

    fun recentOffers(limit: Int = 20) =
        queryOffers(
            null,
            null,
            "created_at_ms desc",
            limit,
        )

    fun pendingOfferCount(): Int =
        readableDatabase.rawQuery(
            "select count(*) from local_offers where sync_state = 0",
            null,
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    data class CachedGeocode(
        val lat: Double,
        val lng: Double,
        val cell: String?,
    )

    data class PendingOfferContext(
        val localId: String,
        val dedupeKey: String,
        val context: OfferContext,
    )

    @Synchronized
    fun saveOrUpdateContext(
        localId: String,
        context: OfferContext,
        syncState: Int = 0,
    ) {
        writableDatabase.insertWithOnConflict(
            "local_offer_context",
            null,
            ContentValues().apply {
                put("local_offer_id", localId)
                putNullable("pickup_label", context.pickupLabel)
                putNullable("destination_label", context.destinationLabel)
                putNullable("pickup_lat", context.pickupLat)
                putNullable("pickup_lng", context.pickupLng)
                putNullable("destination_lat", context.destinationLat)
                putNullable("destination_lng", context.destinationLng)
                putNullable("pickup_cell", context.pickupCell)
                putNullable("destination_cell", context.destinationCell)
                putNullable("estimated_arrival_at", context.estimatedArrivalAt)
                put("context_confidence", context.contextConfidence.coerceIn(0.0, 1.0))
                put("geocode_status", context.geocodeStatus)
                putNullable("geocode_source", context.geocodeSource)
                put("context_version", context.contextVersion)
                put("source_type", context.sourceType)
                put("time_source", context.timeSource)
                put("sync_state", syncState)
                put("updated_at_ms", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun markContextSynced(localId: String) {
        writableDatabase.update(
            "local_offer_context",
            ContentValues().apply { put("sync_state", 1) },
            "local_offer_id = ?",
            arrayOf(localId),
        )
    }

    fun pendingOfferContexts(
        limit: Int = 50,
    ): List<PendingOfferContext> {
        val rows =
            mutableListOf<PendingOfferContext>()

        val sql =
            """
            select c.*, o.dedupe_key
            from local_offer_context c
            join local_offers o
              on o.local_id = c.local_offer_id
            where c.sync_state = 0
              and o.sync_state = 1
            order by c.updated_at_ms asc
            limit ?
            """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(
                limit.coerceIn(1, 200).toString(),
            ),
        ).use { c ->
            while (c.moveToNext()) {
                rows +=
                    PendingOfferContext(
                        c.getString(c.getColumnIndexOrThrow("local_offer_id")),
                        c.getString(c.getColumnIndexOrThrow("dedupe_key")),
                        c.toOfferContext(),
                    )
            }
        }

        return rows
    }

    fun contextForOffer(localId: String): OfferContext? =
        readableDatabase.query(
            "local_offer_context",
            CONTEXT_COLUMNS,
            "local_offer_id = ?",
            arrayOf(localId),
            null,
            null,
            null,
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toOfferContext()
        }

    fun cachedGeocode(
        normalizedQuery: String,
    ): CachedGeocode? =
        readableDatabase.query(
            "local_geocode_cache",
            arrayOf("lat", "lng", "cell"),
            "normalized_query = ?",
            arrayOf(normalizedQuery),
            null,
            null,
            null,
            "1",
        ).use {
            if (!it.moveToFirst()) {
                null
            } else {
                CachedGeocode(
                    it.getDouble(0),
                    it.getDouble(1),
                    if (it.isNull(2)) null else it.getString(2),
                )
            }
        }

    @Synchronized
    fun cacheGeocode(
        normalizedQuery: String,
        originalLabel: String,
        lat: Double,
        lng: Double,
        cell: String?,
    ) {
        writableDatabase.insertWithOnConflict(
            "local_geocode_cache",
            null,
            ContentValues().apply {
                put("normalized_query", normalizedQuery)
                put("original_label", originalLabel.take(220))
                put("lat", lat)
                put("lng", lng)
                putNullable("cell", cell)
                put("updated_at_ms", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---- 0.15: jornada operacional ----------------------------------------------

    @Synchronized
    fun recordJourneyEvent(
        journeyId: String,
        eventType: String,
        state: JourneyOperationalState,
        occurredAt: String = Instant.now().toString(),
    ): JourneyStateEvent {
        val event =
            JourneyStateEvent(
                journeyId = journeyId,
                eventType = eventType.lowercase().take(30),
                state = state,
                occurredAt = occurredAt,
            )

        writableDatabase.insertOrThrow(
            "local_journey_events",
            null,
            ContentValues().apply {
                put("id", event.id)
                put("journey_id", event.journeyId)
                put("event_type", event.eventType)
                put("state", event.state.name)
                put("occurred_at", event.occurredAt)
                put("sync_state", 0)
                put("created_at_ms", System.currentTimeMillis())
            },
        )

        return event
    }

    fun currentJourneyState(
        journeyId: String,
    ): JourneyOperationalState {
        readableDatabase.query(
            "local_journey_events",
            arrayOf("state"),
            "journey_id = ?",
            arrayOf(journeyId),
            null,
            null,
            "occurred_at desc, created_at_ms desc",
            "1",
        ).use { c ->
            if (c.moveToFirst()) {
                return runCatching {
                    JourneyOperationalState.valueOf(
                        c.getString(0),
                    )
                }.getOrDefault(
                    JourneyOperationalState.ACTIVE,
                )
            }
        }

        return journey(journeyId)?.let {
            if (it.endedAt == null) {
                JourneyOperationalState.ACTIVE
            } else {
                JourneyOperationalState.ENDED
            }
        } ?: JourneyOperationalState.NOT_STARTED
    }

    fun pendingJourneyEvents(
        limit: Int = 50,
    ): List<JourneyStateEvent> {
        val rows =
            mutableListOf<JourneyStateEvent>()

        readableDatabase.query(
            "local_journey_events",
            arrayOf(
                "id",
                "journey_id",
                "event_type",
                "state",
                "occurred_at",
            ),
            "sync_state = 0",
            null,
            null,
            null,
            "created_at_ms asc",
            limit.coerceIn(1, 200).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                rows +=
                    JourneyStateEvent(
                        c.getString(0),
                        c.getString(1),
                        c.getString(2),
                        runCatching {
                            JourneyOperationalState.valueOf(
                                c.getString(3),
                            )
                        }.getOrDefault(
                            JourneyOperationalState.ACTIVE,
                        ),
                        c.getString(4),
                    )
            }
        }

        return rows
    }

    @Synchronized
    fun markJourneyEventSynced(id: String) {
        writableDatabase.update(
            "local_journey_events",
            ContentValues().apply { put("sync_state", 1) },
            "id = ?",
            arrayOf(id),
        )
    }

    @Synchronized
    fun ensureOfferedOutcome(
        offer: RideOffer,
        source: String = "offer_engine",
    ): RideOutcome? {
        val journeyId =
            offer.journeyId
                ?.takeIf { it.isNotBlank() }
                ?: return null

        val existing =
            rideOutcomeForOffer(offer.localId)

        if (existing != null) return existing

        val outcome =
            RideOutcome(
                localOfferId = offer.localId,
                journeyId = journeyId,
                status = RideOperationalStatus.OFFERED,
                source = source,
                revision = 1,
            )

        saveRideOutcome(outcome)
        return outcome
    }

    @Synchronized
    fun updateRideOutcome(
        localOfferId: String,
        journeyId: String,
        status: RideOperationalStatus,
        source: String,
        occurredAt: String = Instant.now().toString(),
    ): RideOutcome {
        val previous =
            rideOutcomeForOffer(localOfferId)

        val revision =
            (previous?.revision ?: 0) + 1

        val startedAt =
            when (status) {
                RideOperationalStatus.DOING_RIDE ->
                    previous?.startedAt ?: occurredAt
                else ->
                    previous?.startedAt
            }

        val completedAt =
            if (status == RideOperationalStatus.COMPLETED) {
                occurredAt
            } else {
                null
            }

        val cancelledAt =
            if (
                status == RideOperationalStatus.CANCELLED ||
                status == RideOperationalStatus.NOT_COMPLETED
            ) {
                occurredAt
            } else {
                null
            }

        val correctedAt =
            if (previous != null || source == "history") {
                occurredAt
            } else {
                null
            }

        val outcome =
            RideOutcome(
                localOfferId,
                journeyId,
                status,
                startedAt,
                completedAt,
                cancelledAt,
                correctedAt,
                source.take(40),
                revision,
            )

        saveRideOutcome(outcome)
        return outcome
    }

    private fun saveRideOutcome(
        outcome: RideOutcome,
    ) {
        writableDatabase.insertWithOnConflict(
            "local_ride_outcomes",
            null,
            ContentValues().apply {
                put("local_offer_id", outcome.localOfferId)
                put("journey_id", outcome.journeyId)
                put("status", outcome.status.name)
                putNullable("started_at", outcome.startedAt)
                putNullable("completed_at", outcome.completedAt)
                putNullable("cancelled_at", outcome.cancelledAt)
                putNullable("corrected_at", outcome.correctedAt)
                put("source", outcome.source)
                put("revision", outcome.revision)
                put("sync_state", 0)
                put("updated_at_ms", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun rideOutcomeForOffer(
        localOfferId: String,
    ): RideOutcome? =
        readableDatabase.query(
            "local_ride_outcomes",
            RIDE_OUTCOME_COLUMNS,
            "local_offer_id = ?",
            arrayOf(localOfferId),
            null,
            null,
            null,
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toRideOutcome()
        }

    fun currentDoingRide(
        journeyId: String,
    ): RideOutcome? =
        readableDatabase.query(
            "local_ride_outcomes",
            RIDE_OUTCOME_COLUMNS,
            "journey_id = ? and status = ?",
            arrayOf(
                journeyId,
                RideOperationalStatus.DOING_RIDE.name,
            ),
            null,
            null,
            "updated_at_ms desc",
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toRideOutcome()
        }

    fun pendingRideOutcomes(
        limit: Int = 80,
    ): List<RideOutcome> {
        val rows =
            mutableListOf<RideOutcome>()

        readableDatabase.query(
            "local_ride_outcomes",
            RIDE_OUTCOME_COLUMNS,
            "sync_state = 0",
            null,
            null,
            null,
            "updated_at_ms asc",
            limit.coerceIn(1, 250).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                rows += c.toRideOutcome()
            }
        }

        return rows
    }

    @Synchronized
    fun markRideOutcomeSynced(
        localOfferId: String,
        revision: Int,
    ) {
        writableDatabase.update(
            "local_ride_outcomes",
            ContentValues().apply { put("sync_state", 1) },
            "local_offer_id = ? and revision = ?",
            arrayOf(
                localOfferId,
                revision.toString(),
            ),
        )
    }

    @Synchronized
    fun openExposure(
        journeyId: String,
        cell: String,
        accuracyM: Double?,
        startedAt: String = Instant.now().toString(),
    ): RegionalExposure {
        currentOpenExposure(journeyId)?.let { return it }

        val exposure =
            RegionalExposure(
                journeyId = journeyId,
                cell = cell,
                startedAt = startedAt,
                locationAccuracyM = accuracyM,
            )

        writableDatabase.insertOrThrow(
            "local_zone_exposure",
            null,
            ContentValues().apply {
                put("id", exposure.id)
                put("journey_id", exposure.journeyId)
                put("cell", exposure.cell)
                put("started_at", exposure.startedAt)
                putNullable("location_accuracy_m", exposure.locationAccuracyM)
                put("sync_state", 0)
                put("created_at_ms", System.currentTimeMillis())
            },
        )

        return exposure
    }

    fun currentOpenExposure(
        journeyId: String,
    ): RegionalExposure? =
        readableDatabase.query(
            "local_zone_exposure",
            EXPOSURE_COLUMNS,
            "journey_id = ? and ended_at is null",
            arrayOf(journeyId),
            null,
            null,
            "created_at_ms desc",
            "1",
        ).use {
            if (!it.moveToFirst()) null else it.toExposure()
        }

    @Synchronized
    fun closeExposure(
        journeyId: String,
        reason: String,
        nextOfferLocalId: String? = null,
        endedAt: String = Instant.now().toString(),
    ): RegionalExposure? {
        val open =
            currentOpenExposure(journeyId)
                ?: return null

        val seconds =
            runCatching {
                Duration.between(
                    Instant.parse(open.startedAt),
                    Instant.parse(endedAt),
                ).seconds.coerceAtLeast(0)
            }.getOrDefault(0)

        writableDatabase.update(
            "local_zone_exposure",
            ContentValues().apply {
                put("ended_at", endedAt)
                put("duration_seconds", seconds)
                put("close_reason", reason.take(40))
                putNullable("next_offer_local_id", nextOfferLocalId)
                put("sync_state", 0)
            },
            "id = ?",
            arrayOf(open.id),
        )

        return open.copy(
            endedAt = endedAt,
            durationSeconds = seconds,
            closeReason = reason.take(40),
            nextOfferLocalId = nextOfferLocalId,
        )
    }

    fun pendingExposures(
        limit: Int = 80,
    ): List<RegionalExposure> {
        val rows =
            mutableListOf<RegionalExposure>()

        readableDatabase.query(
            "local_zone_exposure",
            EXPOSURE_COLUMNS,
            "sync_state = 0 and ended_at is not null",
            null,
            null,
            null,
            "created_at_ms asc",
            limit.coerceIn(1, 250).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                rows += c.toExposure()
            }
        }

        return rows
    }

    @Synchronized
    fun markExposureSynced(id: String) {
        writableDatabase.update(
            "local_zone_exposure",
            ContentValues().apply { put("sync_state", 1) },
            "id = ?",
            arrayOf(id),
        )
    }

    @Synchronized
    fun clearAllUserData() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("local_zone_exposure", null, null)
            writableDatabase.delete("local_ride_outcomes", null, null)
            writableDatabase.delete("local_journey_events", null, null)
            writableDatabase.delete("local_offer_context", null, null)
            writableDatabase.delete("local_geocode_cache", null, null)
            writableDatabase.delete("local_offers", null, null)
            writableDatabase.delete("local_journeys", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun offersInRange(
        fromIso: String,
        toIso: String,
        limit: Int = 2000,
    ): List<RideOffer> =
        queryOffers(
            "observed_at >= ? and observed_at < ?",
            arrayOf(fromIso, toIso),
            "observed_at asc",
            limit,
        )

    fun journeysInRange(
        fromIso: String,
        toIso: String,
        limit: Int = 100,
    ): List<JourneyRecord> =
        queryJourneys(
            "started_at >= ? and started_at < ?",
            arrayOf(fromIso, toIso),
            "started_at desc",
            limit,
        )

    fun journeySummary(id: String): JourneySummary? {
        val record =
            journey(id) ?: return null

        val sql =
            """
            select
              count(*) offer_count,
              sum(case when verdict='boa' then 1 else 0 end) good_count,
              sum(case when verdict='regular' then 1 else 0 end) regular_count,
              sum(case when verdict='ruim' then 1 else 0 end) bad_count,
              avg(per_km) avg_per_km,
              avg(per_hour) avg_per_hour,
              sum(estimated_profit) estimated_profit
            from local_offers
            where journey_id = ?
            """.trimIndent()

        return readableDatabase.rawQuery(
            sql,
            arrayOf(id),
        ).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                JourneySummary(
                    record,
                    c.getInt(c.getColumnIndexOrThrow("offer_count")),
                    c.intOrZero("good_count"),
                    c.intOrZero("regular_count"),
                    c.intOrZero("bad_count"),
                    c.doubleOrNull("avg_per_km"),
                    c.doubleOrNull("avg_per_hour"),
                    c.doubleOrNull("estimated_profit"),
                )
            }
        }
    }

    private fun queryJourneys(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String,
        limit: Int,
    ): List<JourneyRecord> {
        val rows =
            mutableListOf<JourneyRecord>()

        readableDatabase.query(
            "local_journeys",
            arrayOf(
                "id",
                "platform",
                "started_at",
                "ended_at",
                "end_reason",
            ),
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit.coerceIn(1, 500).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                rows += c.toJourney()
            }
        }

        return rows
    }

    private fun queryOffers(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String,
        limit: Int,
    ): List<RideOffer> {
        val rows =
            mutableListOf<RideOffer>()

        readableDatabase.query(
            "local_offers",
            OFFER_COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit.coerceIn(1, 2000).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                rows += c.toOffer()
            }
        }

        return rows
    }

    private fun Cursor.toJourney() =
        JourneyRecord(
            getString(getColumnIndexOrThrow("id")),
            getString(getColumnIndexOrThrow("platform")),
            getString(getColumnIndexOrThrow("started_at")),
            stringOrNull("ended_at"),
            stringOrNull("end_reason"),
        )

    private fun Cursor.toOffer() =
        RideOffer(
            localId =
                getString(getColumnIndexOrThrow("local_id")),
            journeyId =
                stringOrNull("journey_id"),
            platform =
                getString(getColumnIndexOrThrow("platform")),
            observedAt =
                getString(getColumnIndexOrThrow("observed_at")),
            sourcePackage =
                getString(getColumnIndexOrThrow("source_package")),
            captureMethod =
                getString(getColumnIndexOrThrow("capture_method")),
            rawText =
                getString(getColumnIndexOrThrow("raw_text")),
            fare =
                getDouble(getColumnIndexOrThrow("fare")),
            pickupKm =
                doubleOrNull("pickup_km"),
            tripKm =
                doubleOrNull("trip_km"),
            totalKm =
                doubleOrNull("total_km"),
            pickupMinutes =
                intOrNull("pickup_minutes"),
            tripMinutes =
                intOrNull("trip_minutes"),
            totalMinutes =
                intOrNull("total_minutes"),
            perKm =
                doubleOrNull("per_km"),
            perHour =
                doubleOrNull("per_hour"),
            perMinute =
                doubleOrNull("per_minute"),
            estimatedCost =
                doubleOrNull("estimated_cost"),
            estimatedProfit =
                doubleOrNull("estimated_profit"),
            profitPerHour =
                doubleOrNull("profit_per_hour"),
            profitPercent =
                doubleOrNull("profit_percent"),
            passengerRating =
                doubleOrNull("passenger_rating"),
            advertisedPerKm =
                doubleOrNull("advertised_per_km"),
            serviceType =
                getString(getColumnIndexOrThrow("service_type")),
            verdict =
                getString(getColumnIndexOrThrow("verdict")),
            confidence =
                getDouble(getColumnIndexOrThrow("confidence")),
            offerType =
                getString(getColumnIndexOrThrow("offer_type")),
            context =
                contextForOffer(
                    getString(getColumnIndexOrThrow("local_id")),
                ),
            costPerKmUsed =
                doubleOrNull("cost_per_km_used"),
            costSource =
                stringOrNull("cost_source"),
            costProfileVersion =
                stringOrNull("cost_profile_version"),
            costProfileUpdatedAt =
                stringOrNull("cost_profile_updated_at"),
            parserVersion =
                getString(getColumnIndexOrThrow("parser_version")),
            dedupeKey =
                getString(getColumnIndexOrThrow("dedupe_key")),
        )

    private fun Cursor.toOfferContext() =
        OfferContext(
            pickupLabel = stringOrNull("pickup_label"),
            destinationLabel = stringOrNull("destination_label"),
            pickupLat = doubleOrNull("pickup_lat"),
            pickupLng = doubleOrNull("pickup_lng"),
            destinationLat = doubleOrNull("destination_lat"),
            destinationLng = doubleOrNull("destination_lng"),
            pickupCell = stringOrNull("pickup_cell"),
            destinationCell = stringOrNull("destination_cell"),
            estimatedArrivalAt = stringOrNull("estimated_arrival_at"),
            contextConfidence = doubleOrNull("context_confidence") ?: 0.0,
            geocodeStatus = stringOrNull("geocode_status") ?: "unresolved",
            geocodeSource = stringOrNull("geocode_source"),
            contextVersion = stringOrNull("context_version") ?: OfferContextEngine.VERSION,
            sourceType = stringOrNull("source_type") ?: "live_ocr",
            timeSource = stringOrNull("time_source") ?: "system_observed_at",
        )

    private fun Cursor.toRideOutcome() =
        RideOutcome(
            localOfferId =
                getString(getColumnIndexOrThrow("local_offer_id")),
            journeyId =
                getString(getColumnIndexOrThrow("journey_id")),
            status =
                runCatching {
                    RideOperationalStatus.valueOf(
                        getString(getColumnIndexOrThrow("status")),
                    )
                }.getOrDefault(
                    RideOperationalStatus.OFFERED,
                ),
            startedAt = stringOrNull("started_at"),
            completedAt = stringOrNull("completed_at"),
            cancelledAt = stringOrNull("cancelled_at"),
            correctedAt = stringOrNull("corrected_at"),
            source =
                getString(getColumnIndexOrThrow("source")),
            revision =
                getInt(getColumnIndexOrThrow("revision")),
        )

    private fun Cursor.toExposure() =
        RegionalExposure(
            id =
                getString(getColumnIndexOrThrow("id")),
            journeyId =
                getString(getColumnIndexOrThrow("journey_id")),
            cell =
                getString(getColumnIndexOrThrow("cell")),
            startedAt =
                getString(getColumnIndexOrThrow("started_at")),
            endedAt =
                stringOrNull("ended_at"),
            durationSeconds =
                longOrNull("duration_seconds"),
            closeReason =
                stringOrNull("close_reason"),
            nextOfferLocalId =
                stringOrNull("next_offer_local_id"),
            locationAccuracyM =
                doubleOrNull("location_accuracy_m"),
        )

    private fun Cursor.stringOrNull(c: String): String? {
        val i = getColumnIndexOrThrow(c)
        return if (isNull(i)) null else getString(i)
    }

    private fun Cursor.doubleOrNull(c: String): Double? {
        val i = getColumnIndexOrThrow(c)
        return if (isNull(i)) null else getDouble(i)
    }

    private fun Cursor.intOrNull(c: String): Int? {
        val i = getColumnIndexOrThrow(c)
        return if (isNull(i)) null else getInt(i)
    }

    private fun Cursor.longOrNull(c: String): Long? {
        val i = getColumnIndexOrThrow(c)
        return if (isNull(i)) null else getLong(i)
    }

    private fun Cursor.intOrZero(c: String): Int {
        val i = getColumnIndexOrThrow(c)
        return if (isNull(i)) 0 else getInt(i)
    }

    private fun ContentValues.putNullable(k: String, v: String?) {
        if (v == null) putNull(k) else put(k, v)
    }

    private fun ContentValues.putNullable(k: String, v: Double?) {
        if (v == null) putNull(k) else put(k, v)
    }

    private fun ContentValues.putNullable(k: String, v: Int?) {
        if (v == null) putNull(k) else put(k, v)
    }

    private val CONTEXT_COLUMNS =
        arrayOf(
            "pickup_label",
            "destination_label",
            "pickup_lat",
            "pickup_lng",
            "destination_lat",
            "destination_lng",
            "pickup_cell",
            "destination_cell",
            "estimated_arrival_at",
            "context_confidence",
            "geocode_status",
            "geocode_source",
            "context_version",
            "source_type",
            "time_source",
        )

    private val OFFER_COLUMNS =
        arrayOf(
            "local_id",
            "journey_id",
            "platform",
            "observed_at",
            "source_package",
            "capture_method",
            "raw_text",
            "fare",
            "pickup_km",
            "trip_km",
            "total_km",
            "pickup_minutes",
            "trip_minutes",
            "total_minutes",
            "per_km",
            "per_hour",
            "per_minute",
            "estimated_cost",
            "estimated_profit",
            "profit_per_hour",
            "profit_percent",
            "passenger_rating",
            "advertised_per_km",
            "service_type",
            "verdict",
            "confidence",
            "offer_type",
            "cost_per_km_used",
            "cost_source",
            "cost_profile_version",
            "cost_profile_updated_at",
            "parser_version",
            "dedupe_key",
        )

    private val RIDE_OUTCOME_COLUMNS =
        arrayOf(
            "local_offer_id",
            "journey_id",
            "status",
            "started_at",
            "completed_at",
            "cancelled_at",
            "corrected_at",
            "source",
            "revision",
        )

    private val EXPOSURE_COLUMNS =
        arrayOf(
            "id",
            "journey_id",
            "cell",
            "started_at",
            "ended_at",
            "duration_seconds",
            "close_reason",
            "next_offer_local_id",
            "location_accuracy_m",
        )
}
