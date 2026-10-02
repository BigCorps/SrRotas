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

        val offer =
            snapshot.latestOffer?.takeIf { it.localId==ride.localOfferId }
                ?: LocalStore.get(context).recentOffers(100)
                    .firstOrNull { it.localId==ride.localOfferId }
                ?: return null

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
