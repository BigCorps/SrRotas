package com.srrotas.app

import android.content.Context
import java.time.Instant

/**
 * Ponte intencionalmente separada do ActiveAssistant026:
 * - ActiveAssistant tradicional roda quando NÃO há corrida ativa.
 * - DestinationRadarAssistant roda quando HÁ corrida ativa e ETA/destino existem.
 */
object DestinationRadarAssistantBridgeV1 {
    private const val PREFS="sr_radar_destination_assistant_v1"
    private const val KEY_LAST_ID="last_opportunity_id"
    private const val KEY_LAST_AT="last_shown_at"
    private const val COOLDOWN_MS=20*60_000L

    enum class Kind { STRONG, DISCOVERY }
    data class Signal(val headline:String,val action:String,val opportunityId:String,val kind:Kind=Kind.STRONG)
    internal data class Decision(val signal:Signal?,val reason:String)
    data class CooldownDiagnostic(
        val lastOpportunityId:String?,
        val lastShownAtMs:Long,
        val remainingMs:Long,
    )

    fun signal(context:Context,result:RadarContextualResultV1):Signal? {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val spec=RadarDestinationContextV1.current(context)
        val decision=decide(result, System.currentTimeMillis(),
            p.getString(KEY_LAST_ID,null), p.getLong(KEY_LAST_AT,0L),
            spec != null && DestinationRadarRuntimeV1.latestFor(spec.localOfferId) === result,
            spec?.eta ?: result.destinationEta)
        RadarContextualDiagnosticV1.assistantDecision(result,decision.reason)
        return decision.signal
    }

    // JVM puro; strong conserva headline/action/id e cooldown do backend.
    internal fun decide(result:RadarContextualResultV1, now:Long,
        lastId:String?, lastAt:Long, currentRideValid:Boolean, eta:String):Decision {
        val a=result.assistant
        val signal=if(a.eligible) {
            if(a.headline.isNullOrBlank() || a.opportunityId.isNullOrBlank())
                return Decision(null,"payload_incompleto")
            Signal(a.headline,a.actionLabel?:"Ver",a.opportunityId,Kind.STRONG)
        } else {
            if(!currentRideValid) return Decision(null,"current_ride_changed")
            val opportunity=result.opportunities.firstOrNull()
                ?: return Decision(null,"backend_not_eligible_no_opportunities")
            val etaMs=runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull()
            if(etaMs==null || etaMs-now !in 0L..18*60_000L)
                return Decision(null,"outside_discovery_eta_window")
            if(opportunity.id.isBlank()) return Decision(null,"payload_incompleto")
            Signal("Há oportunidades próximas ao seu destino. Quer ver?","Ver",opportunity.id,Kind.DISCOVERY)
        }
        if(lastId==signal.opportunityId && now-lastAt<COOLDOWN_MS)
            return Decision(null,"cooldown_mesma_oportunidade")
        return Decision(signal,if(signal.kind==Kind.STRONG) "backend_strong" else "discovery_opportunity_available")
    }

    fun markShown(context:Context,opportunityId:String) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ID,opportunityId)
            .putLong(KEY_LAST_AT,System.currentTimeMillis()).apply()
    }

    fun diagnosticState(context:Context):CooldownDiagnostic {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val lastAt=p.getLong(KEY_LAST_AT,0L)
        val now=System.currentTimeMillis()
        return CooldownDiagnostic(
            lastOpportunityId=p.getString(KEY_LAST_ID,null),
            lastShownAtMs=lastAt,
            remainingMs=if(lastAt<=0L)0L else (COOLDOWN_MS-(now-lastAt)).coerceAtLeast(0L),
        )
    }
}
