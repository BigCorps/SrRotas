package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 0.20.3 — coordenador único de sincronização com isolamento de ownership.
 *
 * Problema observado na 0.19:
 * - ofertas/contextos podiam ficar presos quando o banco local considerava
 *   uma jornada já sincronizada, mas o backend não possuía mais aquele journey_id;
 * - o botão do Perfil não chamava a fila 0.15 de eventos/outcomes/exposições;
 * - múltiplos flushes independentes podiam repetir centenas de requests 400/404.
 *
 * Regra 0.20:
 * 1. garante/recria idempotentemente todas as jornadas referenciadas;
 * 2. envia ofertas;
 * 3. envia atualizações de contexto;
 * 4. envia eventos, outcomes e exposições;
 * 5. encerra jornadas finalizadas;
 * 6. marca localmente como sincronizado somente após HTTP 2xx.
 *
 * Nenhum dado pendente é apagado para "zerar contador".
 */
object SyncCoordinator {
    data class QueueSnapshot(
        val offers: Int,
        val contexts: Int,
        val journeyEvents: Int,
        val rideOutcomes: Int,
        val exposures: Int,
    ) {
        val total: Int
            get() = offers + contexts + journeyEvents + rideOutcomes + exposures
    }

    data class SyncResult(
        val before: QueueSnapshot,
        val after: QueueSnapshot,
        val journeysEnsured: Int,
        val orphanCandidatesRepaired: Int,
        val requestsSucceeded: Int,
        val requestsFailed: Int,
        val quarantinedItems: Int = 0,
        val conflictJourneys: Int = 0,
        val skipped: Boolean = false,
        val reason: String? = null,
    ) {
        val syncedItems: Int
            get() =
                (before.total - after.total - quarantinedItems)
                    .coerceAtLeast(0)

        fun userMessage(): String = when {
            skipped && reason == "offline" ->
                "Sem internet. Os dados continuam salvos no aparelho."
            skipped && reason == "not_paired" ->
                "Conecte sua conta para sincronizar."
            skipped ->
                "Sincronização não iniciada."
            after.total == 0 && quarantinedItems > 0 ->
                "Tudo sincronizado. $syncedItems item(ns) enviado(s) · " +
                    "$quarantinedItems registro(s) antigo(s) preservado(s) somente neste aparelho por pertencerem a outra sessão."
            after.total == 0 && before.total > 0 ->
                "Tudo sincronizado · $syncedItems item(ns) enviado(s)."
            after.total == 0 ->
                "Tudo sincronizado."
            requestsFailed > 0 ->
                "Sincronização parcial · ${after.total} item(ns) ainda aguardando."
            else ->
                "Sincronizando em lotes · ${after.total} item(ns) ainda aguardando."
        }
    }

