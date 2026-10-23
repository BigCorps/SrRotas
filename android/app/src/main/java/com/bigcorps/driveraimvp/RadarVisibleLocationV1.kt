package com.srrotas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/**
 * UI-only location snapshot for the visible Radar. Never starts a tracker,
 * persists coordinates, or requires fine/background location permission.
 */
internal object RadarVisibleLocationV1 {
    private const val MAX_AGE_MS = 20L * 60_000L
    data class Position(val lat: Double, val lng: Double, val ageMs: Long)

    fun latest(context: Context, nowMs: Long = System.currentTimeMillis()): Position? {
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!coarse && !fine) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.filter { location ->
                location.latitude.isFinite() && location.longitude.isFinite() &&
                    location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
                    location.time > 0L && nowMs >= location.time &&
                    nowMs - location.time <= MAX_AGE_MS
            }.maxByOrNull { it.time }?.let {
                Position(it.latitude, it.longitude, nowMs - it.time)
            }
        }.getOrNull()
    }

    const val MARKER_ID = "__radar_user_position__"

    fun marker(position: Position) = RadarMiniMapViewV1.Marker(
        id = MARKER_ID,
        lat = position.lat,
        lng = position.lng,
        title = "Sua posição aproximada",
        type = "user_location",
        potential = "insufficient",
    )
}
