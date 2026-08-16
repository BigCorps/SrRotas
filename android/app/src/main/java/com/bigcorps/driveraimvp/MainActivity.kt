package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    companion object {
        private const val REQ_MEDIA_PROJECTION = 4101
        private const val REQ_NOTIFICATIONS = 4102
    }

    private lateinit var repo: SettingsRepository
    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var backendInput: EditText
    private lateinit var pairingInput: EditText
    private lateinit var minKmInput: EditText
    private lateinit var minHourInput: EditText
    private lateinit var minFareInput: EditText
    private lateinit var maxPickupInput: EditText
    private lateinit var minProfitInput: EditText
    private lateinit var costKmInput: EditText
    private lateinit var ocrCheck: CheckBox
    private lateinit var consentCheck: CheckBox
    private lateinit var accessibilityButton: Button
    private lateinit var startJourneyButton: Button
    private lateinit var stopJourneyButton: Button
    private lateinit var overlayButton: Button
    private lateinit var pairingStatus: TextView
    private lateinit var latestSummary: TextView
    private lateinit var latestRaw: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var aiQuestionInput: EditText
    private lateinit var aiAnswer: TextView

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatus()
            refreshDiagnostics()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        setContentView(buildUi())
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        registerCaptureReceiver()
        refreshStatus()
        refreshDiagnostics()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(captureReceiver) }
        super.onPause()
    }

    @Deprecated("Deprecated in Android API, mantido sem dependência AndroidX neste Alpha.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MEDIA_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            toast("A captura não foi autorizada.")
            return
        }

        val serviceIntent = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        toast("Jornada iniciada. Abra o Uber Driver.")
        refreshStatus()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(247, 240, 200)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(44))
        }
        scroll.addView(root)

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_srrotas)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170)))

        root.addView(title("Sr. Rotas 2.0 Alpha", 27f))
        root.addView(body("Seu copiloto inteligente para avaliar ofertas do Uber em tempo real. O cálculo acontece no aparelho; a IA e o MCP entram no histórico e nas análises."))
        root.addView(space(16))

        root.addView(section("1. Iniciar jornada"))
        root.addView(body(
            "Durante uma jornada o Android compartilha a tela com o próprio Sr. Rotas. O app lê aproximadamente um frame por segundo com OCR local, descarta a imagem após o processamento e não toca no Uber. A autorização do Android é necessária em cada nova sessão."
        ))

        consentCheck = CheckBox(this).apply {
            text = "Entendi e autorizo a análise local da tela durante minhas jornadas."
            textSize = 15f
            setTextColor(Color.rgb(7, 55, 70))
            setOnCheckedChangeListener { _, _ -> saveSettings() }
        }
        root.addView(consentCheck)

        serviceStatus = body("")
        root.addView(serviceStatus)
        root.addView(space(8))

        overlayButton = actionButton("Permitir o HUD sobre outros apps") {
            openOverlayPermission()
        }
        root.addView(overlayButton)

        startJourneyButton = actionButton("▶ Iniciar jornada") { startJourney() }
        root.addView(startJourneyButton)

        stopJourneyButton = actionButton("■ Encerrar jornada") {
            stopService(Intent(this, MediaProjectionOcrService::class.java))
            repo.setProjectionActive(false)
            refreshStatus()
            toast("Jornada encerrada.")
        }
        root.addView(stopJourneyButton)

        root.addView(space(18))
        root.addView(section("2. Estratégia do motorista"))
        root.addView(body("O semáforo usa todas as metas preenchidas. Deixe 0 em uma regra que você não quiser considerar."))
        minKmInput = numeric("Mínimo R$/km — ex.: 1,80")
        minHourInput = numeric("Mínimo R$/hora — ex.: 35")
        minFareInput = numeric("Valor mínimo da oferta — ex.: 8")
        maxPickupInput = numeric("Máximo km até o passageiro — ex.: 5")
        minProfitInput = numeric("Lucro estimado mínimo — ex.: 8")
        costKmInput = numeric("Custo real estimado por km — ex.: 0,85")
        listOf(minKmInput, minHourInput, minFareInput, maxPickupInput, minProfitInput, costKmInput).forEach(root::addView)
        root.addView(actionButton("Salvar estratégia") {
            saveSettings()
            toast("Estratégia salva.")
        })

        root.addView(space(18))
        root.addView(section("3. Leitura auxiliar (opcional)"))
        root.addView(body(
            "MediaProjection é o motor principal. A Acessibilidade fica como segundo caminho de diagnóstico: tenta ler os textos expostos pelo Uber e, quando a jornada principal estiver desligada, pode testar screenshot + OCR local."
        ))
        accessibilityButton = actionButton("Abrir configurações de Acessibilidade") {
            saveSettings()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(accessibilityButton)
        ocrCheck = CheckBox(this).apply {
            text = "Permitir OCR auxiliar quando MediaProjection estiver desligado"
            textSize = 15f
            setTextColor(Color.rgb(7, 55, 70))
            setOnCheckedChangeListener { _, _ -> saveSettings() }
        }
        root.addView(ocrCheck)

        root.addView(space(18))
        root.addView(section("4. Backend Sr. Rotas"))
        backendInput = textInput("URL do backend, ex.: https://srrotas.com")
        pairingInput = textInput("Código de pareamento")
        pairingInput.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(backendInput)
        root.addView(pairingInput)
        root.addView(actionButton("Parear aparelho") { pairDevice() })
        pairingStatus = body("")
        root.addView(pairingStatus)

        root.addView(space(18))
        root.addView(section("5. Pesquisa IA"))
        root.addView(body("Pergunte sobre as ofertas estruturadas já sincronizadas. Ex.: Qual horário teve as melhores oportunidades nesta semana?"))
        aiQuestionInput = EditText(this).apply {
            hint = "Pergunte ao Sr. Rotas..."
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(aiQuestionInput)
        root.addView(actionButton("Perguntar à IA") { askAi() })
        aiAnswer = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(7, 55, 70))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(aiAnswer)

        root.addView(space(18))
        root.addView(section("6. Diagnóstico da leitura"))
        latestSummary = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(7, 55, 70))
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(latestSummary)
        latestRaw = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(latestRaw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(actionButton("Atualizar diagnóstico") { refreshDiagnostics() })

        root.addView(space(18))
        root.addView(section("Primeiro teste real"))
        root.addView(body(
            "1) Permita o HUD. 2) Toque em Iniciar jornada e autorize a tela no diálogo do Android. 3) Abra o Uber Driver. 4) Quando tocar uma oferta, veja se o HUD aparece. 5) Volte ao Sr. Rotas e copie o diagnóstico caso algum valor seja lido errado. O texto bruto fica apenas neste aparelho."
        ))
        return scroll
    }

    private fun loadSettings() {
        val s = repo.load()
        backendInput.setText(s.backendUrl)
        minKmInput.setText(s.minPerKm.toPt())
        minHourInput.setText(s.minPerHour.toPt())
        minFareInput.setText(s.minFare.toPt())
        maxPickupInput.setText(s.maxPickupKm.toPt())
        minProfitInput.setText(s.minProfit.toPt())
        costKmInput.setText(s.costPerKm.toPt())
        ocrCheck.isChecked = s.ocrEnabled
        consentCheck.isChecked = s.consentAccepted
        pairingStatus.text = if (s.deviceToken.isBlank()) "Aparelho ainda não pareado." else "✓ Aparelho pareado com o backend."
    }

    private fun saveSettings() {
        val current = repo.load()
        repo.save(
            current.copy(
                backendUrl = if (::backendInput.isInitialized) backendInput.text.toString() else current.backendUrl,
                minPerKm = valueOf(minKmInput, current.minPerKm),
                minPerHour = valueOf(minHourInput, current.minPerHour),
                minFare = valueOf(minFareInput, current.minFare),
                maxPickupKm = valueOf(maxPickupInput, current.maxPickupKm),
                minProfit = valueOf(minProfitInput, current.minProfit),
                costPerKm = valueOf(costKmInput, current.costPerKm),
                ocrEnabled = if (::ocrCheck.isInitialized) ocrCheck.isChecked else current.ocrEnabled,
                consentAccepted = if (::consentCheck.isInitialized) consentCheck.isChecked else current.consentAccepted,
            )
        )
    }

    private fun valueOf(input: EditText, fallback: Double): Double = input.numberOr(fallback)

    private fun startJourney() {
        saveSettings()
        if (!consentCheck.isChecked) {
            toast("Marque o consentimento antes de iniciar.")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Primeiro permita o HUD sobre outros apps.")
            openOverlayPermission()
            return
        }
        requestNotificationPermissionIfNeeded()
        @Suppress("DEPRECATION")
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun pairDevice() {
        saveSettings()
        pairingStatus.text = "Pareando..."
        BackendClient.pair(
            context = this,
            backendUrl = backendInput.text.toString(),
            pairingCode = pairingInput.text.toString(),
        ) { result ->
            result.onSuccess {
                pairingStatus.text = "✓ Aparelho pareado com sucesso."
                pairingInput.setText("")
            }.onFailure {
                pairingStatus.text = "Falha no pareamento: ${it.message}"
            }
        }
    }

    private fun askAi() {
        saveSettings()
        aiAnswer.text = "Consultando..."
        BackendClient.ask(this, aiQuestionInput.text.toString()) { result ->
            aiAnswer.text = result.fold(
                onSuccess = { it },
                onFailure = { "Falha: ${it.message}" },
            )
        }
    }

    private fun refreshStatus() {
        val projection = repo.isProjectionActive()
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = isAccessibilityServiceEnabled()
        serviceStatus.text = buildString {
            append(if (projection) "✓ Jornada ativa" else "○ Jornada parada")
            append("\n")
            append(if (overlay) "✓ HUD autorizado" else "○ HUD sem permissão")
            append("\n")
            append(if (accessibility) "✓ Leitura auxiliar ativa" else "○ Leitura auxiliar desligada")
        }
        stopJourneyButton.isEnabled = projection
    }

    private fun refreshDiagnostics() {
        latestSummary.text = repo.latestSummary()
        val method = repo.latestMethod().takeIf { it.isNotBlank() }?.let { "Método: $it\n\n" } ?: ""
        val raw = repo.latestRaw()
        val log = LocalLog.tail(this, 30)
        latestRaw.text = when {
            raw.isNotBlank() -> "$method$raw\n\n--- LOG LOCAL ---\n$log"
            else -> "Nenhum texto bruto capturado ainda.\n\n--- LOG LOCAL ---\n$log"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${DriverAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
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

    private fun title(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.rgb(7, 55, 70))
        setPadding(0, 0, 0, dp(8))
    }

    private fun section(text: String) = title(text, 20f)

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(45, 70, 72))
        setLineSpacing(0f, 1.12f)
    }

    private fun textInput(hint: String) = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundColor(Color.WHITE)
    }

    private fun numeric(hint: String) = textInput(hint).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun space(height: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun EditText.numberOr(default: Double): Double = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: default
    private fun Double.toPt(): String = toString().replace('.', ',')
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
