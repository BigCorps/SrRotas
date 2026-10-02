package com.srrotas.app
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
class BillingStatusView(context:Context):LinearLayout(context){
 private val status=UiKit.body(context,"Consultando Inteligência...",14f)
 private val detail=UiKit.body(context,"O Copiloto continua grátis mesmo sem assinatura.",12f)
 init{
  orientation=VERTICAL
  addView(UiKit.sectionTitle(context,"Copiloto e Inteligência"))
  addView(status)
  val row=LinearLayout(context).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,UiKit.dp(context,10),0,0)}
  row.addView(LinearLayout(context).apply{orientation=VERTICAL;addView(UiKit.body(context,"Sr. Rotas Copiloto",11f));addView(UiKit.title(context,"GRÁTIS",20f))},LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
  row.addView(UiKit.pill(context,"Inteligência · R$ 9,90 / 30 dias","primary"))
  addView(row)
  addView(UiKit.margin(detail,top=7))
  addView(UiKit.margin(UiKit.body(context,"Pagamento da Inteligência é feito na Central Web via Pix. Leitura de ofertas, HUD e cálculos básicos não expiram.",11f),top=8))
  refresh()
 }
 fun refresh(){
  val settings=SettingsRepository(context).load()
  if(settings.deviceToken.isBlank()){status.text="Copiloto disponível. Conecte sua conta para consultar a Inteligência.";detail.text="Leitura, HUD, custos e histórico pessoal continuam disponíveis.";return}
  BackendClient.fetchBillingStatus(context){result->result.onSuccess{b->
   val state=AccessResolver10B.state(context)
   status.text=when{
    b.subscriptionActive->"Inteligência ativa${b.currentPeriodEnd?.let{" até ${date(it)}"}?:""}."
    state=="TRIAL_ACTIVE"->"Trial da Inteligência ativo."
    else->"Copiloto gratuito ativo. Inteligência opcional."
   }
   detail.text=when{
    b.subscriptionActive->"Estatísticas Premium, Pergunte, inteligência regional e MCP liberados."
    state=="TRIAL_ACTIVE"->"Durante o trial, os recursos da Inteligência ficam liberados sem cobrança automática."
    else->"O fim do trial ou da assinatura não bloqueia o Copiloto."
   }
  }.onFailure{status.text="Copiloto gratuito ativo.";detail.text="Não foi possível consultar a Inteligência agora: ${it.message}"}}
 }
 private fun date(value:String)=runCatching{DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value))}.getOrDefault(value.take(10))
}
