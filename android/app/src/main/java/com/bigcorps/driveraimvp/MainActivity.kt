package com.bigcorps.driveraimvp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var repo: SettingsRepository
    private lateinit var backendInput: EditText
    private lateinit var pairingInput: EditText
    private lateinit var minKmInput: EditText
    private lateinit var minHourInput: EditText
    private lateinit var costKmInput: EditText
    private lateinit var ocrCheck: CheckBox
    private lateinit var consentCheck: CheckBox
    private lateinit var accessibilityButton: Button
    private lateinit var pairingStatus: TextView
    private lateinit var latestSummary: TextView
    private lateinit var latestRaw: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var aiQuestionInput: EditText
    private lateinit var aiAnswer: TextView

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshDiagnostics()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(this)
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

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(40))
        }
        scroll.addView(root)

        root.addView(title("Driver AI MVP", 28f))
        root.addView(body("Primeiro protótipo para observar ofertas do Uber Driver, calcular rentabilidade localmente e registrar dados no backend."))
        root.addView(space(14))

        root.addView(section("1. Acessibilidade e privacidade"))
        root.addView(body(
            "O aplicativo usa o Serviço de Acessibilidade exclusivamente para ler informações exibidas pelo Uber Driver (como valor, distância e tempo). " +
                "Se o texto não estiver disponível na árvore da interface e o OCR estiver ativado, o serviço pode capturar a tela do Uber e reconhecer texto localmente no aparelho. " +
                "A imagem não é enviada nem armazenada no backend. O app não aceita ou rejeita corridas e não executa toques no Uber."
        ))

        consentCheck = CheckBox(this).apply {
            text = "Li a explicação e autorizo esse uso durante meus testes."
            textSize = 15f
            setOnCheckedChangeListener { _, checked ->
                accessibilityButton.isEnabled = checked
                saveSettings()
            }
        }
        root.addView(consentCheck)

        serviceStatus = body("")
        root.addView(serviceStatus)

        accessibilityButton = Button(this).apply {
            text = "Abrir configurações de Acessibilidade"
            isAllCaps = false
            setOnClickListener {
                saveSettings()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(accessibilityButton)

        ocrCheck = CheckBox(this).apply {
            text = "Usar screenshot + OCR local quando necessário"
            textSize = 15f
            setOnCheckedChangeListener { _, _ -> saveSettings() }
        }
        root.addView(ocrCheck)

        root.addView(space(18))
        root.addView(section("2. Regras locais"))
        minKmInput = numeric("Mínimo R$/km (ex.: 1,80)")
        minHourInput = numeric("Mínimo R$/hora (ex.: 35)")
        costKmInput = numeric("Custo estimado do carro por km (ex.: 0,85)")
        root.addView(minKmInput)
        root.addView(minHourInput)
        root.addView(costKmInput)
        root.addView(Button(this).apply {
            text = "Salvar regras"
            isAllCaps = false
            setOnClickListener {
                saveSettings()
                toast("Regras salvas.")
            }
        })

        root.addView(space(18))
        root.addView(section("3. Backend separado"))
        backendInput = textInput("URL do backend, ex.: https://seu-backend.vercel.app")
        pairingInput = textInput("Código de pareamento")
        pairingInput.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(backendInput)
        root.addView(pairingInput)
        root.addView(Button(this).apply {
            text = "Parear aparelho"
            isAllCaps = false
            setOnClickListener { pairDevice() }
        })
        pairingStatus = body("")
        root.addView(pairingStatus)

        root.addView(space(18))
        root.addView(section("4. Pesquisa IA"))
        root.addView(body("Pergunte sobre as ofertas já registradas. Ex.: Qual horário está rendendo melhor esta semana?"))
        aiQuestionInput = EditText(this).apply {
            hint = "Pergunte sobre seus dados..."
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(aiQuestionInput)
        root.addView(Button(this).apply {
            text = "Perguntar à IA"
            isAllCaps = false
            setOnClickListener { askAi() }
        })
        aiAnswer = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(aiAnswer)

        root.addView(space(18))
        root.addView(section("5. Última captura"))
        latestSummary = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(latestSummary)
        latestRaw = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        root.addView(latestRaw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply {
            text = "Atualizar diagnóstico"
            isAllCaps = false
            setOnClickListener { refreshDiagnostics() }
        })

        root.addView(space(18))
        root.addView(section("O que observar no primeiro teste"))
        root.addView(body(
            "Quando surgir uma oferta real, verifique se aparece um overlay com valor, R$/km e R$/hora. " +
                "Mesmo se o parser não reconhecer tudo, volte aqui e copie o texto bruto capturado: ele será a base para calibrarmos o layout atual do Uber."
        ))
        return scroll
    }

    private fun loadSettings() {
        val s = repo.load()
        backendInput.setText(s.backendUrl)
        minKmInput.setText(s.minPerKm.toPt())
        minHourInput.setText(s.minPerHour.toPt())
        costKmInput.setText(s.costPerKm.toPt())
        ocrCheck.isChecked = s.ocrEnabled
        consentCheck.isChecked = s.consentAccepted
        accessibilityButton.isEnabled = s.consentAccepted
        pairingStatus.text = if (s.deviceToken.isBlank()) "Aparelho ainda não pareado." else "Aparelho pareado com o backend."
    }

    private fun saveSettings() {
        val current = repo.load()
        repo.save(
            current.copy(
                backendUrl = if (::backendInput.isInitialized) backendInput.text.toString() else current.backendUrl,
                minPerKm = if (::minKmInput.isInitialized) minKmInput.numberOr(current.minPerKm) else current.minPerKm,
                minPerHour = if (::minHourInput.isInitialized) minHourInput.numberOr(current.minPerHour) else current.minPerHour,
                costPerKm = if (::costKmInput.isInitialized) costKmInput.numberOr(current.costPerKm) else current.costPerKm,
                ocrEnabled = if (::ocrCheck.isInitialized) ocrCheck.isChecked else current.ocrEnabled,
                consentAccepted = if (::consentCheck.isInitialized) consentCheck.isChecked else current.consentAccepted,
            )
        )
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
                pairingStatus.text = "Aparelho pareado com sucesso."
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
        val enabled = isAccessibilityServiceEnabled()
        serviceStatus.text = if (enabled) "✓ Serviço de Acessibilidade ativo" else "Serviço de Acessibilidade ainda não está ativo."
    }

    private fun refreshDiagnostics() {
        latestSummary.text = repo.latestSummary()
        val method = repo.latestMethod().takeIf { it.isNotBlank() }?.let { "Método: $it\n\n" } ?: ""
        val raw = repo.latestRaw()
        val log = LocalLog.tail(this, 25)
        latestRaw.text = when {
            raw.isNotBlank() -> "$method$raw\n\n--- LOG ---\n$log"
            else -> "Nenhum texto bruto capturado ainda.\n\n--- LOG ---\n$log"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${DriverAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun registerCaptureReceiver() {
        val filter = IntentFilter(DriverAccessibilityService.ACTION_CAPTURE_UPDATED)
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
        setTextColor(Color.BLACK)
        setPadding(0, 0, 0, dp(8))
    }

    private fun section(text: String) = title(text, 20f)

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.DKGRAY)
        setLineSpacing(0f, 1.12f)
    }

    private fun textInput(hint: String) = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun numeric(hint: String) = textInput(hint).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun space(height: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun EditText.numberOr(default: Double): Double = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: default
    private fun Double.toPt(): String = toString().replace('.', ',')
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
