package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Compatibilidade de ciclo de vida.
 * 0.21.1 remove badges de versão espalhados pela UI; a versão aparece somente
 * em Configurações. Mantemos aqui apenas o flush que já era validado.
 */
object VersionBadgeUpdater {
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                JourneySyncClient.flush(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
