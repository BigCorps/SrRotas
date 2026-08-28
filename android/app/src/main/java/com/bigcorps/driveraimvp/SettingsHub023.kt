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

/** Configurações locais do aparelho; conta/assinatura permanecem na Web. */
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
    private val statusHost = LinearLayout(context)
    private val grid = GridLayout(context)

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(
            SrAppHeader023(context, "Configurações", "Ajustes do aparelho, jornada, HUD e aparência."),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        root.addView(statusHost, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
        grid.columnCount = SrUi023.preferredColumns(context)
        grid.setPadding(0, SrUi023.dp(context, 8), 0, SrUi023.dp(context, 28))
        root.addView(grid, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        refresh()
    }

    fun refresh() {
        renderStatus()
        renderGrid()
    }

    private fun renderStatus() {
        statusHost.removeAllViews()
        val repo = SettingsRepository(context)
        val s = repo.load()
        val active = repo.currentJourneyId().isNotBlank()
        val overlayOk = Settings.canDrawOverlays(context)
        val locationOk = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val captureOk = !active || repo.isProjectionActive()
        val pending = listOf(overlayOk, locationOk, captureOk, s.ocrEnabled, s.onboardingCompleted).count { !it }

        statusHost.addView(SrUi023.card(context, 14, 18).apply {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(SrUi023.iconBox(context, R.drawable.sr23_ic_shield_check, if (pending == 0) SrUi023.palette(context).teal else SrUi023.palette(context).orange, 52))
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(SrUi023.dp(context, 11), 0, SrUi023.dp(context, 6), 0)
                addView(SrUi023.title(context, if (pending == 0) "Aplicativo pronto" else "Aplicativo quase pronto", 16f))
                addView(SrUi023.body(context, "${if (active) "Jornada ativa" else "Jornada parada"} · ${if (pending == 0) "sem pendências" else "$pending pendência(s)"}", 10.5f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(SrUi023.pill(context, if (pending == 0) "OK" else "Revisar", if (pending == 0) "good" else "warn"))
            addView(row)
            addView(themeSelector(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = SrUi023.dp(context, 12) })
        })
    }

    private fun themeSelector(): View {
        val active = Strategy021Store.load(context).appTheme
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = SrUi023.rounded(SrUi023.palette(context).surfaceMuted, 13, SrUi023.palette(context).outline, 1, context)
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
        grid.columnCount = SrUi023.preferredColumns(context)
        val p = SrUi023.palette(context)
        val sync = SyncCoordinator.pending(context)
        val tiles = listOf(
            Tile("Jornada e permissões", "Status, acessos e última jornada", R.drawable.sr23_ic_route, p.teal, null, actions.journey),
            Tile("Estratégia e HUD", "Metas, métricas e aparência do HUD", R.drawable.sr23_ic_sliders, p.blue, null, actions.strategy),
            Tile("Aparência", "Tema Claro, Escuro ou Automático", R.drawable.sr23_ic_sun_moon, p.orange, null, actions.appearance),
            Tile("Notificações", "Alertas, resumos e avisos", R.drawable.sr23_ic_bell, p.red, null, actions.notifications),
            Tile("Dados e sincronização", "Backup e sincronização", R.drawable.sr23_ic_cloud_sync, p.teal, if (sync.total == 0) "Sincronizado" else "${sync.total} pendente(s)", actions.sync),
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
