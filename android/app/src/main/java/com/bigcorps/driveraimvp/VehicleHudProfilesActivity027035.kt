package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class VehicleHudProfilesActivity027035 : Activity() {
    private lateinit var host:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); UiKit.applySystemBars(this)
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@VehicleHudProfilesActivity027035).background) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(SrAppHeader023(this,"Perfis por veículo","Salve configurações do HUD para alternar entre veículos sem refazer os limites."))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@VehicleHudProfilesActivity027035,14),UiKit.dp(this@VehicleHudProfilesActivity027035,10),UiKit.dp(this@VehicleHudProfilesActivity027035,14),UiKit.dp(this@VehicleHudProfilesActivity027035,24)) }
        content.addView(UiKit.primaryButton(this,"Salvar configuração atual como perfil") { askName() })
        host=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; content.addView(UiKit.margin(host,top=10))
        page.addView(content); scroll.addView(page); outer.addView(scroll,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(outer); UiKit.applySafeArea(outer); render()
    }
    private fun askName() {
        val input=EditText(this).apply { hint="Ex.: Dolphin ou HB20"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Nome do veículo").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar"){_,_->
            val name=input.text?.toString().orEmpty().trim(); if(name.isBlank()) return@setPositiveButton
            VehicleHudProfileStore027035.saveCurrent(this,name); render(); Toast.makeText(this,"Perfil $name salvo.",Toast.LENGTH_SHORT).show()
        }.show()
    }
    private fun render() {
        host.removeAllViews(); val profiles=VehicleHudProfileStore027035.list(this)
        if(profiles.isEmpty()) { host.addView(SrUi023.body(this,"Nenhum perfil personalizado salvo ainda.",11f)); return }
        profiles.forEachIndexed { index,p ->
            host.addView(UiKit.margin(SrUi023.card(this,12,15).apply {
                addView(SrUi023.title(this@VehicleHudProfilesActivity027035,p.name,14f))
                addView(SrUi023.body(this@VehicleHudProfilesActivity027035,"R$ ${String.format(java.util.Locale.US,"%.2f",p.minPerKm)}/km · R$ ${String.format(java.util.Locale.US,"%.0f",p.minPerHour)}/h · busca até ${p.maxPickupMinutes} min",10f))
                val row=LinearLayout(this@VehicleHudProfilesActivity027035).apply { orientation=LinearLayout.HORIZONTAL }
                row.addView(UiKit.secondaryButton(this@VehicleHudProfilesActivity027035,"Usar perfil") { VehicleHudProfileStore027035.apply(this@VehicleHudProfilesActivity027035,p); Toast.makeText(this@VehicleHudProfilesActivity027035,"Perfil ${p.name} aplicado.",Toast.LENGTH_SHORT).show() },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                row.addView(UiKit.secondaryButton(this@VehicleHudProfilesActivity027035,"Excluir") { VehicleHudProfileStore027035.delete(this@VehicleHudProfilesActivity027035,p.name); render() },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply { marginStart=UiKit.dp(this@VehicleHudProfilesActivity027035,7) })
                addView(UiKit.margin(row,top=8))
            },top=if(index==0)0 else 8))
        }
    }
}
