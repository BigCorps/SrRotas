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
 * 0.20 — coordenador único de sincronização.
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
        val skipped: Boolean = false,
        val reason: String? = null,
    ) {
        val syncedItems: Int get() = (before.total - after.total).coerceAtLeast(0)

        fun userMessage(): String = when {
            skipped && reason == "offline" ->
                "Sem internet. Os dados continuam salvos no aparelho."
            skipped && reason == "not_paired" ->
                "Conecte sua conta para sincronizar."
            skipped ->
                "Sincronização não iniciada."
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
        Thread(runnable, "SrRotasSync020").apply {
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
        val pendingStarts =
            store.pendingJourneyStarts(200)
                .map { it.id }
                .toSet()

        val offers = store.pendingOffers(250)
        val journeyEvents = store.pendingJourneyEvents(250)
        val outcomes = store.pendingRideOutcomes(250)
        val exposures = store.pendingExposures(250)
        val journeyEnds = store.pendingJourneyEnds(200)

        val requiredJourneyIds = linkedSetOf<String>()
        store.pendingJourneyStarts(200).forEach {
            requiredJourneyIds += it.id
        }
        offers.mapNotNullTo(requiredJourneyIds) {
            it.journeyId?.takeIf(String::isNotBlank)
        }
        journeyEvents.forEach { requiredJourneyIds += it.journeyId }
        outcomes.forEach { requiredJourneyIds += it.journeyId }
        exposures.forEach { requiredJourneyIds += it.journeyId }
        journeyEnds.forEach { requiredJourneyIds += it.id }

        var succeeded = 0
        var failed = 0
        var ensured = 0
        var repairedCandidates = 0

        requiredJourneyIds.forEach { id ->
            val journey = store.journey(id)
            if (journey == null) {
                failed += 1
                LocalLog.append(
                    context,
                    "SYNC 0.20: jornada local ausente para filho pendente id=$id",
                )
            } else {
                val ok = ensureJourney(
                    context,
                    journey,
                    settings.backendUrl,
                    settings.deviceToken,
                )
                if (ok) {
                    ensured += 1
                    succeeded += 1
                    if (id !in pendingStarts) {
                        // A jornada já estava marcada como sincronizada localmente,
                        // mas foi garantida novamente para reparar eventual órfã.
                        repairedCandidates += 1
                    }
                } else {
                    failed += 1
                }
            }
        }

        offers.forEach { offer ->
            val result = sendOffer(
                context,
                offer,
                settings.backendUrl,
                settings.deviceToken,
            )
            if (result) succeeded += 1 else failed += 1
        }

        // Contextos geocodificados depois do POST inicial da oferta.
        // pendingOfferContexts só retorna contexto cujo pai já está sync_state=1.
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

        // End é enviado por último para que filhos de uma jornada recuperada
        // sejam persistidos antes de ela voltar ao estado ENDED.
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
            "SYNC 0.20: ${before.total} -> ${after.total}; " +
                "jornadas garantidas=$ensured; reparos candidatos=$repairedCandidates; " +
                "ok=$succeeded falhas=$failed",
        )

        return SyncResult(
            before = before,
            after = after,
            journeysEnsured = ensured,
            orphanCandidatesRepaired = repairedCandidates,
            requestsSucceeded = succeeded,
            requestsFailed = failed,
        )
    }

    private fun ensureJourney(
        context: Context,
        journey: JourneyRecord,
        baseUrl: String,
        token: String,
    ): Boolean {
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
            return true
        }

        LocalLog.append(
            context,
            "SYNC 0.20 ensureJourney ${journey.id.take(8)}: " +
                "${result.status} ${result.errorCode ?: "erro"}",
        )
        return false
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
                if (ensureJourney(context, journey, baseUrl, token)) {
                    result = post()
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
                if (ensureJourney(context, journey, baseUrl, token)) {
                    result = post()
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
