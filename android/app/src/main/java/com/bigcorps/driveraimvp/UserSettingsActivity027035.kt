package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class UserSettingsActivity027035 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@UserSettingsActivity027035).background) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(SrAppHeader023(this,"Usuário","Privacidade, suporte e indicação."))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@UserSettingsActivity027035,14),UiKit.dp(this@UserSettingsActivity027035,10),UiKit.dp(this@UserSettingsActivity027035,14),UiKit.dp(this@UserSettingsActivity027035,24)) }
        content.addView(actionCard("Privacidade","Como seus dados e a Base Coletiva são tratados",R.drawable.sr23_ic_shield_help){ open("https://srrotas.com/privacidade") })
        content.addView(UiKit.margin(actionCard("Suporte","Ajuda e contato com o Sr. Rotas",R.drawable.sr23_ic_info){ open("https://srrotas.com") },top=9))
        content.addView(UiKit.margin(SrUi023.card(this,14,18).apply {
            addView(SrUi023.title(this@UserSettingsActivity027035,"Indicação",15f))
            addView(SrUi023.body(this@UserSettingsActivity027035,"Em breve você poderá gerar ou usar um código de indicação para descontos, brindes e campanhas de parceiros/influenciadores.",10.5f))
            addView(UiKit.margin(SrUi023.softCard(this@UserSettingsActivity027035,"neutral",10).apply {
                addView(SrUi023.body(this@UserSettingsActivity027035,"CÓDIGO DE INDICAÇÃO · EM BREVE",11f))
            },top=9))
        },top=9))
        page.addView(content); scroll.addView(page); outer.addView(scroll,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(outer); UiKit.applySafeArea(outer)
    }
    private fun actionCard(title:String, subtitle:String, icon:Int, action:()->Unit)=SrUi023.card(this,12,16).apply {
        addView(SrUi023.title(this@UserSettingsActivity027035,title,14f)); addView(SrUi023.body(this@UserSettingsActivity027035,subtitle,10.5f)); isClickable=true; isFocusable=true; setOnClickListener{ action() }
    }
    private fun open(url:String)=runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.getOrNull()
}
