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

    fun enrichAsync(
        context: Context,
        offer: RideOffer,
        onResult: (OfferContext) -> Unit,
    ) {
        val initial = offer.context ?: return
        if (!initial.hasTextContext()) return

        val app = context.applicationContext
        executor.execute {
            val store = LocalStore.get(app)
            var pickup = resolve(
                app,
                store,
                initial.pickupLabel,
                initial.pickupLat,
                initial.pickupLng,
            )
            var destination = resolve(
                app,
                store,
                initial.destinationLabel,
                initial.destinationLat,
                initial.destinationLng,
            )

            // 0.24.2: um primeiro resultado do Android Geocoder pode ser
            // formalmente válido no Brasil, porém completamente incompatível
            // com o outro ponto da mesma oferta. Nesses casos, o lado textual
            // fraco deixa de ser confirmado/celularizado.
            if (pickup != null && destination != null) {
                val distance = OfferContextQuality0242.distanceKm(
                    pickup.lat,
                    pickup.lng,
                    destination.lat,
                    destination.lng,
                )
                val keepPickup = OfferContextQuality0242.keepPairSide(
                    initial.pickupLabel,
                    initial.destinationLabel,
                    distance,
                )
                val keepDestination = OfferContextQuality0242.keepPairSide(
                    initial.destinationLabel,
                    initial.pickupLabel,
                    distance,
                )
                if (!keepPickup) pickup = null
                if (!keepDestination) destination = null
            }

            pickup?.rememberRegion()
            destination?.rememberRegion()

            val resolvedCount =
                listOf(pickup, destination)
                    .count { it?.lat != null && it.lng != null }
            val expectedCount =
                listOf(initial.pickupLabel, initial.destinationLabel)
                    .count { OfferContextQuality0242.canGeocode(it) }
            val status = when {
                expectedCount == 0 -> "unresolved"
                resolvedCount == expectedCount -> "resolved"
                resolvedCount > 0 -> "partial"
                else -> "unresolved"
            }
            val sources = listOfNotNull(
                pickup?.source,
                destination?.source,
            ).distinct().joinToString("+").ifBlank {
                if (initial.hasTextContext()) "quality_filtered" else "none"
            }

            val enriched = initial.copy(
                pickupLat = pickup?.lat,
                pickupLng = pickup?.lng,
                destinationLat = destination?.lat,
                destinationLng = destination?.lng,
                pickupCell = pickup?.cell,
                destinationCell = destination?.cell,
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
        if (!OfferContextQuality0242.canGeocode(label)) return null

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
                regionByCell[it.cell ?: ""]
                    ?: reverseRegion(context, it.lat, it.lng),
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
    private fun geocodeBrazil(
        context: Context,
        label: String,
    ): Address? = runCatching {
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
    private fun reverseRegion(
        context: Context,
        lat: Double,
        lng: Double,
    ): String? {
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
            .mapNotNull {
                it?.trim()?.takeIf { value -> value.length in 3..80 }
            }
            .distinct()

        return candidates.firstOrNull()
    }
}
