package com.srrotas.app

import android.content.Context
import android.content.SharedPreferences

/** Mantém a preferência textual ligada diretamente ao resumo das 3 últimas corridas. */
object OfferNotificationPreferenceWatcher0262 {
    private const val PREFS = "driver_ai_settings"
    private const val KEY = "text_notification_enabled"
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(context: Context) {
        if (listener != null) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val created = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == KEY) {
                OfferNotifier.syncTextNotificationState(
                    app,
                    changed.getBoolean(KEY, false),
                )
            }
        }
        listener = created
        prefs.registerOnSharedPreferenceChangeListener(created)
        OfferNotifier.syncTextNotificationState(app, prefs.getBoolean(KEY, false))
    }
}
