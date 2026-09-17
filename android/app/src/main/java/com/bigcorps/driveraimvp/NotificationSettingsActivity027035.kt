package com.srrotas.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class NotificationSettingsActivity027035 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@NotificationSettingsActivity027035).background) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(SrAppHeader023(this,"Notificações","Alertas, resumos e avisos do Sr. Rotas."))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@NotificationSettingsActivity027035,14),UiKit.dp(this@NotificationSettingsActivity027035,10),UiKit.dp(this@NotificationSettingsActivity027035,14),UiKit.dp(this@NotificationSettingsActivity027035,24)) }
        val view=PushSettingsView(this); view.refresh(); content.addView(view)
        page.addView(content); scroll.addView(page); outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(outer); UiKit.applySafeArea(outer)
    }
}
