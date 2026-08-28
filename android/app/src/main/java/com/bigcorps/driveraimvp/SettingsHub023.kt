package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Hub visual final das configurações locais do aparelho. */
class SettingsHub023(
    context: Context,
    private val actions: Actions = Actions(),
) : ScrollView(context) {
    data class Actions(
        val journey: () -> Unit = {},
        val strategy: () -> Unit = {},
        val appearance: () -> Unit = {},
        val notifications: () -> Unit = {},
        val sync: () -> Unit = {},
        val privacy: () -> Unit = {},
    )

    private val root = LinearLayout(context)
    private val header = LinearLayout(context)
    private val grid = GridLayout(context)

    init {
        isFillViewport = true
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(SrUi023.palette(context).background)
        addView(root)
        root.addView(header)
        grid.columnCount = 2
        grid.setPadding(SrUi023.dp(context, 12), SrUi023.dp(context, 12), SrUi023.dp(context, 12), SrUi023.dp(context, 28))
        root.addView(grid)
        refresh()
    }

    fun refresh() {
        renderHeader()
        renderGrid()
    }

    private fun renderHeader() {
        header.removeAllViews()
        val repo = SettingsRepository(context)
        val s = repo.load()
        val active = repo.currentJourneyId().isNotBlank()
        val overlayOk = Settings.canDrawOverlays(context)
        val locationOk = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val captureOk = !active || repo.isProjectionActive()
        val pending = listOf(overlayOk, locationOk, captureOk, s.ocrEnabled, s.onboardingCompleted).count { !it }

        header.addView(SrUi023.curvedHeader(context).apply {
            addView(SrUi023.title(context, "Configurações", 30f, true))
            addView(SrUi023.body(context, "Ajuste o Sr. Rotas do seu jeito.", 12f, true))
            val status = SrUi023.card(context, 14, 18).apply {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(SrUi023.iconBox(context, R.drawable.sr23_ic_shield_check, SrUi023.palette(context).teal, 54), LinearLayout.LayoutParams(SrUi023.dp(context, 54), SrUi023.dp(context, 54)))
                row.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(SrUi023.title(context, if (pending == 0) "Aplicativo pronto" else "Aplicativo quase pronto", 16f))
                    addView(SrUi023.body(context, "${if (active) "Jornada ativa" else "Jornada parada"} · ${if (pending == 0) "sem pendências" else "$pending pendência(s)"}", 10.5f))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = SrUi023.dp(context, 11) })
                row.addView(SrUi023.pill(context, if (pending == 0) "OK" else "Revisar", if (pending == 0) "good" else "warn"))
                addView(row)
                addView(themeSelector(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
            }
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 15) })
        })
    }

    private fun themeSelector(): View {
        val active = Strategy021Store.load(context).appTheme
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("auto" to "Automático", "light" to "Claro", "dark" to "Escuro").forEach { (key, label) ->
                addView(SrUi023.segment(context, label, active == key) {
                    Strategy021Store.saveAppTheme(context, key)
                    if (Strategy021Store.load(context).hudThemeMode == "follow_app") {
                        val repo = SettingsRepository(context)
                        repo.save(repo.load().copy(hudTheme = key))
                    }
                    Preference021Sync.sync(context)
                    (context as? Activity)?.recreate()
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
    }

    private fun renderGrid() {
        grid.removeAllViews()
        val p = SrUi023.palette(context)
        val sync = SyncCoordinator.pending(context)
        val tiles = listOf(
            Tile("Jornada e permissões", "Status, acessos e última jornada", R.drawable.sr23_ic_route, p.teal, null, actions.journey),
            Tile("Estratégia e HUD", "Metas, métricas e aparência do HUD", R.drawable.sr23_ic_sliders, p.blue, null, actions.strategy),
            Tile("Aparência", "Tema e preferências visuais", R.drawable.sr23_ic_sun_moon, p.orange, null, actions.appearance),
            Tile("Notificações", "Alertas, resumos e avisos", R.drawable.sr23_ic_bell, p.red, null, actions.notifications),
            Tile("Dados e sincronização", "Backup e sincronização", R.drawable.sr23_ic_cloud_sync, p.teal, if (sync.total == 0) "✓ Sincronizado" else "${sync.total} pendente(s)", actions.sync),
            Tile("Privacidade e suporte", "Privacidade, termos e ajuda", R.drawable.sr23_ic_shield_help, p.navy, null, actions.privacy),
        )
        tiles.forEach { tile ->
            grid.addView(SrUi023.menuTile(context, tile.title, tile.subtitle, tile.icon, tile.tone, tile.badge, tile.action), GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(SrUi023.dp(context, 4), SrUi023.dp(context, 4), SrUi023.dp(context, 4), SrUi023.dp(context, 4))
            })
        }
    }

    private data class Tile(val title: String, val subtitle: String, val icon: Int, val tone: Int, val badge: String?, val action: () -> Unit)
}
