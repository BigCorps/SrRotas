package com.srrotas.app

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Editor inline 0.26.4 para hodômetro + abastecimento/recarga antes da jornada.
 *
 * Usa exatamente o mesmo SharedPreferences da JourneyFlow026 para preservar
 * compatibilidade com os rascunhos existentes. Nenhum schema/banco novo.
 */
object JourneyInlineDraft0264 {
    private const val PREFS = "sr_journey_flow_026"
    private const val KEY_SAVED_AT = "saved_at_ms"
    private const val KEY_START_KM = "start_km"
    private const val KEY_ENERGY_MODE = "energy_mode"
    private const val KEY_AMOUNT = "amount"
    private const val KEY_QUANTITY = "quantity"
    private const val KEY_FUEL_TYPE = "fuel_type"
    private const val KEY_ARMED = "armed"

    private const val MODE_NONE = "none"
    private const val MODE_FUEL = "fuel"
    private const val MODE_ELECTRIC = "electric"

    private data class Draft(
        val startKm: Double?,
        val energyMode: String,
        val amountPaid: Double?,
        val quantity: Double?,
        val fuelType: String?,
    )

    fun attach(anchor: TextView) {
        val parent = anchor.parent as? LinearLayout ?: return
        if ((0 until parent.childCount).any {
                parent.getChildAt(it).contentDescription == "sr0264_journey_inline"
            }
        ) return

        val host = buildHost(anchor.context, anchor)
        host.contentDescription = "sr0264_journey_inline"
        host.visibility = View.GONE
        val index = parent.indexOfChild(anchor)
        parent.addView(
            host,
            (index + 1).coerceAtMost(parent.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(anchor.context, 7) },
        )
        anchor.setOnClickListener {
            host.visibility = if (host.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        updateAnchor(anchor)
    }

    /** Chamado pelo polish enquanto a tela está viva; aplica o rascunho assim que o journey_id existir. */
    fun maybeApplyToCurrentJourney(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ARMED, false)) return false
        val draft = load(context) ?: return false
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        if (journeyId.isBlank()) return false

        val store = JourneyMetricsStore026.get(context)
        draft.startKm?.let { store.saveOdometer(journeyId, startKm = it) }
        val kind = JourneyFlowRules026.energyKind(draft.energyMode)
        if (
            kind != null &&
            JourneyMetricsRules026.validEnergyEntry(
                kind,
                draft.amountPaid,
                draft.quantity,
                JourneyFlowRules026.unitFor(kind),
            )
        ) {
            store.addEnergy(
                journeyId = journeyId,
                kind = kind,
                amountPaid = draft.amountPaid,
                quantity = draft.quantity,
                unit = JourneyFlowRules026.unitFor(kind),
                fuelType = if (kind == JourneyMetricsRules026.KIND_FUEL) draft.fuelType else null,
            )
        }
        clear(context)
        JourneyMetricsClient026.syncPending(context)
        return true
    }

    private fun buildHost(context: Context, anchor: TextView): View =
        SrUi023.card(context, 11, 15).apply host@{
            addView(SrUi023.title(context, "Odômetro e gastos", 13.5f))

            val old = load(context)
            val startKm = decimalInput(context, "Quilometragem inicial (km)", old?.startKm)
            addView(startKm, lp(context, 7))

            val mode = styledSpinner(context).apply {
                adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Nenhum gasto agora", "Combustível", "Recarga elétrica"),
                )
                setSelection(
                    when (old?.energyMode) {
                        MODE_FUEL -> 1
                        MODE_ELECTRIC -> 2
                        else -> 0
                    },
                )
            }
            addView(mode, lp(context, 7))

