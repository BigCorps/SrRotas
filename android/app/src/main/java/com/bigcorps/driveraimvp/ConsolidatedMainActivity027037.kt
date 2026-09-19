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
import android.widget.ScrollView
import android.widget.Toast

/**
 * Shell único RC3.7.
 *
 * Não existe watcher/polish que substitui tabs ou reconstrói cabeçalho. Cada rota
 * tem uma única instância e Configurações/Usuário são overlays explícitos do shell.
 */
open class ConsolidatedMainActivity027037 : Activity() {
    companion object {
        private const val REQ_MEDIA_PROJECTION = 4101
        private const val REQ_NOTIFICATIONS = 4102
        private const val REQ_LOCATION = 4103
        private const val STATE_SELECTED_ROUTE = "sr_selected_route"
    }

    private lateinit var repo: SettingsRepository
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var content: FrameLayout
    private lateinit var navHost: FrameLayout

    private lateinit var nowPanel: NowPanel027037
    private lateinit var statisticsPanel: HistoryPanel
    private lateinit var historyPanel: RideHistoryPanel027035
    private lateinit var radarPanel: RadarPanel027035
    private lateinit var settingsPanel: SettingsPanel027037
    private lateinit var userPanel: UserPanel024

    private var selected = SrBottomNav023.Route.NOW
    private var auxiliaryVisible: View? = null
    private var appliedThemeFingerprint = ""
    private var recoveringCapture = false
    private val tabs = linkedMapOf<SrBottomNav023.Route, View>()

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            nowPanel.refreshJourneyState()
            if (selected == SrBottomNav023.Route.SETTINGS && auxiliaryVisible == null) {
                historyPanel.refresh()
            }
            if (auxiliaryVisible === settingsPanel) settingsPanel.refresh()
            JourneyBubbleController.refreshOffer(this@ConsolidatedMainActivity027037)
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
            userPanel.refresh()
            renderNav()
        })
        nowPanel.refreshJourneyState()
        if (selected == SrBottomNav023.Route.NOW && auxiliaryVisible == null) nowPanel.refresh()
        if (selected == SrBottomNav023.Route.HISTORY && auxiliaryVisible == null) statisticsPanel.refresh(false)
        if (selected == SrBottomNav023.Route.SETTINGS && auxiliaryVisible == null) historyPanel.refresh()
        if (selected == SrBottomNav023.Route.USER && auxiliaryVisible == null) radarPanel.refresh()
        if (auxiliaryVisible === settingsPanel) settingsPanel.refresh()
        if (auxiliaryVisible === userPanel) userPanel.refresh()

        if (Settings.canDrawOverlays(this)) JourneyBubbleController.show(this)
        MessagePresetClient023.refresh(this)
        SyncCoordinator.sync(this) {
            ReportSelection0211.flush(this)
            if (auxiliaryVisible === settingsPanel) settingsPanel.refresh()
            JourneyBubbleController.refresh(this)
        }
    }

    override fun onPause() {
        runCatching { unregisterReceiver(captureReceiver) }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_ROUTE, selected.name)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Mantido sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MEDIA_PROJECTION) return

        val wasRecovery = recoveringCapture
        recoveringCapture = false
        if (resultCode != RESULT_OK || data == null) {
            toast(if (wasRecovery) "A recuperação foi cancelada. A jornada continua aberta." else "A captura não foi autorizada.")
            nowPanel.refreshJourneyState()
            return
        }

        val existingJourney = repo.currentJourneyId()
            .takeIf(String::isNotBlank)
            ?.let { LocalStore.get(this).journey(it) }
            ?.takeIf { it.endedAt == null }

        val journey = if (wasRecovery && existingJourney != null) {
            existingJourney
        } else {
            JourneyCoordinator.startJourney(this, platform = "multi")
        }
        JourneyInlineDraft0264.maybeApplyToCurrentJourney(this)

        val serviceIntent = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }
        val failure = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        }.exceptionOrNull()

        if (failure != null) {
            CaptureHealthState0263.markInactive(this, "service_start_failed")
            if (!wasRecovery) JourneyCoordinator.endJourney(this, "service_start_failed")
            JourneyBubbleController.show(this)
            toast(if (wasRecovery) "Não foi possível recuperar a leitura: ${failure.message}" else "Não foi possível iniciar: ${failure.message}")
            return
        }

        toast(
            if (wasRecovery) "Leitura recuperada na mesma jornada ${journey.id.take(8)}."
            else "Jornada ${journey.id.take(8)} iniciada · ${ReaderLab027036.modeLabel(ReaderLab027036.mode(this))}."
        )
        navigate(SrBottomNav023.Route.NOW)
        nowPanel.refreshJourneyState()
        JourneyBubbleController.refresh(this)
        SyncCoordinator.sync(this)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SrUi023.palette(this@ConsolidatedMainActivity027037).background)
        }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        statisticsPanel = HistoryPanel(this)
        nowPanel = NowPanel027037(this)
        historyPanel = RideHistoryPanel027035(this)
        radarPanel = RadarPanel027035(this)
        settingsPanel = SettingsPanel027037(this)
        userPanel = UserPanel024(this)

        tabs[SrBottomNav023.Route.HISTORY] = statisticsContainer()
        tabs[SrBottomNav023.Route.AI] = ConsolidatedAiPanel027037(this)
        tabs[SrBottomNav023.Route.NOW] = nowPanel
        tabs[SrBottomNav023.Route.SETTINGS] = historyPanel
        tabs[SrBottomNav023.Route.USER] = radarPanel

        tabs.values.forEach(::addContentViewHidden)
        addContentViewHidden(settingsPanel)
        addContentViewHidden(userPanel)

        navHost = FrameLayout(this)
        root.addView(
            navHost,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(SrUi023.dp(this@ConsolidatedMainActivity027037, 10), 0, SrUi023.dp(this@ConsolidatedMainActivity027037, 10), SrUi023.dp(this@ConsolidatedMainActivity027037, 7))
            },
        )
        renderNav()
        return root
    }

    private fun addContentViewHidden(view: View) {
        content.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.visibility = View.GONE
    }

    private fun statisticsContainer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(UiKit.palette(this@ConsolidatedMainActivity027037).background)
        addView(SrAppHeader023(this@ConsolidatedMainActivity027037, "Estatísticas", ""))
        val scroll = ScrollView(this@ConsolidatedMainActivity027037).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val holder = LinearLayout(this@ConsolidatedMainActivity027037).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SrUi023.dp(context, 14), SrUi023.dp(context, 10), SrUi023.dp(context, 14), SrUi023.dp(context, 18))
            addView(statisticsPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        scroll.addView(holder, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun navigate(route: SrBottomNav023.Route) {
        auxiliaryVisible?.visibility = View.GONE
        auxiliaryVisible = null
        selected = route
        tabs.forEach { (key, view) -> view.visibility = if (key == route) View.VISIBLE else View.GONE }
        settingsPanel.visibility = View.GONE
        userPanel.visibility = View.GONE
        renderNav()
        when (route) {
            SrBottomNav023.Route.HISTORY -> statisticsPanel.refresh(false)
            SrBottomNav023.Route.NOW -> nowPanel.refresh()
            SrBottomNav023.Route.SETTINGS -> historyPanel.refresh()
            SrBottomNav023.Route.USER -> radarPanel.refresh()
            else -> Unit
        }
    }

    private fun showAuxiliary(view: View) {
        tabs.values.forEach { it.visibility = View.GONE }
        settingsPanel.visibility = View.GONE
        userPanel.visibility = View.GONE
        view.visibility = View.VISIBLE
        auxiliaryVisible = view
        renderNav()
    }

    private fun renderNav() {
        navHost.removeAllViews()
        navHost.addView(
            SrBottomNav023(this, selected, ::navigate),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    fun openSettingsFromPanel() = showAuxiliary(settingsPanel)
    fun openUserFromHeader() = showAuxiliary(userPanel)

    fun toggleJourneyFromNow() {
        if (repo.currentJourneyId().isNotBlank()) {
            JourneyPreflight027037.openEndOdometer(this) { stopCurrentJourney() }
        } else {
            startJourney()
        }
    }

    fun openNowFromAssistant() {
        navigate(SrBottomNav023.Route.NOW)
        nowPanel.showMoment()
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
        JourneyPreflight027037.armForNextJourney(this)
        requestNotificationPermissionIfNeeded()

        when (ReaderLab027036.mode(this)) {
            ReaderLab027036.MODE_M2 -> startM2OnlyJourney()
            else -> requestCapturePermission(recovery = false)
        }
    }

    private fun startM2OnlyJourney() {
        if (!ReaderLab027036.disclosureAccepted(this) || !ReaderLab027036.isAccessibilityEnabled(this)) {
            toast("Ative a Acessibilidade do Sr. Rotas antes do teste M2.")
            ReaderLab027036.showDisclosureAndOpenSettings(this)
            return
        }
        stopService(Intent(this, MediaProjectionOcrService::class.java))
        repo.setProjectionActive(false)
        val journey = JourneyCoordinator.startJourney(this, platform = "uber")
        JourneyInlineDraft0264.maybeApplyToCurrentJourney(this)
        JourneyBubbleController.show(this)
        nowPanel.refreshJourneyState()
        toast("Jornada ${journey.id.take(8)} iniciada em M2 isolado. M1 está desligado.")
    }

    fun recoverCurrentJourneyCapture() {
        if (ReaderLab027036.mode(this) == ReaderLab027036.MODE_M2) {
            toast("M2 isolado não usa MediaProjection. Confira a Acessibilidade.")
            return
        }
        if (repo.currentJourneyId().isBlank()) {
            startJourney()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Primeiro permita o HUD.")
            openOverlayPermission()
            return
        }
        requestNotificationPermissionIfNeeded()
        requestCapturePermission(recovery = true)
    }

    private fun requestCapturePermission(recovery: Boolean) {
        recoveringCapture = recovery
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            @Suppress("DEPRECATION")
            projectionManager.createScreenCaptureIntent()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            toast("Selecione Uber ou 99 na janela de compartilhamento do Android.")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQ_MEDIA_PROJECTION)
    }

    private fun stopCurrentJourney() {
        stopService(Intent(this, MediaProjectionOcrService::class.java))
        CaptureHealthState0263.markInactive(this, "user_stop")
        repo.setProjectionActive(false)
        JourneyCoordinator.endJourney(this, "user_stop")
        JourneyBubbleController.show(this)
        SyncCoordinator.sync(this)
        nowPanel.refreshJourneyState()
        toast("Jornada encerrada.")
    }

    fun openJourneyAndPermissions() {
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

    fun showAppearanceOnly() {
        val values = arrayOf("Automático", "Claro", "Escuro")
        val keys = arrayOf("auto", "light", "dark")
        val active = Strategy021Store.load(this).appTheme
        val current = keys.indexOf(active).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Aparência")
            .setSingleChoiceItems(values, current) { dialog, which ->
                Strategy021Store.saveAppTheme(this, keys[which])
                if (Strategy021Store.load(this).hudThemeMode == "follow_app") {
                    val settings = SettingsRepository(this).load()
                    SettingsRepository(this).save(settings.copy(hudTheme = keys[which]))
                }
                Preference021Sync.sync(this)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun showDemoMode() {
        AlertDialog.Builder(this)
            .setTitle("Modo Demonstração")
            .setView(DemoCapturePanel024(this))
            .setPositiveButton("Fechar", null)
            .show()
    }

    fun showNotifications() {
        val view = PushSettingsView(this)
        view.refresh()
        AlertDialog.Builder(this).setTitle("Notificações").setView(view).setPositiveButton("Fechar", null).show()
    }

    fun requestFullSync(showToast: Boolean = true) {
        BackendClient.syncPreferences(this)
        CostProfileSync.refreshOrFlush(this)
        MessagePresetClient023.refresh(this)
        SyncCoordinator.sync(this) { result ->
            if (auxiliaryVisible === settingsPanel) settingsPanel.refresh()
            JourneyBubbleController.refresh(this)
            if (showToast) toast(result.userMessage())
        }
    }

    fun openWeb(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("Não foi possível abrir o navegador.") }
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
        runCatching { unregisterReceiver(captureReceiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
    }

    private fun handleBubbleAction(sourceIntent: Intent?) {
        val action = sourceIntent?.getStringExtra(MainActivity.EXTRA_BUBBLE_ACTION) ?: return
        sourceIntent.removeExtra(MainActivity.EXTRA_BUBBLE_ACTION)
        when (action) {
            MainActivity.BUBBLE_ACTION_HISTORY -> navigate(SrBottomNav023.Route.SETTINGS)
            MainActivity.BUBBLE_ACTION_NOW -> openNowFromAssistant()
            MainActivity.BUBBLE_ACTION_START -> {
                navigate(SrBottomNav023.Route.NOW)
                if (repo.currentJourneyId().isBlank()) content.post { startJourney() }
                else if (ReaderLab027036.m1Enabled(this) && !repo.isProjectionActive()) content.post { recoverCurrentJourneyCapture() }
            }
        }
    }

    private fun themeFingerprint(): String = "${Strategy021Store.load(this).appTheme}|${Appearance021.isDark(this)}"

    private fun recreateIfThemeChanged(): Boolean {
        val next = themeFingerprint()
        if (next == appliedThemeFingerprint) return false
        appliedThemeFingerprint = next
        recreate()
        return true
    }

    fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
