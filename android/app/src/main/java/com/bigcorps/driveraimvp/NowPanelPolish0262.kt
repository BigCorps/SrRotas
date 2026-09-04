package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView

/**
 * Acabamento estreito para os minicards regionais do Agora sem duplicar a
 * implementação do NowPanel023: formata a métrica de busca como solicitado.
 */
object NowPanelPolish0262 {
    private val attached = java.util.WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity || attached.containsKey(activity)) return
                val decor = activity.window.decorView
                val listener = ViewTreeObserver.OnGlobalLayoutListener { polish(decor) }
                attached[activity] = listener
                decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
                polish(decor)
            }

            override fun onActivityDestroyed(activity: Activity) {
                attached.remove(activity)?.let { listener ->
                    val observer = activity.window.decorView.viewTreeObserver
                    if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                }
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        })
    }

    internal fun polish(root: View) {
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is SrSoftShadowCard023) polishMetricCard(child)
            if (child is ViewGroup) polish(child)
        }
    }

    private fun polishMetricCard(card: ViewGroup) {
        val textChildren = (0 until card.childCount)
            .map { card.getChildAt(it) }
            .filterIsInstance<TextView>()
        if (textChildren.size < 2) return
        val label = textChildren[0]
        val value = textChildren[1]
        if (label.text?.toString()?.trim() != "Busca") return

        label.text = "Busca/min"
        val raw = value.text?.toString()?.trim().orEmpty()
        val stripped = raw.replace(Regex("\\s+min$", RegexOption.IGNORE_CASE), "")
        if (stripped != raw) value.text = stripped
    }
}
