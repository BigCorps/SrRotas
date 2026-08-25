package com.srrotas.app

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object OfferContextGeocoder {
    private val executor = Executors.newSingleThreadExecutor()
    private val regionByCell = ConcurrentHashMap<String, String>()

    fun enrichAsync(context: Context, offer: RideOffer, onResult: (OfferContext) -> Unit) {
        val initial = offer.context ?: return
        if (!initial.hasTextContext()) return

        val app = context.applicationContext
        executor.execute {
            val store = LocalStore.get(app)
            val pickup = resolve(app, store, initial.pickupLabel, initial.pickupLat, initial.pickupLng)
            val destination = resolve(app, store, initial.destinationLabel, initial.destinationLat, initial.destinationLng)

            pickup?.rememberRegion()
            destination?.rememberRegion()

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

    /**
     * Rótulo regional obtido pelo Geocoder para uso exclusivamente estatístico.
     * Não substitui o texto original de destino exibido ao motorista/Maps.
     */
    fun regionLabelForCell(cell: String?): String? =
        cell?.takeIf(String::isNotBlank)?.let(regionByCell::get)

    private data class Resolved(
        val lat: Double,
        val lng: Double,
        val cell: String?,
        val source: String,
        val regionLabel: String? = null,
    ) {
        fun rememberRegion() {
            val c = cell?.takeIf(String::isNotBlank) ?: return
            val r = regionLabel?.trim()?.takeIf { it.length in 3..80 } ?: return
            regionByCell[c] = r
        }
    }

    private fun resolve(
        context: Context,
        store: LocalStore,
        label: String?,
        existingLat: Double?,
        existingLng: Double?,
    ): Resolved? {
        if (existingLat != null && existingLng != null) {
            return Resolved(
                existingLat,
                existingLng,
                OfferContextEngine.geoCell(existingLat, existingLng),
                "existing",
                reverseRegion(context, existingLat, existingLng),
            )
        }

        val query = label?.trim()?.takeIf { it.length >= 4 } ?: return null
        val cacheKey = OfferContextEngine.normalizePlaceForCache(query)
        store.cachedGeocode(cacheKey)?.let {
            return Resolved(
                it.lat,
                it.lng,
                it.cell,
                "cache",
                regionByCell[it.cell ?: ""] ?: reverseRegion(context, it.lat, it.lng),
            )
        }

        if (!Geocoder.isPresent()) return null
        val address = geocodeBrazil(context, query) ?: return null
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val lat = address.latitude
        val lng = address.longitude
        if (lat !in -34.5..5.5 || lng !in -74.5..-32.0) return null

        val cell = OfferContextEngine.geoCell(lat, lng)
        store.cacheGeocode(cacheKey, query, lat, lng, cell)
        return Resolved(
            lat,
            lng,
            cell,
            "android_geocoder",
            regionFromAddress(address),
        )
    }

    @Suppress("DEPRECATION")
    private fun geocodeBrazil(context: Context, label: String): Address? =
        runCatching {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            geocoder.getFromLocationName(
                label,
                1,
                -34.5,
                -74.5,
                5.5,
                -32.0,
            )?.firstOrNull()
        }.getOrNull()

    @Suppress("DEPRECATION")
    private fun reverseRegion(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(context, Locale("pt", "BR"))
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.let(::regionFromAddress)
        }.getOrNull()
    }

    private fun regionFromAddress(address: Address): String? {
        val candidates = listOf(
            address.subLocality,
            address.subAdminArea,
            address.locality,
        )
            .mapNotNull { it?.trim()?.takeIf { value -> value.length in 3..80 } }
            .distinct()

        // Prefere bairro/subprefeitura; município é último fallback.
        return candidates.firstOrNull()
    }
}
