package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** 0.28: restaura o modo compacto como opção explícita da janela canônica. */
class FloatingWindowSettingsActivity027034 : Activity() {
    private lateinit var enabled: CheckBox
    private lateinit var compactPanel: CheckBox
    private lateinit var offerCount: Spinner
    private lateinit var textSize: Spinner
    private lateinit var buttonSize: SeekBar
    private lateinit var buttonOpacity: SeekBar
    private lateinit var windowOpacity: SeekBar
    private lateinit var theme: Spinner
    private lateinit var assistantEnabled: CheckBox
    private lateinit var assistantDisplay: Spinner
    private lateinit var buttonSizeLabel: TextView
    private lateinit var buttonOpacityLabel: TextView
    private lateinit var windowOpacityLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiKit.applySystemBars(this); FloatingWindowOpacity027034.ensureWatcher(this)
        val prefs=JourneyUiPreferences(this)
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@FloatingWindowSettingsActivity027034).background) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(SrAppHeader023(this@FloatingWindowSettingsActivity027034,"Janela flutuante","Botão, painel, Assistente Ativo e mensagens.")) }
        scroll.addView(page); outer.addView(scroll,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@FloatingWindowSettingsActivity027034,14),UiKit.dp(this@FloatingWindowSettingsActivity027034,10),UiKit.dp(this@FloatingWindowSettingsActivity027034,14),UiKit.dp(this@FloatingWindowSettingsActivity027034,24)) }
        page.addView(content)

        content.addView(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@FloatingWindowSettingsActivity027034,"Comportamento"))
            enabled=CheckBox(this@FloatingWindowSettingsActivity027034).apply { text="Exibir janela flutuante durante a jornada";isChecked=prefs.enabled();setTextColor(UiKit.palette(this@FloatingWindowSettingsActivity027034).ink) }; addView(enabled)
            compactPanel=CheckBox(this@FloatingWindowSettingsActivity027034).apply { text="Usar painel compacto";isChecked=prefs.compactPanel();setTextColor(UiKit.palette(this@FloatingWindowSettingsActivity027034).ink) }; addView(compactPanel)
            addView(UiKit.body(this@FloatingWindowSettingsActivity027034,"O modo compacto reduz largura, espaçamentos e texto do painel sem esconder ações ou ofertas.",9.5f))
            addView(UiKit.margin(UiKit.body(this@FloatingWindowSettingsActivity027034,"Quantidade de ofertas no painel",10.5f),top=8))
            offerCount=SrUi023.spinner(this@FloatingWindowSettingsActivity027034,listOf("1 oferta","2 ofertas","3 ofertas","4 ofertas","5 ofertas")).apply{setSelection((prefs.offerCount()-1).coerceIn(0,4))}; addView(offerCount)
            addView(UiKit.margin(UiKit.body(this@FloatingWindowSettingsActivity027034,"Tamanho do texto",10.5f),top=8))
            textSize=SrUi023.spinner(this@FloatingWindowSettingsActivity027034,listOf("Pequeno","Padrão","Grande")).apply{setSelection(when(prefs.textSize()){ "small"->0;"large"->2;else->1})};addView(textSize)
        })

        content.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@FloatingWindowSettingsActivity027034,"Aparência"))
            buttonSizeLabel=UiKit.body(this@FloatingWindowSettingsActivity027034,"",10.5f);addView(buttonSizeLabel)
            buttonSize=SeekBar(this@FloatingWindowSettingsActivity027034).apply{max=30;progress=(prefs.sizeDp()-46).coerceIn(0,30);setOnSeekBarChangeListener(listener{updateLabels()})};addView(buttonSize)
            buttonOpacityLabel=UiKit.body(this@FloatingWindowSettingsActivity027034,"",10.5f);addView(UiKit.margin(buttonOpacityLabel,top=8))
            buttonOpacity=SeekBar(this@FloatingWindowSettingsActivity027034).apply{max=40;progress=(prefs.opacityPercent()-60).coerceIn(0,40);setOnSeekBarChangeListener(listener{updateLabels()})};addView(buttonOpacity)
            windowOpacityLabel=UiKit.body(this@FloatingWindowSettingsActivity027034,"",10.5f);addView(UiKit.margin(windowOpacityLabel,top=8))
            windowOpacity=SeekBar(this@FloatingWindowSettingsActivity027034).apply{max=40;progress=(prefs.windowOpacityPercent()-60).coerceIn(0,40);setOnSeekBarChangeListener(listener{updateLabels()})};addView(windowOpacity)
            addView(UiKit.margin(UiKit.body(this@FloatingWindowSettingsActivity027034,"Cor da janela flutuante",10.5f),top=8))
            theme=SrUi023.spinner(this@FloatingWindowSettingsActivity027034,listOf("Seguir tema do aplicativo","Tema claro","Tema escuro")).apply{setSelection(when(prefs.windowThemeMode()){ "light"->1;"dark"->2;else->0})};addView(theme)
            addView(UiKit.margin(UiKit.body(this@FloatingWindowSettingsActivity027034,"A posição é mantida automaticamente dentro da área visível, inclusive após rotação.",9.5f),top=8))
        },top=12))

        content.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@FloatingWindowSettingsActivity027034,"Assistente Ativo"))
            assistantEnabled=CheckBox(this@FloatingWindowSettingsActivity027034).apply{text="Exibir sugestões do Assistente Ativo";isChecked=ActiveAssistant026.isEnabled(this@FloatingWindowSettingsActivity027034);setTextColor(UiKit.palette(this@FloatingWindowSettingsActivity027034).ink)};addView(assistantEnabled)
            addView(UiKit.margin(UiKit.body(this@FloatingWindowSettingsActivity027034,"Tempo de permanência do balão",10.5f),top=7))
            val seconds=listOf(5,8,12,20,30)
            assistantDisplay=SrUi023.spinner(this@FloatingWindowSettingsActivity027034,seconds.map{"$it segundos"}).apply{setSelection(seconds.indexOf(prefs.assistantDisplaySeconds()).takeIf{it>=0}?:2)};addView(assistantDisplay)
        },top=12))

        content.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@FloatingWindowSettingsActivity027034,"Mensagens"))
            addView(UiKit.body(this@FloatingWindowSettingsActivity027034,"Edite os atalhos exibidos no trilho da janela flutuante.",10f))
            addView(UiKit.margin(UiKit.secondaryButton(this@FloatingWindowSettingsActivity027034,"Configurar mensagens"){startActivity(Intent(this@FloatingWindowSettingsActivity027034,MessageSettingsActivity027035::class.java))},top=7))
        },top=12))

        content.addView(UiKit.margin(UiKit.body(this,"Uber suportado no lançamento inicial · 99 em implementação.",10f),top=12))
        val footer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(UiKit.dp(this@FloatingWindowSettingsActivity027034,14),UiKit.dp(this@FloatingWindowSettingsActivity027034,8),UiKit.dp(this@FloatingWindowSettingsActivity027034,14),UiKit.dp(this@FloatingWindowSettingsActivity027034,10));setBackgroundColor(UiKit.palette(this@FloatingWindowSettingsActivity027034).surface);addView(UiKit.primaryButton(this@FloatingWindowSettingsActivity027034,"Salvar janela flutuante"){save()})}
        outer.addView(footer);setContentView(outer);UiKit.applySafeArea(outer);updateLabels()
    }
    private fun updateLabels(){if(!::buttonSizeLabel.isInitialized)return;buttonSizeLabel.text="Tamanho do botão: ${buttonSize.progress+46} dp";buttonOpacityLabel.text="Opacidade do botão: ${buttonOpacity.progress+60}%";windowOpacityLabel.text="Opacidade da janela: ${windowOpacity.progress+60}%"}
    private fun save(){
        val prefs=JourneyUiPreferences(this);prefs.setEnabled(enabled.isChecked);prefs.setCompactPanel(compactPanel.isChecked);prefs.setOfferCount(offerCount.selectedItemPosition+1);prefs.setTextSize(when(textSize.selectedItemPosition){0->"small";2->"large";else->"standard"});prefs.setSizeDp(buttonSize.progress+46);prefs.setOpacityPercent(buttonOpacity.progress+60);prefs.setWindowOpacityPercent(windowOpacity.progress+60);prefs.setWindowThemeMode(when(theme.selectedItemPosition){1->"light";2->"dark";else->"follow_app"});prefs.setAssistantDisplaySeconds(listOf(5,8,12,20,30)[assistantDisplay.selectedItemPosition.coerceIn(0,4)]);ActiveAssistant026.setEnabled(this,assistantEnabled.isChecked);JourneyBubbleController.refresh(this);FloatingWindowOpacity027034.applyNow(this);Toast.makeText(this,"Janela flutuante atualizada.",Toast.LENGTH_SHORT).show()
    }
    private fun listener(action:()->Unit)=object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(seekBar:SeekBar?,progress:Int,fromUser:Boolean)=action();override fun onStartTrackingTouch(seekBar:SeekBar?)=Unit;override fun onStopTrackingTouch(seekBar:SeekBar?)=Unit}
}
