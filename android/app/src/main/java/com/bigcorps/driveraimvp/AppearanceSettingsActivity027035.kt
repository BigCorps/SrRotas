package com.srrotas.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class AppearanceSettingsActivity027035 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@AppearanceSettingsActivity027035).background) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(SrAppHeader023(this, "Aparência", "Tema do aplicativo e comportamento visual."))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(UiKit.dp(this@AppearanceSettingsActivity027035,14), UiKit.dp(this@AppearanceSettingsActivity027035,10), UiKit.dp(this@AppearanceSettingsActivity027035,14), UiKit.dp(this@AppearanceSettingsActivity027035,24)) }
        page.addView(content)
        scroll.addView(page)
        outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        val active = Strategy021Store.load(this).appTheme
        listOf("auto" to "Automático", "light" to "Claro", "dark" to "Escuro").forEach { (key,label) ->
            content.addView(UiKit.margin(SrUi023.card(this,12,16).apply {
                addView(SrUi023.title(this@AppearanceSettingsActivity027035, label, 14f))
                addView(SrUi023.body(this@AppearanceSettingsActivity027035, when(key){"auto"->"Segue o tema do aparelho";"light"->"Interface clara";else->"Interface escura"},10.5f))
                if (active == key) addView(SrUi023.pill(this@AppearanceSettingsActivity027035,"ATIVO","good"))
                setOnClickListener {
                    Strategy021Store.saveAppTheme(this@AppearanceSettingsActivity027035,key)
                    if (Strategy021Store.load(this@AppearanceSettingsActivity027035).hudThemeMode == "follow_app") {
                        val repo=SettingsRepository(this@AppearanceSettingsActivity027035)
                        repo.save(repo.load().copy(hudTheme=key))
                    }
                    Preference021Sync.sync(this@AppearanceSettingsActivity027035)
                    Toast.makeText(this@AppearanceSettingsActivity027035,"Aparência atualizada.",Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }, top=8))
        }
        setContentView(outer); UiKit.applySafeArea(outer)
    }
}
