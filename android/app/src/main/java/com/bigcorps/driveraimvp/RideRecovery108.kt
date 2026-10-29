package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * vc108 — recuperação pós-jornada de corridas realizadas que ficaram sem confirmação.
 *
 * Fontes (nenhuma é promovida sozinha):
 *  - resumo da sessão Uber digitalizado: apenas contagem de referência; o faturamento do
 *    resumo nunca é somado às tarifas das corridas;
 *  - histórico Uber digitalizado: evidência individual (tarifa, horário, serviço);
 *  - ofertas observadas da jornada e seus estados (`local_ride_outcomes`).
 *
 * Uma corrida só passa a COMPLETED quando o motorista confirma um candidato que tem oferta
 * observada na própria jornada; a gravação usa o caminho existente
 * (`JourneyCoordinator.correctRide` → revisão incremental → sync idempotente por revisão),
 * com origem `recovery_108`, e pode ser desfeita. Histórico sem oferta observada permanece
 * como diferença não resolvida; ofertas não viram corridas concluídas automaticamente.
 */
object RideRecovery108 {
    const val SOURCE = "recovery_108"
    const val SOURCE_UNDO = "recovery_108_undo"
    private const val PREFS = "ride_recovery_v108"
    private const val BEFORE_MIN = 10L
    private const val AFTER_MIN = 75L

    enum class Kind { RECOVERABLE, STARTED_NOT_CONFIRMED, ALREADY_CONFIRMED, AMBIGUOUS, NO_OBSERVED_OFFER, CANCELLED_IN_HISTORY }
    enum class Decision { PENDING, CONFIRMED, REJECTED, DUPLICATE }

    data class OfferState(val offer: RideOffer, val status: RideOperationalStatus?, val source: String?)

    data class Item(
        val key: String,
        val kind: Kind,
        val offerId: String?,
        val imported: UberCompletedRide026?,
        val fare: Double?,
        val evidence: String,
        val decision: Decision,
    ) {
        val actionable get() = (kind == Kind.RECOVERABLE || kind == Kind.STARTED_NOT_CONFIRMED) && offerId != null
    }

    data class Report(
        val journeyId: String,
        val uberCompleted: Int?,
        val uberEarnings: Double?,
        val confirmed: Int,
        val items: List<Item>,
    ) {
        val pending get() = items.count { it.actionable && it.decision == Decision.PENDING }
        val recovered get() = items.count { it.decision == Decision.CONFIRMED }
        val withoutOffer get() = items.count { it.kind == Kind.NO_OBSERVED_OFFER || it.kind == Kind.AMBIGUOUS }
        /** Diferença entre o resumo Uber e o que está comprovado/pendente no Sr. Rotas (nunca negativa). */
        val unresolved get() = uberCompleted?.let { maxOf(0, it - confirmed - pending) }
    }

    data class Stored(val decision: Decision, val offerId: String?, val previousStatus: String?, val decidedAt: String)

    // ------------------------------------------------------------------ pure core

