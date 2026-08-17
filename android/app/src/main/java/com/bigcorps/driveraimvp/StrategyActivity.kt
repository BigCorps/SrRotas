package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.Instant

class StrategyActivity : Activity() {
    private lateinit var repo:SettingsRepository
    private lateinit var redKm:EditText;private lateinit var greenKm:EditText;private lateinit var redHour:EditText;private lateinit var greenHour:EditText
    private lateinit var redRating:EditText;private lateinit var greenRating:EditText;private lateinit var redMinute:EditText;private lateinit var greenMinute:EditText
    private lateinit var minFare:EditText;private lateinit var maxPickup:EditText;private lateinit var minProfit:EditText;private lateinit var costKm:EditText
    private lateinit var redProfitHour:EditText;private lateinit var greenProfitHour:EditText;private lateinit var redProfitPct:EditText;private lateinit var greenProfitPct:EditText
    private lateinit var positionSpinner:Spinner;private lateinit var themeSpinner:Spinner;private lateinit var colorBlind:CheckBox;private lateinit var opacity:SeekBar;private lateinit var fontSize:SeekBar
    private lateinit var textNotification:CheckBox;private lateinit var voiceNotification:CheckBox;private lateinit var privateScreenshot:CheckBox;private lateinit var passengerMessage:EditText
    private lateinit var metricsBox:LinearLayout
    private val metricLabels=linkedMapOf("per_minute" to "R$/min","per_km" to "R$/km","rating" to "Avaliação","per_hour" to "R$/hora","profit_hour" to "Lucro/hora","profit_percent" to "Margem %","profit" to "Lucro líquido")
    private val order=mutableListOf<String>();private val enabled=mutableSetOf<String>()

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);repo=SettingsRepository(this);setContentView(buildUi());load()}

    private fun buildUi():View{
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(244,246,248))};val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(38))};scroll.addView(root)
        root.addView(title("Estratégia e HUD",28f));root.addView(body("Configure o seu Cherry Picker: vermelho para ofertas abaixo do limite, amarelo para intermediárias e verde para as que atingem sua meta. Os presets são apenas pontos de partida editáveis."));root.addView(space(12))
        root.addView(horizontalButtons(listOf("Equilibrado" to{applyPreset("balanced")},"Conservador" to{applyPreset("conservative")},"Volume" to{applyPreset("volume")})))
        root.addView(space(16));root.addView(section("Filtros da oferta"))
        val km=thresholdCard("R$/km","Valor por quilômetro considerando embarque + viagem.",1.45,1.80);redKm=km.first;greenKm=km.second;root.addView(km.third)
        val hr=thresholdCard("R$/hora","Valor da oferta dividido pelo tempo total estimado.",28.0,35.0);redHour=hr.first;greenHour=hr.second;root.addView(hr.third)
        val rat=thresholdCard("Avaliação do passageiro","Quando a avaliação estiver disponível no card do Uber.",4.70,4.85);redRating=rat.first;greenRating=rat.second;root.addView(rat.third)
        val min=thresholdCard("R$/min","Valor da oferta dividido pelos minutos totais. Esta é a primeira métrica do HUD na 0.5.",0.48,0.60);redMinute=min.first;greenMinute=min.second;root.addView(min.third)
        val ph=thresholdCard("Lucro por hora","Lucro estimado após custo por km, dividido pelo tempo total. Use 0/0 para não classificar por esta métrica.",0.0,0.0);redProfitHour=ph.first;greenProfitHour=ph.second;root.addView(ph.third)
        val pp=thresholdCard("Margem de lucro %","Percentual estimado que sobra após o custo por km. Use 0/0 para não classificar por esta métrica.",0.0,0.0);redProfitPct=pp.first;greenProfitPct=pp.second;root.addView(pp.third)

        root.addView(space(12));root.addView(section("Limites adicionais"));minFare=numeric("Valor mínimo da oferta — 0 desativa");maxPickup=numeric("Máximo km até o passageiro — ex.: 5");minProfit=numeric("Lucro líquido mínimo — 0 desativa");costKm=numeric("Custo estimado por km — ex.: 0,85")
        listOf(minFare,maxPickup,minProfit,costKm).forEach(root::addView)

        root.addView(space(18));root.addView(section("Aparência do HUD"));root.addView(body("Escolha o que aparece no card flutuante e use ↑/↓ para definir a ordem. A 0.5 usa letras maiores e remove as bordas internas que podiam parecer um traço sobre o texto."));metricsBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(metricsBox)
        root.addView(label("Posição do card"));positionSpinner=spinner(listOf("Esquerda","Centro","Direita"));root.addView(positionSpinner)
        root.addView(label("Tema"));themeSpinner=spinner(listOf("Claro","Escuro","Verde"));root.addView(themeSpinner)
        colorBlind=CheckBox(this).apply{text="Modo para daltonismo (símbolos + paleta alternativa)"};root.addView(colorBlind)
        root.addView(label("Opacidade do card (30%–100%)"));opacity=SeekBar(this).apply{max=70};root.addView(opacity)
        root.addView(label("Tamanho da fonte (14–24)"));fontSize=SeekBar(this).apply{max=10};root.addView(fontSize)
        root.addView(actionButton("👁 Pré-visualizar HUD"){preview()})

        root.addView(space(18));root.addView(section("Avançado"));textNotification=CheckBox(this).apply{text="Notificação textual com resumo da oferta"};voiceNotification=CheckBox(this).apply{text="Notificação por voz"};privateScreenshot=CheckBox(this).apply{text="Salvar captura automática privada quando uma oferta for reconhecida (desligado por padrão)"};root.addView(textNotification);root.addView(voiceNotification);root.addView(privateScreenshot);root.addView(body("Capturas privadas ficam somente no armazenamento interno do Sr. Rotas, não entram na galeria e não são enviadas ao servidor. O app mantém no máximo 30."));root.addView(actionButton("Apagar capturas privadas"){PrivateScreenshotStore.clear(this);toast("Capturas privadas apagadas.")})
        root.addView(label("Mensagem padrão para passageiro"));passengerMessage=EditText(this).apply{minLines=3;maxLines=6;gravity=Gravity.TOP;setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(Color.WHITE)};root.addView(passengerMessage);root.addView(actionButton("Copiar mensagem"){copyPassengerMessage()})
        root.addView(space(18));root.addView(actionButton("Salvar estratégia e HUD"){save();BackendClient.syncPreferences(this);toast("Configurações salvas e sincronização solicitada.")});root.addView(actionButton("← Voltar"){finish()});root.addView(space(12));root.addView(body("Sr. Rotas é desenvolvido pela BigCorps • contato@bigcorps.com.br"))
        return scroll
    }

    private fun thresholdCard(name:String,help:String,redDefault:Double,greenDefault:Double):Triple<EditText,EditText,View>{
        val red=numeric("Vermelho abaixo de $redDefault");val green=numeric("Verde a partir de $greenDefault")
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));background=rounded(Color.WHITE);val head=LinearLayout(this@StrategyActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};head.addView(TextView(this@StrategyActivity).apply{text=name;textSize=19f;setTextColor(Color.rgb(10,40,52));setTypeface(typeface,1)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));head.addView(Button(this@StrategyActivity).apply{text="?";isAllCaps=false;setOnClickListener{AlertDialog.Builder(this@StrategyActivity).setTitle(name).setMessage(help).setPositiveButton("OK",null).show()}});addView(head);addView(red);addView(green)}
        card.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,dp(10))};return Triple(red,green,card)
    }

    private fun renderMetrics(){metricsBox.removeAllViews();order.forEachIndexed{index,key->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4));background=rounded(Color.WHITE)};val cb=CheckBox(this).apply{text=metricLabels[key]?:key;isChecked=key in enabled;setOnCheckedChangeListener{_,checked->if(checked)enabled+=key else enabled-=key}};row.addView(cb,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));row.addView(Button(this).apply{text="↑";isEnabled=index>0;setOnClickListener{val k=order.removeAt(index);order.add(index-1,k);renderMetrics()}});row.addView(Button(this).apply{text="↓";isEnabled=index<order.lastIndex;setOnClickListener{val k=order.removeAt(index);order.add(index+1,k);renderMetrics()}});metricsBox.addView(row,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,dp(6))})}}

    private fun load(){val s=repo.load();redKm.setText(pt(s.redPerKmBelow));greenKm.setText(pt(s.minPerKm));redHour.setText(pt(s.redPerHourBelow));greenHour.setText(pt(s.minPerHour));redRating.setText(pt(s.redRatingBelow));greenRating.setText(pt(s.goodRatingFrom));redMinute.setText(pt(s.redPerMinuteBelow));greenMinute.setText(pt(s.minPerMinute));redProfitHour.setText(pt(s.redProfitPerHourBelow));greenProfitHour.setText(pt(s.minProfitPerHour));redProfitPct.setText(pt(s.redProfitPercentBelow));greenProfitPct.setText(pt(s.minProfitPercent));minFare.setText(pt(s.minFare));maxPickup.setText(pt(s.maxPickupKm));minProfit.setText(pt(s.minProfit));costKm.setText(pt(s.costPerKm));order.clear();order+=s.hudMetricOrder.split(',').filter(metricLabels::containsKey);metricLabels.keys.filterNot(order::contains).forEach(order::add);enabled.clear();enabled+=s.hudEnabledMetrics.split(',').filter(metricLabels::containsKey);renderMetrics();positionSpinner.setSelection(when(s.hudPosition){"center"->1;"right"->2;else->0});themeSpinner.setSelection(when(s.hudTheme){"dark"->1;"green"->2;else->0});colorBlind.isChecked=s.colorBlindMode;opacity.progress=s.hudOpacity-30;fontSize.progress=(s.hudFontSize-14).coerceIn(0,10);textNotification.isChecked=s.textNotificationEnabled;voiceNotification.isChecked=s.voiceNotificationEnabled;privateScreenshot.isChecked=s.privateScreenshotEnabled;passengerMessage.setText(s.defaultPassengerMessage)}
    private fun save(){val old=repo.load();repo.save(old.copy(minPerKm=num(greenKm,old.minPerKm),redPerKmBelow=num(redKm,old.redPerKmBelow),minPerHour=num(greenHour,old.minPerHour),redPerHourBelow=num(redHour,old.redPerHourBelow),goodRatingFrom=num(greenRating,old.goodRatingFrom),redRatingBelow=num(redRating,old.redRatingBelow),minPerMinute=num(greenMinute,old.minPerMinute),redPerMinuteBelow=num(redMinute,old.redPerMinuteBelow),minFare=num(minFare,old.minFare),maxPickupKm=num(maxPickup,old.maxPickupKm),minProfit=num(minProfit,old.minProfit),minProfitPerHour=num(greenProfitHour,old.minProfitPerHour),redProfitPerHourBelow=num(redProfitHour,old.redProfitPerHourBelow),minProfitPercent=num(greenProfitPct,old.minProfitPercent),redProfitPercentBelow=num(redProfitPct,old.redProfitPercentBelow),costPerKm=num(costKm,old.costPerKm),hudMetricOrder=order.joinToString(","),hudEnabledMetrics=order.filter(enabled::contains).joinToString(","),hudPosition=listOf("left","center","right")[positionSpinner.selectedItemPosition],hudTheme=listOf("light","dark","green")[themeSpinner.selectedItemPosition],colorBlindMode=colorBlind.isChecked,hudOpacity=opacity.progress+30,hudFontSize=fontSize.progress+14,textNotificationEnabled=textNotification.isChecked,voiceNotificationEnabled=voiceNotification.isChecked,privateScreenshotEnabled=privateScreenshot.isChecked,defaultPassengerMessage=passengerMessage.text.toString().trim().take(600)))}

    private fun applyPreset(kind:String){when(kind){"conservative"->{redKm.setText("1,80");greenKm.setText("2,20");redHour.setText("35");greenHour.setText("45");redRating.setText("4,75");greenRating.setText("4,90");redMinute.setText("0,60");greenMinute.setText("0,75")};"volume"->{redKm.setText("1,20");greenKm.setText("1,50");redHour.setText("24");greenHour.setText("30");redRating.setText("4,65");greenRating.setText("4,80");redMinute.setText("0,40");greenMinute.setText("0,50")};else->{redKm.setText("1,45");greenKm.setText("1,80");redHour.setText("28");greenHour.setText("35");redRating.setText("4,70");greenRating.setText("4,85");redMinute.setText("0,48");greenMinute.setText("0,60")}};toast("Preset aplicado. Revise e salve.")}
    private fun preview(){save();if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")));toast("Autorize o HUD e toque em Pré-visualizar novamente.");return};val sample=RideOffer(observedAt=Instant.now().toString(),sourcePackage="preview",captureMethod="preview",rawText="preview",fare=28.75,pickupKm=1.2,tripKm=7.3,totalKm=8.5,pickupMinutes=5,tripMinutes=20,totalMinutes=25,perKm=3.38,perHour=69.0,perMinute=1.15,estimatedCost=7.23,estimatedProfit=21.52,profitPerHour=51.65,profitPercent=74.85,passengerRating=4.95,advertisedPerKm=3.38,serviceType="uberx",verdict="boa",confidence=.99,offerType="exclusive",dedupeKey="preview");OverlayController(this).show(sample,15000)}
    private fun copyPassengerMessage(){val text=passengerMessage.text.toString().trim();if(text.isBlank()){toast("Digite uma mensagem primeiro.");return};(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mensagem Sr. Rotas",text));toast("Mensagem copiada. O Sr. Rotas não envia automaticamente.")}

    private fun horizontalButtons(items:List<Pair<String,()->Unit>>):View=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;items.forEach{(label,action)->addView(Button(this@StrategyActivity).apply{text=label;isAllCaps=false;setOnClickListener{action()}},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))}}
    private fun title(t:String,s:Float)=TextView(this).apply{text=t;textSize=s;setTextColor(Color.rgb(7,55,70));setPadding(0,0,0,dp(8));setTypeface(typeface,1)};private fun section(t:String)=title(t,21f);private fun body(t:String)=TextView(this).apply{text=t;textSize=14f;setTextColor(Color.rgb(58,75,80));setLineSpacing(0f,1.1f)};private fun label(t:String)=TextView(this).apply{text=t;textSize=14f;setTextColor(Color.rgb(30,55,62));setPadding(0,dp(8),0,dp(4))}
    private fun numeric(h:String)=EditText(this).apply{hint=h;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setSingleLine(true);setPadding(dp(12),dp(9),dp(12),dp(9));setBackgroundColor(Color.rgb(247,247,247))};private fun spinner(items:List<String>)=Spinner(this).apply{adapter=ArrayAdapter(this@StrategyActivity,android.R.layout.simple_spinner_dropdown_item,items)};private fun actionButton(t:String,a:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{a()}};private fun rounded(color:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(14).toFloat()};private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
    private fun num(e:EditText,f:Double)=e.text.toString().trim().replace(',','.').toDoubleOrNull()?:f;private fun pt(v:Double)=v.toString().replace('.',',');private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt();private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
}
