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

    private const val FIELD93_DONE = "field93_migration_done"
    private const val FIELD93_APPLIED = "field_r4_migration_applied"

    internal data class Field93State(val done:Boolean, val ui:Boolean, val runtime:Boolean, val assistant:Boolean)
    internal fun migrateField93(state:Field93State, field:Boolean, version:Int):Field93State =
        if(!field || version<93 || state.done) state
        else state.copy(done=true, assistant=state.assistant || (state.ui && state.runtime))

    @Synchronized
    fun migrateField93IfNeeded(context:Context) {
        val p=prefs(context)
        val before=Field93State(p.getBoolean(FIELD93_DONE,false),uiEnabled(context),runtimeEnabled(context),assistantEnabled(context))
        val after=migrateField93(before,fieldControlsVisible(),BuildConfig.VERSION_CODE)
        if(after==before) return
        val applied=!before.assistant && after.assistant
        p.edit().putBoolean(FIELD93_DONE,true).putBoolean(ASSISTANT,after.assistant)
            .putBoolean(FIELD93_APPLIED,applied).apply()
        if(applied) RadarContextualDiagnosticV1.fieldR4MigrationApplied()
    }

    fun fieldR4MigrationApplied(context:Context):Boolean = prefs(context).getBoolean(FIELD93_APPLIED,false)

    // Uma escolha explícita (inclusive Rollback antes do primeiro sync) consome a migração.
    private fun manualStageChosen(context:Context) {
        if(fieldControlsVisible() && BuildConfig.VERSION_CODE>=93)
            prefs(context).edit().putBoolean(FIELD93_DONE,true).apply()
    }

    fun uiEnabled(context: Context) = prefs(context).getBoolean(UI, false)
    fun runtimeEnabled(context: Context) = prefs(context).getBoolean(RUNTIME, false)
    fun assistantEnabled(context: Context) = prefs(context).getBoolean(ASSISTANT, false)

    internal fun setUiEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(UI, value).apply()

    internal fun setRuntimeEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(RUNTIME, value).apply()

    internal fun setAssistantEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(ASSISTANT, value).apply()

    @Synchronized
    fun enableUiOnly(context: Context) {
        manualStageChosen(context)
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, false)
            .putBoolean(ASSISTANT, false)
            .apply()
    }

    @Synchronized
    fun enableRuntime(context: Context) {
        manualStageChosen(context)
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, true)
            .putBoolean(ASSISTANT, false)
            .apply()
    }

    @Synchronized
    fun enableAssistant(context: Context) {
        manualStageChosen(context)
        prefs(context).edit()
            .putBoolean(UI, true)
            .putBoolean(RUNTIME, true)
            .putBoolean(ASSISTANT, true)
            .apply()
    }

    @Synchronized
    fun disableAll(context: Context) {
        manualStageChosen(context)
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
