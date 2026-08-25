package com.srrotas.app

import android.content.Intent
import android.net.Uri

/** Rota completa: posição atual -> busca -> destino, via Google Maps URLs. */
object CombinedRoute0212 {
    fun intent(context: OfferContext?): Intent? {
        context ?: return null
        val pickup = point(context.pickupLabel, context.pickupLat, context.pickupLng) ?: return null
        val destination = point(context.destinationLabel, context.destinationLat, context.destinationLng) ?: return null
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", destination)
            .appendQueryParameter("waypoints", pickup)
            .appendQueryParameter("travelmode", "driving")
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun point(label: String?, lat: Double?, lng: Double?): String? =
        if (lat != null && lng != null) "$lat,$lng" else label?.trim()?.takeIf { it.length >= 4 }
}
