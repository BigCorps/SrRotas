package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class SettingsStandaloneActivity027035 : Activity() {
    private lateinit var hub: SettingsHub023
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        hub = SettingsHub023(this, SettingsHub023.Actions(
            journey = { startActivity(Intent(this, OnboardingActivity::class.java)) },
            strategy = { startActivity(Intent(this, Strategy021Activity::class.java)) },
            appearance = { startActivity(Intent(this, AppearanceSettingsActivity027035::class.java)) },
            notifications = { startActivity(Intent(this, NotificationSettingsActivity027035::class.java)) },
            sync = { syncNow() },
            privacy = { startActivity(Intent(this, UserSettingsActivity027035::class.java)) },
            demo = {},
        ))
        setContentView(hub); UiKit.applySafeArea(hub)
        hub.post { Rc35UiPolish027035.decorateSettingsHub(hub) }
    }
    override fun onResume() { super.onResume(); if (::hub.isInitialized) { hub.refresh(); hub.post { Rc35UiPolish027035.decorateSettingsHub(hub) } } }
    private fun syncNow() {
        BackendClient.syncPreferences(this)
        CostProfileSync.refreshOrFlush(this)
        MessagePresetClient023.refresh(this)
        SyncCoordinator.sync(this) { result ->
            runOnUiThread { hub.refresh(); hub.post { Rc35UiPolish027035.decorateSettingsHub(hub) }; JourneyBubbleController.refresh(this); Toast.makeText(this,result.userMessage(),Toast.LENGTH_SHORT).show() }
        }
    }
}
