package com.srrotas.app
import android.content.Context

object DestinationRadarInteractionV1 {
    internal fun viewIfCurrentRide(expectedId:String, currentId:String?,
        signal:DestinationRadarAssistantBridgeV1.Signal, onView:()->Unit, launch:(String?)->Unit):Boolean {
        if(currentId!=expectedId) return false
        onView()
        launch(signal.opportunityId)
        return true
    }

    fun shown(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        DestinationRadarAssistantBridgeV1.markShown(context,signal)
        RadarContextualTelemetryV1.track(context,"assistant_shown",spec,result,signal.opportunityId)
    }
    fun ignore(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        RadarContextualTelemetryV1.track(context,"assistant_ignored",spec,result,signal.opportunityId)
    }
    fun view(context:Context,spec:RadarDestinationSpecV1,result:RadarContextualResultV1,signal:DestinationRadarAssistantBridgeV1.Signal){
        if(signal.kind==DestinationRadarAssistantBridgeV1.Kind.REGION) RadarContextualDiagnosticV1.regionViewClicked()
        RadarContextualTelemetryV1.track(context,"assistant_viewed",spec,result,signal.opportunityId)
    }
}
