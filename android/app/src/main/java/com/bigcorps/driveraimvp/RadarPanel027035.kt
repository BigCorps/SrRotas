package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RadarPanel027035(context:Context):ScrollView(context) {
    private val body=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private val events=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private val places=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private val status=SrUi023.body(context,"",10.5f)
    init {
        isFillViewport=true; setBackgroundColor(UiKit.palette(context).background)
        val root=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL }
        root.addView(SrAppHeader023(context,"Radar","Eventos catalogados e seus lugares de interesse."))
        body.setPadding(UiKit.dp(context,14),UiKit.dp(context,10),UiKit.dp(context,14),UiKit.dp(context,26))
        root.addView(body,LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context),LinearLayout.LayoutParams.WRAP_CONTENT))
        body.addView(SrUi023.card(context,12,16).apply {
            addView(SrUi023.title(context,"Lugares de interesse",14f))
            addView(SrUi023.body(context,"Crie marcações particulares ou compartilháveis. Nada é publicado automaticamente.",10f))
            addView(UiKit.margin(UiKit.primaryButton(context,"+ Marcar lugar") { addPlace() },top=8))
            addView(UiKit.margin(places,top=7))
        })
        body.addView(UiKit.margin(SrUi023.card(context,12,16).apply {
            addView(SrUi023.title(context,"Eventos próximos",14f)); addView(status)
            addView(UiKit.margin(UiKit.secondaryButton(context,"Atualizar Radar") { refreshEvents(true) },top=7)); addView(UiKit.margin(events,top=7))
        },top=10))
        addView(root,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        refreshPlaces(); refreshEvents(false)
    }
    fun refresh(){ refreshPlaces(); refreshEvents(false) }
    private fun addPlace() {
        val holder=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(context,18),4,UiKit.dp(context,18),4) }
        val name=UiKit.input(context,"Nome do lugar"); val address=UiKit.input(context,"Endereço ou referência"); val note=UiKit.input(context,"Observação · opcional")
        val sharing=Spinner(context).apply { adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,listOf("Particular","Compartilhável")) }
        holder.addView(name); holder.addView(UiKit.margin(address,top=7)); holder.addView(UiKit.margin(note,top=7)); holder.addView(UiKit.margin(sharing,top=7))
        AlertDialog.Builder(context).setTitle("Marcar lugar no Radar").setView(holder).setNegativeButton("Cancelar",null).setPositiveButton("Salvar"){_,_->
            if(name.text.toString().trim().isNotEmpty() && address.text.toString().trim().isNotEmpty()) {
                RadarPlaceStore027035.add(context,name.text.toString(),address.text.toString(),note.text.toString(),if(sharing.selectedItemPosition==1)"shareable" else "private"); refreshPlaces()
            }
        }.show()
    }
    private fun refreshPlaces() {
        places.removeAllViews(); val list=RadarPlaceStore027035.list(context)
        if(list.isEmpty()){ places.addView(SrUi023.body(context,"Nenhum lugar marcado ainda.",10f)); return }
        list.forEachIndexed { index,p -> places.addView(UiKit.margin(SrUi023.softCard(context,"neutral",10).apply {
            addView(SrUi023.title(context,p.name,12.5f)); addView(SrUi023.body(context,p.address,10f)); if(p.note.isNotBlank()) addView(SrUi023.body(context,p.note,9.5f))
            addView(SrUi023.pill(context,if(p.sharing=="shareable")"COMPARTILHÁVEL" else "PARTICULAR",if(p.sharing=="shareable")"purple" else "good"))
            val row=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(smallButton("Mapa"){ openMap(p.address) },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            row.addView(smallButton("Compartilhar"){ share(p) },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(context,5)})
            row.addView(smallButton("Excluir"){ RadarPlaceStore027035.delete(context,p.id); refreshPlaces() },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(context,5)})
            addView(UiKit.margin(row,top=7))
        },top=if(index==0)0 else 6)) }
    }
    private fun refreshEvents(force:Boolean) {
        status.text="Consultando eventos próximos…"; events.removeAllViews()
        EventRadarClient026.fetchNearby(context,force=force) { result -> result.onSuccess { found ->
            status.text=if(found.opportunities.isEmpty()) "Nenhum evento relevante nas próximas horas." else "${found.opportunities.size} oportunidade(s) catalogada(s)."
            found.opportunities.take(12).forEachIndexed { index,item -> events.addView(UiKit.margin(SrUi023.softCard(context,"neutral",10).apply {
                addView(SrUi023.title(context,item.name,12.5f)); val place=listOfNotNull(item.venueName,item.address).joinToString(" · "); if(place.isNotBlank()) addView(SrUi023.body(context,place,9.5f))
                addView(SrUi023.body(context,"${EventRadarRules026.typeLabel(item.type)} · fim ${formatTime(item.expectedEndAt)} · ${String.format(Locale("pt","BR"),"%.1f",item.distanceKm)} km",9.5f))
                item.address?.let { a -> addView(UiKit.margin(smallButton("Abrir no mapa") { openMap(a) },top=6)) }
            },top=if(index==0)0 else 6)) }
        }.onFailure { status.text="Radar indisponível agora: ${it.message ?: "tente novamente"}" } }
    }
    private fun smallButton(label:String,action:()->Unit)=TextView(context).apply { text=label;textSize=10f;gravity=Gravity.CENTER;minHeight=UiKit.dp(context,36);setTextColor(SrUi023.palette(context).blue);background=SrUi023.rounded(android.graphics.Color.TRANSPARENT,10,SrUi023.palette(context).blue,1,context);setOnClickListener{action()} }
    private fun openMap(value:String){ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=${Uri.encode(value)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private fun share(p:RadarPlaceStore027035.Place){ val text="${p.name}\n${p.address}${if(p.note.isBlank())"" else "\n${p.note}"}"; runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Compartilhar lugar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private fun formatTime(value:String)=runCatching { DateTimeFormatter.ofPattern("HH:mm",Locale("pt","BR")).withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value)) }.getOrDefault("—")
}
