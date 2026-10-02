package com.srrotas.app

import android.content.Context

/**
 * Rollout local seguro do Radar Contextual.
 *
 * Defaults SEMPRE false. O vc88 Field inclui controles de homologação para
 * avançar manualmente pelas fases sem gerar APKs diferentes:
 * R0 legado -> R2 UI -> R3 runtime -> R4 assistente.
 */
object RadarContextualFlagsV1 {
    private const val PREFS = "sr_radar_contextual_flags_v1"
    private const val UI = "ui_enabled"
    private const val RUNTIME = "runtime_enabled"
    private const val ASSISTANT = "assistant_enabled"

    fun uiEnabled(context: Context) = prefs(context).getBoolean(UI, false)
    fun runtimeEnabled(context: Context) = prefs(context).getBoolean(RUNTIME, false)
    fun assistantEnabled(context: Context) = prefs(context).getBoolean(ASSISTANT, false)

    internal fun setUiEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(UI, value).apply()

    internal fun setRuntimeEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(RUNTIME, value).apply()

    internal fun setAssistantEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(ASSISTANT, value).apply()

    fun enableUiOnly(context: Context) {
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, false)
            .putBoolean(ASSISTANT, false)
            .apply()
    }

    fun enableRuntime(context: Context) {
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, true)
            .putBoolean(ASSISTANT, false)
            .apply()
    }

    fun enableAssistant(context: Context) {
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, true)
            .putBoolean(ASSISTANT, true)
            .apply()
    }

    fun disableAll(context: Context) {
        prefs(context).edit()
            .putBoolean(UI, false)
            .putBoolean(RUNTIME, false)
            .putBoolean(ASSISTANT, false)
            .apply()
    }

    fun stage(context: Context): String = when {
        assistantEnabled(context) -> "R4 · UI + runtime + assistente"
        runtimeEnabled(context) -> "R3 · UI + runtime"
        uiEnabled(context) -> "R2 · UI contextual"
        else -> "R0 · Radar legado"
    }

    /** Controles de homologação nunca devem aparecer numa versão Play final. */
    fun fieldControlsVisible(): Boolean = BuildConfig.VERSION_NAME.contains("-field")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
