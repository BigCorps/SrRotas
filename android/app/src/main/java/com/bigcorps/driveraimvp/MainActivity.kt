package com.srrotas.app

import android.Manifest
import android.app.Activity
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
    private lateinit var strategySummary: TextView
    private lateinit var consentCheck: CheckBox
    private lateinit var serviceStatus: TextView
    private lateinit var stopJourneyButton: TextView
    private lateinit var localHistory: TextView
    private lateinit var latestSummary: TextView
    private lateinit var latestRaw: TextView
    private lateinit var aiQuestionInput: EditText
    private lateinit var aiAnswer: TextView
    private lateinit var backendInput: EditText
    private lateinit var pairingInput: EditText
    private lateinit var pairingStatus: TextView
    private lateinit var ocrCheck: CheckBox

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
    }

    override fun onResume() {
        super.onResume(); registerCaptureReceiver(); refreshAll(); BackendClient.flushPendingOffers(this)
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
            addView(UiKit.pill(this@MainActivity, "0.6 Alpha", "primary"))
        }
    }

    private fun buildBottomNav(): View {
        val p = UiKit.palette(this)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(8)); setBackgroundColor(p.surface) }
        listOf("inicio" to "Início", "jornada" to "Jornada", "historico" to "Histórico", "ia" to "IA", "perfil" to "Perfil").forEach { (key, label) ->
            val item = TextView(this).apply {
                text = label; gravity = Gravity.CENTER; textSize = 12f; setPadding(dp(4), dp(10), dp(4), dp(10)); setOnClickListener { showTab(key) }
            }
            nav[key] = item; bar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    private fun buildHomeTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.body(this, "Tudo o que você precisa para começar uma jornada, sem telas técnicas no caminho."))
        val statusCard = UiKit.card(this).apply {
            homeStatus = UiKit.title(this@MainActivity, "Pronto para rodar", 23f); addView(homeStatus)
            addView(UiKit.body(this@MainActivity, "O Sr. Rotas calcula. Você decide a corrida."))
            homeJourneyButton = UiKit.primaryButton(this@MainActivity, "Iniciar jornada") { toggleJourneyFromHome() }
            addView(UiKit.margin(homeJourneyButton, top = 14))
        }
        root.addView(UiKit.margin(statusCard, top = 14))

        root.addView(UiKit.margin(UiKit.sectionTitle(this, "Última jornada"), top = 14))
        homeHistory = UiKit.body(this, "Nenhuma jornada registrada.", 15f)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(homeHistory) }))

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
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Permitir HUD sobre outros apps") { openOverlayPermission() }, top = 12))
        root.addView(UiKit.margin(UiKit.primaryButton(this, "Iniciar jornada") { startJourney() }, top = 10))
        stopJourneyButton = UiKit.secondaryButton(this, "Encerrar jornada") { stopCurrentJourney() }
        root.addView(UiKit.margin(stopJourneyButton, top = 8))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Durante a corrida"))
            addView(UiKit.body(this@MainActivity, "• Toque no card para fechá-lo.\n• Segure e arraste para mudar a posição.\n• O tamanho, fonte e métricas ficam em Estratégia e HUD."))
        }, top = 14))
    }.first

    private fun buildHistoryTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Histórico", 27f)); root.addView(UiKit.body(this, "Resumo local das ofertas observadas. Analytics completos entram na fase de Histórico e Métricas."))
        localHistory = UiKit.body(this, "", 15f)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(localHistory) }, top = 14))
        latestSummary = UiKit.body(this, "", 15f)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(UiKit.sectionTitle(this@MainActivity, "Última leitura")); addView(latestSummary) }, top = 12))
        latestRaw = UiKit.body(this, "", 11f).apply { visibility = View.GONE; setTextIsSelectable(true) }
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(latestRaw) }, top = 10))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Mostrar / ocultar diagnóstico") { latestRaw.visibility = if (latestRaw.visibility == View.VISIBLE) View.GONE else View.VISIBLE }, top = 10))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Compartilhar diagnóstico") { DiagnosticBundle.share(this) }, top = 8))
    }.first

    private fun buildAiTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Pesquisa IA", 27f)); root.addView(UiKit.body(this, "Pergunte sobre as ofertas já observadas e sincronizadas. A IA interpreta seus dados; os cálculos básicos continuam sem IA."))
        aiQuestionInput = UiKit.input(this, "Ex.: Em quais horários apareceram as melhores ofertas esta semana?", multiline = true)
        root.addView(UiKit.margin(aiQuestionInput, top = 14))
        root.addView(UiKit.margin(UiKit.primaryButton(this, "Perguntar à IA") { askAi() }, top = 10))
        aiAnswer = UiKit.body(this, "Faça uma pergunta para começar.", 15f)
        root.addView(UiKit.margin(UiKit.card(this).apply { addView(aiAnswer) }, top = 12))
    }.first

    private fun buildProfileTab(): View = tabScroll().also { (_, root) ->
        root.addView(UiKit.title(this, "Perfil e conexão", 27f)); root.addView(UiKit.body(this, "Pareamento, sincronização, configurações e recursos avançados."))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Conta / backend"))
            backendInput = UiKit.input(this@MainActivity, "URL do backend")
            pairingInput = UiKit.input(this@MainActivity, "Código de pareamento").apply { inputType = InputType.TYPE_CLASS_NUMBER }
            addView(backendInput); addView(UiKit.margin(pairingInput, top = 8))
            addView(UiKit.margin(UiKit.primaryButton(this@MainActivity, "Parear aparelho") { pairDevice() }, top = 10))
            pairingStatus = UiKit.body(this@MainActivity, "", 13f); addView(UiKit.margin(pairingStatus, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Sincronizar agora") { saveBaseSettings(); BackendClient.syncPreferences(this@MainActivity); BackendClient.flushPendingOffers(this@MainActivity); toast("Sincronização solicitada.") }, top = 10))
        }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Estratégia e HUD") { startActivity(Intent(this, StrategyActivity::class.java)) }, top = 12))
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@MainActivity, "Avançado"))
            ocrCheck = CheckBox(this@MainActivity).apply { text = "Permitir leitura auxiliar quando a MediaProjection estiver desligada"; setTextColor(UiKit.palette(this@MainActivity).ink); setOnCheckedChangeListener { _, _ -> saveBaseSettings() } }
            addView(ocrCheck)
            addView(UiKit.margin(UiKit.secondaryButton(this@MainActivity, "Abrir Acessibilidade") { saveBaseSettings(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, top = 8))
        }, top = 12))
        root.addView(UiKit.margin(UiKit.body(this, "Sr. Rotas — desenvolvido pela BigCorps\nSuporte: contato@bigcorps.com.br\nVersão ${BuildConfig.VERSION_NAME}", 13f), top = 18, bottom = 24))
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
    }

    private fun toggleJourneyFromHome() { if (repo.isProjectionActive() || repo.currentJourneyId().isNotBlank()) stopCurrentJourney() else startJourney() }
    private fun loadSettings() { val s = repo.load(); backendInput.setText(s.backendUrl); ocrCheck.isChecked = s.ocrEnabled; consentCheck.isChecked = s.consentAccepted; pairingStatus.text = if (s.deviceToken.isBlank()) "Aparelho ainda não pareado." else "Aparelho pareado e pronto para sincronizar." }
    private fun saveBaseSettings() { val c = repo.load(); repo.save(c.copy(backendUrl = backendInput.text.toString(), ocrEnabled = ocrCheck.isChecked, consentAccepted = consentCheck.isChecked)) }

    private fun startJourney() {
        saveBaseSettings(); if (!consentCheck.isChecked) { toast("Marque o consentimento antes de iniciar."); showTab("jornada"); return }
        if (!Settings.canDrawOverlays(this)) { toast("Primeiro permita o HUD."); openOverlayPermission(); return }
        requestNotificationPermissionIfNeeded(); @Suppress("DEPRECATION") startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
    }

    private fun stopCurrentJourney() { stopService(Intent(this, MediaProjectionOcrService::class.java)); repo.setProjectionActive(false); JourneyCoordinator.endJourney(this, "user_stop"); refreshAll(); toast("Jornada encerrada.") }
    private fun openOverlayPermission() { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
    private fun requestNotificationPermissionIfNeeded() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS) }

    private fun pairDevice() { saveBaseSettings(); pairingStatus.text = "Pareando..."; BackendClient.pair(this, backendInput.text.toString(), pairingInput.text.toString()) { r -> r.onSuccess { pairingStatus.text = "Aparelho pareado com sucesso."; pairingInput.setText(""); BackendClient.syncPreferences(this); BackendClient.flushPendingOffers(this) }.onFailure { pairingStatus.text = "Falha no pareamento: ${it.message}" } } }
    private fun askAi() { saveBaseSettings(); aiAnswer.text = "Consultando..."; BackendClient.ask(this, aiQuestionInput.text.toString()) { r -> aiAnswer.text = r.fold({ it }, { "Falha: ${it.message}" }) } }

    private fun refreshAll() { if (!::homeStatus.isInitialized) return; refreshStatus(); refreshLocalHistory(); refreshStrategy(); refreshDiagnostics() }
    private fun refreshStrategy() { val s = repo.load(); strategySummary.text = "Meta: R$ ${fmt(s.minPerMinute)}/min  ·  R$ ${fmt(s.minPerKm)}/km  ·  R$ ${fmt(s.minPerHour)}/h\nHUD: ${sizeLabel(s.hudCardSize)}  ·  ${themeLabel(s.hudTheme)}  ·  fonte ${s.hudFontSize}" }

    private fun refreshStatus() {
        val projection = repo.isProjectionActive(); val current = repo.currentJourneyId(); val overlay = Settings.canDrawOverlays(this); val accessibility = isAccessibilityServiceEnabled()
        val active = projection || current.isNotBlank()
        homeStatus.text = if (active) "Jornada em andamento" else "Pronto para rodar"
        homeJourneyButton.text = if (active) "Encerrar jornada" else "Iniciar jornada"
        serviceStatus.text = buildString { append(if (active) "Jornada ativa" else "Jornada parada"); if (current.isNotBlank()) append("  ·  ${current.take(8)}"); append("\n"); append(if (overlay) "HUD autorizado" else "HUD sem permissão"); append("  ·  "); append(if (accessibility) "auxiliar ativo" else "auxiliar desligado") }
        stopJourneyButton.isEnabled = active; stopJourneyButton.alpha = if (active) 1f else .45f
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

    private fun refreshDiagnostics() { latestSummary.text = repo.latestSummary(); val method = repo.latestMethod().takeIf { it.isNotBlank() }?.let { "Método: $it\n\n" } ?: ""; val raw = repo.latestRaw(); val log = LocalLog.tail(this, 45); latestRaw.text = if (raw.isNotBlank()) "$method$raw\n\n--- LOG LOCAL ---\n$log" else "Nenhum texto bruto capturado.\n\n--- LOG LOCAL ---\n$log" }
    private fun isAccessibilityServiceEnabled(): Boolean { val expected = "$packageName/${DriverAccessibilityService::class.java.name}"; val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false; return enabled.split(':').any { it.equals(expected, true) } }
    private fun registerCaptureReceiver() { val filter = IntentFilter(AppSignals.ACTION_CAPTURE_UPDATED); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED) else { @Suppress("DEPRECATION") registerReceiver(captureReceiver, filter) } }
    private fun sizeLabel(v: String) = when (v) { "compact" -> "Compacto"; "large" -> "Grande"; else -> "Normal" }
    private fun themeLabel(v: String) = when (v) { "light" -> "Claro"; "dark" -> "Escuro"; else -> "Automático" }
    private fun fmt(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
