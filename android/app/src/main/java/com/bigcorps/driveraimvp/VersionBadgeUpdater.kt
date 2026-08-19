package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object VersionBadgeUpdater {
    private val legacyBadge = Regex("^0\\.\\d+(?:\\.\\d+)?\\s+Beta$", RegexOption.IGNORE_CASE)

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activity.window?.decorView?.post { updateTree(activity.window?.decorView) }
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

    private fun updateTree(view: View?) {
        when (view) {
            is TextView -> {
                val current = view.text?.toString()?.trim().orEmpty()
                if (legacyBadge.matches(current)) {
                    val version = BuildConfig.VERSION_NAME.substringBefore('-')
                    view.text = "$version Beta"
                }
            }
            is ViewGroup -> for (index in 0 until view.childCount) updateTree(view.getChildAt(index))
        }
    }
}
