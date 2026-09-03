package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Fluxo 0.26 para hodômetro/energia sem tocar no ciclo validado de MediaProjection.
 *
 * - Antes da jornada: guarda um rascunho local e o associa ao próximo journey_id.
 * - Durante a jornada: não mantém formulário aberto/visível.
 * - Encerramento: oferece km final, mas nunca impede encerrar sem preencher.
 * - Estatísticas: permite completar/corrigir km e lançamentos depois.
 */
object JourneyFlow026 {
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

    data class Draft(
        val startKm: Double?,
        val energyMode: String,
        val amountPaid: Double?,
        val quantity: Double?,
        val fuelType: String?,
        val savedAtMs: Long,
    )

    fun journeyControl(
        context: Context,
        label: String,
        iconRes: Int?,
        onClick: () -> Unit,
    ): View? = when (label.trim().lowercase()) {
        "iniciar jornada" -> startControl(context, iconRes, onClick)
        "encerrar jornada" -> endControl(context, iconRes, onClick)
        else -> null
    }

    fun editorButton(
        context: Context,
        journeyId: String,
        onSaved: () -> Unit,
    ): View = secondaryButton(context, "Completar / corrigir dados") {
        openJourneyEditor(context, journeyId, onSaved)
    }

    private fun startControl(
        context: Context,
        iconRes: Int?,
        onClick: () -> Unit,
    ): View {
        // Se houve um rascunho armado e o app foi recriado depois da autorização,
        // tenta associá-lo imediatamente à jornada corrente.
        applyDraftToCurrentJourneyIfPossible(context)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                primaryButton(context, "Iniciar jornada", iconRes) {
                    openStartEditor(context, startAfterSave = true, onStart = onClick)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                secondaryButton(context, startShortcutLabel(context)) {
                    openStartEditor(context, startAfterSave = false, onStart = onClick)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 7) },
            )
        }
    }

    private fun endControl(
        context: Context,
        iconRes: Int?,
        onClick: () -> Unit,
    ): View {
        applyDraftToCurrentJourneyIfPossible(context)
        ActiveAssistant026.ensureRunning(context)
        return primaryButton(context, "Encerrar jornada", iconRes) {
            openEndEditor(context, onEnd = onClick)
        }
    }

    private fun startShortcutLabel(context: Context): String {
        val draft = loadDraft(context)
        return if (draft == null) {
            "Km / abastecimento ou recarga"
        } else {
            buildString {
                append("Dados preparados")
                draft.startKm?.let { append(" · ${formatKm(it)}") }
                when (draft.energyMode) {
                    MODE_FUEL -> append(" · combustível")
                    MODE_ELECTRIC -> append(" · recarga")
                }
            }
        }
    }

    private fun openStartEditor(
        context: Context,
        startAfterSave: Boolean,
        onStart: () -> Unit,
    ) {
        val draft = loadDraft(context)
        val form = StartForm(context, draft)
        val dialog = AlertDialog.Builder(context)
            .setTitle("Antes de iniciar")
            .setMessage("Informe o hodômetro para medir somente os quilômetros desta jornada. Abastecimento ou recarga é opcional.")
            .setView(form.root)
            .setPositiveButton(if (startAfterSave) "Salvar e iniciar" else "Salvar", null)
            .setNegativeButton("Cancelar", null)
            .apply {
                if (startAfterSave) setNeutralButton("Iniciar sem preencher", null)
            }
            .create()

        dialog.setOnShowListener {
            styleDialog(context, dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = form.read()
                result.onFailure { error -> toast(context, error.message ?: "Confira os dados.") }
                result.onSuccess { saved ->
                    saveDraft(context, saved, armed = startAfterSave)
                    dialog.dismiss()
                    if (startAfterSave) {
                        armAndWatch(context)
                        ActiveAssistant026.arm(context)
                        onStart()
                    } else {
                        toast(context, "Dados preparados para a próxima jornada.")
                    }
                }
            }
            if (startAfterSave) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    clearDraft(context)
                    dialog.dismiss()
                    ActiveAssistant026.arm(context)
                    onStart()
                }
            }
        }
        dialog.show()
    }

    private fun openEndEditor(context: Context, onEnd: () -> Unit) {
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        if (journeyId.isBlank()) {
            ActiveAssistant026.stop(context)
            onEnd()
            return
        }
        val store = JourneyMetricsStore026.get(context)
        val metric = store.metric(journeyId)
        val endInput = decimalInput(context, "Quilometragem final (km)", metric?.odometerEndKm)
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 6), SrUi023.dp(context, 18), 0)
            addView(
                SrUi023.body(
                    context,
                    metric?.odometerStartKm?.let { "Km inicial: ${formatKm(it)}" }
                        ?: "Km inicial ainda não informado. Você poderá completar depois em Estatísticas.",
                    11f,
                ),
            )
            addView(endInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 10) })
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Encerrar jornada")
            .setMessage("Informe o hodômetro final para calcular a distância rodada nesta jornada.")
            .setView(holder)
            .setPositiveButton("Salvar e encerrar", null)
            .setNeutralButton("Encerrar sem informar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            styleDialog(context, dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val end = JourneyFlowRules026.decimalFlexible(endInput.text?.toString())
                if (end == null) {
                    toast(context, "Informe o km final ou use ‘Encerrar sem informar’.")
                    return@setOnClickListener
                }
                val normalized = JourneyMetricsRules026.normalizedOdometer(end)
                if (normalized == null) {
                    toast(context, "Quilometragem final inválida.")
                    return@setOnClickListener
                }
                if (!JourneyFlowRules026.validEnd(metric?.odometerStartKm, normalized)) {
                    toast(context, "O km final não pode ser menor que o km inicial.")
                    return@setOnClickListener
                }
                if (store.saveOdometer(journeyId, endKm = normalized) == null) {
                    toast(context, "Não foi possível salvar a quilometragem.")
                    return@setOnClickListener
                }
                JourneyMetricsClient026.syncPending(context)
                dialog.dismiss()
                ActiveAssistant026.stop(context)
                onEnd()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                ActiveAssistant026.stop(context)
                onEnd()
            }
        }
        dialog.show()
    }

    fun openJourneyEditor(
        context: Context,
        journeyId: String,
        onSaved: () -> Unit = {},
    ) {
        val snapshot = JourneyMetricsStore026.get(context).snapshot(journeyId)
        val labels = buildList {
            add("Quilometragem inicial e final")
            add("Adicionar combustível")
            add("Adicionar recarga")
            snapshot.energyEntries.forEachIndexed { index, entry ->
                add("Editar lançamento ${index + 1} · ${energyLabel(entry)}")
            }
        }.toTypedArray()

        val dialog = AlertDialog.Builder(context)
            .setTitle("Dados da jornada")
            .setItems(labels) { _, which ->
                when {
                    which == 0 -> openOdometerEditor(context, journeyId, onSaved)
                    which == 1 -> openEnergyEditor(context, journeyId, MODE_FUEL, null, onSaved)
                    which == 2 -> openEnergyEditor(context, journeyId, MODE_ELECTRIC, null, onSaved)
                    else -> snapshot.energyEntries.getOrNull(which - 3)?.let { entry ->
                        openEnergyEditor(context, journeyId, entry.kind, entry, onSaved)
                    }
                }
            }
            .setNegativeButton("Fechar", null)
            .create()
        dialog.setOnShowListener { styleDialog(context, dialog) }
        dialog.show()
    }

    private fun openOdometerEditor(context: Context, journeyId: String, onSaved: () -> Unit) {
        val store = JourneyMetricsStore026.get(context)
        val metric = store.metric(journeyId)
        val start = decimalInput(context, "Km inicial", metric?.odometerStartKm)
        val end = decimalInput(context, "Km final", metric?.odometerEndKm)
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 6), SrUi023.dp(context, 18), 0)
            addView(start)
            addView(end, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 8) })
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Quilometragem da jornada")
            .setView(form)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            styleDialog(context, dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val startKm = JourneyFlowRules026.decimalFlexible(start.text?.toString())
                val endKm = JourneyFlowRules026.decimalFlexible(end.text?.toString())
                if (startKm == null && endKm == null) {
                    toast(context, "Informe pelo menos uma quilometragem.")
                    return@setOnClickListener
                }
                if (!JourneyFlowRules026.validEnd(startKm, endKm)) {
                    toast(context, "O km final não pode ser menor que o km inicial.")
                    return@setOnClickListener
                }
                if (store.saveOdometer(journeyId, startKm, endKm) == null) {
                    toast(context, "Quilometragem inválida.")
                    return@setOnClickListener
                }
                JourneyMetricsClient026.syncPending(context)
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.show()
    }

    private fun openEnergyEditor(
        context: Context,
        journeyId: String,
        requestedMode: String,
        existing: JourneyMetricsStore026.EnergyEntry?,
        onSaved: () -> Unit,
    ) {
        val mode = if (requestedMode == JourneyMetricsRules026.KIND_ELECTRIC) MODE_ELECTRIC else MODE_FUEL
        val amount = decimalInput(context, "Valor pago (R$) · opcional", existing?.amountPaid)
        val quantity = decimalInput(
            context,
            if (mode == MODE_ELECTRIC) "Energia (kWh) · opcional" else "Quantidade (litros) · opcional",
            existing?.quantity,
        )
        val fuelTypes = listOf("Gasolina", "Etanol", "Diesel", "GNV", "Outro")
        val fuel = styledSpinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fuelTypes)
            existing?.fuelType?.let { old ->
                val idx = fuelTypes.indexOfFirst { it.equals(old, ignoreCase = true) }
                if (idx >= 0) setSelection(idx)
            }
        }
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 6), SrUi023.dp(context, 18), 0)
            addView(amount)
            addView(quantity, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = SrUi023.dp(context, 8) })
            if (mode == MODE_FUEL) {
                addView(SrUi023.body(context, "Combustível", 10f), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 8) })
                addView(fuel)
            }
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (mode == MODE_ELECTRIC) "Recarga" else "Abastecimento")
            .setMessage("Você pode informar valor, quantidade ou ambos.")
            .setView(form)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            styleDialog(context, dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amountValue = JourneyFlowRules026.decimalFlexible(amount.text?.toString())
                val quantityValue = JourneyFlowRules026.decimalFlexible(quantity.text?.toString())
                val kind = JourneyFlowRules026.energyKind(mode)!!
                val unit = JourneyFlowRules026.unitFor(kind)
                if (!JourneyMetricsRules026.validEnergyEntry(kind, amountValue, quantityValue, unit)) {
                    toast(context, "Informe um valor pago ou uma quantidade válida.")
                    return@setOnClickListener
                }
                val saved = JourneyMetricsStore026.get(context).addEnergy(
                    journeyId = journeyId,
                    kind = kind,
                    amountPaid = amountValue,
                    quantity = quantityValue,
                    unit = unit,
                    fuelType = if (kind == JourneyMetricsRules026.KIND_FUEL) fuel.selectedItem?.toString() else null,
                    recordedAt = existing?.recordedAt ?: java.time.Instant.now().toString(),
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                )
                if (saved == null) {
                    toast(context, "Não foi possível salvar o lançamento.")
                    return@setOnClickListener
                }
                JourneyMetricsClient026.syncPending(context)
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.show()
    }

    private fun applyDraftToCurrentJourneyIfPossible(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ARMED, false)) return false
        val draft = loadDraft(context) ?: return false
        val journeyId = SettingsRepository(context).currentJourneyId().trim()
        if (journeyId.isBlank()) return false
        val store = JourneyMetricsStore026.get(context)
        draft.startKm?.let { store.saveOdometer(journeyId, startKm = it) }
        val kind = JourneyFlowRules026.energyKind(draft.energyMode)
        if (kind != null && JourneyMetricsRules026.validEnergyEntry(
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
        clearDraft(context)
        JourneyMetricsClient026.syncPending(context)
        return true
    }

    private fun armAndWatch(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ARMED, true).apply()
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val startedAt = System.currentTimeMillis()
        val watcher = object : Runnable {
            override fun run() {
                if (applyDraftToCurrentJourneyIfPossible(app)) return
                if (System.currentTimeMillis() - startedAt < 120_000L) handler.postDelayed(this, 750L)
            }
        }
        handler.postDelayed(watcher, 500L)
    }

    private fun loadDraft(context: Context): Draft? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = p.getLong(KEY_SAVED_AT, 0L)
        if (!JourneyFlowRules026.draftIsFresh(savedAt, System.currentTimeMillis())) {
            clearDraft(context)
            return null
        }
        return Draft(
            startKm = p.getString(KEY_START_KM, null)?.toDoubleOrNull(),
            energyMode = p.getString(KEY_ENERGY_MODE, MODE_NONE) ?: MODE_NONE,
            amountPaid = p.getString(KEY_AMOUNT, null)?.toDoubleOrNull(),
            quantity = p.getString(KEY_QUANTITY, null)?.toDoubleOrNull(),
            fuelType = p.getString(KEY_FUEL_TYPE, null),
            savedAtMs = savedAt,
        )
    }

    private fun saveDraft(context: Context, draft: Draft, armed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .putNullableString(KEY_START_KM, draft.startKm)
            .putString(KEY_ENERGY_MODE, draft.energyMode)
            .putNullableString(KEY_AMOUNT, draft.amountPaid)
            .putNullableString(KEY_QUANTITY, draft.quantity)
            .putString(KEY_FUEL_TYPE, draft.fuelType)
            .putBoolean(KEY_ARMED, armed)
            .apply()
    }

    private fun clearDraft(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun primaryButton(context: Context, label: String, iconRes: Int?, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 48)
            setPadding(SrUi023.dp(context, 14), SrUi023.dp(context, 11), SrUi023.dp(context, 14), SrUi023.dp(context, 11))
            setTextColor(Color.WHITE)
            background = SrUi023.rounded(SrUi023.palette(context).blue, 14, SrUi023.palette(context).blue, 1, context)
            iconRes?.let {
                setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                compoundDrawablePadding = SrUi023.dp(context, 8)
            }
            setOnClickListener { action() }
        }

    private fun secondaryButton(context: Context, label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = SrUi023.dp(context, 42)
            setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 9), SrUi023.dp(context, 12), SrUi023.dp(context, 9))
            setTextColor(SrUi023.palette(context).blue)
            background = SrUi023.rounded(
                SrUi023.palette(context).surfaceMuted,
                13,
                SrUi023.palette(context).blue,
                1,
                context,
            )
            setOnClickListener { action() }
        }

    private fun decimalInput(context: Context, hint: String, value: Double?): EditText =
        EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(SrUi023.palette(context).ink)
            setHintTextColor(SrUi023.palette(context).muted)
            setSingleLine(true)
            value?.let { setText(formatPlain(it)) }
            background = SrUi023.rounded(
                SrUi023.palette(context).surface,
                12,
                SrUi023.palette(context).outline,
                1,
                context,
            )
            setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 11), SrUi023.dp(context, 12), SrUi023.dp(context, 11))
        }

    private fun styledSpinner(context: Context): Spinner =
        Spinner(context).apply {
            minimumHeight = SrUi023.dp(context, 46)
            setPadding(
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 7),
                SrUi023.dp(context, 10),
                SrUi023.dp(context, 7),
            )
            background = SrUi023.rounded(
                SrUi023.palette(context).surface,
                12,
                SrUi023.palette(context).outline,
                1,
                context,
            )
        }

    private fun styleDialog(context: Context, dialog: AlertDialog) {
        val p = SrUi023.palette(context)
        dialog.window?.setBackgroundDrawable(
            SrUi023.rounded(p.surface, 20, p.outline, 1, context),
        )
        listOf(
            AlertDialog.BUTTON_POSITIVE to p.blue,
            AlertDialog.BUTTON_NEGATIVE to p.muted,
            AlertDialog.BUTTON_NEUTRAL to p.purple,
        ).forEach { (which, color) ->
            dialog.getButton(which)?.apply {
                setTextColor(color)
                isAllCaps = false
                setTypeface(typeface, Typeface.BOLD)
                minHeight = SrUi023.dp(context, 44)
            }
        }
    }

    private fun energyLabel(entry: JourneyMetricsStore026.EnergyEntry): String = buildString {
        append(if (entry.kind == JourneyMetricsRules026.KIND_ELECTRIC) "Recarga" else "Combustível")
        entry.amountPaid?.let { append(" · R$ ${format2(it)}") }
        entry.quantity?.let {
            append(if (entry.kind == JourneyMetricsRules026.KIND_ELECTRIC) " · ${format2(it)} kWh" else " · ${format2(it)} L")
        }
    }

    private fun formatKm(value: Double): String = String.format(java.util.Locale("pt", "BR"), "%,.1f km", value)
    private fun format2(value: Double): String = String.format(java.util.Locale("pt", "BR"), "%.2f", value)
    private fun formatPlain(value: Double): String = String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")
    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private class StartForm(context: Context, draft: Draft?) {
        val root: ScrollView
        private val startKm = decimalInput(context, "Quilometragem inicial (km)", draft?.startKm)
        private val mode = styledSpinner(context)
        private val amount = decimalInput(context, "Valor pago (R$) · opcional", draft?.amountPaid)
        private val quantity = decimalInput(context, "Litros / kWh · opcional", draft?.quantity)
        private val fuel = styledSpinner(context)
        private val fuelLabel = SrUi023.body(context, "Tipo de combustível", 10f)

        init {
            val modes = listOf("Nenhum gasto agora", "Combustível", "Recarga elétrica")
            mode.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modes)
            mode.setSelection(when (draft?.energyMode) { MODE_FUEL -> 1; MODE_ELECTRIC -> 2; else -> 0 })
            val fuelTypes = listOf("Gasolina", "Etanol", "Diesel", "GNV", "Outro")
            fuel.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fuelTypes)
            draft?.fuelType?.let { old -> fuelTypes.indexOfFirst { it.equals(old, true) }.takeIf { it >= 0 }?.let { index -> fuel.setSelection(index) } }

            val holder = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(SrUi023.dp(context, 18), SrUi023.dp(context, 6), SrUi023.dp(context, 18), SrUi023.dp(context, 6))
                addView(SrUi023.title(context, "Quilometragem", 12f))
                addView(startKm, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 6) })
                addView(SrUi023.title(context, "Abastecimento / recarga", 12f), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 10) })
                addView(mode)
                addView(amount, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 8) })
                addView(quantity, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 8) })
                addView(fuelLabel, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = SrUi023.dp(context, 8) })
                addView(fuel)
            }
            root = ScrollView(context).apply { addView(holder) }

            fun updateVisibility() {
                val selected = mode.selectedItemPosition
                val hasEnergy = selected != 0
                amount.visibility = if (hasEnergy) View.VISIBLE else View.GONE
                quantity.visibility = if (hasEnergy) View.VISIBLE else View.GONE
                val fuelVisible = selected == 1
                fuel.visibility = if (fuelVisible) View.VISIBLE else View.GONE
                fuelLabel.visibility = if (fuelVisible) View.VISIBLE else View.GONE
                quantity.hint = if (selected == 2) "Energia (kWh) · opcional" else "Quantidade (litros) · opcional"
            }
            mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateVisibility()
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
            updateVisibility()
        }

        fun read(): Result<Draft> = runCatching {
            val start = JourneyFlowRules026.decimalFlexible(startKm.text?.toString())
            if (start != null) require(JourneyMetricsRules026.normalizedOdometer(start) != null) { "Quilometragem inicial inválida." }
            val energyMode = when (mode.selectedItemPosition) { 1 -> MODE_FUEL; 2 -> MODE_ELECTRIC; else -> MODE_NONE }
            val amountValue = if (energyMode == MODE_NONE) null else JourneyFlowRules026.decimalFlexible(amount.text?.toString())
            val quantityValue = if (energyMode == MODE_NONE) null else JourneyFlowRules026.decimalFlexible(quantity.text?.toString())
            val kind = JourneyFlowRules026.energyKind(energyMode)
            if (kind != null) {
                require(JourneyMetricsRules026.validEnergyEntry(kind, amountValue, quantityValue, JourneyFlowRules026.unitFor(kind))) {
                    "Para registrar o gasto, informe valor pago ou quantidade. Se não quiser registrar agora, selecione ‘Nenhum gasto agora’."
                }
            }
            require(start != null || kind != null) { "Informe o km inicial ou um abastecimento/recarga; se preferir, use ‘Iniciar sem preencher’." }
            Draft(
                startKm = JourneyMetricsRules026.normalizedOdometer(start),
                energyMode = energyMode,
                amountPaid = amountValue,
                quantity = quantityValue,
                fuelType = if (energyMode == MODE_FUEL) fuel.selectedItem?.toString() else null,
                savedAtMs = System.currentTimeMillis(),
            )
        }
    }

    private fun android.content.SharedPreferences.Editor.putNullableString(key: String, value: Double?) =
        if (value == null) remove(key) else putString(key, value.toString())
}
