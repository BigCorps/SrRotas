package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    companion object { private const val REQ_MEDIA_PROJECTION=4101; private const val REQ_NOTIFICATIONS=4102 }
    private lateinit var repo: SettingsRepository
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var backendInput: EditText; private lateinit var pairingInput: EditText
    private lateinit var minKmInput: EditText; private lateinit var minHourInput: EditText; private lateinit var minFareInput: EditText
    private lateinit var maxPickupInput: EditText; private lateinit var minProfitInput: EditText; private lateinit var costKmInput: EditText
    private lateinit var ocrCheck: CheckBox; private lateinit var consentCheck: CheckBox
    private lateinit var startJourneyButton: Button; private lateinit var stopJourneyButton: Button
    private lateinit var pairingStatus: TextView; private lateinit var latestSummary: TextView; private lateinit var latestRaw: TextView
    private lateinit var serviceStatus: TextView; private lateinit var localHistory: TextView
    private lateinit var aiQuestionInput: EditText; private lateinit var aiAnswer: TextView

    private val captureReceiver=object:BroadcastReceiver(){ override fun onReceive(context: Context?, intent: Intent?){ refreshStatus(); refreshDiagnostics(); refreshLocalHistory() } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); repo=SettingsRepository(this); projectionManager=getSystemService(MediaProjectionManager::class.java); setContentView(buildUi()); loadSettings() }
    override fun onResume(){ super.onResume(); registerCaptureReceiver(); refreshStatus(); refreshDiagnostics(); refreshLocalHistory(); BackendClient.flushPendingOffers(this) }
    override fun onPause(){ runCatching{unregisterReceiver(captureReceiver)}; super.onPause() }

    @Deprecated("Deprecated in Android API, mantido sem dependência AndroidX neste Alpha.")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data); if(requestCode!=REQ_MEDIA_PROJECTION)return
        if(resultCode!=RESULT_OK || data==null){ toast("A captura não foi autorizada."); return }
        val journey=JourneyCoordinator.startJourney(this)
        val serviceIntent=Intent(this,MediaProjectionOcrService::class.java).apply{ action=MediaProjectionOcrService.ACTION_START; putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE,resultCode); putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA,data) }
        val failed=runCatching { if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent) }.exceptionOrNull()
        if(failed!=null){ JourneyCoordinator.endJourney(this,"service_start_failed"); toast("Não foi possível iniciar a jornada: ${failed.message}"); return }
        toast("Jornada ${journey.id.take(8)} iniciada. Abra o Uber Driver."); refreshStatus(); refreshLocalHistory()
    }

    private fun buildUi():View{
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(247,240,200))}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(44))}; scroll.addView(root)
        root.addView(ImageView(this).apply{setImageResource(R.drawable.logo_srrotas);adjustViewBounds=true;scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(150)))
        root.addView(title("Sr. Rotas 2.0 Alpha",27f)); root.addView(body("Copiloto inteligente para avaliar ofertas. A captura, OCR e diagnóstico funcionam localmente; o backend recebe somente dados estruturados por padrão.")); root.addView(body("Versão instalada: ${BuildConfig.VERSION_NAME}")); root.addView(space(16))

        root.addView(section("1. Jornada")); root.addView(body("O Android compartilha a tela com o próprio Sr. Rotas durante a sessão autorizada. Aproximadamente um frame por segundo é analisado localmente e descartado após o OCR. O Sr. Rotas não toca nos botões do Uber."))
        consentCheck=CheckBox(this).apply{text="Entendi e autorizo a análise local da tela durante minhas jornadas.";textSize=15f;setTextColor(Color.rgb(7,55,70));setOnCheckedChangeListener{_,_->saveSettings()}}; root.addView(consentCheck)
        serviceStatus=body(""); root.addView(serviceStatus); root.addView(space(8))
        root.addView(actionButton("Permitir o HUD sobre outros apps"){openOverlayPermission()})
        startJourneyButton=actionButton("▶ Iniciar jornada"){startJourney()}; root.addView(startJourneyButton)
        stopJourneyButton=actionButton("■ Encerrar jornada"){
            stopService(Intent(this,MediaProjectionOcrService::class.java)); repo.setProjectionActive(false); JourneyCoordinator.endJourney(this,"user_stop"); refreshStatus(); refreshLocalHistory(); toast("Jornada encerrada.")
        }; root.addView(stopJourneyButton)
        localHistory=TextView(this).apply{textSize=13f;setTextColor(Color.rgb(45,70,72));setTextIsSelectable(true);setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(Color.argb(150,255,255,255))}; root.addView(localHistory)

        root.addView(space(18));root.addView(section("2. Estratégia do motorista"));root.addView(body("O semáforo considera todas as metas preenchidas. Use 0 para desativar uma regra."))
        minKmInput=numeric("Mínimo R$/km — ex.: 1,80");minHourInput=numeric("Mínimo R$/hora — ex.: 35");minFareInput=numeric("Valor mínimo da oferta — ex.: 8");maxPickupInput=numeric("Máximo km até o passageiro — ex.: 5");minProfitInput=numeric("Lucro estimado mínimo — ex.: 8");costKmInput=numeric("Custo real estimado por km — ex.: 0,85")
        listOf(minKmInput,minHourInput,minFareInput,maxPickupInput,minProfitInput,costKmInput).forEach(root::addView)
        root.addView(actionButton("Salvar e sincronizar estratégia"){saveSettings();BackendClient.syncPreferences(this);toast("Estratégia salva.")})

        root.addView(space(18));root.addView(section("3. Backend Sr. Rotas"));backendInput=textInput("URL do backend");pairingInput=textInput("Código de pareamento").apply{inputType=InputType.TYPE_CLASS_NUMBER};root.addView(backendInput);root.addView(pairingInput);root.addView(actionButton("Parear aparelho"){pairDevice()});pairingStatus=body("");root.addView(pairingStatus)
        root.addView(actionButton("Sincronizar pendências"){saveSettings();BackendClient.syncPreferences(this);BackendClient.flushPendingOffers(this);toast("Sincronização solicitada.");refreshLocalHistory()})

        root.addView(space(18));root.addView(section("4. Pesquisa IA"));root.addView(body("Pergunte sobre ofertas observadas e sincronizadas. O Sr. Rotas não trata uma oferta observada como corrida aceita ou concluída."))
        aiQuestionInput=EditText(this).apply{hint="Ex.: Em quais horários apareceram as melhores ofertas nesta semana?";minLines=2;maxLines=4;gravity=Gravity.TOP;setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(Color.WHITE)};root.addView(aiQuestionInput);root.addView(actionButton("Perguntar à IA"){askAi()});aiAnswer=TextView(this).apply{textSize=15f;setTextColor(Color.rgb(7,55,70));setTextIsSelectable(true);setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(Color.WHITE)};root.addView(aiAnswer)

        root.addView(space(18));root.addView(section("5. Diagnóstico da leitura"));root.addView(body("O texto OCR bruto fica no aparelho. Para calibrar o Alpha, você pode compartilhá-lo manualmente junto com os valores interpretados e o log local. Screenshots não entram no pacote."))
        latestSummary=TextView(this).apply{textSize=18f;setTextColor(Color.rgb(7,55,70));setPadding(0,dp(6),0,dp(8))};root.addView(latestSummary)
        latestRaw=TextView(this).apply{textSize=12f;setTextColor(Color.DKGRAY);setTextIsSelectable(true);setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(Color.WHITE)};root.addView(latestRaw,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(actionButton("Atualizar diagnóstico"){refreshDiagnostics();refreshLocalHistory()});root.addView(actionButton("Compartilhar diagnóstico (texto)"){DiagnosticBundle.share(this)})

        root.addView(space(18));root.addView(section("6. Leitura auxiliar — opcional"));root.addView(body("MediaProjection continua sendo o motor principal. A Acessibilidade é apenas um caminho auxiliar de diagnóstico e deve permanecer desligada se não for necessária."));root.addView(actionButton("Abrir configurações de Acessibilidade"){saveSettings();startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        ocrCheck=CheckBox(this).apply{text="Permitir OCR auxiliar quando MediaProjection estiver desligado";textSize=15f;setTextColor(Color.rgb(7,55,70));setOnCheckedChangeListener{_,_->saveSettings()}};root.addView(ocrCheck)
        root.addView(space(18));root.addView(section("Teste Alpha"));root.addView(body("1) Permita o HUD. 2) Inicie a jornada e autorize a tela inteira. 3) Abra o Uber Driver. 4) Compare o HUD com pelo menos 5 ofertas. 5) Se algo estiver errado, compartilhe o diagnóstico textual e anote os valores que apareciam no Uber."))
        return scroll
    }

    private fun loadSettings(){val s=repo.load();backendInput.setText(s.backendUrl);minKmInput.setText(s.minPerKm.toPt());minHourInput.setText(s.minPerHour.toPt());minFareInput.setText(s.minFare.toPt());maxPickupInput.setText(s.maxPickupKm.toPt());minProfitInput.setText(s.minProfit.toPt());costKmInput.setText(s.costPerKm.toPt());ocrCheck.isChecked=s.ocrEnabled;consentCheck.isChecked=s.consentAccepted;pairingStatus.text=if(s.deviceToken.isBlank())"Aparelho ainda não pareado." else "✓ Aparelho pareado com o backend."}
    private fun saveSettings(){val c=repo.load();repo.save(c.copy(backendUrl=if(::backendInput.isInitialized)backendInput.text.toString() else c.backendUrl,minPerKm=valueOf(minKmInput,c.minPerKm),minPerHour=valueOf(minHourInput,c.minPerHour),minFare=valueOf(minFareInput,c.minFare),maxPickupKm=valueOf(maxPickupInput,c.maxPickupKm),minProfit=valueOf(minProfitInput,c.minProfit),costPerKm=valueOf(costKmInput,c.costPerKm),ocrEnabled=if(::ocrCheck.isInitialized)ocrCheck.isChecked else c.ocrEnabled,consentAccepted=if(::consentCheck.isInitialized)consentCheck.isChecked else c.consentAccepted))}
    private fun valueOf(input:EditText,fallback:Double)=input.numberOr(fallback)
    private fun startJourney(){saveSettings();if(!consentCheck.isChecked){toast("Marque o consentimento antes de iniciar.");return};if(!Settings.canDrawOverlays(this)){toast("Primeiro permita o HUD sobre outros apps.");openOverlayPermission();return};requestNotificationPermissionIfNeeded();@Suppress("DEPRECATION") startActivityForResult(projectionManager.createScreenCaptureIntent(),REQ_MEDIA_PROJECTION)}
    private fun openOverlayPermission(){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
    private fun requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),REQ_NOTIFICATIONS)}
    private fun pairDevice(){saveSettings();pairingStatus.text="Pareando...";BackendClient.pair(this,backendInput.text.toString(),pairingInput.text.toString()){result->result.onSuccess{pairingStatus.text="✓ Aparelho pareado com sucesso.";pairingInput.setText("");BackendClient.syncPreferences(this);BackendClient.flushPendingOffers(this)}.onFailure{pairingStatus.text="Falha no pareamento: ${it.message}"}}}
    private fun askAi(){saveSettings();aiAnswer.text="Consultando...";BackendClient.ask(this,aiQuestionInput.text.toString()){result->aiAnswer.text=result.fold({it},{"Falha: ${it.message}"})}}

    private fun refreshStatus(){val projection=repo.isProjectionActive();val overlay=Settings.canDrawOverlays(this);val accessibility=isAccessibilityServiceEnabled();val current=repo.currentJourneyId();serviceStatus.text=buildString{append(if(projection)"✓ Jornada ativa" else "○ Jornada parada");if(current.isNotBlank())append(" · ${current.take(8)}");append("\n");append(if(overlay)"✓ HUD autorizado" else "○ HUD sem permissão");append("\n");append(if(accessibility)"✓ Leitura auxiliar ativa" else "○ Leitura auxiliar desligada")};stopJourneyButton.isEnabled=projection||current.isNotBlank()}
    private fun refreshLocalHistory(){val store=LocalStore.get(this);val summary=JourneyCoordinator.currentSummary(this)?:store.latestJourney()?.let{store.journeySummary(it.id)};localHistory.text=buildString{if(summary==null)append("Histórico local: nenhuma jornada registrada ainda.") else {append(if(summary.journey.endedAt==null)"Jornada atual" else "Última jornada");append(" · ${summary.journey.id.take(8)}");append("\nOfertas observadas: ${summary.offerCount} · boas ${summary.goodCount} · regulares ${summary.regularCount} · ruins ${summary.badCount}");summary.averagePerKm?.let{append("\nMédia observada: R$ ${"%.2f".format(it)}/km")};summary.averagePerHour?.let{append(" · R$ ${"%.2f".format(it)}/h")};summary.estimatedProfitObserved?.let{append("\nLucro estimado das ofertas observadas: R$ ${"%.2f".format(it)}")}};append("\nPendentes de sincronização: ${store.pendingOfferCount()}")}}
    private fun refreshDiagnostics(){latestSummary.text=repo.latestSummary();val method=repo.latestMethod().takeIf{it.isNotBlank()}?.let{"Método: $it\n\n"}?:"";val raw=repo.latestRaw();val log=LocalLog.tail(this,30);latestRaw.text=if(raw.isNotBlank())"$method$raw\n\n--- LOG LOCAL ---\n$log" else "Nenhum texto bruto capturado ainda.\n\n--- LOG LOCAL ---\n$log"}
    private fun isAccessibilityServiceEnabled():Boolean{val expected="$packageName/${DriverAccessibilityService::class.java.name}";val enabled=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false;return enabled.split(':').any{it.equals(expected,true)}}
    private fun registerCaptureReceiver(){val filter=IntentFilter(AppSignals.ACTION_CAPTURE_UPDATED);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(captureReceiver,filter,RECEIVER_NOT_EXPORTED) else {@Suppress("DEPRECATION") registerReceiver(captureReceiver,filter)}}
    private fun title(text:String,size:Float)=TextView(this).apply{this.text=text;textSize=size;setTextColor(Color.rgb(7,55,70));setPadding(0,0,0,dp(8))};private fun section(text:String)=title(text,20f);private fun body(text:String)=TextView(this).apply{this.text=text;textSize=15f;setTextColor(Color.rgb(45,70,72));setLineSpacing(0f,1.12f)}
    private fun textInput(hint:String)=EditText(this).apply{this.hint=hint;setSingleLine(true);setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.WHITE)};private fun numeric(hint:String)=textInput(hint).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};private fun actionButton(label:String,action:()->Unit)=Button(this).apply{text=label;isAllCaps=false;setOnClickListener{action()}};private fun space(height:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(height))}
    private fun EditText.numberOr(default:Double)=text.toString().trim().replace(',','.').toDoubleOrNull()?:default;private fun Double.toPt()=toString().replace('.',',');private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt();private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_SHORT).show()
}
