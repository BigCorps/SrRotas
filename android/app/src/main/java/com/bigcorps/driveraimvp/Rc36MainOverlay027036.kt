package com.srrotas.app

/** Compatibilidade para referências históricas. O shell RC3.7 controla estes painéis diretamente. */
@Deprecated("Substituído por ConsolidatedMainActivity027037")
object Rc36MainOverlay027036 {
    fun openSettings(activity: MainActivity) = activity.openSettingsFromPanel()
    fun openUser(activity: MainActivity) = activity.openUserFromHeader()
}
