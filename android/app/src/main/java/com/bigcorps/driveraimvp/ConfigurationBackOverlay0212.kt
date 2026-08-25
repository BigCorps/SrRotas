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

/** Botão de voltar fixo sobre as telas de configuração longas. */
object ConfigurationBackOverlay0212 {
    private const val TAG = "sr_config_back_0212"
    private val targets = setOf(
        "Strategy021Activity",
        "StrategyActivity",
        "CostProfileActivity",
        "OnboardingActivity",
        "FieldValidationActivity",
    )
    private val confirmBeforeLeaving = setOf(
        "Strategy021Activity",
        "StrategyActivity",
        "CostProfileActivity",
    )

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = add(activity)
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
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
        decor.addView(
            button,
            FrameLayout.LayoutParams(UiKit.dp(activity, 42), UiKit.dp(activity, 42)).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = UiKit.dp(activity, 8)
                topMargin = UiKit.dp(activity, 40)
            },
        )
    }

    private fun leave(activity: Activity) {
        if (activity::class.java.simpleName !in confirmBeforeLeaving) {
            activity.finish()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Voltar")
            .setMessage("Se houver alterações ainda não salvas, elas serão descartadas. Deseja sair?")
            .setNegativeButton("Continuar aqui", null)
            .setPositiveButton("Voltar") { _, _ -> activity.finish() }
            .show()
    }
}
