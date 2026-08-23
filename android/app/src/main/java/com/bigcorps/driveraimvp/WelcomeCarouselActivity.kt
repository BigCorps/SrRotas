package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class WelcomeCarouselActivity : Activity() {
    private var page=0
    private lateinit var content:LinearLayout
    private lateinit var dots:TextView
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);UiKit.applySystemBars(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(UiKit.dp(this@WelcomeCarouselActivity,22),UiKit.dp(this@WelcomeCarouselActivity,22),UiKit.dp(this@WelcomeCarouselActivity,22),UiKit.dp(this@WelcomeCarouselActivity,22));setBackgroundColor(UiKit.palette(this@WelcomeCarouselActivity).background)};root.addView(ImageView(this).apply{setImageResource(R.drawable.logo_srrotas);scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,UiKit.dp(this,86)));content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL};root.addView(content,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));dots=UiKit.body(this,"",12f).apply{gravity=Gravity.CENTER};root.addView(dots);setContentView(root);UiKit.applySafeArea(root);render()}
    private fun render(){content.removeAllViews();val cards=listOf(
        Triple("Bem-vindo ao Sr. Rotas","Seu copiloto de rentabilidade.","O Sr. Rotas analisa suas ofertas e ajuda você a enxergar rapidamente os números que importam."),
        Triple("Analise suas ofertas","R$/min · R$/km · R$/hora","Origem, destino e indicadores 🟢 🟡 🔴 aparecem sem você precisar fazer contas na rua."),
        Triple("Já deixamos tudo preparado","Comece sem configurar dezenas de campos.","Você pode personalizar valores, perfil, métricas, Painel de Rota, aparência, voz e limites de busca depois."),
        Triple("Aprenda com suas corridas","Onde, quando e quais ofertas costumam valer mais a pena.","A área Agora combina seu histórico com a Base Sr. Rotas regional. Sua oferta individual continua privada."),
        Triple("Seus primeiros 7 dias são grátis","7 dias grátis. Tudo liberado.","O período começa somente na sua primeira oferta válida. Não existe cobrança automática. O trial inclui 5 créditos temporários de IA."),
    );val c=cards[page];content.addView(UiKit.title(this,c.first,30f));content.addView(UiKit.margin(UiKit.title(this,c.second,19f),top=12));content.addView(UiKit.margin(UiKit.body(this,c.third,15f),top=9));content.addView(UiKit.margin(UiKit.card(this).apply{addView(UiKit.body(this@WelcomeCarouselActivity,when(page){0->"Instale → entenda rapidamente → comece a usar.";1->"O Sr. Rotas calcula. Você decide a corrida.";2->"Padrão inicial: Popular. Tudo continua editável.";3->"Tendência histórica não é garantia de corrida.";else->"Começar o trial não contrata plano nem cadastra cobrança."},13f))},top=18));val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};if(page>0)buttons.addView(UiKit.secondaryButton(this,"Voltar"){page--;render()},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));buttons.addView(UiKit.primaryButton(this,if(page==4)"Preparar meus 7 dias grátis" else "Continuar"){if(page<4){page++;render()}else finishWelcome()},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{if(page>0)marginStart=UiKit.dp(this@WelcomeCarouselActivity,8)});content.addView(UiKit.margin(buttons,top=20));dots.text=(0..4).joinToString("  "){if(it==page)"●" else "○"}}
    private fun finishWelcome(){StrategyPresets021.apply(this,"popular");val r=SettingsRepository(this);r.save(r.load().copy(maxPickupKm=4.0));Strategy021Store.saveMaxPickupMinutes(this,8);Strategy021Store.markWelcomeSeen(this);startActivity(Intent(this,OnboardingActivity::class.java));finish()}
}
