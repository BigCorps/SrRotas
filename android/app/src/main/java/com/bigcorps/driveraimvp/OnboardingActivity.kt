package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import java.time.Instant

class OnboardingActivity : Activity() {
    companion object { private const val REQ_NOTIFICATIONS = 7701 }

    private lateinit var repo: SettingsRepository
    private lateinit var content: FrameLayout
    private lateinit var progress: TextView
    private var step: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        UiKit.applySystemBars(this)
        step = repo.load().onboardingStep.coerceIn(0, 5)
        setContentView(buildShell())
        renderStep()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && step == 2) renderStep()
    }

    private fun buildShell(): View {
        val p = UiKit.palette(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.background) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10)); setBackgroundColor(p.surface)
            addView(TextView(this@OnboardingActivity).apply { text = "SR"; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(p.primaryDark); gravity = Gravity.CENTER; background = UiKit.rounded(this@OnboardingActivity, p.surfaceAlt, 16) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(LinearLayout(this@OnboardingActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                addView(UiKit.title(this@OnboardingActivity, "Configurar Sr. Rotas", 20f))
                progress = UiKit.body(this@OnboardingActivity, "", 12f); addView(progress)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(top)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun renderStep() {
        content.removeAllViews()
        progress.text = "Etapa ${step + 1} de 6"
        val (scroll, root) = page()
        when (step) {
            0 -> renderWelcome(root)
            1 -> renderAccount(root)
            2 -> renderPermissions(root)
            3 -> renderStrategy(root)
            4 -> renderHudTutorial(root)
            else -> renderReady(root)
        }
        content.addView(scroll)
        repo.markOnboardingStep(step)
    }

    private fun renderWelcome(root: LinearLayout) {
        val s = repo.load()
        root.addView(UiKit.title(this, "Bem-vindo ao Sr. Rotas", 28f))
        root.addView(UiKit.body(this, "Você decide a corrida. O Sr. Rotas faz as contas. Vamos deixar tudo pronto sem termos técnicos desnecessários."))
        val card = UiKit.card(this)
        card.addView(UiKit.sectionTitle(this, "Como funciona"))
        card.addView(UiKit.body(this, "1. Você conecta sua conta/aparelho.\\n2. Libera o HUD.\\n3. Escolhe uma estratégia.\\n4. Testa o card.\\n5. Inicia a primeira jornada."))
        root.addView(UiKit.margin(card, top = 16))
        val name = UiKit.input(this, "Como podemos chamar você?").apply { setText(s.driverDisplayName.takeUnless { it == "Motorista" } ?: "") }
        root.addView(UiKit.margin(name, top = 14))
        root.addView(UiKit.margin(UiKit.primaryButton(this, "Continuar") {
            val value = name.text.toString().trim().ifBlank { "Motorista" }
            repo.save(repo.load().copy(driverDisplayName = value))
            next()
        }, top = 12))
    }

    private fun renderAccount(root: LinearLayout) {
        val s = repo.load()
        root.addView(UiKit.title(this, "Conectar aparelho", 28f))
        root.addView(UiKit.body(this, "A conta mantém seu histórico e preferências ligados ao motorista. O token do aparelho fica salvo somente no aplicativo."))

        if (s.deviceToken.isNotBlank()) {
            val card = UiKit.card(this)
            card.addView(UiKit.pill(this, "APARELHO CONECTADO", "good"))
            card.addView(UiKit.margin(UiKit.title(this, s.driverDisplayName, 20f), top = 10))
            card.addView(UiKit.body(this, if (s.accountEmail.isBlank()) "Sessão Alpha existente. Você pode continuar sem perder os testes atuais." else s.accountEmail))
            card.addView(UiKit.margin(UiKit.secondaryButton(this, "Atualizar dados da conta") {
                BackendClient.fetchAccount(this) { result ->
                    result.onSuccess { toast("Conta atualizada."); renderStep() }.onFailure { toast("Não foi possível atualizar: ${it.message}") }
                }
            }, top = 10))
            root.addView(UiKit.margin(card, top = 14))
            root.addView(UiKit.margin(UiKit.primaryButton(this, "Continuar com este aparelho") { next() }, top = 12))
            return
        }

        val email = UiKit.input(this, "E-mail").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = UiKit.input(this, "Senha — mínimo 8 caracteres").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val name = UiKit.input(this, "Nome").apply { setText(s.driverDisplayName.takeUnless { it == "Motorista" } ?: "") }
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@OnboardingActivity, "Criar ou entrar"))
            addView(email); addView(UiKit.margin(password, top = 8)); addView(UiKit.margin(name, top = 8))
            addView(UiKit.margin(UiKit.primaryButton(this@OnboardingActivity, "Criar conta") {
                val display = name.text.toString().trim().ifBlank { "Motorista" }
                BackendClient.registerAccount(this@OnboardingActivity, email.text.toString(), password.text.toString(), display) { result ->
                    result.onSuccess { toast("Conta criada e aparelho conectado."); next() }
                        .onFailure { toast("Não foi possível criar: ${it.message}") }
                }
            }, top = 10))
            addView(UiKit.margin(UiKit.secondaryButton(this@OnboardingActivity, "Entrar") {
                BackendClient.loginAccount(this@OnboardingActivity, email.text.toString(), password.text.toString()) { result ->
                    result.onSuccess { toast("Aparelho conectado."); next() }
                        .onFailure { toast("Não foi possível entrar: ${it.message}") }
                }
            }, top = 8))
        }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Continuar em modo local para teste") {
            toast("Modo local: ofertas continuam sendo analisadas, mas não sincronizam sem sessão.")
            next()
        }, top = 12))
    }

    private fun renderPermissions(root: LinearLayout) {
        root.addView(UiKit.title(this, "Permissões essenciais", 28f))
        root.addView(UiKit.body(this, "O objetivo é pedir somente o necessário. A captura da tela será autorizada pelo Android a cada jornada."))

        val overlay = Settings.canDrawOverlays(this)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        root.addView(UiKit.margin(statusCard("HUD sobre outros apps", overlay, "Necessário para mostrar a rentabilidade sem sair do Uber.", "Permitir HUD") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, top = 14))
        root.addView(UiKit.margin(statusCard("Notificações", notifications, "Usadas para manter a jornada ativa e, se você quiser, avisos do Sr. Rotas.", "Permitir notificações") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }, top = 10))

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.pill(this@OnboardingActivity, "NA HORA DA JORNADA", "primary"))
            addView(UiKit.margin(UiKit.title(this@OnboardingActivity, "Captura da tela", 18f), top = 8))
            addView(UiKit.body(this@OnboardingActivity, "O Android mostra o aviso de MediaProjection sempre que você iniciar uma jornada. Não existe autorização escondida ou permanente."))
        }, top = 10))

        root.addView(UiKit.margin(UiKit.primaryButton(this, if (overlay) "Continuar" else "Continuar mesmo assim") { next() }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { back() }, top = 8))
    }

    private fun renderStrategy(root: LinearLayout) {
        root.addView(UiKit.title(this, "Escolha seu ponto de partida", 28f))
        root.addView(UiKit.body(this, "Você pode alterar tudo depois. O objetivo aqui é começar com uma configuração coerente."))
        val size = Spinner(this).apply { adapter = ArrayAdapter(this@OnboardingActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Compacto", "Normal", "Grande")); setSelection(1) }

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@OnboardingActivity, "Estratégia"))
            addView(UiKit.primaryButton(this@OnboardingActivity, "Equilibrado — recomendado") { applyPreset("balanced"); toast("Equilibrado aplicado.") })
            addView(UiKit.margin(UiKit.secondaryButton(this@OnboardingActivity, "Conservador") { applyPreset("conservative"); toast("Conservador aplicado.") }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@OnboardingActivity, "Volume") { applyPreset("volume"); toast("Volume aplicado.") }, top = 8))
            addView(UiKit.margin(UiKit.body(this@OnboardingActivity, "Tamanho inicial do card"), top = 12))
            addView(size)
        }, top = 14))

        root.addView(UiKit.margin(UiKit.primaryButton(this, "Salvar e continuar") {
            val current = repo.load()
            repo.save(current.copy(hudCardSize = listOf("compact", "normal", "large")[size.selectedItemPosition]))
            BackendClient.syncPreferences(this)
            next()
        }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { back() }, top = 8))
    }

    private fun renderHudTutorial(root: LinearLayout) {
        root.addView(UiKit.title(this, "Teste o Painel de Rota", 28f))
        root.addView(UiKit.body(this, "Faça isso parado. O card foi pensado para exigir o mínimo de atenção quando você estiver na rua."))

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@OnboardingActivity, "Gestos"))
            addView(UiKit.body(this@OnboardingActivity, "• Toque: fecha o card atual.\\n• Segure por cerca de 0,4 s e arraste: muda a posição.\\n• A posição fica salva para as próximas ofertas."))
            addView(UiKit.margin(UiKit.primaryButton(this@OnboardingActivity, "Pré-visualizar HUD") { previewHud() }, top = 12))
            addView(UiKit.margin(UiKit.secondaryButton(this@OnboardingActivity, "Abrir ajustes completos") { startActivity(Intent(this@OnboardingActivity, StrategyActivity::class.java)) }, top = 8))
        }, top = 14))

        root.addView(UiKit.margin(UiKit.primaryButton(this, "Entendi, continuar") { next() }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { back() }, top = 8))
    }

    private fun renderReady(root: LinearLayout) {
        val s = repo.load()
        val overlay = Settings.canDrawOverlays(this)
        val paired = s.deviceToken.isNotBlank()
        val online = ConnectivityState.isOnline(this)

        root.addView(UiKit.title(this, "Tudo pronto para testar", 28f))
        root.addView(UiKit.body(this, "Você pode começar a primeira jornada agora ou voltar ao aplicativo. Se algo estiver pendente, o Sr. Rotas mostra claramente na tela inicial."))

        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(checkLine("Nome", s.driverDisplayName.isNotBlank(), s.driverDisplayName))
            addView(checkLine("Conta / aparelho", paired, if (paired) "Conectado" else "Modo local"))
            addView(checkLine("HUD", overlay, if (overlay) "Autorizado" else "Permissão pendente"))
            addView(checkLine("Internet", online, if (online) "Online" else "Offline"))
            addView(checkLine("Estratégia", true, "${s.hudCardSize} · R$ ${fmt(s.minPerKm)}/km"))
        }, top = 14))

        root.addView(UiKit.margin(UiKit.primaryButton(this, "Concluir configuração") {
            repo.completeOnboarding()
            val latest = repo.load()
            BackendClient.updateAccountProfile(this, latest.driverDisplayName, true)
            setResult(RESULT_OK)
            finish()
        }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { back() }, top = 8))
    }

    private fun statusCard(title: String, ok: Boolean, help: String, actionLabel: String, action: () -> Unit): View =
        UiKit.card(this).apply {
            addView(UiKit.pill(this@OnboardingActivity, if (ok) "PRONTO" else "PENDENTE", if (ok) "good" else "warn"))
            addView(UiKit.margin(UiKit.title(this@OnboardingActivity, title, 19f), top = 8))
            addView(UiKit.body(this@OnboardingActivity, help))
            if (!ok) addView(UiKit.margin(UiKit.primaryButton(this@OnboardingActivity, actionLabel, action), top = 10))
        }

    private fun checkLine(label: String, ok: Boolean, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
        addView(UiKit.pill(this@OnboardingActivity, if (ok) "✓" else "!", if (ok) "good" else "warn"))
        addView(UiKit.body(this@OnboardingActivity, label, 14f).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(UiKit.body(this@OnboardingActivity, value, 13f))
    }

    private fun previewHud() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Autorize o HUD primeiro.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val sample = RideOffer(
            observedAt = Instant.now().toString(), sourcePackage = "preview", captureMethod = "preview", rawText = "preview",
            fare = 28.75, pickupKm = 1.2, tripKm = 7.3, totalKm = 8.5, pickupMinutes = 5, tripMinutes = 20, totalMinutes = 25,
            perKm = 3.38, perHour = 69.0, perMinute = 1.15, estimatedCost = 7.23, estimatedProfit = 21.52, profitPerHour = 51.65,
            profitPercent = 74.85, passengerRating = 4.95, advertisedPerKm = 3.38, serviceType = "uberx", verdict = "boa", confidence = .99,
            offerType = "exclusive", dedupeKey = "onboarding-preview"
        )
        OverlayController(this).show(sample, 15000)
    }

    private fun applyPreset(kind: String) {
        val s = repo.load()
        val updated = when (kind) {
            "conservative" -> s.copy(redPerKmBelow = 1.80, minPerKm = 2.20, redPerHourBelow = 35.0, minPerHour = 45.0, redRatingBelow = 4.75, goodRatingFrom = 4.90, redPerMinuteBelow = 0.60, minPerMinute = 0.75)
            "volume" -> s.copy(redPerKmBelow = 1.20, minPerKm = 1.50, redPerHourBelow = 24.0, minPerHour = 30.0, redRatingBelow = 4.65, goodRatingFrom = 4.80, redPerMinuteBelow = 0.40, minPerMinute = 0.50)
            else -> s.copy(redPerKmBelow = 1.45, minPerKm = 1.80, redPerHourBelow = 28.0, minPerHour = 35.0, redRatingBelow = 4.70, goodRatingFrom = 4.85, redPerMinuteBelow = 0.48, minPerMinute = 0.60)
        }
        repo.save(updated)
    }

    private fun next() { step = (step + 1).coerceAtMost(5); repo.markOnboardingStep(step); renderStep() }
    private fun back() { step = (step - 1).coerceAtLeast(0); repo.markOnboardingStep(step); renderStep() }
    private fun page(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply { setFillViewport(true) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(32)); setBackgroundColor(UiKit.palette(this@OnboardingActivity).background) }
        scroll.addView(root); return scroll to root
    }
    private fun fmt(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