    fun build(
        journeyId: String,
        offers: List<OfferState>,
        imports: List<UberCompletedRide026>,
        session: UberSessionSummary026?,
        decisions: Map<String, Stored> = emptyMap(),
    ): Report {
        val used = HashSet<String>()
        val items = mutableListOf<Item>()
        val completed = imports.filter { it.rideStatus == UberCompletedRide026.STATUS_COMPLETED }
            .sortedBy { it.occurredAt ?: "~" }
        completed.forEach { ride ->
            val candidates = offers.filter { it.offer.localId !in used && compatible(it.offer, ride) }
            val ranked = candidates.sortedBy { gapMinutes(it.offer, ride) ?: Long.MAX_VALUE }
            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            val ambiguous = best != null && second != null &&
                ((gapMinutes(second.offer, ride) ?: Long.MAX_VALUE) - (gapMinutes(best.offer, ride) ?: Long.MAX_VALUE) < 10)
            val key = ride.sourceKey
            val stored = decisions[key]
            items += when {
                best == null -> Item(key, Kind.NO_OBSERVED_OFFER, null, ride, ride.fare,
                    "Histórico Uber sem oferta observada compatível nesta jornada.", stored?.decision ?: Decision.PENDING)
                ambiguous -> Item(key, Kind.AMBIGUOUS, null, ride, ride.fare,
                    "Mais de uma oferta observada com a mesma tarifa em horários próximos; confirme manualmente na lista de corridas.",
                    stored?.decision ?: Decision.PENDING)
                else -> {
                    used += best.offer.localId
                    val done = best.status == RideOperationalStatus.COMPLETED
                    val decision = when {
                        stored?.decision == Decision.CONFIRMED && done -> Decision.CONFIRMED
                        done -> Decision.DUPLICATE
                        stored != null && stored.decision != Decision.CONFIRMED -> stored.decision
                        else -> Decision.PENDING
                    }
                    Item(key, if (done && decision == Decision.DUPLICATE) Kind.ALREADY_CONFIRMED else Kind.RECOVERABLE,
                        best.offer.localId, ride, ride.fare, evidence(best, ride), decision)
                }
            }
        }
        imports.filter { it.rideStatus == UberCompletedRide026.STATUS_CANCELLED }.forEach { ride ->
            items += Item(ride.sourceKey, Kind.CANCELLED_IN_HISTORY, null, ride, ride.fare,
                "Cancelada no histórico Uber: não conta como corrida concluída.", Decision.REJECTED)
        }
        // Corrida iniciada e encerrada automaticamente pelo fim da jornada, sem histórico que a confirme.
        offers.filter {
            it.offer.localId !in used && it.status == RideOperationalStatus.NOT_COMPLETED && it.source == "journey_end_auto"
        }.forEach { state ->
            val key = "offer:${state.offer.localId}"
            items += Item(key, Kind.STARTED_NOT_CONFIRMED, state.offer.localId, null, state.offer.fare,
                "Corrida iniciada no Sr. Rotas e encerrada pelo fim da jornada sem confirmação.",
                decisions[key]?.decision ?: Decision.PENDING)
        }
        // Confirmações feitas aqui continuam visíveis (desfazer) mesmo depois que o estado mudou.
        offers.filter { it.offer.localId !in used && it.source == SOURCE && it.status == RideOperationalStatus.COMPLETED }
            .forEach { state ->
                val key = decisions.entries.firstOrNull { it.value.offerId == state.offer.localId }?.key ?: "offer:${state.offer.localId}"
                if (items.none { it.key == key }) items += Item(key, Kind.STARTED_NOT_CONFIRMED, state.offer.localId, null,
                    state.offer.fare, "Confirmada na recuperação pós-jornada.", Decision.CONFIRMED)
            }
        return Report(
            journeyId = journeyId,
            uberCompleted = session?.completedTrips,
            uberEarnings = session?.earnings,
            confirmed = offers.count { it.status == RideOperationalStatus.COMPLETED },
            items = items,
        )
    }

    private fun compatible(offer: RideOffer, ride: UberCompletedRide026): Boolean {
        if (offer.platform != "uber") return false
        if (abs(offer.fare - ride.fare) > 0.011) return false
        val a = service(offer.serviceType); val b = service(ride.serviceType)
        if (a != null && b != null && a != b) return false
        val gap = signedGapMinutes(offer, ride) ?: return true
        return gap in -BEFORE_MIN..AFTER_MIN
    }

    private fun service(raw: String?): String? = raw?.lowercase()?.replace(Regex("[^a-z0-9]"), "")
        ?.takeUnless { it.isBlank() || it == "unknown" }

    private fun signedGapMinutes(offer: RideOffer, ride: UberCompletedRide026): Long? {
        val at = ride.occurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val seen = runCatching { Instant.parse(offer.observedAt) }.getOrNull() ?: return null
        return Duration.between(seen, at).toMinutes()
    }

    private fun gapMinutes(offer: RideOffer, ride: UberCompletedRide026) = signedGapMinutes(offer, ride)?.let(::abs)

    private fun evidence(state: OfferState, ride: UberCompletedRide026): String = buildString {
        append("Tarifa igual (R$ ${"%.2f".format(java.util.Locale("pt", "BR"), ride.fare)})")
        gapMinutes(state.offer, ride)?.let { append(" · histórico $it min após a oferta") } ?: append(" · histórico sem horário")
        service(ride.serviceType)?.let { append(" · serviço ${ride.serviceType}") }
        append(" · estado atual: ${label(state.status)}")
    }

    fun label(status: RideOperationalStatus?): String = when (status) {
        RideOperationalStatus.OFFERED, null -> "oferta sem decisão"
        RideOperationalStatus.DOING_RIDE -> "em corrida"
        RideOperationalStatus.COMPLETED -> "realizada"
        RideOperationalStatus.NOT_COMPLETED -> "não realizada"
        RideOperationalStatus.CANCELLED -> "cancelada"
    }

