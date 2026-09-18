package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** Botão de voltar fixo para qualquer configuração que saia do shell principal. */
object ConfigurationBackOverlay0212 {
    private const val TAG = "sr_config_back_0212"
    private val targets = setOf(
        "Strategy021Activity", "StrategyActivity", "CostProfileActivity",
        "OnboardingActivity", "FieldValidationActivity",
        "FloatingWindowSettingsActivity027034", "AppearanceSettingsActivity027035",
        "NotificationSettingsActivity027035", "UserSettingsActivity027035",
        "MessageSettingsActivity027035", "VehicleHudProfilesActivity027035",
        "SettingsStandaloneActivity027035",
    )

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = add(activity)
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun add(activity: Activity) {
        if (activity::class.java.simpleName !in targets) return
        val decor = activity.window.decorView as? FrameLayout ?: return
        if (decor.findViewWithTag<View>(TAG) != null) return
        val p = UiKit.palette(activity)
        val button = TextView(activity).apply {
            tag = TAG
            text = "‹"
            textSize = 31f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.ink)
            background = UiKit.rounded(activity, p.surface, 999, p.line, 1)
            elevation = UiKit.dp(activity, 5).toFloat()
            contentDescription = "Voltar"
            setOnClickListener { leave(activity) }
        }
        decor.addView(button, FrameLayout.LayoutParams(UiKit.dp(activity, 42), UiKit.dp(activity, 42)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = UiKit.dp(activity, 8)
            topMargin = UiKit.dp(activity, 40)
        })
    }

    private fun leave(activity: Activity) {
        if (!hasUnsavedChanges(activity)) {
            activity.finish()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Alterações não salvas")
            .setMessage("Você alterou esta configuração. Deseja sair sem salvar?")
            .setNegativeButton("Continuar editando", null)
            .setPositiveButton("Sair sem salvar") { _, _ -> activity.finish() }
            .show()
    }

    private fun hasUnsavedChanges(activity: Activity): Boolean {
        if (activity !is Strategy021Activity) return false
        return runCatching {
            val method = Strategy021Activity::class.java.getDeclaredMethod("candidateSettings").apply { isAccessible = true }
            val candidate = method.invoke(activity) as? DriverSettings ?: return@runCatching false
            candidate != SettingsRepository(activity).load()
        }.getOrDefault(false)
    }
}
