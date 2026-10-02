package com.srrotas.app

data class RadarContextualEvidenceV1(
    val kind:String,
    val label:String,
    val value:Double?,
    val samples:Int?,
    val reliability:String?,
)

data class RadarContextualContextV1(
    val id:String,
    val name:String,
    val type:String,
    val egressStartAt:String,
    val egressEndAt:String,
    val confidence:Double,
)

data class RadarContextualOpportunityV1(
    val id:String,
    val poiId:String,
    val title:String,
    val subtitle:String?,
    val poiType:String,
    val lat:Double,
    val lng:Double,
    val distanceKm:Double,
    val eta:String,
    val continuityProbabilityPct:Double?,
    val baselineProbabilityPct:Double?,
    val deltaProbabilityPct:Double?,
    val confidence:Double,
    val rankingScore:Double,
    val potential:String,
    val reasonHeadline:String,
    val windowStart:String?,
    val windowEnd:String?,
    val context:RadarContextualContextV1?,
    val evidence:List<RadarContextualEvidenceV1>,
)

data class RadarContextualBaselineV1(
    val probabilityPct:Double?,
    val samples:Int,
    val reliability:String,
    val source:String,
)

data class RadarContextualAssistantV1(
    val eligible:Boolean,
    val reason:String,
    val headline:String?,
    val actionLabel:String?,
    val opportunityId:String?,
    val minEtaMinutes:Int,
    val maxEtaMinutes:Int,
)

data class RadarContextualResultV1(
    val generatedAt:String,
    val destinationLat:Double,
    val destinationLng:Double,
    val destinationEta:String,
    val destinationLabel:String?,
    val destinationCell:String,
    val baseline:RadarContextualBaselineV1,
    val opportunities:List<RadarContextualOpportunityV1>,
    val assistant:RadarContextualAssistantV1,
)
