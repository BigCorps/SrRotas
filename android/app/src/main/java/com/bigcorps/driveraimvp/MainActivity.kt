package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/**
 * 0.23.0 — shell visual final sem trocar a arquitetura Android Views.
 *
 * Mantém o ciclo validado de jornada/MediaProjection/OCR. Esta Activity apenas
 * reorganiza a navegação para Histórico · IA · Agora · Configurações · Usuário.
 */
class MainActivity : Activity() {
    companion object {
        private const val REQ_MEDIA_PROJECTION = 4101
        private const val REQ_NOTIFICATIONS = 4102
        private const val REQ_LOCATION = 4103

        const val EXTRA_BUBBLE_ACTION = "com.srrotas.app.extra.BUBBLE_ACTION"
        const val BUBBLE_ACTION_START = "start"
        const val BUBBLE_ACTION_HISTORY = "history"
        private const val STATE_SELECTED_ROUTE = "sr_selected_route"
    }

    private lateinit var repo: SettingsRepository
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var content: FrameLayout
    private lateinit var navHost: FrameLayout
    private lateinit var nowPanel: NowPanel023
    private lateinit var historyPanel: HistoryPanel
    private lateinit var settingsPanel: SettingsHub023

    private var selected = SrBottomNav023.Route.NOW
    private var appliedThemeFingerprint = ""
    private val tabs = linkedMapOf<SrBottomNav023.Route, View>()

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            nowPanel.refreshJourneyState()
            settingsPanel.refresh()
            JourneyBubbleController.refreshOffer(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        selected = savedInstanceState
            ?.getString(STATE_SELECTED_ROUTE)
            ?.let { runCatching { SrBottomNav023.Route.valueOf(it) }.getOrNull() }
            ?: SrBottomNav023.Route.NOW
        appliedThemeFingerprint = themeFingerprint()

        UiKit.applySystemBars(this)
        val root = buildUi()
        setContentView(root)
        UiKit.applySafeArea(root)
        navigate(selected)

        if (Strategy021Store.shouldShowWelcome(this)) {
            startActivity(Intent(this, WelcomeCarouselActivity::class.java))
        } else if (!repo.load().onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        } else {
            handleBubbleAction(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBubbleAction(intent)
    }

    override fun onResume() {
        super.onResume()
        if (recreateIfThemeChanged()) return
        registerCaptureReceiver()
        PushManager.ensureIdentity(this)
        Preference021Sync.refresh(this, onDone = themeRefresh@{
            if (recreateIfThemeChanged()) return@themeRefresh
            nowPanel.refreshJourneyState()
            settingsPanel.refresh()
            renderNav()
        })
        nowPanel.refreshJourneyState()
        settingsPanel.refresh()
        if (selected == SrBottomNav023.Route.NOW) nowPanel.refresh()
        if (selected == SrBottomNav023.Route.HISTORY) refreshHistory()

        if (Settings.canDrawOverlays(this)) JourneyBubbleController.show(this)
        MessagePresetClient023.refresh(this)

        SyncCoordinator.sync(this) {
            ReportSelection0211.flush(this)
            settingsPanel.refresh()
            JourneyBubbleController.refresh(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_ROUTE, selected.name)
        super.onSaveInstanceState(outState)
    }

    private fun themeFingerprint(): String =
        "${Strategy021Store.load(this).appTheme}|${Appearance021.isDark(this)}"

    /**
     * Todas as Views da Activity são criadas com a mesma paleta.
     * Se a preferência mudar enquanto a Activity estava pausada ou durante o sync,
     * recriamos a tela inteira em vez de misturar componentes dos dois temas.
     */
    private fun recreateIfThemeChanged(): Boolean {
        val next = themeFingerprint()
        if (next == appliedThemeFingerprint) return false
        appliedThemeFingerprint = next
        recreate()
        return true
    }

    override fun onPause() {
        runCatching { unregisterReceiver(captureReceiver) }
        super.onPause()
    }

    @Deprecated("Mantido sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MEDIA_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            toast("A captura não foi autorizada.")
            return
        }

        val journey = JourneyCoordinator.startJourney(this, platform = "multi")
        val serviceIntent = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }

        val failure = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }.exceptionOrNull()

        if (failure != null) {
            JourneyCoordinator.endJourney(this, "service_start_failed")
            JourneyBubbleController.show(this)
            toast("Não foi possível iniciar: ${failure.message}")
            return
        }

        toast("Jornada ${journey.id.take(8)} iniciada. Abra seu aplicativo de motorista.")
        navigate(SrBottomNav023.Route.NOW)
        nowPanel.refreshJourneyState()
        settingsPanel.refresh()
        JourneyBubbleController.refresh(this)
        SyncCoordinator.sync(this)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SrUi023.palette(this@MainActivity).background)
        }

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        nowPanel = NowPanel023(this)
        historyPanel = HistoryPanel(this)
        settingsPanel = SettingsHub023(
            this,
            SettingsHub023.Actions(
                journey = ::openJourneyAndPermissions,
                strategy = { startActivity(Intent(this, Strategy021Activity::class.java)) },
                appearance = { startActivity(Intent(this, Strategy021Activity::class.java)) },
                notifications = ::showNotifications,
                sync = { requestFullSync(showToast = true) },
                privacy = { openWeb("https://srrotas.com/privacidade") },
            ),
        )

        tabs[SrBottomNav023.Route.HISTORY] = historyContainer()
        tabs[SrBottomNav023.Route.AI] = AiPanel023(this)
        tabs[SrBottomNav023.Route.NOW] = nowPanel
        tabs[SrBottomNav023.Route.SETTINGS] = settingsPanel
        tabs.values.forEach { view ->
            content.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            view.visibility = View.GONE
        }

        navHost = FrameLayout(this)
        root.addView(
            navHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(SrUi023.dp(this@MainActivity, 10), 0, SrUi023.dp(this@MainActivity, 10), SrUi023.dp(this@MainActivity, 7))
            },
        )
        renderNav()
        return root
    }

    private fun historyContainer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(UiKit.palette(this@MainActivity).background)
        addView(
            SrAppHeader023(this@MainActivity, "Histórico", "Suas jornadas, ofertas e desempenho."),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        val holder = FrameLayout(this@MainActivity).apply {
            setPadding(
                SrUi023.dp(this@MainActivity, 14),
                SrUi023.dp(this@MainActivity, 10),
                SrUi023.dp(this@MainActivity, 14),
                SrUi023.dp(this@MainActivity, 12),
            )
            addView(historyPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        addView(holder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun navigate(route: SrBottomNav023.Route) {
        if (route == SrBottomNav023.Route.USER) {
            SrUserWeb023.open(this)
            return
        }
        selected = route
        tabs.forEach { (key, view) -> view.visibility = if (key == route) View.VISIBLE else View.GONE }
        renderNav()
        when (route) {
            SrBottomNav023.Route.NOW -> nowPanel.refresh()
            SrBottomNav023.Route.HISTORY -> refreshHistory()
            SrBottomNav023.Route.SETTINGS -> settingsPanel.refresh()
            else -> Unit
        }
    }

    private fun renderNav() {
        navHost.removeAllViews()
        navHost.addView(
            SrBottomNav023(this, selected, ::navigate),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun refreshHistory() {
        historyPanel.refresh(false)
        UiVisibilityPolicy.hideHistoricalImportUi(historyPanel)
    }

    /** Compatibilidade com o NowPanel legado ainda compilado no projeto. */
    fun openSettingsFromPanel() { navigate(SrBottomNav023.Route.SETTINGS) }

    /** Chamado pelo NowPanel023 sem duplicar a lógica de jornada. */
    fun toggleJourneyFromNow() {
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) stopCurrentJourney()
        else startJourney()
    }

    private fun startJourney() {
        val s = repo.load()
        if (!s.onboardingCompleted || !s.consentAccepted) {
            toast("Conclua a configuração guiada antes de iniciar.")
            startActivity(Intent(this, OnboardingActivity::class.java))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Primeiro permita o HUD.")
            openOverlayPermission()
            return
        }
        requestNotificationPermissionIfNeeded()

        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            @Suppress("DEPRECATION")
            projectionManager.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQ_MEDIA_PROJECTION)
    }

    private fun stopCurrentJourney() {
        stopService(Intent(this, MediaProjectionOcrService::class.java))
        repo.setProjectionActive(false)
        JourneyCoordinator.endJourney(this, "user_stop")
        JourneyBubbleController.show(this)
        SyncCoordinator.sync(this)
        nowPanel.refreshJourneyState()
        settingsPanel.refresh()
        toast("Jornada encerrada.")
    }

    private fun openJourneyAndPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return
        }
        startActivity(Intent(this, OnboardingActivity::class.java))
    }

    private fun showNotifications() {
        val view = PushSettingsView(this)
        view.refresh()
        AlertDialog.Builder(this)
            .setTitle("Notificações")
            .setView(view)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun requestFullSync(showToast: Boolean) {
        BackendClient.syncPreferences(this)
        CostProfileSync.refreshOrFlush(this)
        MessagePresetClient023.refresh(this)
        SyncCoordinator.sync(this) { result ->
            settingsPanel.refresh()
            JourneyBubbleController.refresh(this)
            if (showToast) toast(result.userMessage())
        }
    }

    private fun openOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun registerCaptureReceiver() {
        val filter = IntentFilter(AppSignals.ACTION_CAPTURE_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
    }

    private fun handleBubbleAction(sourceIntent: Intent?) {
        val action = sourceIntent?.getStringExtra(EXTRA_BUBBLE_ACTION) ?: return
        sourceIntent.removeExtra(EXTRA_BUBBLE_ACTION)
        when (action) {
            BUBBLE_ACTION_HISTORY -> navigate(SrBottomNav023.Route.HISTORY)
            BUBBLE_ACTION_START -> {
                navigate(SrBottomNav023.Route.NOW)
                if (!repo.isProjectionActive() && repo.currentJourneyId().isBlank()) {
                    content.post { startJourney() }
                }
            }
        }
    }

    private fun openWeb(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("Não foi possível abrir o navegador.") }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
