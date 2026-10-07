package com.srrotas.app

import android.content.Context
import android.content.SharedPreferences
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
    private const val KEY_LAST_REGION_RIDE_ID="last_region_ride_id"
    private const val KEY_LAST_REGION_AT="last_region_at"
    private const val COOLDOWN_MS=20*60_000L

    enum class Kind { STRONG, DISCOVERY, REGION }
    data class Signal(val headline:String,val action:String,val opportunityId:String?,val kind:Kind=Kind.STRONG,
        val cooldownKey:String= requireNotNull(opportunityId))
    internal data class Decision(val signal:Signal?,val reason:String)
    data class CooldownDiagnostic(
        val lastOpportunityId:String?,
        val lastShownAtMs:Long,
        val remainingMs:Long,
        val regionLastRideMatchesCurrent:Boolean,
    )

    internal data class RegionHistory(val rideKey:String?,val at:Long,val migrated:Boolean=false)
    internal fun resolveRegionHistory(currentKey:String, savedKey:String?, savedAt:Long, legacyAt:Long):RegionHistory =
        if(savedKey!=null) RegionHistory(savedKey,savedAt)
        else if(legacyAt>0L) RegionHistory(currentKey,legacyAt,true)
        else RegionHistory(null,0L)

    internal fun regionHistory(p:SharedPreferences,currentKey:String):RegionHistory {
        val savedKey=p.getString(KEY_LAST_REGION_RIDE_ID,null)
        val history=resolveRegionHistory(currentKey,savedKey,p.getLong(KEY_LAST_REGION_AT,0L),
            if(savedKey==null) p.getLong(currentKey,0L) else 0L)
        if(history.migrated) p.edit().putString(KEY_LAST_REGION_RIDE_ID,history.rideKey)
            .putLong(KEY_LAST_REGION_AT,history.at).remove(currentKey).apply()
        return history
    }

    fun signal(context:Context,result:RadarContextualResultV1):Signal? {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val spec=RadarDestinationContextV1.current(context)
        val regionKey=spec?.localOfferId?.let { "region:$it" }
            ?.takeIf { result.opportunities.isEmpty() && !result.assistant.eligible }
        val history=regionKey?.let { regionHistory(p,it) }
        val decision=decide(result, System.currentTimeMillis(),
            if(regionKey!=null) history?.rideKey else p.getString(KEY_LAST_ID,null),
            if(regionKey!=null) history?.at ?: 0L else p.getLong(KEY_LAST_AT,0L),
            spec != null && DestinationRadarRuntimeV1.latestFor(spec.localOfferId) === result,
            spec?.eta ?: result.destinationEta, spec?.localOfferId)
        RadarContextualDiagnosticV1.assistantDecision(result,decision.reason,decision.signal)
        return decision.signal
    }

    // JVM puro; strong conserva headline/action/id e cooldown do backend.
    internal fun decide(result:RadarContextualResultV1, now:Long,
        lastId:String?, lastAt:Long, currentRideValid:Boolean, eta:String, localOfferId:String?=null):Decision {
        val a=result.assistant
        val signal=if(a.eligible) {
            if(a.headline.isNullOrBlank() || a.opportunityId.isNullOrBlank())
                return Decision(null,"payload_incompleto")
            Signal(a.headline,a.actionLabel?:"Ver",a.opportunityId,Kind.STRONG)
        } else {
            if(!currentRideValid) return Decision(null,"current_ride_changed")
            val opportunity=result.opportunities.firstOrNull()
            if(opportunity==null && localOfferId.isNullOrBlank())
                return Decision(null,"backend_not_eligible_no_opportunities")
            if(opportunity==null && lastId=="region:$localOfferId")
                return Decision(null,"cooldown_mesma_corrida")
            val etaMs=runCatching { Instant.parse(eta).toEpochMilli() }.getOrNull()
            if(etaMs==null || etaMs-now !in -10*60_000L..18*60_000L)
                return Decision(null,if(opportunity==null) "outside_region_eta_window" else "outside_discovery_eta_window")
            if(opportunity==null) {
                Signal("Radar analisou sua região de chegada. Quer ver?","Ver região",null,Kind.REGION,
                    cooldownKey="region:$localOfferId")
            } else {
                if(opportunity.id.isBlank()) return Decision(null,"payload_incompleto")
                Signal("Há oportunidades próximas ao seu destino. Quer ver?","Ver",opportunity.id,Kind.DISCOVERY)
            }
        }
        if(lastId==signal.cooldownKey && now-lastAt<COOLDOWN_MS)
            return Decision(null,"cooldown_mesma_oportunidade")
        return Decision(signal,when(signal.kind) {
            Kind.STRONG -> "backend_strong"
            Kind.DISCOVERY -> "discovery_opportunity_available"
            Kind.REGION -> "region_signal_generated"
        })
    }

    internal fun markRegionShown(edit:SharedPreferences.Editor,cooldownKey:String,now:Long) {
        edit.putString(KEY_LAST_REGION_RIDE_ID,cooldownKey).putLong(KEY_LAST_REGION_AT,now)
    }

    fun markShown(context:Context,signal:Signal) {
        val now=System.currentTimeMillis()
        val edit=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
        if(signal.kind==Kind.REGION) markRegionShown(edit,signal.cooldownKey,now)
        else edit.putString(KEY_LAST_ID,signal.cooldownKey).putLong(KEY_LAST_AT,now)
        edit.putLong("last_any_shown_at",now).apply()
    }

    fun diagnosticState(context:Context):CooldownDiagnostic {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val lastAt=p.getLong("last_any_shown_at",p.getLong(KEY_LAST_AT,0L))
        val now=System.currentTimeMillis()
        val currentKey=RadarDestinationContextV1.current(context)?.localOfferId?.let { "region:$it" }
        val regionMatches=currentKey!=null && regionHistory(p,currentKey).rideKey==currentKey
        return CooldownDiagnostic(
            lastOpportunityId=p.getString(KEY_LAST_ID,null),
            lastShownAtMs=lastAt,
            remainingMs=if(p.getLong(KEY_LAST_AT,0L)<=0L)0L else (COOLDOWN_MS-(now-p.getLong(KEY_LAST_AT,0L))).coerceAtLeast(0L),
            regionLastRideMatchesCurrent=regionMatches,
        )
    }
}
