package com.srrotas.app

import android.content.Context

/**
 * Cola fina para o integrador. Não é chamada automaticamente por nenhum fluxo.
 */
object RadarContextualIntegrationV1 {
    fun startRuntimeIfEnabled(context:Context,listener:DestinationRadarRuntimeV1.Listener?=null) {
        if(RadarContextualFlagsV1.runtimeEnabled(context)) {
            DestinationRadarRuntimeV1.start(context,listener)
        } else {
            DestinationRadarRuntimeV1.stop()
        }
    }

    fun assistantAllowed(context:Context):Boolean =
        RadarContextualFlagsV1.runtimeEnabled(context) &&
        RadarContextualFlagsV1.assistantEnabled(context)
}
