package com.srrotas.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlin.math.abs

/**
 * vc107: the Radar no longer draws maps. "Ver no Google Maps" hands a location to
 * the external Google Maps app through the existing universal link
 * (OfferMaps.searchIntent): Android opens the Maps app when installed, otherwise
 * the browser. Only a location is shared — never the Sr. Rotas intelligence layer.
 * Nothing here opens automatically.
 */
internal object RadarExternalMapsV1 {
    fun validCoordinate(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 && !(abs(lat) < 1e-6 && abs(lng) < 1e-6)

    /** Valid coordinates win; a non-blank address is the fallback; otherwise there is no button. */
    fun intent(lat: Double?, lng: Double?, label: String?): Intent? =
        if (validCoordinate(lat, lng)) OfferMaps.searchIntent(null, lat, lng)
        else OfferMaps.searchIntent(label?.trim()?.takeIf { it.length >= 3 }, null, null)

    fun available(lat: Double?, lng: Double?, label: String?): Boolean = intent(lat, lng, label) != null

    fun open(context: Context, lat: Double?, lng: Double?, label: String?): Boolean {
        val intent = intent(lat, lng, label) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum aplicativo de mapas ou navegador disponível para abrir esta localização.", Toast.LENGTH_LONG).show()
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