            val amount = decimalInput(context, "Valor pago (R$) · opcional", old?.amountPaid)
            val quantity = decimalInput(context, "Quantidade (litros) · opcional", old?.quantity)
            val fuelLabel = SrUi023.body(context, "Tipo de combustível", 10f)
            val fuelTypes = listOf("Gasolina", "Etanol", "Diesel", "GNV", "Outro")
            val fuel = styledSpinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fuelTypes)
                old?.fuelType?.let { previous ->
                    fuelTypes.indexOfFirst { it.equals(previous, true) }
                        .takeIf { it >= 0 }
                        ?.let(::setSelection)
                }
            }
            addView(amount, lp(context, 7))
            addView(quantity, lp(context, 7))
            addView(fuelLabel, lp(context, 7))
            addView(fuel, lp(context, 4))

            fun updateVisibility() {
                val selected = mode.selectedItemPosition
                val energy = selected != 0
                amount.visibility = if (energy) View.VISIBLE else View.GONE
                quantity.visibility = if (energy) View.VISIBLE else View.GONE
                fuelLabel.visibility = if (selected == 1) View.VISIBLE else View.GONE
                fuel.visibility = if (selected == 1) View.VISIBLE else View.GONE
                quantity.hint = if (selected == 2) {
                    "Energia (kWh) · opcional"
                } else {
                    "Quantidade (litros) · opcional"
                }
            }
            mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateVisibility()
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            updateVisibility()

            val buttons = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            buttons.addView(
                actionButton(context, "Limpar", primary = false) {
                    clear(context)
                    startKm.setText("")
                    amount.setText("")
                    quantity.setText("")
                    mode.setSelection(0)
                    updateAnchor(anchor)
                    Toast.makeText(context, "Dados preparados removidos.", Toast.LENGTH_SHORT).show()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            buttons.addView(
                actionButton(context, "Salvar e recolher", primary = true) {
                    val start = JourneyFlowRules026.decimalFlexible(startKm.text?.toString())
                    val normalizedStart = JourneyMetricsRules026.normalizedOdometer(start)
                    if (start != null && normalizedStart == null) {
                        toast(context, "Quilometragem inicial inválida.")
                        return@actionButton
                    }
                    val energyMode = when (mode.selectedItemPosition) {
                        1 -> MODE_FUEL
                        2 -> MODE_ELECTRIC
                        else -> MODE_NONE
                    }
                    val amountValue = if (energyMode == MODE_NONE) null else JourneyFlowRules026.decimalFlexible(amount.text?.toString())
                    val quantityValue = if (energyMode == MODE_NONE) null else JourneyFlowRules026.decimalFlexible(quantity.text?.toString())
                    val kind = JourneyFlowRules026.energyKind(energyMode)
                    if (kind != null && !JourneyMetricsRules026.validEnergyEntry(
                            kind,
                            amountValue,
                            quantityValue,
                            JourneyFlowRules026.unitFor(kind),
                        )
                    ) {
                        toast(context, "Para registrar gasto, informe valor pago ou quantidade.")
                        return@actionButton
                    }
                    if (normalizedStart == null && kind == null) {
                        toast(context, "Informe o km inicial ou um abastecimento/recarga.")
                        return@actionButton
                    }
                    save(
                        context,
                        Draft(
                            startKm = normalizedStart,
                            energyMode = energyMode,
                            amountPaid = amountValue,
                            quantity = quantityValue,
                            fuelType = if (energyMode == MODE_FUEL) fuel.selectedItem?.toString() else null,
                        ),
                    )
                    updateAnchor(anchor)
                    this@host.visibility = View.GONE
                    Toast.makeText(context, "Dados preparados para a próxima jornada.", Toast.LENGTH_SHORT).show()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = SrUi023.dp(context, 7)
                },
            )
            addView(buttons, lp(context, 9))
        }

    private fun load(context: Context): Draft? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = p.getLong(KEY_SAVED_AT, 0L)
        if (!JourneyFlowRules026.draftIsFresh(savedAt, System.currentTimeMillis())) {
            clear(context)
            return null
        }
        return Draft(
            startKm = p.getString(KEY_START_KM, null)?.toDoubleOrNull(),
            energyMode = p.getString(KEY_ENERGY_MODE, MODE_NONE) ?: MODE_NONE,
            amountPaid = p.getString(KEY_AMOUNT, null)?.toDoubleOrNull(),
            quantity = p.getString(KEY_QUANTITY, null)?.toDoubleOrNull(),
            fuelType = p.getString(KEY_FUEL_TYPE, null),
        )
    }

    private fun save(context: Context, draft: Draft) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .putNullableDouble(KEY_START_KM, draft.startKm)
            .putString(KEY_ENERGY_MODE, draft.energyMode)
            .putNullableDouble(KEY_AMOUNT, draft.amountPaid)
            .putNullableDouble(KEY_QUANTITY, draft.quantity)
            .putString(KEY_FUEL_TYPE, draft.fuelType)
            // Já fica armado. O polish aplica ao primeiro journey_id aberto.
            .putBoolean(KEY_ARMED, true)
            .apply()
    }

    private fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun updateAnchor(anchor: TextView) {
        val draft = load(anchor.context)
        anchor.text = if (draft == null) {
            "Km / abastecimento ou recarga"
        } else {
            buildString {
                append("Dados preparados")
                draft.startKm?.let { append(" · ${String.format(java.util.Locale("pt", "BR"), "%,.1f km", it)}") }
                when (draft.energyMode) {
                    MODE_FUEL -> append(" · combustível")
                    MODE_ELECTRIC -> append(" · recarga")
                }
            }
        }
    }

    private fun decimalInput(context: Context, hintText: String, value: Double?): EditText =
        EditText(context).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setTextColor(SrUi023.palette(context).ink)
            setHintTextColor(SrUi023.palette(context).muted)
            background = SrUi023.rounded(
                SrUi023.palette(context).surface,
                11,
                SrUi023.palette(context).outline,
                1,
                context,
            )
            setPadding(
                SrUi023.dp(context, 11),
                SrUi023.dp(context, 9),
                SrUi023.dp(context, 11),
                SrUi023.dp(context, 9),
            )
            value?.let { setText(String.format(java.util.Locale.US, "%.1f", it).removeSuffix(".0")) }
        }

    private fun styledSpinner(context: Context): Spinner = Spinner(context).apply {
        minimumHeight = SrUi023.dp(context, 42)
        background = SrUi023.rounded(
            SrUi023.palette(context).surface,
            11,
            SrUi023.palette(context).outline,
            1,
            context,
        )
        setPadding(
            SrUi023.dp(context, 9),
            SrUi023.dp(context, 6),
            SrUi023.dp(context, 9),
            SrUi023.dp(context, 6),
        )
    }

    private fun actionButton(context: Context, label: String, primary: Boolean, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 40)
            setPadding(SrUi023.dp(context, 8), SrUi023.dp(context, 7), SrUi023.dp(context, 8), SrUi023.dp(context, 7))
            if (primary) {
                setTextColor(android.graphics.Color.WHITE)
                background = SrUi023.rounded(SrUi023.palette(context).blue, 12, null, 0, context)
            } else {
                setTextColor(SrUi023.palette(context).blue)
                background = SrUi023.rounded(
                    SrUi023.palette(context).surfaceMuted,
                    12,
                    SrUi023.palette(context).blue,
                    1,
                    context,
                )
            }
            setOnClickListener { action() }
        }

    private fun lp(context: Context, top: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = SrUi023.dp(context, top) }

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun android.content.SharedPreferences.Editor.putNullableDouble(key: String, value: Double?) =
        if (value == null) remove(key) else putString(key, value.toString())
}
