package com.srrotas.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object JourneyLocationRuntime {
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun ensure(context: Context) {
        val app = context.applicationContext
        val id = SettingsRepository(app).currentJourneyId()
        if (id.isBlank()) return
        if (hasPermission(app)) {
            JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_START)
            return
        }
        val intent = Intent(context, LocationPermissionActivity::class.java).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { LocalLog.append(app, "Não foi possível solicitar localização: ${it.message}") }
    }
}
