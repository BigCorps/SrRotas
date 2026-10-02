package com.srrotas.app

import android.content.Context

/**
 * Rollout local seguro. Defaults FALSE.
 *
 * Fases sugeridas:
 * 1. banco/backend publicados e homologados (sem flag Android);
 * 2. uiEnabled=true (Radar manual);
 * 3. runtimeEnabled=true (refresh em corrida);
 * 4. assistantEnabled=true (balão proativo).
 *
 * O integrador pode substituir a origem por feature flags remotas no futuro,
 * mantendo estes quatro conceitos.
 */
object RadarContextualFlagsV1 {
    private const val PREFS="sr_radar_contextual_flags_v1"
    private const val UI="ui_enabled"
    private const val RUNTIME="runtime_enabled"
    private const val ASSISTANT="assistant_enabled"

    fun uiEnabled(context:Context)=prefs(context).getBoolean(UI,false)
    fun runtimeEnabled(context:Context)=prefs(context).getBoolean(RUNTIME,false)
    fun assistantEnabled(context:Context)=prefs(context).getBoolean(ASSISTANT,false)

    internal fun setUiEnabled(context:Context,value:Boolean)=prefs(context).edit().putBoolean(UI,value).apply()
    internal fun setRuntimeEnabled(context:Context,value:Boolean)=prefs(context).edit().putBoolean(RUNTIME,value).apply()
    internal fun setAssistantEnabled(context:Context,value:Boolean)=prefs(context).edit().putBoolean(ASSISTANT,value).apply()

    fun disableAll(context:Context)=prefs(context).edit()
        .putBoolean(UI,false).putBoolean(RUNTIME,false).putBoolean(ASSISTANT,false).apply()

    private fun prefs(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
}
