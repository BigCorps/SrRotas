package com.srrotas.app

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import java.util.WeakHashMap

/** Campo compacto pré-jornada: odômetro + valor de abastecimento/recarga. */
object Rc35JourneyDraft027035 {
    private const val PREFS="sr_journey_flow_026"
    private data class State(val view:View,val km:EditText,val amount:EditText,val mode:Spinner)
    private val states=WeakHashMap<NowPanel023,State>()

    fun attach(now:NowPanel023,active:Boolean) {
        val state=states[now] ?: create(now).also { states[now]=it }
        state.view.visibility=if(active)View.GONE else View.VISIBLE
    }
    fun persist(now:NowPanel023) {
        val s=states[now]?:return
        val km=s.km.text?.toString()?.trim()?.replace(',','.')?.toDoubleOrNull()
        val amount=s.amount.text?.toString()?.trim()?.replace(',','.')?.toDoubleOrNull()
        val mode=when(s.mode.selectedItemPosition){1->"fuel";2->"electric";else->"none"}
        val p=now.context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putLong("saved_at_ms",System.currentTimeMillis())
            .putBoolean("armed",true)
            .putString("energy_mode",mode)
        if(km==null)p.remove("start_km") else p.putString("start_km",km.toString())
        if(amount==null)p.remove("amount") else p.putString("amount",amount.toString())
        p.remove("quantity").remove("fuel_type").apply()
    }
    private fun create(now:NowPanel023):State {
        val km=input(now.context,"Odômetro inicial (km)")
        val amount=input(now.context,"Abastecimento / recarga (R$)")
        val mode=Spinner(now.context).apply { adapter=ArrayAdapter(now.context,android.R.layout.simple_spinner_dropdown_item,listOf("Sem gasto","Combustível","Recarga")) }
        val row=LinearLayout(now.context).apply {
            contentDescription="sr35_journey_draft";orientation=LinearLayout.VERTICAL;setPadding(UiKit.dp(context,11),UiKit.dp(context,9),UiKit.dp(context,11),UiKit.dp(context,9));background=SrUi023.rounded(0xFFE7F7EF.toInt(),12,0xFF19A96B.toInt(),2,context)
            addView(SrUi023.body(context,"Antes de iniciar",10f).apply{setTypeface(typeface,android.graphics.Typeface.BOLD);setTextColor(0xFF0C7048.toInt())})
            val fields=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
            fields.addView(km,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));fields.addView(amount,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(context,6)});addView(UiKit.margin(fields,top=6));addView(UiKit.margin(mode,top=6))
        }
        val results=runCatching { NowPanel023::class.java.getDeclaredField("results").apply{isAccessible=true}.get(now) as LinearLayout }.getOrNull()
        val parent=results?.parent as? LinearLayout
        if(parent!=null){ val index=parent.indexOfChild(results).coerceAtLeast(0);parent.addView(row,index,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{topMargin=UiKit.dp(now.context,8)}) }
        return State(row,km,amount,mode)
    }
    private fun input(context:Context,hintText:String)=EditText(context).apply { hint=hintText;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setSingleLine(true);textSize=11f;setPadding(UiKit.dp(context,8),UiKit.dp(context,7),UiKit.dp(context,8),UiKit.dp(context,7));background=SrUi023.rounded(ColorWhite,9,0xFFB7DCCB.toInt(),1,context) }
    private val ColorWhite:Int=0xFFFFFFFF.toInt()
}
