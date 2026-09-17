package com.srrotas.app

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Editor dedicado das mensagens da janela flutuante. */
class MessageSettingsActivity027035 : Activity() {
    private val editors = mutableListOf<Pair<MessageShortcut023, EditText>>()
    private lateinit var host: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(UiKit.palette(this@MessageSettingsActivity027035).background) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(SrAppHeader023(this,"Mensagens","Atalhos usados na janela flutuante. O Sr. Rotas copia; nunca envia automaticamente."))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@MessageSettingsActivity027035,14),UiKit.dp(this@MessageSettingsActivity027035,10),UiKit.dp(this@MessageSettingsActivity027035,14),UiKit.dp(this@MessageSettingsActivity027035,24)) }
        host=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(host)
        content.addView(UiKit.margin(UiKit.secondaryButton(this,"+ Adicionar mensagem") { addSlot() },top=9))
        page.addView(content); scroll.addView(page); outer.addView(scroll,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        val footer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(UiKit.dp(this@MessageSettingsActivity027035,14),8,UiKit.dp(this@MessageSettingsActivity027035,14),10); addView(UiKit.primaryButton(this@MessageSettingsActivity027035,"Salvar mensagens") { save() }) }
        outer.addView(footer)
        setContentView(outer); UiKit.applySafeArea(outer)
        load()
    }

    private fun load() {
        val current=MessagePresetStore023.load(this)
        val base=MessagePresetStore023.base(this)
        val initial=MessagePresetEditorRules024.editorSlots(if(current.isNotEmpty()) current else base, SettingsRepository(this).load().defaultPassengerMessage)
        editors.clear(); host.removeAllViews(); initial.forEach(::appendEditor)
    }

    private fun appendEditor(item: MessageShortcut023) {
        val index=editors.size
        val edit=UiKit.input(this,"Mensagem ${index+1}").apply { setText(item.text); minLines=2; maxLines=4 }
        editors += item to edit
        host.addView(UiKit.margin(SrUi023.card(this,11,13).apply {
            addView(SrUi023.body(this@MessageSettingsActivity027035,"Atalho ${index+1}",10f))
            addView(UiKit.margin(edit,top=5))
        },top=if(index==0)0 else 7))
    }

    private fun addSlot() {
        if (editors.size >= MessagePresetEditorRules024.MAX_SLOTS) { Toast.makeText(this,"Limite de 12 mensagens.",Toast.LENGTH_SHORT).show(); return }
        val i=editors.size
        appendEditor(MessageShortcut023(
            id="local-slot-${i+1}", order=i, shortLabel=(i+1).toString(), accessibilityLabel="Mensagem rápida ${i+1}",
            text="", colorToken=MessageShortcutRules023.colorFor(i), enabled=true,
        ))
    }

    private fun save() {
        val items=editors.mapIndexed { index,(base,edit) -> base.copy(order=index, shortLabel=(index+1).toString(), text=edit.text?.toString().orEmpty().trim(), enabled=true) }
        MessagePresetStore023.saveLocal(this,items)
        Preference021Sync.sync(this)
        JourneyBubbleController.refresh(this)
        Toast.makeText(this,"Mensagens atualizadas.",Toast.LENGTH_SHORT).show()
    }
}
