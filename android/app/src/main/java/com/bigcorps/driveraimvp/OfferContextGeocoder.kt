package com.srrotas.app

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import java.util.concurrent.Executors

object OfferContextGeocoder {
    private val executor = Executors.newSingleThreadExecutor()

    fun enrichAsync(context: Context, offer: RideOffer, onResult: (OfferContext) -> Unit) {
        val initial = offer.context ?: return
        if (!initial.hasTextContext()) return

        val app = context.applicationContext
        executor.execute {
            val store = LocalStore.get(app)
            val pickup = resolve(app, store, initial.pickupLabel, initial.pickupLat, initial.pickupLng)
            val destination = resolve(app, store, initial.destinationLabel, initial.destinationLat, initial.destinationLng)

            val resolvedCount = listOf(pickup, destination).count { it?.lat != null && it.lng != null }
            val expectedCount = listOf(initial.pickupLabel, initial.destinationLabel).count { !it.isNullOrBlank() }
            val status = when {
                expectedCount == 0 -> "unresolved"
                resolvedCount == expectedCount -> "resolved"
                resolvedCount > 0 -> "partial"
                else -> "unresolved"
            }
            val sources = listOfNotNull(pickup?.source, destination?.source).distinct().joinToString("+").ifBlank { "none" }

            val enriched = initial.copy(
                pickupLat = pickup?.lat ?: initial.pickupLat,
                pickupLng = pickup?.lng ?: initial.pickupLng,
                destinationLat = destination?.lat ?: initial.destinationLat,
                destinationLng = destination?.lng ?: initial.destinationLng,
                pickupCell = pickup?.cell ?: initial.pickupCell,
                destinationCell = destination?.cell ?: initial.destinationCell,
                geocodeStatus = status,
                geocodeSource = sources,
            )
            onResult(enriched)
        }
    }

    private data class Resolved(
        val lat: Double,
        val lng: Double,
        val cell: String?,
        val source: String,
    )

    private fun resolve(
        context: Context,
        store: LocalStore,
        label: String?,
        existingLat: Double?,
        existingLng: Double?,
    ): Resolved? {
        if (existingLat != null && existingLng != null) {
            return Resolved(existingLat, existingLng, OfferContextEngine.geoCell(existingLat, existingLng), "existing")
        }

        val query = label?.trim()?.takeIf { it.length >= 4 } ?: return null
        val cacheKey = OfferContextEngine.normalizePlaceForCache(query)
        store.cachedGeocode(cacheKey)?.let {
            return Resolved(it.lat, it.lng, it.cell, "cache")
        }

        if (!Geocoder.isPresent()) return null
        val address = geocodeBrazil(context, query) ?: return null
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val lat = address.latitude
        val lng = address.longitude
        if (lat !in -34.5..5.5 || lng !in -74.5..-32.0) return null

        val cell = OfferContextEngine.geoCell(lat, lng)
        store.cacheGeocode(cacheKey, query, lat, lng, cell)
        return Resolved(lat, lng, cell, "android_geocoder")
    }

    @Suppress("DEPRECATION")
    private fun geocodeBrazil(context: Context, label: String): Address? {
        return runCatching {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            // Restringe ao Brasil. Continua sendo enriquecimento auxiliar:
            // falha/ambiguidade nunca invalida a oferta.
            geocoder.getFromLocationName(
                label,
                1,
                -34.5,
                -74.5,
                5.5,
                -32.0,
            )?.firstOrNull()
        }.getOrNull()
    }
}
