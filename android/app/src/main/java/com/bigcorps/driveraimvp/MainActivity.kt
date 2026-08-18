package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object { private const val REQ_MEDIA_PROJECTION = 4101; private const val REQ_NOTIFICATIONS = 4102 }

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
    private lateinit var backendInput: EditText
    private lateinit var pairingInput: EditText
    private lateinit var pairingStatus: TextView
    private lateinit var accountStatus: TextView
    private lateinit var billingStatusView: BillingStatusView
    private lateinit var pushSettingsView: PushSettingsView
    private lateinit var betaFeedbackView: BetaFeedbackView

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshAll() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        UiKit.applySystemBars(this)
        setContentView(buildUi())
        loadSettings()
        showTab("inicio")
        if (!repo.load().onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        registerCaptureReceiver()
        PushManager.ensureIdentity(this)
        refreshAll()
        BackendClient.flushPendingOffers(this)
    }

    override fun onPause() { runCatching { unregisterReceiver(captureReceiver) }; super.onPause() }

    @Deprecated("Mantido sem AndroidX neste Alpha")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MEDIA_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) { toast("A captura não foi autorizada."); return }
        val journey = JourneyCoordinator.startJourney(this)
        val intent = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }
        val failure = runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent) }.exceptionOrNull()
        if (failure != null) { JourneyCoordinator.endJourney(this, "service_start_failed"); toast("Não foi possível iniciar: ${failure.message}"); return }
        toast("Jornada ${journey.id.take(8)} iniciada. Abra o Uber Driver.")
        refreshAll(); showTab("jornada")
    }

    private fun buildUi(): View {
        val p = UiKit.palette(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.background) }
        root.addView(buildHeader())
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        tabs["inicio"] = buildHomeTab(); tabs["jornada"] = buildJourneyTab(); tabs["historico"] = buildHistoryTab(); tabs["ia"] = buildAiTab(); tabs["perfil"] = buildProfileTab()
        tabs.values.forEach { content.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)); it.visibility = View.GONE }
        root.addView(buildBottomNav())
        return root
    }

    private fun buildHeader(): View {
        val p = UiKit.palette(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8)); setBackgroundColor(p.surface)
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.logo_srrotas); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                addView(UiKit.title(this@MainActivity, "Sr. Rotas", 21f))
                addView(UiKit.body(this@MainActivity, "Seu copiloto de rentabilidade", 12f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(UiKit.pill(this@MainActivity, "0.13 Beta", "primary"))
        }
    }

    private fun buildBottomNav(): View {
        val p = UiKit.palette(this)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(8)); setBackgroundColor(p.surface) }
        listOf("inicio" to "Início", "jornada" to "Jornada", "historico" to "Histórico", "ia" to "IA", "perfil" to "Perfil").forEach { (key, label) ->
            val item = TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 12f; setPadding(dp(4), dp(10), dp(4), dp(10)); setOnClickListener { showTab(key) } }
            nav[key] = item; bar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    private fun buildHomeTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.body(this, "Pronto para usar na rua: status, próxima ação e jornada sem telas técnicas."))

        onboardingCard = UiKit.card(this).apply {
            addView(UiKit.pill(this@MainActivity, "CONFIGURAÇÃO", "warn"))
            addView(UiKit.margin(UiKit.title(this@MainActivity, "Conclua os próximos passos", 20f), top = 8))
            addView(UiKit.body(this@MainActivity, "Conta, permissões, estratégia e HUD em um fluxo guiado."))
            addView(UiKit.margin(UiKit.primaryButton(this@MainActivity, "Continuar configuração") { startActivity(Intent(this@MainActivity, OnboardingActivity::class.java)) }, top = 10))
        }
        root.addView(UiKit.margin(onboardingCard, top = 14))

        val statusCard = UiKit.card(this).apply {
            homeStatus = UiKit.title(this@MainActivity, "Pronto para rodar", 23f); addView(homeStatus)
            addView(UiKit.body(this@MainActivity, "O Sr. Rotas calcula. Você decide a corrida."))
            homeConnection = UiKit.body(this@MainActivity, "", 13f); addView(UiKit.margin(homeConnection, top = 8))
            homeJourneyButton = UiKit.primaryButton(this@MainActivity, "Iniciar jornada") { toggleJourneyFromHome() }
            addView(UiKit.margin(homeJourneyButton, top = 14))
        }
        root.addView(UiKit.margin(statusCard, top = 12))

        root.addView(UiKit.margin(UiKit.sectionTitle(this, "Última jornada"), top = 14))
        homeHistory = UiKit.body(this, "Nenhuma jornada registrada.", 15f)
        root.addView(UiKit.card(this).apply { addView(homeHistory) })

        root.addView(UiKit.margin(UiKit.sectionTitle(this, "Sua estratégia"), top = 14))
        strategySummary = UiKit.body(this, "", 14f)
        root.addView(UiKit.card(this).apply {
            addView(strategySummary)
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Ajustar estratégia e HUD") { startActivity(Intent(this@MainActivity, StrategyActivity::class.java)) }, top = 12))
        })
    }.first

    private fun buildJourneyTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Jornada", 27f)); root.addView(UiKit.body(this, "Controles grandes e diretos para usar antes de sair dirigindo."))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Permissões e privacidade"))
            addView(UiKit.body(this@MainActivity, "A captura é autorizada pelo Android em cada jornada. O OCR roda localmente e o app não aceita nem recusa corridas."))
            consentCheck = CheckBox(this@MainActivity).apply { text = "Autorizo a análise local durante minhas jornadas"; setTextColor(UiKit.palette(this@MainActivity).ink); setOnCheckedChangeListener { _, _ -> saveBaseSettings() } }
            addView(consentCheck)
            serviceStatus = UiKit.body(this@MainActivity, "", 14f); addView(UiKit.margin(serviceStatus, top = 8))
        }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Revisar configuração guiada") { startActivity(Intent(this, OnboardingActivity::class.java)) }, top = 10))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Permitir HUD sobre outros apps") { openOverlayPermission() }, top = 8))
        root.addView(UiKit.margin(UiKit.primaryButton(this, "Iniciar jornada") { startJourney() }, top = 10))
        stopJourneyButton = UiKit.secondaryButton(this, "Encerrar jornada") { stopCurrentJourney() }
        root.addView(UiKit.margin(stopJourneyButton, top = 8))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Durante a corrida"))
            addView(UiKit.body(this@MainActivity, "• Toque no card para fechá-lo.\\n• Segure e arraste para mudar a posição.\\n• O tamanho, fonte e métricas ficam em Estratégia e HUD."))
        }, top = 14))
    }.first

    private fun buildHistoryTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Histórico e analytics", 27f))
        root.addView(UiKit.body(this, "Compare períodos, horários, categorias e jornadas. Tudo abaixo representa ofertas observadas — não corridas concluídas."))
        historyPanel = HistoryPanel(this)
        root.addView(UiKit.margin(historyPanel, top = 14))

        localHistory = UiKit.body(this, "", 13f)
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Resumo local do aparelho"))
            addView(localHistory)
        }, top = 12))

        latestSummary = UiKit.body(this, "", 15f)
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Diagnóstico da última leitura"))
            addView(latestSummary)
        }, top = 12))
        latestRaw = UiKit.body(this, "", 11f).apply { visibility = View.GONE; setTextIsSelectable(true) }
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(latestRaw) }, top = 10))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Mostrar / ocultar diagnóstico") { latestRaw.visibility = if (latestRaw.visibility == View.VISIBLE) View.GONE else View.VISIBLE }, top = 10))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Compartilhar diagnóstico") { DiagnosticBundle.share(this) }, top = 8))
    }.first

    private fun buildAiTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "IA e integrações", 27f))
        root.addView(UiKit.body(this, "Escolha entre a IA do Sr. Rotas ou conecte seus próprios clientes de IA via MCP."))
        aiMcpPanel = AiMcpPanel(this)
        root.addView(UiKit.margin(aiMcpPanel, top = 14))
    }.first

    private fun buildProfileTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Perfil e conexão", 27f)); root.addView(UiKit.body(this, "Conta, sincronização, suporte e opções avançadas."))

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Sua conta"))
            accountStatus = UiKit.body(this@MainActivity, "", 15f); addView(accountStatus)
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Atualizar conta") {
                BackendClient.fetchAccount(this@MainActivity) { result ->
                    result.onSuccess { toast("Conta atualizada."); refreshAll() }.onFailure { toast("Não foi possível atualizar: ${it.message}") }
                }
            }, top = 10))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Configuração guiada") { startActivity(Intent(this@MainActivity, OnboardingActivity::class.java)) }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Sair deste aparelho") { logout() }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Excluir minha conta e dados") { confirmDeleteAccount() }, top = 8))
        }, top = 14))

        billingStatusView = BillingStatusView(this)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(billingStatusView) }, top = 12))

        pushSettingsView = PushSettingsView(this)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(pushSettingsView) }, top = 12))

        betaFeedbackView = BetaFeedbackView(this)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(betaFeedbackView) }, top = 12))

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Pareamento Alpha legado"))
            addView(UiKit.body(this@MainActivity, "Mantido durante os testes para não quebrar aparelhos já pareados. Novas contas devem usar o fluxo de criar conta/entrar."))
            backendInput = UiKit.input(this@MainActivity, "URL do backend")
            pairingInput = UiKit.input(this@MainActivity, "Código de pareamento").apply { inputType = InputType.TYPE_CLASS_NUMBER }
            addView(UiKit.margin(backendInput, top = 8)); addView(UiKit.margin(pairingInput, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Parear aparelho legado") { pairDevice() }, top = 10))
            pairingStatus = UiKit.body(this@MainActivity, "", 13f); addView(UiKit.margin(pairingStatus, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Sincronizar agora") { saveBaseSettings(); BackendClient.syncPreferences(this@MainActivity); BackendClient.flushPendingOffers(this@MainActivity); toast("Sincronização solicitada.") }, top = 10))
        }, top = 12))

        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Estratégia e HUD") { startActivity(Intent(this, StrategyActivity::class.java)) }, top = 12))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Privacidade e suporte"))
            addView(UiKit.body(this@MainActivity, "A leitura de ofertas usa MediaProjection autorizada pelo Android em cada jornada. O Sr. Rotas não usa mais Serviço de Acessibilidade."))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Política de Privacidade") { openWeb("https://srrotas.com/privacidade") }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Termos de Uso") { openWeb("https://srrotas.com/termos") }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Exclusão de conta e dados") { openWeb("https://srrotas.com/excluir-conta") }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Suporte") { openWeb("https://srrotas.com/suporte") }, top = 8))
        }, top = 12))
        root.addView(UiKit.margin(UiKit.body(this, "Sr. Rotas — desenvolvido pela BigCorps\\nSuporte: contato@bigcorps.com.br\\nVersão ${BuildConfig.VERSION_NAME}", 13f), top = 18, bottom = 24))
    }.first

    private fun tabScroll(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(28)); setBackgroundColor(UiKit.palette(this@MainActivity).background) }
        scroll.addView(root); return scroll to root
    }

    private fun showTab(key: String) {
        tabs.forEach { (k, v) -> v.visibility = if (k == key) View.VISIBLE else View.GONE }
        nav.forEach { (k, v) ->
            val p = UiKit.palette(this); v.setTextColor(if (k == key) p.primaryDark else p.muted); v.setTypeface(v.typeface, if (k == key) Typeface.BOLD else Typeface.NORMAL)
            v.background = if (k == key) UiKit.rounded(this, p.surfaceAlt, 13) else null
        }
        refreshAll()
        if (key == "historico" && ::historyPanel.isInitialized) historyPanel.refresh(false)
    }

    private fun toggleJourneyFromHome() {
        val s = repo.load()
        if (!s.onboardingCompleted) { startActivity(Intent(this, OnboardingActivity::class.java)); return }
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) stopCurrentJourney() else startJourney()
    }

    private fun loadSettings() {
        val s = repo.load()
        backendInput.setText(s.backendUrl); consentCheck.isChecked = s.consentAccepted
        pairingStatus.text = if (s.deviceToken.isBlank()) "Aparelho não pareado." else "Aparelho conectado."
    }

    private fun saveBaseSettings() {
        val c = repo.load()
        repo.save(c.copy(backendUrl = backendInput.text.toString(), consentAccepted = consentCheck.isChecked))
    }

    private fun startJourney() {
        saveBaseSettings()
        if (!repo.load().onboardingCompleted) { toast("Conclua a configuração guiada primeiro."); startActivity(Intent(this, OnboardingActivity::class.java)); return }
        if (!consentCheck.isChecked) { toast("Marque o consentimento antes de iniciar."); showTab("jornada"); return }
        if (!Settings.canDrawOverlays(this)) { toast("Primeiro permita o HUD."); openOverlayPermission(); return }
        requestNotificationPermissionIfNeeded()
        @Suppress("DEPRECATION") startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
    }

    private fun stopCurrentJourney() {
        stopService(Intent(this, MediaProjectionOcrService::class.java)); repo.setProjectionActive(false); JourneyCoordinator.endJourney(this, "user_stop"); refreshAll(); toast("Jornada encerrada.")
    }

    private fun openOverlayPermission() { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
    private fun requestNotificationPermissionIfNeeded() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS) }

    private fun pairDevice() {
        saveBaseSettings(); pairingStatus.text = "Pareando..."
        BackendClient.pair(this, backendInput.text.toString(), pairingInput.text.toString()) { r ->
            r.onSuccess { pairingStatus.text = "Aparelho conectado."; pairingInput.setText(""); BackendClient.syncPreferences(this); BackendClient.flushPendingOffers(this); refreshAll() }
                .onFailure { pairingStatus.text = "Falha no pareamento: ${it.message}" }
        }
    }


    private fun confirmDeleteAccount() {
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) {
            toast("Encerre a jornada antes de excluir a conta.")
            return
        }
        if (repo.load().deviceToken.isBlank()) {
            toast("Nenhuma conta conectada neste aparelho.")
            return
        }
        val input = EditText(this).apply {
            hint = "Digite EXCLUIR"
            setSingleLine(true)
        }
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
                        toast("Conta e dados excluídos.")
                        val restart = Intent(this, OnboardingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restart)
                        finishAffinity()
                    }.onFailure { toast("Não foi possível excluir: ${it.message}") }
                }
            }
            .show()
    }

    private fun logout() {
        if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) { toast("Encerre a jornada antes de sair."); return }
        if (repo.load().deviceToken.isBlank()) { toast("Nenhuma sessão ativa."); return }
        BackendClient.logoutAccount(this) { result ->
            result.onSuccess { toast("Sessão encerrada neste aparelho."); refreshAll(); startActivity(Intent(this, OnboardingActivity::class.java)) }
                .onFailure { toast("Não foi possível sair: ${it.message}") }
        }
    }


    private fun refreshAll() {
        if (!::homeStatus.isInitialized) return
        refreshStatus(); refreshLocalHistory(); refreshStrategy(); refreshDiagnostics(); refreshAccount()
    }

    private fun refreshStrategy() {
        val s = repo.load()
        strategySummary.text = "Meta: R$ ${fmt(s.minPerMinute)}/min  ·  R$ ${fmt(s.minPerKm)}/km  ·  R$ ${fmt(s.minPerHour)}/h\\nHUD: ${sizeLabel(s.hudCardSize)}  ·  ${themeLabel(s.hudTheme)}  ·  fonte ${s.hudFontSize}"
    }

    private fun refreshStatus() {
        val s = repo.load()
        val projection = repo.isProjectionActive(); val current = repo.currentJourneyId(); val overlay = Settings.canDrawOverlays(this)
        val active = projection || current.isNotBlank()
        onboardingCard.visibility = if (s.onboardingCompleted) View.GONE else View.VISIBLE
        homeStatus.text = when { !s.onboardingCompleted -> "Configuração pendente"; active -> "Jornada em andamento"; else -> "Pronto para rodar" }
        homeJourneyButton.text = when { !s.onboardingCompleted -> "Continuar configuração"; active -> "Encerrar jornada"; else -> "Iniciar jornada" }
        val online = ConnectivityState.isOnline(this)
        val paired = s.deviceToken.isNotBlank()
        val pending = LocalStore.get(this).pendingOfferCount()
        homeConnection.text = "${if (online) "Online" else "Offline"}  ·  ${if (paired) "Conta/aparelho conectado" else "Modo local"}  ·  $pending pendente(s)"
        serviceStatus.text = buildString {
            append(if (active) "Jornada ativa" else "Jornada parada"); if (current.isNotBlank()) append("  ·  ${current.take(8)}"); append("\n")
            append(if (overlay) "HUD autorizado" else "HUD sem permissão"); append("  ·  captura por MediaProjection")
        }
        stopJourneyButton.isEnabled = active; stopJourneyButton.alpha = if (active) 1f else .45f
    }

    private fun refreshAccount() {
        val s = repo.load()
        if (::billingStatusView.isInitialized) billingStatusView.refresh()
        if (::pushSettingsView.isInitialized) pushSettingsView.refresh()
        if (::betaFeedbackView.isInitialized) betaFeedbackView.refresh()
        accountStatus.text = buildString {
            append(s.driverDisplayName)
            if (s.accountEmail.isNotBlank()) append("\n${s.accountEmail}") else if (s.deviceToken.isNotBlank()) append("\nSessão Alpha / pareamento legado") else append("\nModo local")
            append("\n${if (ConnectivityState.isOnline(this@MainActivity)) "Online" else "Offline"}  ·  ${LocalStore.get(this@MainActivity).pendingOfferCount()} pendente(s)")
        }
    }

    private fun refreshLocalHistory() {
        val store = LocalStore.get(this); val summary = JourneyCoordinator.currentSummary(this) ?: store.latestJourney()?.let { store.journeySummary(it.id) }
        val text = buildString {
            if (summary == null) append("Nenhuma jornada registrada ainda.") else {
                append(if (summary.journey.endedAt == null) "Jornada atual" else "Última jornada"); append("  ·  ${summary.offerCount} ofertas")
                append("\nBoas ${summary.goodCount}  ·  Atenção ${summary.regularCount}  ·  Abaixo ${summary.badCount}")
                summary.averagePerKm?.let { append("\nMédia R$ ${fmt(it)}/km") }; summary.averagePerHour?.let { append("  ·  R$ ${fmt(it)}/h") }
            }
            append("\nPendentes ${store.pendingOfferCount()}  ·  Capturas privadas ${PrivateScreenshotStore.count(this@MainActivity)}")
        }
        homeHistory.text = text; localHistory.text = text
    }

    private fun refreshDiagnostics() {
        latestSummary.text = repo.latestSummary()
        val method = repo.latestMethod().takeIf { it.isNotBlank() }?.let { "Método: $it\n\n" } ?: ""
        val raw = repo.latestRaw(); val log = LocalLog.tail(this, 45)
        latestRaw.text = if (raw.isNotBlank()) "$method$raw\n\n--- LOG LOCAL ---\n$log" else "Nenhum texto bruto capturado.\n\n--- LOG LOCAL ---\n$log"
    }


    private fun registerCaptureReceiver() {
        val filter = IntentFilter(AppSignals.ACTION_CAPTURE_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(captureReceiver, filter) }
    }

    private fun openWeb(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("Não foi possível abrir o navegador.") } }
    private fun sizeLabel(v: String) = when (v) { "compact" -> "Compacto"; "large" -> "Grande"; else -> "Normal" }
    private fun themeLabel(v: String) = when (v) { "light" -> "Claro"; "dark" -> "Escuro"; else -> "Automático" }
    private fun fmt(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
