package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import java.util.Locale

/** Editor pré-jornada único, compatível com o draft 0.26 já persistido. */
object JourneyPreflight027037 {
    private const val PREFS = "sr_journey_flow_026"
    private const val KEY_SAVED_AT = "saved_at_ms"
    private const val KEY_START_KM = "start_km"
    private const val KEY_ENERGY_MODE = "energy_mode"
    private const val KEY_AMOUNT = "amount"
    private const val KEY_QUANTITY = "quantity"
    private const val KEY_FUEL_TYPE = "fuel_type"
    private const val KEY_ARMED = "armed"

    fun openOdometer(context: Context, onChanged: () -> Unit = {}) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val input = decimalInput(context, "Odômetro inicial (km)")
        p.getString(KEY_START_KM, null)?.let(input::setText)
        val dialog = AlertDialog.Builder(context)
            .setTitle("Odômetro inicial")
            .setMessage("Este valor mede somente os quilômetros da próxima jornada.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.trim().orEmpty().replace(',', '.')
                val km = raw.toDoubleOrNull()
                if (km == null || JourneyMetricsRules026.normalizedOdometer(km) == null) {
                    toast(context, "Odômetro inválido.")
                    return@setOnClickListener
                }
                p.edit()
                    .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                    .putString(KEY_START_KM, km.toString())
                    .putBoolean(KEY_ARMED, false)
                    .apply()
                dialog.dismiss()
                onChanged()
            }
        }
        dialog.show()
    }

    fun openEnergy(context: Context, onChanged: () -> Unit = {}) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 4), SrUi023.dp(context, 18), 0)
        }
        val mode = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Combustível", "Recarga elétrica"))
            if (p.getString(KEY_ENERGY_MODE, "fuel") == "electric") setSelection(1)
        }
        val amount = decimalInput(context, "Valor pago (R$) · opcional").apply { p.getString(KEY_AMOUNT, null)?.let(::setText) }
        val quantity = decimalInput(context, "Quantidade · litros ou kWh · opcional").apply { p.getString(KEY_QUANTITY, null)?.let(::setText) }
        val fuel = Spinner(context).apply {
            val values = listOf("Gasolina", "Etanol", "Diesel", "GNV", "Outro")
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
            p.getString(KEY_FUEL_TYPE, null)?.let { old -> values.indexOfFirst { it.equals(old, true) }.takeIf { it >= 0 }?.let(::setSelection) }
        }
        holder.addView(mode)
        holder.addView(UiKit.margin(amount, top = 8))
        holder.addView(UiKit.margin(quantity, top = 8))
        holder.addView(UiKit.margin(fuel, top = 8))

        val dialog = AlertDialog.Builder(context)
            .setTitle("Abastecimento / recarga")
            .setMessage("Informe valor, quantidade ou ambos. O dado será associado à próxima jornada.")
            .setView(holder)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amountValue = amount.text?.toString()?.trim().orEmpty().replace(',', '.').toDoubleOrNull()
                val quantityValue = quantity.text?.toString()?.trim().orEmpty().replace(',', '.').toDoubleOrNull()
                if (amountValue == null && quantityValue == null) {
                    toast(context, "Informe o valor pago ou a quantidade.")
                    return@setOnClickListener
                }
                val electric = mode.selectedItemPosition == 1
                p.edit()
                    .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                    .putString(KEY_ENERGY_MODE, if (electric) "electric" else "fuel")
                    .apply {
                        if (amountValue == null) remove(KEY_AMOUNT) else putString(KEY_AMOUNT, amountValue.toString())
                        if (quantityValue == null) remove(KEY_QUANTITY) else putString(KEY_QUANTITY, quantityValue.toString())
                        if (electric) remove(KEY_FUEL_TYPE) else putString(KEY_FUEL_TYPE, fuel.selectedItem?.toString())
                    }
                    .putBoolean(KEY_ARMED, false)
                    .apply()
                dialog.dismiss()
                onChanged()
            }
        }
        dialog.show()
    }


    fun openEndOdometer(context: Context, onEnd: () -> Unit) {
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        if (journeyId.isBlank()) {
            onEnd()
            return
        }
        val store = JourneyMetricsStore026.get(context)
        val metric = store.metric(journeyId)
        val input = decimalInput(context, "Odômetro final (km)")
        metric?.odometerEndKm?.let { input.setText(it.toString()) }
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 4), SrUi023.dp(context, 18), 0)
            addView(
                SrUi023.body(
                    context,
                    metric?.odometerStartKm?.let { "Odômetro inicial: ${String.format(Locale("pt", "BR"), "%.1f km", it)}" }
                        ?: "Odômetro inicial não informado. Você poderá completar depois em Estatísticas.",
                    10.5f,
                ),
            )
            addView(UiKit.margin(input, top = 8))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Encerrar jornada")
            .setMessage("Informe o odômetro final para calcular a distância real ou encerre sem informar.")
            .setView(holder)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Encerrar sem km", null)
            .setPositiveButton("Salvar e encerrar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                onEnd()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val end = input.text?.toString()?.trim().orEmpty().replace(',', '.').toDoubleOrNull()
                if (end == null || JourneyMetricsRules026.normalizedOdometer(end) == null) {
                    toast(context, "Odômetro final inválido.")
                    return@setOnClickListener
                }
                if (metric?.odometerStartKm != null && end < metric.odometerStartKm) {
                    toast(context, "O odômetro final não pode ser menor que o inicial.")
                    return@setOnClickListener
                }
                if (store.saveOdometer(journeyId, endKm = end) == null) {
                    toast(context, "Não foi possível salvar o odômetro final.")
                    return@setOnClickListener
                }
                JourneyMetricsClient026.syncPending(context)
                dialog.dismiss()
                onEnd()
            }
        }
        dialog.show()
    }

    fun armForNextJourney(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasDraft = p.contains(KEY_START_KM) || (p.getString(KEY_ENERGY_MODE, "none") ?: "none") != "none"
        p.edit().putBoolean(KEY_ARMED, hasDraft).apply()
    }

    fun summary(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = mutableListOf<String>()
        p.getString(KEY_START_KM, null)?.toDoubleOrNull()?.let { items += String.format(Locale("pt", "BR"), "%.1f km", it) }
        when (p.getString(KEY_ENERGY_MODE, "none")) {
            "fuel" -> items += "abastecimento preparado"
            "electric" -> items += "recarga preparada"
        }
        return if (items.isEmpty()) "Opcional antes de iniciar" else items.joinToString(" · ")
    }

    private fun decimalInput(context: Context, hint: String) = EditText(context).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true)
        setTextColor(SrUi023.palette(context).ink)
        setHintTextColor(SrUi023.palette(context).muted)
        background = SrUi023.rounded(SrUi023.palette(context).surface, 12, SrUi023.palette(context).outline, 1, context)
        setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 11), SrUi023.dp(context, 12), SrUi023.dp(context, 11))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
