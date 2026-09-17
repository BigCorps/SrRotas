package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * RC3.5 — camada de reorganização estrutural/UX.
 * Não toca OCR, parser, deduplicação ou fórmulas financeiras.
 */
object Rc35UiPolish027035 {
    private val main=Handler(Looper.getMainLooper())
    private var currentMain=WeakReference<MainActivity>(null)
    private var currentStrategy=WeakReference<Strategy021Activity>(null)
    private var assistantOverlayIdentity=0
    private var assistantShownAt=0L

    fun install(application:Application) {
        application.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks{
            override fun onActivityResumed(activity:Activity){
                when(activity){
                    is MainActivity -> { currentMain=WeakReference(activity); activity.window.decorView.post { decorateMain(activity) } }
                    is Strategy021Activity -> { currentStrategy=WeakReference(activity); activity.window.decorView.post { decorateStrategy(activity) } }
                }
            }
            override fun onActivityPaused(activity:Activity)=Unit
            override fun onActivityDestroyed(activity:Activity){ if(currentMain.get()===activity)currentMain.clear();if(currentStrategy.get()===activity)currentStrategy.clear() }
            override fun onActivityCreated(activity:Activity,state:Bundle?)=Unit
            override fun onActivityStarted(activity:Activity)=Unit
            override fun onActivityStopped(activity:Activity)=Unit
            override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle)=Unit
        })
        main.post(watcher)
    }

    private val watcher=object:Runnable{
        override fun run(){
            currentMain.get()?.let { runCatching { decorateMain(it) } }
            currentStrategy.get()?.let { runCatching { decorateStrategy(it) } }
            currentMain.get()?.applicationContext?.let { runCatching { decorateBubble(it); enforceAssistantTimeout(it) } }
            main.postDelayed(this,700L)
        }
    }

    private fun decorateMain(activity:MainActivity){
        replaceLegacyTabs(activity); ensureSettingsGear(activity)
        getPrivate<NowPanel023>(activity,"nowPanel")?.let(::decorateNow)
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaceLegacyTabs(activity:MainActivity){
        val tabs=getPrivate<MutableMap<SrBottomNav023.Route,View>>(activity,"tabs")?:return
        val content=getPrivate<FrameLayout>(activity,"content")?:return
        val selected=getPrivate<SrBottomNav023.Route>(activity,"selected")?:SrBottomNav023.Route.NOW
        fun replace(route:SrBottomNav023.Route,next:View){
            val old=tabs[route]
            if(old?.javaClass==next.javaClass)return
            if(old?.parent===content)content.removeView(old)
            next.visibility=if(selected==route)View.VISIBLE else View.GONE
            content.addView(next,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
            tabs[route]=next
        }
        if(tabs[SrBottomNav023.Route.SETTINGS] !is RideHistoryPanel027035) replace(SrBottomNav023.Route.SETTINGS,RideHistoryPanel027035(activity))
        if(tabs[SrBottomNav023.Route.USER] !is RadarPanel027035) replace(SrBottomNav023.Route.USER,RadarPanel027035(activity))
        (tabs[SrBottomNav023.Route.SETTINGS] as? RideHistoryPanel027035)?.takeIf{it.visibility==View.VISIBLE}?.refresh()
    }

    private fun ensureSettingsGear(activity:MainActivity){
        // Reserva a extremidade direita do cabeçalho para a engrenagem, evitando
        // sobreposição com títulos longos em telas estreitas.
        walk(activity.window.decorView) { view ->
            if (view is SrAppHeader023) {
                val right = view.paddingRight.coerceAtLeast(UiKit.dp(activity, 58))
                if (right != view.paddingRight) {
                    view.setPadding(view.paddingLeft, view.paddingTop, right, view.paddingBottom)
                }
            }
        }
        val content=activity.findViewById<FrameLayout>(android.R.id.content)?:return
        if(content.findViewWithTag<View>("sr_rc35_settings_gear")!=null)return
        val gear=ImageView(activity).apply{
            tag="sr_rc35_settings_gear";setImageResource(R.drawable.sr23_ic_settings);imageTintList=ColorStateList.valueOf(SrUi023.palette(activity).blue);contentDescription="Configurações";scaleType=ImageView.ScaleType.CENTER_INSIDE
            background=SrUi023.rounded(SrUi023.palette(activity).surface,999,SrUi023.palette(activity).outline,1,activity);elevation=UiKit.dp(activity,6).toFloat();setPadding(UiKit.dp(activity,10),UiKit.dp(activity,10),UiKit.dp(activity,10),UiKit.dp(activity,10));setOnClickListener{activity.startActivity(Intent(activity,SettingsStandaloneActivity027035::class.java))}
        }
        content.addView(gear,FrameLayout.LayoutParams(UiKit.dp(activity,44),UiKit.dp(activity,44),Gravity.TOP or Gravity.END).apply{topMargin=UiKit.dp(activity,34);rightMargin=UiKit.dp(activity,12)})
    }

    fun decorateSettingsHub(settings:SettingsHub023){
        replaceMascot(settings)
        hideTile(settings,"Assistente ativo")
        hideTile(settings,"Modo Demonstração")
        hideTile(settings,"Jornada e permissões")
        renameTile(settings,"Configuração do HUD","Configuração do HUD","Métricas, limites e perfis por veículo")
        renameTile(settings,"Privacidade e suporte","Usuário","Privacidade, suporte e indicação")?.let { card ->
            findFirst(card){it is ImageView}?.let{(it as ImageView).setImageResource(R.drawable.sr23_ic_user)}
        }
        compactTile(settings,"Screenshots das ofertas","Screenshots de corridas")
        val grid=getPrivate<LinearLayout>(settings,"grid")?:return
        ensureSettingsRow(grid,"sr35_window","Janela flutuante","Botão, opacidades, tema, Assistente Ativo e mensagens",R.drawable.sr23_ic_sliders,SrUi023.palette(settings.context).cyan){settings.context.startActivity(Intent(settings.context,FloatingWindowSettingsActivity027034::class.java))}
        ensureSettingsRow(grid,"sr35_digitize","Digitalização","Jornada e histórico da Uber · fluxo manual atual",R.drawable.sr23_ic_camera,SrUi023.palette(settings.context).blue){settings.context.startActivity(Intent(settings.context,UberDigitizationActivity026::class.java))}
        ensureSettingsRow(grid,"sr35_platforms","Plataformas","Uber suportado · 99 em implementação",R.drawable.sr23_ic_route,SrUi023.palette(settings.context).teal){
            AlertDialog.Builder(settings.context).setTitle("Plataformas").setMessage("Uber é a plataforma homologada para o lançamento inicial. O suporte ao 99 continua em implementação e não bloqueia o lançamento.").setPositiveButton("Entendi",null).show()
        }
        ensureSettingsRow(grid,"sr35_setup","Acessos e configuração inicial","Permissões, HUD e configuração inicial do aparelho",R.drawable.sr23_ic_route,SrUi023.palette(settings.context).teal){settings.context.startActivity(Intent(settings.context,OnboardingActivity::class.java))}
    }

    private fun replaceMascot(settings:SettingsHub023){
        val title=textViews(settings).firstOrNull{it.text?.toString() in setOf("Sr. Rotas está pronto","Sr. Rotas precisa de um ajuste","Sr. Rotas precisa de atenção")}?:return
        val card=ancestor(title){it is SrSoftShadowCard023} as? ViewGroup?:return
        val image=findFirst(card){it is ImageView} as? ImageView?:return
        image.setImageResource(R.drawable.sr0265_settings_ready);image.scaleType=ImageView.ScaleType.CENTER_INSIDE
    }
    private fun hideTile(settings:SettingsHub023,title:String){ cardForTitle(settings,title)?.visibility=View.GONE }
    private fun compactTile(settings:SettingsHub023,old:String,next:String){
        val title=textViews(settings).firstOrNull{it.text?.toString()?.trim()==old}?:return;title.text=next
        val card=ancestor(title){it is SrSoftShadowCard023} as? ViewGroup?:return;card.minimumHeight=UiKit.dp(settings.context,92);card.layoutParams?.let{it.height=ViewGroup.LayoutParams.WRAP_CONTENT;card.layoutParams=it}
    }
    private fun renameTile(settings:SettingsHub023,old:String,next:String,subtitle:String):ViewGroup?{
        val title=textViews(settings).firstOrNull{it.text?.toString()?.trim()==old}?:return null;title.text=next
        val card=ancestor(title){it is SrSoftShadowCard023} as? ViewGroup?:return null
        textViews(card).firstOrNull{it!==title && it.text?.toString()?.isNotBlank()==true}?.text=subtitle
        return card
    }
    private fun cardForTitle(root:View,title:String):ViewGroup?=textViews(root).firstOrNull{it.text?.toString()?.trim()==title}?.let{ancestor(it){v->v is SrSoftShadowCard023} as? ViewGroup}
    private fun ensureSettingsRow(grid:LinearLayout,tag:String,title:String,subtitle:String,icon:Int,tone:Int,action:()->Unit){
        if(findFirst(grid){it.contentDescription==tag}!=null)return
        val c=SrUi023.card(grid.context,11,15).apply{contentDescription=tag;orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isClickable=true;isFocusable=true;addView(SrUi023.iconBox(context,icon,tone,40));addView(LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(UiKit.dp(context,10),0,0,0);addView(SrUi023.title(context,title,13.5f));addView(SrUi023.body(context,subtitle,9.5f))},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));setOnClickListener{action()}}
        grid.addView(c,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{topMargin=UiKit.dp(grid.context,8)})
    }

    private fun decorateStrategy(activity:Strategy021Activity){
        val content=getPrivate<LinearLayout>(activity,"content")?:return
        for(i in 0 until content.childCount){val child=content.getChildAt(i);val texts=textViews(child).map{it.text?.toString()?.trim().orEmpty()};if(texts.any{it=="Janela flutuante"||it=="Mensagens rápidas"||it=="Mensagens"})child.visibility=View.GONE}
        getPrivate<LinearLayout>(activity,"previewHost")?.let{preview->var current:View=preview;while(current.parent is View && current.parent!==content)current=current.parent as View;if(current.parent===content)current.visibility=View.GONE}
        if(findFirst(content){it.contentDescription=="sr35_hud_actions"}==null){
            content.addView(UiKit.margin(SrUi023.card(activity,12,15).apply{contentDescription="sr35_hud_actions";addView(SrUi023.title(activity,"Teste e perfis",14f));addView(SrUi023.body(activity,"A prévia usa o HUD real no próprio aparelho. Perfis personalizados podem guardar ajustes por veículo.",10f));addView(UiKit.margin(UiKit.primaryButton(activity,"Pré-visualizar HUD real"){HudLivePreview027035.show(activity)},top=8));addView(UiKit.margin(UiKit.secondaryButton(activity,"Perfis por veículo"){activity.startActivity(Intent(activity,VehicleHudProfilesActivity027035::class.java))},top=6))},top=12))
        }
    }

    private fun decorateNow(now:NowPanel023){
        val optIn=SettingsRepository(now.context).load().collectiveStatsOptIn
        if(now.tag != "rc35"){now.tag="rc35";setPrivate(now,"source",if(optIn)"collective" else "personal");now.refresh()}
        getPrivate<LinearLayout>(now,"modeBox")?.visibility=View.GONE;getPrivate<LinearLayout>(now,"sourceBox")?.visibility=View.GONE
        hideLegacySearch(now);hideInlineRadar(now);ensureFilterMenu(now,optIn);polishPersonalBase(now);polishJourney(now)
    }
    private fun hideLegacySearch(now:NowPanel023){
        textViews(now).firstOrNull{it.text?.toString()?.trim() in setOf("Pesquisar região","Recolher pesquisa","Pesquisa")}?.let{ancestor(it){v->v is SrSoftShadowCard023}?.visibility=View.GONE}
    }
    private fun hideInlineRadar(now:NowPanel023){ textViews(now).firstOrNull{it.text?.toString()?.trim()=="Sr. Rotas Radar"}?.let{ancestor(it){v->v is SrSoftShadowCard023}?.visibility=View.GONE} }
    private fun ensureFilterMenu(now:NowPanel023,optIn:Boolean){
        val results=getPrivate<LinearLayout>(now,"results")?:return;val parent=results.parent as? LinearLayout?:return
        if(findFirst(parent){it.contentDescription=="sr35_now_filters"}!=null)return
        val card=SrUi023.card(now.context,11,12).apply{contentDescription="sr35_now_filters"}
        val body=LinearLayout(now.context).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE}
        val toggle=TextView(now.context).apply{text="☰  Filtros e pesquisa";textSize=12f;setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER_VERTICAL;minHeight=UiKit.dp(now.context,40);setTextColor(SrUi023.palette(now.context).blue);setOnClickListener{body.visibility=if(body.visibility==View.VISIBLE)View.GONE else View.VISIBLE}}
        card.addView(toggle);card.addView(body)
        val modeRow=LinearLayout(now.context).apply{orientation=LinearLayout.HORIZONTAL};listOf("now" to "Momento","today" to "Hoje","week" to "Semanal","search" to "Pesquisa").forEach{(key,label)->modeRow.addView(filterButton(now,label){setPrivate(now,"mode",key);now.refresh()},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))};body.addView(modeRow)
        val sourceRow=LinearLayout(now.context).apply{orientation=LinearLayout.HORIZONTAL};sourceRow.addView(filterButton(now,"Base Coletiva",enabled=optIn){setPrivate(now,"source","collective");now.refresh()},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));sourceRow.addView(filterButton(now,"Base Pessoal"){setPrivate(now,"source","personal");now.refresh()},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(now.context,5)});body.addView(UiKit.margin(sourceRow,top=6))
        val input=UiKit.input(now.context,"Pesquisar região");body.addView(UiKit.margin(input,top=6));body.addView(UiKit.margin(UiKit.primaryButton(now.context,"Pesquisar"){getPrivate<EditText>(now,"region")?.setText(input.text?.toString().orEmpty());setPrivate(now,"mode","search");now.refresh()},top=6))
        val idx=parent.indexOfChild(results).coerceAtLeast(0);parent.addView(card,idx,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{topMargin=UiKit.dp(now.context,8)})
    }
    private fun filterButton(now:NowPanel023,label:String,enabled:Boolean=true,action:()->Unit)=TextView(now.context).apply{text=label;textSize=9f;gravity=Gravity.CENTER;minHeight=UiKit.dp(now.context,36);isEnabled=enabled;alpha=if(enabled)1f else .45f;setTextColor(SrUi023.palette(now.context).blue);background=SrUi023.rounded(Color.TRANSPARENT,9,SrUi023.palette(now.context).outline,1,now.context);setOnClickListener{if(enabled)action()}}
    private fun polishPersonalBase(now:NowPanel023){
        textViews(now).filter{it.text?.toString()?.startsWith("Base pessoal:",true)==true}.forEach{footer->val card=ancestor(footer){it is SrSoftShadowCard023} as? ViewGroup?:return@forEach;val image=findFirst(card){it is ImageView} as? ImageView?:return@forEach;image.imageTintList=ColorStateList.valueOf(Color.WHITE);(image.parent as? View)?.background=SrUi023.rounded(0xFF19A96B.toInt(),12,null,0,now.context)}
        textViews(now).filter{it.text?.toString()?.trim() in setOf("R$/km","R$/h","R$/hora","Busca/min.","Busca/minutos")}.forEach{it.gravity=Gravity.CENTER;it.textAlignment=View.TEXT_ALIGNMENT_CENTER}
    }
    private fun polishJourney(now:NowPanel023){
        val active=SettingsRepository(now.context).currentJourneyId().isNotBlank()
        val start=textViews(now).firstOrNull{it.text?.toString()?.trim() in setOf("Iniciar","Encerrar")}
        start?.let{button->
            val tone=if(active)SrUi023.palette(now.context).red else SrUi023.palette(now.context).blue;button.setTextColor(Color.WHITE);button.background=SrUi023.rounded(tone,12,null,0,now.context)
            if(active)button.setOnClickListener{askEndOdometer(now.context as? MainActivity?:return@setOnClickListener)}
            (button.parent as? LinearLayout)?.let{row->for(i in 0 until row.childCount)(row.getChildAt(i).layoutParams as? LinearLayout.LayoutParams)?.let{lp->lp.width=0;lp.weight=if(i==0)0.42f else 0.58f;row.getChildAt(i).layoutParams=lp}}
        }
        Rc35JourneyDraft027035.attach(now,active)
        findFirst(now){it.contentDescription=="sr0264_journey_inline"}?.visibility=View.GONE
        textViews(now).filter{it.text?.toString()?.startsWith("Km / abastecimento",true)==true||it.text?.toString()?.startsWith("Dados preparados",true)==true}.forEach{it.visibility=View.GONE}
        if(!active) start?.setOnClickListener { Rc35JourneyDraft027035.persist(now); (now.context as? MainActivity)?.toggleJourneyFromNow() }
    }
    private fun askEndOdometer(activity:MainActivity){
        val journeyId=SettingsRepository(activity).currentJourneyId();if(journeyId.isBlank()){activity.toggleJourneyFromNow();return}
        val input=EditText(activity).apply{hint="Odômetro final (km) · opcional";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setSingleLine(true)}
        AlertDialog.Builder(activity).setTitle("Encerrar jornada").setMessage("Registre o odômetro final para fechar a distância real desta jornada.").setView(input).setNegativeButton("Cancelar",null).setNeutralButton("Encerrar sem km"){_,_->activity.toggleJourneyFromNow()}.setPositiveButton("Salvar e encerrar"){_,_->
            val raw=input.text?.toString()?.trim().orEmpty().replace(',','.');if(raw.isNotBlank()){val km=raw.toDoubleOrNull();if(km==null||JourneyMetricsStore026.get(activity).saveOdometer(journeyId,endKm=km)==null){Toast.makeText(activity,"Odômetro final inválido.",Toast.LENGTH_SHORT).show();return@setPositiveButton};JourneyMetricsClient026.syncPending(activity)};activity.toggleJourneyFromNow()
        }.show()
    }

    private fun decorateBubble(context:Context){
        val panel=getPrivate<ViewGroup>(JourneyBubbleController,"panel")?:return
        val prefs=JourneyUiPreferences(context);val dark=when(prefs.windowThemeMode()){ "dark"->true;"light"->false;else->Appearance021.isDark(context) };val pal=UiKit.palette(dark)
        panel.background=UiKit.rounded(context,pal.surface,14,pal.line,1);panel.alpha=prefs.windowOpacityPercent()/100f
        textViews(panel).forEach{tv->when(tv.text?.toString()?.trim()){ "Busca / retirada","Retirada"->tv.text="Busca";"dados insuficientes","Dados insuficientes","% Dados insuf."->tv.text="—" };if(tv.text?.toString()?.trim() in setOf("Busca","Destino"))tv.textSize=10.5f}
        decorateExpandedMetrics(context,panel)
    }
    private fun decorateExpandedMetrics(context:Context,panel:ViewGroup){
        val id=getPrivate<String>(JourneyBubbleController,"deepExpandedOfferId")?:return
        val offer=LocalStore.get(context).recentOffers(30).firstOrNull{it.localId==id}?:return
        val original=textViews(panel).firstOrNull{val t=it.text?.toString().orEmpty();t.contains("/km")&&t.contains("/min")&&t.contains("/h")}?:return
        val parent=original.parent as? LinearLayout?:return;if(findFirst(parent){it.contentDescription=="sr35_metric_blocks"}!=null)return
        original.visibility=View.GONE
        val row=LinearLayout(context).apply{contentDescription="sr35_metric_blocks";orientation=LinearLayout.HORIZONTAL}
        listOf("R$/km" to offer.perKm?.let{String.format(Locale("pt","BR"),"%.2f",it)},"R$/min" to offer.perMinute?.let{String.format(Locale("pt","BR"),"%.2f",it)},"R$/h" to offer.perHour?.let{String.format(Locale("pt","BR"),"%.0f",it)},"km" to offer.totalKm?.let{String.format(Locale("pt","BR"),"%.1f",it)},"min" to offer.totalMinutes?.toString()).forEachIndexed{i,(label,value)->row.addView(LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=SrUi023.rounded(UiKit.palette(context).surfaceMuted,8,UiKit.palette(context).line,1,context);setPadding(2,4,2,4);addView(SrUi023.body(context,label,7.5f).apply{gravity=Gravity.CENTER});addView(SrUi023.body(context,value?:"—",9f).apply{gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD)})},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{if(i>0)marginStart=UiKit.dp(context,3)})}
        parent.addView(row,(parent.indexOfChild(original)+1).coerceAtMost(parent.childCount),LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{topMargin=UiKit.dp(context,5)})
    }

    private fun enforceAssistantTimeout(context:Context){
        val overlay=getPrivate<View>(ActiveAssistant026,"overlay");val identity=overlay?.let { System.identityHashCode(it) } ?: 0
        if(identity==0){assistantOverlayIdentity=0;assistantShownAt=0;return}
        if(identity!=assistantOverlayIdentity){assistantOverlayIdentity=identity;assistantShownAt=System.currentTimeMillis();return}
        val due=JourneyUiPreferences(context).assistantDisplaySeconds()*1000L
        if(assistantShownAt>0&&System.currentTimeMillis()-assistantShownAt>=due){runCatching{val method=ActiveAssistant026::class.java.getDeclaredMethod("dismissOverlay").apply{isAccessible=true};method.invoke(ActiveAssistant026)};assistantShownAt=0}
    }

    private fun textViews(root:View):List<TextView>{val out=mutableListOf<TextView>();walk(root){if(it is TextView)out+=it};return out}
    private fun findFirst(root:View,predicate:(View)->Boolean):View?{if(predicate(root))return root;if(root is ViewGroup)for(i in 0 until root.childCount)findFirst(root.getChildAt(i),predicate)?.let{return it};return null}
    private fun walk(root:View,action:(View)->Unit){action(root);if(root is ViewGroup)for(i in 0 until root.childCount)walk(root.getChildAt(i),action)}
    private fun ancestor(view:View,predicate:(View)->Boolean):View?{var cur:View?=view;while(cur!=null){if(predicate(cur))return cur;cur=cur.parent as? View};return null}
    @Suppress("UNCHECKED_CAST") private fun <T> getPrivate(target:Any,name:String):T?=runCatching{target.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(target) as? T}.getOrNull()
    private fun setPrivate(target:Any,name:String,value:Any?)=runCatching{target.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(target,value)}.isSuccess
}
