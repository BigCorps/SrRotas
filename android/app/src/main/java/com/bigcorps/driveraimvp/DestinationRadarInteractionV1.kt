package com.srrotas.app
import android.content.Context

object DestinationRadarInteractionV1 {
    fun shown(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        DestinationRadarAssistantBridgeV1.markShown(context,signal.opportunityId)
        RadarContextualTelemetryV1.track(context,"assistant_shown",spec,result,signal.opportunityId)
    }
    fun ignore(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        RadarContextualTelemetryV1.track(context,"assistant_ignored",spec,result,signal.opportunityId)
    }
    fun view(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        RadarContextualTelemetryV1.track(context,"assistant_viewed",spec,result,signal.opportunityId)
    }
}
