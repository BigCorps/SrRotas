package com.srrotas.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class HistoryQuickActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@HistoryQuickActivity, 16), UiKit.dp(this@HistoryQuickActivity, 18), UiKit.dp(this@HistoryQuickActivity, 16), UiKit.dp(this@HistoryQuickActivity, 28))
            setBackgroundColor(UiKit.palette(this@HistoryQuickActivity).background)
            addView(UiKit.title(this@HistoryQuickActivity, "Histórico", 27f))
            addView(UiKit.body(this@HistoryQuickActivity, "Ofertas observadas e correção das corridas realizadas."))
            addView(UiKit.margin(HistoryPanel(this@HistoryQuickActivity), top = 14))
            addView(UiKit.margin(UiKit.secondaryButton(this@HistoryQuickActivity, "Voltar") { finish() }, top = 12))
        }
        scroll.addView(root)
        setContentView(scroll)
        UiKit.applySafeArea(scroll)
    }
}
