package com.srrotas.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

object OfferMaps {
    fun searchIntent(label: String?, lat: Double?, lng: Double?): Intent? {
        val query = when {
            lat != null && lng != null ->
                String.format(Locale.US, "%.6f,%.6f", lat, lng)
            !label.isNullOrBlank() -> label.trim()
            else -> return null
        }
        val uri = Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}",
        )
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun pendingIntent(
        context: Context,
        requestCode: Int,
        label: String?,
        lat: Double?,
        lng: Double?,
    ): PendingIntent? {
        val intent = searchIntent(label, lat, lng) ?: return null
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
