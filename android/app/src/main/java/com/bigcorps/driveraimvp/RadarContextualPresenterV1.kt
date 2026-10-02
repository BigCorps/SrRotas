package com.srrotas.app

object RadarContextualPresenterV1 {
    data class Marker(
        val id:String,val lat:Double,val lng:Double,val title:String,
        val type:String,val potential:String,val score:Double,
    )
    data class Card(
        val id:String,val title:String,val meta:String,val potentialLabel:String,
        val reason:String,val why:List<String>,val canNavigate:Boolean,
    )
    data class Screen(
        val destinationLat:Double,val destinationLng:Double,val destinationLabel:String?,
        val baselineLabel:String,val markers:List<Marker>,val cards:List<Card>,
    )

    fun map(result:RadarContextualResultV1):Screen {
        val baselineLabel=result.baseline.probabilityPct?.let {
            "Permanecer no destino · ${it.toInt()}% em até 10 min"
        } ?: "Permanecer no destino · amostra insuficiente"
        val visible=result.opportunities.take(8)
        return Screen(
            destinationLat=result.destinationLat,destinationLng=result.destinationLng,
            destinationLabel=result.destinationLabel,baselineLabel=baselineLabel,
            markers=visible.map {
                Marker(it.id,it.lat,it.lng,it.title,it.poiType,it.potential,it.rankingScore)
            },
            cards=visible.map {
                val pct=it.continuityProbabilityPct?.let { p -> "${p.toInt()}%" } ?: "sem amostra"
                Card(
                    id=it.id,title=it.title,
                    meta="${"%.1f".format(java.util.Locale("pt","BR"),it.distanceKm)} km · $pct",
                    potentialLabel=when(it.potential){
                        "high"->"POTENCIAL ALTO";"medium"->"POTENCIAL MÉDIO";"low"->"POTENCIAL BAIXO";else->"CONTEXTO"
                    },
                    reason=it.reasonHeadline,
                    why=it.evidence.map { e -> e.label }.take(5),
                    canNavigate=true
                )
            }
        )
    }
}
