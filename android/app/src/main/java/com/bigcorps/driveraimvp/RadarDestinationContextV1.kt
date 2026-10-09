package com.srrotas.app

data class RadarDestinationSpecV1(
    val lat:Double,
    val lng:Double,
    val eta:String,
    val label:String?,
    val localOfferId:String,
)

object RadarDestinationContextV1 {
    /**
     * Fonte única: currentRide.localOfferId -> oferta persistida -> OfferContext.
     * Não cria estado paralelo e não depende de latestOffer continuar apontando
     * para a corrida em andamento.
     */
    fun current(context:android.content.Context):RadarDestinationSpecV1? {
        val snapshot=JourneyCoordinator.snapshot(context)
        if(!snapshot.isDoingRide) return null
        val ride=snapshot.currentRide ?: return null

        val latest = snapshot.latestOffer?.takeIf { it.localId==ride.localOfferId }
        val offer =
            latest
                ?: LocalStore.get(context).recentOffers(100)
                    .firstOrNull { it.localId==ride.localOfferId }
                ?: run {
                    FieldPipelineTraceV1.event(context, ride.localOfferId, "RADAR_SPEC_BLOCKED", "source_offer_missing")
                    return null
                }

        FieldPipelineTraceV1.event(context,offer.localId,"RADAR_SPEC_SOURCE",
            if(latest != null) "latest_offer_snapshot" else "persisted_recent_offer")
        FieldPipelineTraceV1.context(context, offer.localId, offer.context)
        val block = FieldPipelineFactsV1.specBlock(offer)
        FieldPipelineTraceV1.event(context, offer.localId, if(block == null) "RADAR_SPEC_READY" else "RADAR_SPEC_BLOCKED", block ?: "ready")
        FieldPipelineFactsV1.etaDeltaSeconds(offer.context?.estimatedArrivalAt, System.currentTimeMillis())?.let {
            FieldPipelineTraceV1.event(context, offer.localId, "ETA_DELTA_SECONDS", "present", it)
        }
        return fromOffer(offer)
    }

    fun fromOffer(offer:RideOffer):RadarDestinationSpecV1? {
        val c=offer.context ?: return null
        return RadarDestinationSpecV1(
            lat=c.destinationLat ?: return null,
            lng=c.destinationLng ?: return null,
            eta=c.estimatedArrivalAt?.takeIf(String::isNotBlank) ?: return null,
            label=c.destinationLabel,
            localOfferId=offer.localId,
        )
    }
}
