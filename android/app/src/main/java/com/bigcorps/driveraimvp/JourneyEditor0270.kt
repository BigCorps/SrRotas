package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Editor de jornada 0.27.
 *
 * Usa um formulário de contraste explícito para não herdar texto preto sobre
 * superfícies escuras. Persiste na mesma JourneyMetricsStore já existente.
 */
object JourneyEditor0270 {
    fun open(
        context: Context,
        journeyId: String,
        onSaved: () -> Unit = {},
    ) {
        val snapshot = JourneyMetricsStore026.get(context).snapshot(journeyId)
        val labels =
            buildList {
                add("Quilometragem inicial e final")
                add("Adicionar combustível")
                add("Adicionar recarga")
                snapshot.energyEntries.forEachIndexed { index, entry ->
                    add(
                        "Editar lançamento ${index + 1} · ${energyLabel(entry)}",
                    )
                }
            }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Dados da jornada")
            .setItems(labels) { _, which ->
                when {
                    which == 0 ->
                        openOdometer(context, journeyId, onSaved)
                    which == 1 ->
                        openEnergy(
                            context,
                            journeyId,
                            JourneyMetricsRules026.KIND_FUEL,
                            null,
                            onSaved,
                        )
                    which == 2 ->
                        openEnergy(
                            context,
                            journeyId,
                            JourneyMetricsRules026.KIND_ELECTRIC,
                            null,
                            onSaved,
                        )
                    else ->
                        snapshot.energyEntries
                            .getOrNull(which - 3)
                            ?.let { entry ->
                                openEnergy(
                                    context,
                                    journeyId,
                                    entry.kind,
                                    entry,
                                    onSaved,
                                )
                            }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun openOdometer(
        context: Context,
        journeyId: String,
        onSaved: () -> Unit,
    ) {
        val store = JourneyMetricsStore026.get(context)
        val metric = store.metric(journeyId)
        val start = decimalInput(context, "Km inicial", metric?.odometerStartKm)
        val end = decimalInput(context, "Km final", metric?.odometerEndKm)

        val form =
            form(context).apply {
                addView(label(context, "Quilometragem inicial"))
                addView(start)
                addView(label(context, "Quilometragem final"))
                addView(end)
            }

        val dialog =
            AlertDialog.Builder(context)
                .setTitle("Quilometragem da jornada")
                .setView(form)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val startKm =
                        JourneyFlowRules026.decimalFlexible(
                            start.text?.toString(),
                        )
                    val endKm =
                        JourneyFlowRules026.decimalFlexible(
                            end.text?.toString(),
                        )

                    if (startKm == null && endKm == null) {
                        toast(
                            context,
                            "Informe pelo menos uma quilometragem.",
                        )
                        return@setOnClickListener
                    }

                    if (!JourneyFlowRules026.validEnd(startKm, endKm)) {
                        toast(
                            context,
                            "O km final não pode ser menor que o km inicial.",
                        )
                        return@setOnClickListener
                    }

                    if (
                        store.saveOdometer(
                            journeyId,
                            startKm,
                            endKm,
                        ) == null
                    ) {
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

    private fun openEnergy(
        context: Context,
        journeyId: String,
        kind: String,
        existing: JourneyMetricsStore026.EnergyEntry?,
        onSaved: () -> Unit,
    ) {
        val electric =
            kind == JourneyMetricsRules026.KIND_ELECTRIC
        val amount =
            decimalInput(
                context,
                "Valor pago (R$) · opcional",
                existing?.amountPaid,
            )
        val quantity =
            decimalInput(
                context,
                if (electric) {
                    "Energia (kWh) · opcional"
                } else {
                    "Quantidade (litros) · opcional"
                },
                existing?.quantity,
            )
        val fuelType =
            if (!electric) {
                textInput(
                    context,
                    "Combustível: Gasolina, Etanol, Diesel, GNV ou Outro",
                    existing?.fuelType,
                )
            } else {
                null
            }

        val form =
            form(context).apply {
                addView(label(context, "Valor pago"))
                addView(amount)
                addView(
                    label(
                        context,
                        if (electric) "Energia" else "Quantidade",
                    ),
                )
                addView(quantity)
                if (fuelType != null) {
                    addView(label(context, "Tipo de combustível"))
                    addView(fuelType)
                }
            }

        val dialog =
            AlertDialog.Builder(context)
                .setTitle(if (electric) "Recarga" else "Abastecimento")
                .setMessage(
                    "Você pode informar valor, quantidade ou ambos.",
                )
                .setView(form)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val amountValue =
                        JourneyFlowRules026.decimalFlexible(
                            amount.text?.toString(),
                        )
                    val quantityValue =
                        JourneyFlowRules026.decimalFlexible(
                            quantity.text?.toString(),
                        )
                    val unit =
                        JourneyFlowRules026.unitFor(kind)

                    if (
                        !JourneyMetricsRules026.validEnergyEntry(
                            kind,
                            amountValue,
                            quantityValue,
                            unit,
                        )
                    ) {
                        toast(
                            context,
                            "Informe um valor pago ou uma quantidade válida.",
                        )
                        return@setOnClickListener
                    }

                    val saved =
                        JourneyMetricsStore026.get(context)
                            .addEnergy(
                                journeyId = journeyId,
                                kind = kind,
                                amountPaid = amountValue,
                                quantity = quantityValue,
                                unit = unit,
                                fuelType =
                                    if (electric) {
                                        null
                                    } else {
                                        fuelType
                                            ?.text
                                            ?.toString()
                                            ?.trim()
                                            ?.takeIf(String::isNotBlank)
                                    },
                                recordedAt =
                                    existing?.recordedAt
                                        ?: java.time.Instant.now().toString(),
                                id =
                                    existing?.id
                                        ?: java.util.UUID.randomUUID().toString(),
                            )

                    if (saved == null) {
                        toast(
                            context,
                            "Não foi possível salvar o lançamento.",
                        )
                        return@setOnClickListener
                    }

                    JourneyMetricsClient026.syncPending(context)
                    dialog.dismiss()
                    onSaved()
                }
        }
        dialog.show()
    }

    private fun form(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, 20),
                dp(context, 8),
                dp(context, 20),
                dp(context, 8),
            )
        }

    private fun label(
        context: Context,
        value: String,
    ): TextView =
        TextView(context).apply {
            text = value
            textSize = 11f
            setTextColor(if (Appearance021.isDark(context)) Color.WHITE else Color.DKGRAY)
            setPadding(0, dp(context, 9), 0, dp(context, 3))
        }

    private fun decimalInput(
        context: Context,
        hintValue: String,
        value: Double?,
    ): EditText =
        EditText(context).apply {
            hint = hintValue
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            applyInputContrast(context)
            value?.let {
                setText(
                    String.format(
                        java.util.Locale.US,
                        "%.1f",
                        it,
                    ).removeSuffix(".0"),
                )
            }
        }

    private fun textInput(
        context: Context,
        hintValue: String,
        value: String?,
    ): EditText =
        EditText(context).apply {
            hint = hintValue
            setSingleLine(true)
            applyInputContrast(context)
            value?.let(::setText)
        }

    private fun EditText.applyInputContrast(context: Context) {
        val dark = Appearance021.isDark(context)
        val backgroundColor =
            if (dark) 0xFF173F49.toInt() else Color.WHITE
        val foreground =
            if (dark) Color.WHITE else 0xFF122D35.toInt()
        val hintColor =
            if (dark) 0xFFB8CBD0.toInt() else 0xFF6B7C80.toInt()

        setTextColor(foreground)
        setHintTextColor(hintColor)
        background =
            SrUi023.rounded(
                backgroundColor,
                11,
                if (dark) 0xFF5F8B94.toInt() else 0xFFB9C7CA.toInt(),
                1,
                context,
            )
        setPadding(
            dp(context, 12),
            dp(context, 10),
            dp(context, 12),
            dp(context, 10),
        )
    }

    private fun energyLabel(
        entry: JourneyMetricsStore026.EnergyEntry,
    ): String =
        buildString {
            append(
                if (
                    entry.kind ==
                    JourneyMetricsRules026.KIND_ELECTRIC
                ) {
                    "Recarga"
                } else {
                    "Combustível"
                },
            )
            entry.amountPaid?.let {
                append(
                    String.format(
                        java.util.Locale("pt", "BR"),
                        " · R$ %.2f",
                        it,
                    ),
                )
            }
        }

    private fun toast(
        context: Context,
        text: String,
    ) =
        Toast.makeText(
            context,
            text,
            Toast.LENGTH_SHORT,
        ).show()

    private fun dp(context: Context, value: Int): Int =
        SrUi023.dp(context, value)
}
