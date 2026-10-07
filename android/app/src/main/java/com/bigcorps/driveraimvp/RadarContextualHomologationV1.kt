package com.srrotas.app

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Controles exclusivos do Field APK para homologação progressiva R2/R3/R4. */
class RadarContextualHomologationV1(
    context: Context,
    private val onChanged: () -> Unit,
    private val onDemo: () -> Unit,
) : LinearLayout(context) {
    private val status = SrUi023.body(context, "", 9.5f).apply { gravity = Gravity.CENTER }
    private val diagnostic = SrUi023.body(context, "", 8.6f)
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 1500L)
        }
    }

    init {
        orientation = VERTICAL
        visibility = if (RadarContextualFlagsV1.fieldControlsVisible()) View.VISIBLE else View.GONE
        setPadding(
            SrUi023.dp(context, 10), SrUi023.dp(context, 7),
            SrUi023.dp(context, 10), SrUi023.dp(context, 8),
        )
        background = SrUi023.rounded(
            SrUi023.palette(context).surface,
            12,
            SrUi023.palette(context).outline,
            1,
            context,
        )

        addView(
            SrUi023.title(context, "Homologação Radar Contextual", 11.5f).apply {
                gravity = Gravity.CENTER
            },
        )
        addView(status)

        val row1 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row1.addView(button("1 · UI") {
            RadarContextualFlagsV1.enableUiOnly(context)
            applyChange()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(button("2 · Runtime") {
            RadarContextualFlagsV1.enableRuntime(context)
            applyChange()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 5) })
        row1.addView(button("3 · Assistente") {
            RadarContextualFlagsV1.enableAssistant(context)
            applyChange()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 5) })
        addView(row1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = SrUi023.dp(context, 6)
        })

        val row2 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row2.addView(button("Prévia DEMO") { onDemo() }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(button("Assistente DEMO") {
            DestinationRadarAssistantRendererV1.showPreview(context)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 5) })
        row2.addView(button("Rollback") {
            RadarContextualIntegrationV1.rollback(context)
            applyChange()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 5) })
        addView(row2, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = SrUi023.dp(context, 5)
        })

        addView(
            SrUi023.body(
                context,
                "Primeiro marque ESTOU NESSA CORRIDA no HUD; depois avance 1 → 2 → 3. Em qualquer problema use Rollback.",
                8.5f,
            ).apply { gravity = Gravity.CENTER },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = SrUi023.dp(context, 5)
            },
        )

        val diagnosticCard=SrUi023.softCard(context, "neutral", 10).apply {
            visibility=View.GONE
            addView(SrUi023.title(context,"Diagnóstico operacional R2/R3/R4",10.5f))
            addView(diagnostic)
        }
        val diagnosticScroll=android.widget.ScrollView(context).apply {
            visibility=View.GONE
            addView(diagnosticCard)
        }
        addView(button("Mostrar/ocultar diagnóstico") {
            val visible=diagnosticScroll.visibility!=View.VISIBLE
            diagnosticScroll.visibility=if(visible) View.VISIBLE else View.GONE
            diagnosticCard.visibility=if(visible) View.VISIBLE else View.GONE
        })
        addView(diagnosticScroll,LayoutParams(LayoutParams.MATCH_PARENT,SrUi023.dp(context,110)))
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(tick)
        main.post(tick)
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    fun refresh() {
        status.text = "Atual: ${RadarContextualFlagsV1.stage(context)}"
        diagnostic.text = RadarContextualDiagnosticV1.renderField(context)
    }

    private fun applyChange() {
        // A mudança de estágio é uma entrada operacional: a superfície será
        // atualizada pelo host e RadarContextualPanelV1.refresh() abandona DEMO.
        RadarContextualIntegrationV1.syncRuntime(context)
        refresh()
        onChanged()
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 8.7f
        gravity = Gravity.CENTER
        minHeight = SrUi023.dp(context, 38)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(SrUi023.palette(context).blue)
        background = SrUi023.rounded(
            android.graphics.Color.TRANSPARENT,
            10,
            SrUi023.palette(context).blue,
            1,
            context,
        )
        setOnClickListener { action() }
    }
}