    // ------------------------------------------------------------------ device state

    fun report(context: Context, journeyId: String): Report? {
        val store = LocalStore.get(context)
        val journey = store.journey(journeyId) ?: return null
        val offers = store.offersForJourney(journeyId).map { offer ->
            val outcome = store.rideOutcomeForOffer(offer.localId)
            OfferState(offer, outcome?.status, outcome?.source)
        }
        val start = runCatching { Instant.parse(journey.startedAt) }.getOrNull() ?: return null
        val end = journey.endedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val digitization = UberDigitizationStore026.get(context)
        val imports = digitization.ridesBetween(start.minus(Duration.ofMinutes(BEFORE_MIN)).toString(),
            end.plus(Duration.ofMinutes(AFTER_MIN)).toString())
        return build(journeyId, offers, imports, digitization.sessionForJourney(journeyId), decisions(context, journeyId))
    }

    fun confirm(context: Context, report: Report, item: Item): Boolean {
        val offerId = item.offerId?.takeIf { item.actionable } ?: return false
        val store = LocalStore.get(context)
        val current = store.rideOutcomeForOffer(offerId)
        if (current?.status == RideOperationalStatus.COMPLETED) {
            // Já realizada: por outra via vira duplicada; por esta recuperação, mantém a decisão (desfazer).
            if (current.source != SOURCE) {
                save(context, report.journeyId, item.key, Stored(Decision.DUPLICATE, offerId, current.status.name, Instant.now().toString()))
            }
            return false
        }
        // Guarda o estado anterior antes de gravar, para auditoria e desfazer.
        save(context, report.journeyId, item.key,
            Stored(Decision.CONFIRMED, offerId, current?.status?.name, Instant.now().toString()))
        JourneyCoordinator.correctRide(context, offerId, RideOperationalStatus.COMPLETED, SOURCE) ?: run {
            forget(context, report.journeyId, item.key); return false
        }
        return true
    }

    fun reject(context: Context, report: Report, item: Item) {
        save(context, report.journeyId, item.key, Stored(Decision.REJECTED, item.offerId, null, Instant.now().toString()))
    }

    /** Desfaz a decisão; uma confirmação volta ao estado anterior por uma nova revisão. */
    fun undo(context: Context, report: Report, item: Item): Boolean {
        val stored = decisions(context, report.journeyId)[item.key]
        if (stored?.decision == Decision.CONFIRMED && stored.offerId != null) {
            val previous = stored.previousStatus?.let { runCatching { RideOperationalStatus.valueOf(it) }.getOrNull() }
                ?: RideOperationalStatus.NOT_COMPLETED
            val current = LocalStore.get(context).rideOutcomeForOffer(stored.offerId)
            if (current?.status == RideOperationalStatus.COMPLETED && current.source == SOURCE) {
                JourneyCoordinator.correctRide(context, stored.offerId, previous, SOURCE_UNDO)
            }
        }
        forget(context, report.journeyId, item.key)
        return true
    }

    fun decisions(context: Context, journeyId: String): Map<String, Stored> {
        val json = runCatching { JSONObject(prefs(context).getString(journeyId, "{}")) }.getOrDefault(JSONObject())
        return json.keys().asSequence().mapNotNull { key ->
            val row = json.optJSONObject(key) ?: return@mapNotNull null
            val decision = runCatching { Decision.valueOf(row.optString("decision")) }.getOrNull() ?: return@mapNotNull null
            key to Stored(decision, row.optString("offer_id").takeIf { it.isNotBlank() },
                row.optString("previous_status").takeIf { it.isNotBlank() }, row.optString("decided_at"))
        }.toMap()
    }

    private fun save(context: Context, journeyId: String, key: String, value: Stored) {
        val json = runCatching { JSONObject(prefs(context).getString(journeyId, "{}")) }.getOrDefault(JSONObject())
        json.put(key, JSONObject().put("decision", value.decision.name).put("offer_id", value.offerId ?: "")
            .put("previous_status", value.previousStatus ?: "").put("decided_at", value.decidedAt))
        prefs(context).edit().putString(journeyId, json.toString()).commit()
    }

    private fun forget(context: Context, journeyId: String, key: String) {
        val json = runCatching { JSONObject(prefs(context).getString(journeyId, "{}")) }.getOrDefault(JSONObject())
        json.remove(key)
        prefs(context).edit().putString(journeyId, json.toString()).commit()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
