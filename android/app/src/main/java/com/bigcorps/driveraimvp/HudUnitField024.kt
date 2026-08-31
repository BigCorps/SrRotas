package com.srrotas.app

import android.content.Context
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Campo numérico com unidade fora do texto digitável.
 * A unidade continua visível durante toda a edição.
 */
class HudUnitField024(
    context: Context,
    label: String,
    private val prefix: String = "",
    private val suffix: String = "",
    integer: Boolean = false,
) : LinearLayout(context) {
    private val input = EditText(context)

    init {
        orientation = VERTICAL

        addView(
            UiKit.body(context, label, 10f).apply {
                setTextColor(UiKit.palette(context).muted)
            },
        )

        val field = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(
                context,
                UiKit.palette(context).surfaceAlt,
                12,
                UiKit.palette(context).line,
                1,
            )
            setPadding(
                UiKit.dp(context, 9),
                UiKit.dp(context, 2),
                UiKit.dp(context, 9),
                UiKit.dp(context, 2),
            )
        }

        if (prefix.isNotBlank()) {
            field.addView(
                unitText(prefix),
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }

        input.apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(UiKit.palette(context).ink)
            setHintTextColor(UiKit.palette(context).muted)
            background = null
            gravity = Gravity.CENTER_VERTICAL
            inputType =
                if (integer) {
                    InputType.TYPE_CLASS_NUMBER
                } else {
                    InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
            setPadding(
                UiKit.dp(context, 5),
                UiKit.dp(context, 9),
                UiKit.dp(context, 5),
                UiKit.dp(context, 9),
            )
        }
        field.addView(
            input,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )

        if (suffix.isNotBlank()) {
            field.addView(
                unitText(suffix),
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }

        addView(
            field,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 4)
            },
        )
    }

    private fun unitText(value: String): TextView =
        UiKit.body(context, value, 11f).apply {
            gravity = Gravity.CENTER
            setTextColor(UiKit.palette(context).muted)
        }

    fun setValue(value: Double, decimals: Int = 2) {
        input.setText(
            HudConfigRules024.format(value, decimals)
                .trimEnd('0')
                .trimEnd(','),
        )
        input.setSelection(input.text.length)
    }

    fun setIntegerValue(value: Int) {
        input.setText(value.toString())
        input.setSelection(input.text.length)
    }

    fun valueOrNull(): Double? =
        HudConfigRules024.parseDecimal(input.text.toString())

    fun intValueOrNull(): Int? =
        input.text.toString().trim().toIntOrNull()

    fun addWatcher(watcher: TextWatcher) {
        input.addTextChangedListener(watcher)
    }

    fun setEditable(value: Boolean) {
        input.isEnabled = value
        input.alpha = if (value) 1f else .58f
    }

    fun requestInputFocus() {
        input.requestFocus()
    }
}
