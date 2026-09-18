package com.srrotas.app

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Configurações/Usuário dentro do shell principal, preservando a navegação inferior. */
object Rc36MainOverlay027036 {
    private const val TAG = "sr36_main_overlay"

    fun openSettings(activity: MainActivity) {
        lateinit var hub: SettingsHub023
        hub = SettingsHub023(activity, SettingsHub023.Actions(
            journey = { activity.startActivity(Intent(activity, OnboardingActivity::class.java)) },
            strategy = { activity.startActivity(Intent(activity, Strategy021Activity::class.java)) },
            appearance = { activity.startActivity(Intent(activity, AppearanceSettingsActivity027035::class.java)) },
            notifications = { activity.startActivity(Intent(activity, NotificationSettingsActivity027035::class.java)) },
            sync = {
                BackendClient.syncPreferences(activity)
                CostProfileSync.refreshOrFlush(activity)
                MessagePresetClient023.refresh(activity)
                SyncCoordinator.sync(activity) { result ->
                    activity.runOnUiThread {
                        hub.refresh()
                        hub.post {
                            Rc35UiPolish027035.decorateSettingsHub(hub)
                            Rc36ClosingPolish027036.decorateReaderLabSettings(hub)
                        }
                        JourneyBubbleController.refresh(activity)
                        Toast.makeText(activity, result.userMessage(), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            privacy = { openUser(activity) },
            demo = {},
        ))
        show(activity, hub)
        hub.post {
            Rc35UiPolish027035.decorateSettingsHub(hub)
            Rc36ClosingPolish027036.decorateReaderLabSettings(hub)
        }
    }

    fun openUser(activity: MainActivity) = show(activity, UserPanel024(activity))

    fun close(activity: MainActivity) {
        val content = privateField<FrameLayout>(activity, "content") ?: return
        content.findViewWithTag<View>(TAG)?.let(content::removeView)
    }

    fun isOpen(activity: MainActivity): Boolean =
        privateField<FrameLayout>(activity, "content")?.findViewWithTag<View>(TAG) != null

    private fun show(activity: MainActivity, body: View) {
        val content = privateField<FrameLayout>(activity, "content") ?: return
        close(activity)
        val p = UiKit.palette(activity)
        val host = LinearLayout(activity).apply {
            tag = TAG
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close(activity); true
                } else false
            }
        }
        host.addView(
            TextView(activity).apply {
                text = "‹  Voltar"
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(SrUi023.palette(activity).blue)
                setPadding(UiKit.dp(activity, 14), UiKit.dp(activity, 8), UiKit.dp(activity, 14), UiKit.dp(activity, 6))
                setOnClickListener { close(activity) }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(activity, 42)),
        )
        host.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        host.requestFocus()

        // A barra principal está fora de `content`, então continua visível. Ao
        // navegar por ela, fechamos apenas a sobreposição e deixamos o clique seguir.
        privateField<FrameLayout>(activity, "navHost")?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && isOpen(activity)) close(activity)
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T? = runCatching {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as? T
    }.getOrNull()
}
