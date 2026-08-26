package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQ_MEDIA_PROJECTION = 4101
        private const val REQ_NOTIFICATIONS = 4102
        private const val REQ_LOCATION = 4103

        const val EXTRA_BUBBLE_ACTION = "com.srrotas.app.extra.BUBBLE_ACTION"
        const val BUBBLE_ACTION_START = "start"
        const val BUBBLE_ACTION_HISTORY = "history"
    }

    private lateinit var repo: SettingsRepository
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var content: FrameLayout
    private val tabs = linkedMapOf<String, View>()
    private val nav = linkedMapOf<String, TextView>()

    private lateinit var homeStatus: TextView
    private lateinit var homeJourneyButton: TextView
    private lateinit var homeHistory: TextView
    private lateinit var homeConnection: TextView
    private lateinit var onboardingCard: LinearLayout
    private lateinit var strategySummary: TextView
    private lateinit var consentCheck: CheckBox
    private lateinit var serviceStatus: TextView
    private lateinit var stopJourneyButton: TextView
    private lateinit var localHistory: TextView
    private lateinit var historyPanel: HistoryPanel
    private lateinit var latestSummary: TextView
    private lateinit var latestRaw: TextView
    private lateinit var aiMcpPanel: AiMcpPanel
    private lateinit var pairingStatus: TextView
    private lateinit var accountStatus: TextView
    private lateinit var billingStatusView: BillingStatusView
    private lateinit var pushSettingsView: PushSettingsView

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAll()
            JourneyBubbleController.refreshOffer(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)

        UiKit.applySystemBars(this)
        val root = buildUi()
        setContentView(root)
        UiKit.applySafeArea(root)
        loadSettings()
        showTab("agora")

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
        registerCaptureReceiver()
        PushManager.ensureIdentity(this)
        Preference021Sync.refresh(this) {
            refreshAll()
            (tabs["agora"] as? NowPanel)?.refresh()
        }
        refreshAll()

        if (Settings.canDrawOverlays(this)) JourneyBubbleController.show(this)

        SyncCoordinator.sync(this) {
            ReportSelection0211.flush(this)
            refreshAll()
            JourneyBubbleController.refresh(this)
        }
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

        // 0.22: uma mesma jornada pode observar Uber, 99 e outros apps.
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
        refreshAll()
        showTab("agora")
        JourneyBubbleController.refresh(this)
        SyncCoordinator.sync(this)
    }

    private fun buildUi(): View {
        val p = UiKit.palette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
        }

        content = FrameLayout(this)
        root.addView(
            content,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        tabs["agora"] = NowPanel(this)
        tabs["historico"] = buildHistoryTab()
        tabs["ia"] = buildAiTab()
        tabs["perfil"] = buildProfileTab()

        tabs.values.forEach {
            content.addView(
                it,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            it.visibility = View.GONE
        }
        root.addView(buildBottomNav())
        return root
    }

    private fun buildBottomNav(): View {
        val p = UiKit.palette(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(p.surface)
        }

        listOf(
            "agora" to "⌖\nAgora",
            "historico" to "◷\nHistórico",
            "ia" to "✦\nIA",
            "perfil" to "⚙\nConfigurações",
        ).forEach { (key, label) ->
            val item = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 10.5f
                setLines(2)
                setPadding(dp(4), dp(7), dp(4), dp(7))
                setOnClickListener { showTab(key) }
            }
            nav[key] = item
            bar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    /** 0.22: o nome da aba já está na navegação inferior; não repete título. */
    private fun buildHistoryTab(): View =
        tabScroll().also { (_, root) ->
            historyPanel = HistoryPanel(this)
            UiVisibilityPolicy.hideHistoricalImportUi(historyPanel)
            root.addView(UiKit.margin(historyPanel, top = 2))

            localHistory = UiKit.body(this, "", 13f)
            root.addView(
                UiKit.margin(
                    UiKit.card(this).apply {
                        addView(UiKit.sectionTitle(this@MainActivity, "Resumo local do aparelho"))
                        addView(localHistory)
                    },
                    top = 12,
                ),
            )

            latestSummary = UiKit.body(this, "", 15f)
            root.addView(
                UiKit.margin(
                    UiKit.card(this).apply {
                        addView(UiKit.sectionTitle(this@MainActivity, "Diagnóstico da última leitura"))
                        addView(latestSummary)
                    },
                    top = 12,
                ),
            )

            latestRaw = UiKit.body(this, "", 11f).apply {
                visibility = View.GONE
                setTextIsSelectable(true)
            }
            root.addView(UiKit.margin(UiKit.card(this).apply { addView(latestRaw) }, top = 10))
            root.addView(
                UiKit.margin(
                    UiKit.secondaryButton(this, "Mostrar / ocultar diagnóstico") {
                        latestRaw.visibility = if (latestRaw.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    },
                    top = 10,
                ),
            )
            root.addView(
                UiKit.margin(
                    UiKit.secondaryButton(this, "Compartilhar diagnóstico") { DiagnosticBundle.share(this) },
                    top = 8,
                ),
            )
        }.first

    private fun buildAiTab(): View =
        tabScroll().also { (_, root) ->
            aiMcpPanel = AiMcpPanel(this)
            root.addView(UiKit.margin(aiMcpPanel, top = 2))
        }.first

    /** Configurações organizadas por departamentos, com Aparência em primeiro lugar. */
    private fun buildProfileTab(): View =
        tabScroll().also { (_, root) ->
            addDepartment(root, "APARÊNCIA", first = true)
            root.addView(appThemeCard())

            addDepartment(root, "JORNADA E PERMISSÕES")
            onboardingCard = UiKit.card(this).apply {
                addView(UiKit.sectionTitle(this@MainActivity, "Configuração inicial"))
                addView(UiKit.body(this@MainActivity, "Conclua apenas as permissões necessárias para começar. As opções avançadas podem ser ajustadas depois."))
                addView(UiKit.margin(UiKit.primaryButton(this@MainActivity, "Continuar configuração") {
                    startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                }, top = 9))
            }
            root.addView(onboardingCard)

            root.addView(
                UiKit.margin(
                    UiKit.card(this).apply {
                        addView(UiKit.sectionTitle(this@MainActivity, "Funcionamento"))
                        homeStatus = UiKit.title(this@MainActivity, "", 20f)
                        addView(homeStatus)
                        homeConnection = UiKit.body(this@MainActivity, "", 12f)
                        addView(UiKit.margin(homeConnection, top = 5))
                        serviceStatus = UiKit.body(this@MainActivity, "", 12f)
                        addView(UiKit.margin(serviceStatus, top = 7))

                        consentCheck = CheckBox(this@MainActivity).apply {
                            text = "Autorizo a análise local durante minhas jornadas"
                            setTextColor(UiKit.palette(this@MainActivity).ink)
                            setOnCheckedChangeListener { _, _ -> saveBaseSettings() }
                        }
                        addView(UiKit.margin(consentCheck, top = 8))

                        homeJourneyButton = UiKit.primaryButton(this@MainActivity, "Iniciar jornada") { toggleJourneyFromHome() }
                        addView(UiKit.margin(homeJourneyButton, top = 10))
                        stopJourneyButton = UiKit.secondaryButton(this@MainActivity, "Encerrar jornada") { stopCurrentJourney() }
                        addView(UiKit.margin(stopJourneyButton, top = 7))
                        addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Corrigir permissões") { openEssentialPermissions() }, top = 7))
                    },
                    top = 10,
                ),
            )

            homeHistory = UiKit.body(this, "Nenhuma jornada registrada.", 13f)
            root.addView(
                UiKit.margin(
                    UiKit.card(this).apply {
                        addView(UiKit.sectionTitle(this@MainActivity, "Última jornada"))
                        addView(homeHistory)
                        addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Abrir Histórico") { showTab("historico") }, top = 8))
                    },
                    top = 10,
                ),
            )

            addDepartment(root, "ESTRATÉGIA E HUD")
            strategySummary = UiKit.body(this, "", 13f)
            root.addView(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@MainActivity, "Metas, prioridade e HUD"))
                    addView(strategySummary)
                    addView(UiKit.margin(UiKit.primaryButton(this@MainActivity, "Ajustar estratégia e HUD") {
                        startActivity(Intent(this@MainActivity, Strategy021Activity::class.java))
                    }, top = 9))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Abrir versão Web") {
                        WebHandoff021.open(this@MainActivity, "/app/perfil")
                    }, top = 7))
                },
            )

            addDepartment(root, "CONTA E PLANO")
            root.addView(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@MainActivity, "Sua conta"))
                    accountStatus = UiKit.body(this@MainActivity, "", 15f)
                    addView(accountStatus)
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Atualizar conta") {
                        BackendClient.fetchAccount(this@MainActivity) { result ->
                            result.onSuccess { toast("Conta atualizada."); refreshAll() }
                                .onFailure { toast("Não foi possível atualizar: ${it.message}") }
                        }
                    }, top = 10))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Configuração guiada") {
                        startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                    }, top = 8))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Sair deste aparelho") { logout() }, top = 8))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Excluir minha conta e dados") { confirmDeleteAccount() }, top = 8))
                },
            )

            billingStatusView = BillingStatusView(this)
            root.addView(UiKit.margin(UiKit.card(this).apply { addView(billingStatusView) }, top = 10))

            addDepartment(root, "NOTIFICAÇÕES")
            pushSettingsView = PushSettingsView(this)
            root.addView(UiKit.card(this).apply { addView(pushSettingsView) })

            addDepartment(root, "DADOS E SINCRONIZAÇÃO")
            root.addView(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@MainActivity, "Sincronização"))
                    addView(UiKit.body(this@MainActivity, "O Sr. Rotas sincroniza automaticamente. Se houver itens aguardando, este botão também repara vínculos de jornada e tenta novamente sem apagar dados.", 12f))
                    pairingStatus = UiKit.body(this@MainActivity, "", 13f)
                    addView(UiKit.margin(pairingStatus, top = 8))
                    addView(UiKit.margin(UiKit.primaryButton(this@MainActivity, "Sincronizar agora") {
                        requestFullSync(showToast = true)
                    }, top = 10))
                },
            )

            addDepartment(root, "PRIVACIDADE E SUPORTE")
            root.addView(
                UiKit.card(this).apply {
                    addView(UiKit.sectionTitle(this@MainActivity, "Privacidade e suporte"))
                    addView(UiKit.body(this@MainActivity, "A leitura de ofertas usa MediaProjection autorizada pelo Android em cada jornada. No modo multiplataforma a captura é do display, com processamento OCR local."))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Política de Privacidade") { openWeb("https://srrotas.com/privacidade") }, top = 8))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Termos de Uso") { openWeb("https://srrotas.com/termos") }, top = 8))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Exclusão de conta e dados") { openWeb("https://srrotas.com/excluir-conta") }, top = 8))
                    addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Suporte") { openWeb("https://srrotas.com/suporte") }, top = 8))
                },
            )

            root.addView(
                UiKit.margin(
                    UiKit.body(this, "Sr. Rotas — desenvolvido pela BigCorps\nSuporte: contato@bigcorps.com.br\nVersão ${BuildConfig.VERSION_NAME}", 13f),
                    top = 18,
                    bottom = 24,
                ),
            )
        }.first

    private fun addDepartment(root: LinearLayout, title: String, first: Boolean = false) {
        root.addView(
            UiKit.margin(
                UiKit.body(this, title, 11f).apply {
                    setTextColor(UiKit.palette(this@MainActivity).primaryDark)
                    setTypeface(typeface, Typeface.BOLD)
                    letterSpacing = 0.08f
                },
                top = if (first) 0 else 20,
                bottom = 7,
            ),
        )
    }

    private fun appThemeCard(): View = UiKit.card(this).apply {
        val selected = Strategy021Store.load(this@MainActivity).appTheme
        addView(UiKit.sectionTitle(this@MainActivity, "Tema do aplicativo"))
        addView(UiKit.body(this@MainActivity, "Muda Agora, Histórico, IA e Configurações. O HUD pode seguir o aplicativo ou usar tema próprio.", 12f))
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("auto" to "Automático", "light" to "Claro", "dark" to "Escuro").forEachIndexed { index, (key, label) ->
            val button = if (selected == key) {
                UiKit.primaryButton(this@MainActivity, "✓ $label") { applyAppTheme(key) }
            } else {
                UiKit.secondaryButton(this@MainActivity, label) { applyAppTheme(key) }
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(5)
                },
            )
        }
        addView(UiKit.margin(row, top = 9))
    }

    private fun applyAppTheme(theme: String) {
        Strategy021Store.saveAppTheme(this, theme)
        if (Strategy021Store.load(this).hudThemeMode == "follow_app") {
            repo.save(repo.load().copy(hudTheme = theme))
        }
        Preference021Sync.sync(this)
        recreate()
    }

    fun openSettingsFromPanel() { showTab("perfil") }
    fun toggleJourneyFromNow() { toggleJourneyFromHome() }

    private fun tabScroll(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            setBackgroundColor(UiKit.palette(this@MainActivity).background)
        }
        scroll.addView(root)
        return scroll to root
    }

    private fun showTab(key: String) {
        tabs.forEach { (k, v) -> v.visibility = if (k == key) View.VISIBLE else View.GONE }
        nav.forEach { (k, v) ->
            val p = UiKit.palette(this)
            v.setTextColor(if (k == key) p.primaryDark else p.muted)
            v.setTypeface(v.typeface, if (k == key) Typeface.BOLD else Typeface.NORMAL)
            v.background = if (k == key) UiKit.rounded(this, p.surfaceAlt, 13) else null
        }
        refreshAll()
        if (key == "agora") (tabs["agora"] as? NowPanel)?.refresh()
        if (key == "historico" && ::historyPanel.isInitialized) {
            historyPanel.refresh(false)
            UiVisibilityPolicy.hideHistoricalImportUi(historyPanel)
        }
    }

    private fun toggleJourneyFromHome() {
        val s = repo.load()
        if (!s.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java)); return
        }
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) stopCurrentJourney()
        else startJourney()
    }

    private fun loadSettings() {
        val s = repo.load()
        consentCheck.isChecked = s.consentAccepted
        refreshSyncStatus()
    }

    private fun saveBaseSettings() {
        val c = repo.load()
        repo.save(c.copy(consentAccepted = consentCheck.isChecked))
    }

    private fun startJourney() {
        saveBaseSettings()
        if (!repo.load().onboardingCompleted) {
            toast("Conclua a configuração guiada primeiro.")
            startActivity(Intent(this, OnboardingActivity::class.java)); return
        }
        if (!consentCheck.isChecked) {
            toast("Marque o consentimento antes de iniciar.")
            showTab("perfil"); return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Primeiro permita o HUD.")
            openOverlayPermission(); return
        }

        requestNotificationPermissionIfNeeded()

        // Android 14+: solicita explicitamente o display inteiro para que a mesma
        // jornada sobreviva à troca Uber ↔ 99 ↔ outros apps. O consentimento do
        // sistema continua obrigatório a cada sessão de MediaProjection.
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
        refreshAll()
        toast("Jornada encerrada.")
    }

    private fun openEssentialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION); return
        }
        if (!Settings.canDrawOverlays(this)) { openOverlayPermission(); return }
        toast("Permissões essenciais já estão liberadas.")
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

    private fun requestFullSync(showToast: Boolean) {
        saveBaseSettings()
        BackendClient.syncPreferences(this)
        CostProfileSync.refreshOrFlush(this)
        pairingStatus.text = if (SyncCoordinator.isRunning()) {
            "Sincronização já está em andamento…"
        } else {
            val count = SyncCoordinator.pending(this).total
            if (count > 0) "Sincronizando $count item(ns)…" else "Conferindo sincronização…"
        }

        SyncCoordinator.sync(this) { result ->
            pairingStatus.text = result.userMessage()
            refreshAll()
            JourneyBubbleController.refresh(this)
            if (showToast) toast(result.userMessage())
        }
    }

    private fun refreshSyncStatus() {
        if (!::pairingStatus.isInitialized) return
        val settings = repo.load()
        val queue = SyncCoordinator.pending(this)
        pairingStatus.text = when {
            settings.deviceToken.isBlank() -> "Aparelho sem sessão de nuvem."
            SyncCoordinator.isRunning() -> "Sincronizando ${queue.total} item(ns)…"
            queue.total == 0 -> "Tudo sincronizado."
            else -> "${queue.total} item(ns) aguardando sincronização."
        }
    }

    private fun confirmDeleteAccount() {
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) {
            toast("Encerre a jornada antes de excluir a conta."); return
        }
        if (repo.load().deviceToken.isBlank()) {
            toast("Nenhuma conta conectada neste aparelho."); return
        }

        val input = EditText(this).apply { hint = "Digite EXCLUIR"; setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("Excluir conta e dados?")
            .setMessage("Esta ação remove sua conta Sr. Rotas e os dados associados no servidor, além do histórico local deste aparelho. Digite EXCLUIR para confirmar.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir definitivamente") { _, _ ->
                if (input.text.toString().trim().uppercase() != "EXCLUIR") {
                    toast("Confirmação inválida. Nada foi excluído.")
                    return@setPositiveButton
                }
                BackendClient.deleteAccount(this) { result ->
                    result.onSuccess {
                        JourneyBubbleController.hide(this)
                        toast("Conta e dados excluídos.")
                        val restart = Intent(this, OnboardingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restart)
                        finishAffinity()
                    }.onFailure { toast("Não foi possível excluir: ${it.message}") }
                }
            }.show()
    }

    private fun logout() {
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) {
            toast("Encerre a jornada antes de sair."); return
        }
        if (repo.load().deviceToken.isBlank()) { toast("Nenhuma sessão ativa."); return }
        BackendClient.logoutAccount(this) { result ->
            result.onSuccess {
                JourneyBubbleController.hide(this)
                toast("Sessão encerrada neste aparelho.")
                refreshAll()
                startActivity(Intent(this, OnboardingActivity::class.java))
            }.onFailure { toast("Não foi possível sair: ${it.message}") }
        }
    }

    private fun refreshAll() {
        refreshStatus()
        refreshLocalHistory()
        refreshStrategy()
        refreshDiagnostics()
        refreshAccount()
        refreshSyncStatus()
    }

    private fun refreshStrategy() {
        val s = repo.load()
        strategySummary.text =
            "Meta: R$ ${fmt(s.minPerMinute)}/min  ·  R$ ${fmt(s.minPerKm)}/km  ·  R$ ${fmt(s.minPerHour)}/h\n" +
                "HUD: ${sizeLabel(s.hudCardSize)}  ·  ${themeLabel(s.hudTheme)}  ·  fonte ${s.hudFontSize}\n" +
                "Classificação: média ponderada pela ordem do HUD"
    }

    private fun refreshStatus() {
        val s = repo.load()
        val projection = repo.isProjectionActive()
        val current = repo.currentJourneyId()
        val overlay = Settings.canDrawOverlays(this)
        val active = projection || current.isNotBlank()
        val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val ready = s.onboardingCompleted && overlay && locationGranted && s.ocrEnabled && (!active || projection)

        onboardingCard.visibility = if (s.onboardingCompleted) View.GONE else View.VISIBLE
        homeStatus.text = when {
            !ready -> "Ação necessária"
            active -> "Tudo pronto · jornada ativa"
            else -> "Tudo pronto"
        }
        homeJourneyButton.text = when {
            !s.onboardingCompleted -> "Continuar configuração"
            active -> "Encerrar jornada"
            else -> "Iniciar jornada"
        }

        val online = ConnectivityState.isOnline(this)
        val paired = s.deviceToken.isNotBlank()
        val pending = SyncCoordinator.pending(this).total
        homeConnection.text =
            "${if (online) "Online" else "Offline"}  ·  ${if (paired) "Conta/aparelho conectado" else "Modo local"}  ·  " +
                if (pending == 0) "Tudo sincronizado" else "$pending pendente(s)"

        serviceStatus.text = buildString {
            append(if (active) "Jornada ativa" else "Jornada parada")
            if (current.isNotBlank()) append("  ·  ${current.take(8)}")
            append("\n")
            append(if (overlay) "HUD autorizado" else "HUD sem permissão")
            append("  ·  ${if (locationGranted) "localização autorizada" else "localização pendente"}")
            append("\nCaptura/OCR: ${if (projection) "ativos · multiplataforma" else if (active) "ação necessária" else "prontos para iniciar"}")
        }

        stopJourneyButton.isEnabled = active
        stopJourneyButton.alpha = if (active) 1f else .45f
    }

    private fun refreshAccount() {
        val s = repo.load()
        if (::billingStatusView.isInitialized) billingStatusView.refresh()
        if (::pushSettingsView.isInitialized) pushSettingsView.refresh()
        val pending = SyncCoordinator.pending(this).total
        accountStatus.text = buildString {
            append(s.driverDisplayName)
            if (s.accountEmail.isNotBlank()) append("\n${s.accountEmail}")
            else if (s.deviceToken.isNotBlank()) append("\nSessão Alpha / pareamento legado")
            else append("\nModo local")
            append("\n${if (ConnectivityState.isOnline(this@MainActivity)) "Online" else "Offline"}")
            append(if (pending == 0) "  ·  Tudo sincronizado" else "  ·  $pending pendente(s)")
        }
    }

    private fun refreshLocalHistory() {
        val store = LocalStore.get(this)
        val summary = JourneyCoordinator.currentSummary(this)
            ?: store.latestJourney()?.let { store.journeySummary(it.id) }
        val pending = SyncCoordinator.pending(this).total
        val text = buildString {
            if (summary == null) append("Nenhuma jornada registrada ainda.")
            else {
                append(if (summary.journey.endedAt == null) "Jornada atual" else "Última jornada")
                append("  ·  ${summary.offerCount} ofertas")
                append("\nBoas ${summary.goodCount}  ·  Atenção ${summary.regularCount}  ·  Abaixo ${summary.badCount}")
                summary.averagePerKm?.let { append("\nMédia R$ ${fmt(it)}/km") }
                summary.averagePerHour?.let { append("  ·  R$ ${fmt(it)}/h") }
            }
            append(if (pending == 0) "\nTudo sincronizado" else "\nPendentes $pending")
            append("  ·  Capturas privadas ${PrivateScreenshotStore.count(this@MainActivity)}")
        }
        homeHistory.text = text
        localHistory.text = text
    }

    private fun refreshDiagnostics() {
        latestSummary.text = repo.latestSummary()
        val method = repo.latestMethod().takeIf { it.isNotBlank() }?.let { "Método: $it\n\n" } ?: ""
        val raw = repo.latestRaw()
        val log = LocalLog.tail(this, 45)
        latestRaw.text = if (raw.isNotBlank()) "$method$raw\n\n--- LOG LOCAL ---\n$log"
        else "Nenhum texto bruto capturado.\n\n--- LOG LOCAL ---\n$log"
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
        if (action == BUBBLE_ACTION_HISTORY) { showTab("historico"); return }
        if (action == BUBBLE_ACTION_START) {
            if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) {
                showTab("agora"); return
            }
            showTab("agora")
            content.post { startJourney() }
        }
    }

    private fun openWeb(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("Não foi possível abrir o navegador.") }
    }

    private fun sizeLabel(v: String) = when (v) {
        "compact" -> "Compacto"
        "large" -> "Grande"
        else -> "Normal"
    }

    private fun themeLabel(v: String) = when (v) {
        "light" -> "Claro"
        "dark" -> "Escuro"
        else -> "Automático"
    }

    private fun fmt(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