    private data class HttpResult(
        val status: Int,
        val body: String,
    ) {
        val ok: Boolean get() = status in 200..299

        val errorCode: String?
            get() = runCatching {
                val json = JSONObject(body)
                json.optString("error").ifBlank {
                    json.optString("message")
                }.ifBlank { null }
            }.getOrNull()
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SrRotasSync0203").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }
    private val running = AtomicBoolean(false)
    private val callbacks =
        CopyOnWriteArrayList<(SyncResult) -> Unit>()
    private val syncLock = Any()
    private val main = Handler(Looper.getMainLooper())

    fun isRunning(): Boolean = running.get()

    fun pending(context: Context): QueueSnapshot {
        val db = LocalStore.get(context.applicationContext).readableDatabase

        fun count(sql: String): Int =
            runCatching {
                db.rawQuery(sql, null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)

        return QueueSnapshot(
            offers = count(
                "select count(*) from local_offers where sync_state=0",
            ),
            contexts = count(
                "select count(*) from local_offer_context where sync_state=0",
            ),
            journeyEvents = count(
                "select count(*) from local_journey_events where sync_state=0",
            ),
            rideOutcomes = count(
                "select count(*) from local_ride_outcomes where sync_state=0",
            ),
            exposures = count(
                "select count(*) from local_zone_exposure where sync_state=0 and ended_at is not null",
            ),
        )
    }

    /**
     * Registros preservados localmente porque o backend confirmou que o
     * journey_id pertence a outra conta/sessão. Estado 2 é terminal local:
     * não é "sincronizado" e também não entra novamente na fila da conta atual.
     */
    fun quarantined(context: Context): QueueSnapshot {
        val db = LocalStore.get(context.applicationContext).readableDatabase

        fun count(sql: String): Int =
            runCatching {
                db.rawQuery(sql, null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)

        return QueueSnapshot(
            offers = count(
                "select count(*) from local_offers where sync_state=2",
            ),
            contexts = count(
                "select count(*) from local_offer_context where sync_state=2",
            ),
            journeyEvents = count(
                "select count(*) from local_journey_events where sync_state=2",
            ),
            rideOutcomes = count(
                "select count(*) from local_ride_outcomes where sync_state=2",
            ),
            exposures = count(
                "select count(*) from local_zone_exposure where sync_state=2",
            ),
        )
    }

    /**
     * Pode ser chamado em startup, onResume e manualmente.
     * Chamadas concorrentes são coalescidas; não criam tempestade de retries.
     */
    fun sync(
        context: Context,
        onResult: ((SyncResult) -> Unit)? = null,
    ) {
        val app = context.applicationContext

        val shouldStart =
            synchronized(syncLock) {
                onResult?.let { callbacks.add(it) }

                if (running.get()) {
                    false
                } else {
                    running.set(true)
                    true
                }
            }

        if (!shouldStart) return

        executor.execute {
            val result =
                runCatching { runSync(app) }
                    .getOrElse { error ->
                        LocalLog.append(
                            app,
                            "SYNC 0.20 falhou: ${error.message}",
                        )
                        SyncResult(
                            before = pending(app),
                            after = pending(app),
                            journeysEnsured = 0,
                            orphanCandidatesRepaired = 0,
                            requestsSucceeded = 0,
                            requestsFailed = 1,
                            reason = error.message,
                        )
                    }

            val deliver =
                synchronized(syncLock) {
                    val current = callbacks.toList()
                    callbacks.clear()
                    running.set(false)
                    current
                }

            main.post {
                deliver.forEach { callback ->
                    runCatching { callback(result) }
                }
            }
        }
    }

    private fun runSync(context: Context): SyncResult {
        val settings = SettingsRepository(context).load()
        val before = pending(context)

        if (!ConnectivityState.isOnline(context)) {
            return SyncResult(
                before = before,
                after = before,
                journeysEnsured = 0,
                orphanCandidatesRepaired = 0,
                requestsSucceeded = 0,
                requestsFailed = 0,
                skipped = true,
                reason = "offline",
            )
        }

        if (
            settings.backendUrl.isBlank() ||
            settings.deviceToken.isBlank()
        ) {
            return SyncResult(
                before = before,
                after = before,
                journeysEnsured = 0,
                orphanCandidatesRepaired = 0,
                requestsSucceeded = 0,
                requestsFailed = 0,
                skipped = true,
                reason = "not_paired",
            )
        }

        val store = LocalStore.get(context)
        var succeeded = 0
        var failed = 0
        var ensured = 0
        var repairedCandidates = 0
        var quarantinedItems = 0
        var conflictJourneys = 0

        // Pré-flight de ownership. O backend responde 409/journey_id_conflict
        // quando um UUID local pertence a outra conta. Nessa situação NÃO
        // remapeamos nem fazemos upload para o motorista atual: preservamos
        // os dados localmente como estado 2 e removemos apenas da fila de rede.
        for (round in 0 until 4) {
            val requiredJourneyIds = pendingJourneyIds(context)
            if (requiredJourneyIds.isEmpty()) break

            var quarantinedThisRound = false
            requiredJourneyIds.forEach { id ->
                val journey = store.journey(id)
                if (journey == null) {
                    failed += 1
                    LocalLog.append(
                        context,
                        "SYNC 0.20.3: jornada local ausente para filho pendente id=$id",
                    )
                    return@forEach
                }

                when (
                    ensureJourney(
                        context,
                        journey,
                        settings.backendUrl,
                        settings.deviceToken,
                    )
                ) {
                    EnsureJourneyStatus.OK -> {
                        ensured += 1
                        succeeded += 1
                    }

                    EnsureJourneyStatus.CONFLICT_OTHER_ACCOUNT -> {
                        val quarantined =
                            quarantineConflictingJourney(
                                context,
                                journey,
                            )
                        if (quarantined >= 0) {
                            quarantinedItems += quarantined
                            conflictJourneys += 1
                            repairedCandidates += 1
                            quarantinedThisRound = true
                            LocalLog.append(
                                context,
                                "SYNC 0.20.3: conflito de ownership preservado localmente " +
                                    "jornada=${id.take(8)} itens=$quarantined",
                            )
                        } else {
                            failed += 1
                        }
                    }

                    EnsureJourneyStatus.FAILED -> {
                        failed += 1
                    }
                }
            }

            if (!quarantinedThisRound) {
                break
            }

            LocalLog.append(
                context,
                "SYNC 0.20.3: preflight round=${round + 1}; " +
                    "reavaliando fila após quarentena de ownership",
            )
        }

        // Recarrega tudo depois do pre-flight. Isso é importante porque a
        // quarentena altera sync_state de forma transacional.
        val offers = store.pendingOffers(250)
        val journeyEvents = store.pendingJourneyEvents(250)
        val outcomes = store.pendingRideOutcomes(250)
        val exposures = store.pendingExposures(250)
        val journeyEnds = store.pendingJourneyEnds(200)

        offers.forEach { offer ->
            val result = sendOffer(
                context,
                offer,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        store.pendingOfferContexts(200).forEach { item ->
            val result = sendContext(
                context,
                item,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        journeyEvents.forEach { event ->
            val result = sendJourneyEvent(
                context,
                event,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        outcomes.forEach { outcome ->
            val result = sendOutcome(
                context,
                outcome,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        exposures.forEach { exposure ->
            val result = sendExposure(
                context,
                exposure,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        journeyEnds.forEach { journey ->
            val result = sendJourneyEnd(
                context,
                journey,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        val after = pending(context)

        LocalLog.append(
            context,
            "SYNC 0.20.3: ${before.total} -> ${after.total}; " +
                "jornadas garantidas=$ensured; conflitos=$conflictJourneys; " +
                "preservados_local=$quarantinedItems; ok=$succeeded falhas=$failed",
        )

        return SyncResult(
            before = before,
            after = after,
            journeysEnsured = ensured,
            orphanCandidatesRepaired = repairedCandidates,
            requestsSucceeded = succeeded,
            requestsFailed = failed,
            quarantinedItems = quarantinedItems,
            conflictJourneys = conflictJourneys,
        )
    }

    private enum class EnsureJourneyStatus {
        OK,
        CONFLICT_OTHER_ACCOUNT,
        FAILED,
    }

    private fun pendingJourneyIds(context: Context): LinkedHashSet<String> {
        val db = LocalStore.get(context).readableDatabase
        val ids = linkedSetOf<String>()
        val sql =
            """
            select id as journey_id
              from local_journeys
             where start_synced = 0
            union
            select journey_id
              from local_offers
             where sync_state = 0 and journey_id is not null and journey_id <> ''
            union
            select journey_id
              from local_journey_events
             where sync_state = 0
            union
            select journey_id
              from local_ride_outcomes
             where sync_state = 0
            union
            select journey_id
              from local_zone_exposure
             where sync_state = 0 and ended_at is not null
            union
            select id as journey_id
              from local_journeys
             where ended_at is not null and end_synced = 0
            """.trimIndent()

        runCatching {
            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)
                        ?.takeIf { it.isNotBlank() }
                        ?.let(ids::add)
                }
            }
        }.onFailure {
            LocalLog.append(
                context,
                "SYNC 0.20.3 pendingJourneyIds falhou: ${it.message}",
            )
        }
        return ids
    }

    /**
     * Estado 2 = preservado localmente / não pertencente à sessão autenticada.
     * Nada é apagado. Histórico local continua existindo, mas o dado deixa de
     * ser reenviado indefinidamente para a conta errada.
     *
     * Retorna número de itens que saíram da fila, ou -1 quando não é seguro
     * colocar a jornada em quarentena (por exemplo, jornada ativa atual).
     */
    private fun quarantineConflictingJourney(
        context: Context,
        journey: JourneyRecord,
    ): Int {
        val repo = SettingsRepository(context)
        val currentId = repo.currentJourneyId()

        if (
            currentId == journey.id &&
            (repo.isProjectionActive() || journey.endedAt == null)
        ) {
            LocalLog.append(
                context,
                "SYNC 0.20.3: NÃO colocou jornada ativa em quarentena id=${journey.id.take(8)}",
            )
            return -1
        }

        if (currentId == journey.id && journey.endedAt != null) {
            repo.clearCurrentJourney()
        }

        val db = LocalStore.get(context).writableDatabase

        fun count(sql: String, args: Array<String>): Int =
            db.rawQuery(sql, args).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

        val offers = count(
            "select count(*) from local_offers where journey_id=? and sync_state=0",
            arrayOf(journey.id),
        )
        val contexts = count(
            """
            select count(*)
              from local_offer_context c
              join local_offers o on o.local_id=c.local_offer_id
             where o.journey_id=? and c.sync_state=0
            """.trimIndent(),
            arrayOf(journey.id),
        )
        val events = count(
            "select count(*) from local_journey_events where journey_id=? and sync_state=0",
            arrayOf(journey.id),
        )
        val outcomes = count(
            "select count(*) from local_ride_outcomes where journey_id=? and sync_state=0",
            arrayOf(journey.id),
        )
        val exposures = count(
            "select count(*) from local_zone_exposure where journey_id=? and sync_state=0",
            arrayOf(journey.id),
        )
        val total = offers + contexts + events + outcomes + exposures

        db.beginTransaction()
        try {
            db.execSQL(
                """
                update local_offer_context
                   set sync_state=2
                 where sync_state=0
                   and local_offer_id in (
                       select local_id from local_offers where journey_id=?
                   )
                """.trimIndent(),
                arrayOf(journey.id),
            )
            db.execSQL(
                "update local_offers set sync_state=2 where journey_id=? and sync_state=0",
                arrayOf(journey.id),
            )
            db.execSQL(
                "update local_journey_events set sync_state=2 where journey_id=? and sync_state=0",
                arrayOf(journey.id),
            )
            db.execSQL(
                "update local_ride_outcomes set sync_state=2 where journey_id=? and sync_state=0",
                arrayOf(journey.id),
            )
            db.execSQL(
                "update local_zone_exposure set sync_state=2 where journey_id=? and sync_state=0",
                arrayOf(journey.id),
            )
            db.execSQL(
                "update local_journeys set start_synced=2, end_synced=2 where id=?",
                arrayOf(journey.id),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return total
    }

    private fun ensureJourney(
        context: Context,
        journey: JourneyRecord,
        baseUrl: String,
        token: String,
    ): EnsureJourneyStatus {
        val body = JSONObject().apply {
            put("action", "start")
            put("journey_id", journey.id)
            put("platform", journey.platform)
            put("started_at", journey.startedAt)
        }

        val result = request(
            "POST",
            "${baseUrl.trimEnd('/')}/api/v1/journeys",
            body,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markJourneyStartSynced(journey.id)
            return EnsureJourneyStatus.OK
        }

        LocalLog.append(
            context,
            "SYNC 0.20.3 ensureJourney ${journey.id.take(8)}: " +
                "${result.status} ${result.errorCode ?: "erro"}",
        )

        return if (
            result.status == 409 &&
            result.errorCode == "journey_id_conflict"
        ) {
            EnsureJourneyStatus.CONFLICT_OTHER_ACCOUNT
        } else {
            EnsureJourneyStatus.FAILED
        }
    }

    private fun sendOffer(
        context: Context,
        offer: RideOffer,
        baseUrl: String,
        token: String,
    ): Boolean {
        fun post(): HttpResult =
            request(
                "POST",
                "${baseUrl.trimEnd('/')}/api/v1/offers",
                offer.toJson(),
                token,
            )

        var result = post()

        if (
            !result.ok &&
            result.errorCode == "invalid_journey" &&
            !offer.journeyId.isNullOrBlank()
        ) {
            LocalStore.get(context).journey(offer.journeyId)?.let { journey ->
                when (ensureJourney(context, journey, baseUrl, token)) {
                    EnsureJourneyStatus.OK -> result = post()
                    EnsureJourneyStatus.CONFLICT_OTHER_ACCOUNT -> {
                        quarantineConflictingJourney(context, journey)
                    }
                    EnsureJourneyStatus.FAILED -> Unit
                }
            }
        }

        if (result.ok) {
            LocalStore.get(context).markOfferSynced(offer.localId)
            return true
        }

        LocalLog.append(
            context,
            "SYNC 0.20 oferta ${offer.localId.take(8)}: " +
                "${result.status} ${result.errorCode ?: "erro"}",
        )
        return false
    }

    private fun sendContext(
        context: Context,
        item: LocalStore.PendingOfferContext,
        baseUrl: String,
        token: String,
    ): Boolean {
        val ctx = item.context
        val body = JSONObject().apply {
            put("dedupe_key", item.dedupeKey)
            putNullable("pickup_label", ctx.pickupLabel)
            putNullable("destination_label", ctx.destinationLabel)
            putNullable("pickup_lat", ctx.pickupLat)
            putNullable("pickup_lng", ctx.pickupLng)
            putNullable("destination_lat", ctx.destinationLat)
            putNullable("destination_lng", ctx.destinationLng)
            putNullable("pickup_cell", ctx.pickupCell)
            putNullable("destination_cell", ctx.destinationCell)
            putNullable("estimated_arrival_at", ctx.estimatedArrivalAt)
            put(
                "context_confidence",
                ctx.contextConfidence.coerceIn(0.0, 1.0),
            )
            put("geocode_status", ctx.geocodeStatus)
            putNullable("geocode_source", ctx.geocodeSource)
            put("context_version", ctx.contextVersion)
            put("context_source_type", ctx.sourceType)
            put("context_time_source", ctx.timeSource)
        }

        val result = request(
            "PATCH",
            "${baseUrl.trimEnd('/')}/api/v1/offers",
            body,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markContextSynced(item.localId)
            return true
        }

        LocalLog.append(
            context,
            "SYNC 0.20 contexto ${item.localId.take(8)}: " +
                "${result.status} ${result.errorCode ?: "erro"}",
        )
        return false
    }

    private fun sendJourneyEvent(
        context: Context,
        event: JourneyStateEvent,
        baseUrl: String,
        token: String,
    ): Boolean {
        val body = JSONObject().apply {
            put("action", "state_event")
            put("client_event_id", event.id)
            put("journey_id", event.journeyId)
            put("event_type", event.eventType)
            put("state", event.state.name)
            put("occurred_at", event.occurredAt)
        }

        val result = postJourneyWithRepair(
            context,
            event.journeyId,
            body,
            baseUrl,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markJourneyEventSynced(event.id)
            return true
        }

        logJourneyFailure(context, "evento", event.journeyId, result)
        return false
    }

    private fun sendOutcome(
        context: Context,
        outcome: RideOutcome,
        baseUrl: String,
        token: String,
    ): Boolean {
        val body = JSONObject().apply {
            put("action", "ride_outcome")
            put("local_offer_id", outcome.localOfferId)
            put("journey_id", outcome.journeyId)
            put("status", outcome.status.name)
            putNullable("started_at", outcome.startedAt)
            putNullable("completed_at", outcome.completedAt)
            putNullable("cancelled_at", outcome.cancelledAt)
            putNullable("corrected_at", outcome.correctedAt)
            put("source", outcome.source)
            put("revision", outcome.revision)
        }

        val result = postJourneyWithRepair(
            context,
            outcome.journeyId,
            body,
            baseUrl,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markRideOutcomeSynced(
                outcome.localOfferId,
                outcome.revision,
            )
            return true
        }

        logJourneyFailure(context, "outcome", outcome.journeyId, result)
        return false
    }

    private fun sendExposure(
        context: Context,
        exposure: RegionalExposure,
        baseUrl: String,
        token: String,
    ): Boolean {
        val body = JSONObject().apply {
            put("action", "exposure")
            put("client_exposure_id", exposure.id)
            put("journey_id", exposure.journeyId)
            put("cell", exposure.cell)
            put("started_at", exposure.startedAt)
            put("ended_at", exposure.endedAt)
            put("duration_seconds", exposure.durationSeconds ?: 0L)
            put("close_reason", exposure.closeReason ?: "unknown")
            putNullable("next_offer_local_id", exposure.nextOfferLocalId)
            putNullable(
                "location_accuracy_m",
                exposure.locationAccuracyM,
            )
        }

        val result = postJourneyWithRepair(
            context,
            exposure.journeyId,
            body,
            baseUrl,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markExposureSynced(exposure.id)
            return true
        }

        logJourneyFailure(context, "exposição", exposure.journeyId, result)
        return false
    }

    private fun sendJourneyEnd(
        context: Context,
        journey: JourneyRecord,
        baseUrl: String,
        token: String,
    ): Boolean {
        val endedAt = journey.endedAt ?: return true
        val body = JSONObject().apply {
            put("action", "end")
            put("journey_id", journey.id)
            put("ended_at", endedAt)
            put(
                "end_reason",
                journey.endReason ?: "user_or_system",
            )
        }

        val result = postJourneyWithRepair(
            context,
            journey.id,
            body,
            baseUrl,
            token,
        )

        if (result.ok) {
            LocalStore.get(context).markJourneyEndSynced(journey.id)
            return true
        }

        logJourneyFailure(context, "fim", journey.id, result)
        return false
    }

    private fun postJourneyWithRepair(
        context: Context,
        journeyId: String,
        body: JSONObject,
        baseUrl: String,
        token: String,
    ): HttpResult {
        fun post(): HttpResult =
            request(
                "POST",
                "${baseUrl.trimEnd('/')}/api/v1/journeys",
                body,
                token,
            )

        var result = post()
        if (
            !result.ok &&
            result.errorCode == "journey_not_found"
        ) {
            LocalStore.get(context).journey(journeyId)?.let { journey ->
                when (ensureJourney(context, journey, baseUrl, token)) {
                    EnsureJourneyStatus.OK -> result = post()
                    EnsureJourneyStatus.CONFLICT_OTHER_ACCOUNT -> {
                        quarantineConflictingJourney(context, journey)
                    }
                    EnsureJourneyStatus.FAILED -> Unit
                }
            }
        }
        return result
    }

    private fun logJourneyFailure(
        context: Context,
        type: String,
        journeyId: String,
        result: HttpResult,
    ) {
        LocalLog.append(
            context,
            "SYNC 0.20 $type jornada=${journeyId.take(8)}: " +
                "${result.status} ${result.errorCode ?: "erro"}",
        )
    }

    private fun request(
        method: String,
        url: String,
        body: JSONObject?,
        token: String,
    ): HttpResult {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 18_000
                doOutput = body != null
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8",
                )
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    "X-SrRotas-App-Version",
                    BuildConfig.VERSION_NAME,
                )
                setRequestProperty(
                    "Authorization",
                    "Bearer $token",
                )
            }

        if (body != null) {
            connection.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(Charsets.UTF_8),
                )
            }
        }

        val status = connection.responseCode
        val stream =
            if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val text = runCatching {
            stream?.use {
                BufferedReader(
                    InputStreamReader(it),
                ).readText()
            } ?: ""
        }.getOrDefault("")

        connection.disconnect()
        return HttpResult(status, text)
    }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?,
    ) {
        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }
    }
}
