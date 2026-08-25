package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * 0.21.2: evita que o OCR recapture telas do próprio Sr. Rotas.
 * A bolha é protegida separadamente com FLAG_SECURE.
 */
object OwnUiCaptureGuard0212 {
    private val resumed = AtomicInteger(0)

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity.packageName == application.packageName) resumed.incrementAndGet()
                }

                override fun onActivityPaused(activity: Activity) {
                    if (activity.packageName == application.packageName) {
                        resumed.updateAndGet { (it - 1).coerceAtLeast(0) }
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    fun shouldSkipOcr(): Boolean = resumed.get() > 0
}
