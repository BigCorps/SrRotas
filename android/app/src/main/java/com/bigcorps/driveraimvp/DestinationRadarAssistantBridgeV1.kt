package com.srrotas.app

import android.content.Context

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

    data class Signal(val headline:String,val action:String,val opportunityId:String)
    data class CooldownDiagnostic(
        val lastOpportunityId:String?,
        val lastShownAtMs:Long,
        val remainingMs:Long,
    )

    fun signal(context:Context,result:RadarContextualResultV1):Signal? {
        val a=result.assistant
        if(!a.eligible) {
            RadarContextualDiagnosticV1.assistantDecision(result,"backend_not_eligible")
            return null
        }
        if(a.headline.isNullOrBlank() || a.opportunityId.isNullOrBlank()) {
            RadarContextualDiagnosticV1.assistantDecision(result,"payload_incompleto")
            return null
        }
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        val same=p.getString(KEY_LAST_ID,"")==a.opportunityId
        val recent=now-p.getLong(KEY_LAST_AT,0L)<COOLDOWN_MS
        if(same && recent) {
            RadarContextualDiagnosticV1.assistantDecision(result,"cooldown_mesma_oportunidade")
            return null
        }
        RadarContextualDiagnosticV1.assistantDecision(result,"elegivel_para_exibir")
        return Signal(a.headline,a.actionLabel?:"Ver",a.opportunityId)
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
