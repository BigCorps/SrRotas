package com.srrotas.app
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
class BillingStatusView(context:Context):LinearLayout(context){
 private val status=UiKit.body(context,"Consultando assinatura...",14f);private val credits=UiKit.title(context,"—",24f);private val detail=UiKit.body(context,"",12f)
 init{orientation=VERTICAL;addView(UiKit.sectionTitle(context,"Plano e créditos"));addView(status);val row=LinearLayout(context).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,UiKit.dp(context,10),0,0)};row.addView(LinearLayout(context).apply{orientation=VERTICAL;addView(UiKit.body(context,"Créditos de IA",11f));addView(credits)},LayoutParams(0,LayoutParams.WRAP_CONTENT,1f));row.addView(UiKit.pill(context,"R$ 9,90/mês","primary"));addView(row);addView(UiKit.margin(detail,top=7));addView(UiKit.margin(UiKit.body(context,"Pagamento via Banco Inter é feito no site. O Android apenas reconhece a assinatura.",11f),top=8));refresh()}
 fun refresh(){if(SettingsRepository(context).load().deviceToken.isBlank()){status.text="Conecte sua conta para consultar o plano.";credits.text="—";return};BackendClient.fetchBillingStatus(context){result->result.onSuccess{b->status.text=if(b.subscriptionActive)"Assinatura ativa${b.currentPeriodEnd?.let{" até ${date(it)}"}?:""}."else if(b.billingEnforcement)"Assinatura necessária para o acesso completo."else"Alpha liberado para testes · assinatura ainda não exigida.";credits.text=b.creditBalance.toString();detail.text="Recebidos ${b.lifetimeGranted} · Consumidos ${b.lifetimeSpent}${if(!b.creditPacksAvailable)" · Pacotes avulsos em definição"else""}"}.onFailure{status.text="Não foi possível consultar o plano: ${it.message}"}}}
 private fun date(value:String)=runCatching{DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value))}.getOrDefault(value.take(10))
}
