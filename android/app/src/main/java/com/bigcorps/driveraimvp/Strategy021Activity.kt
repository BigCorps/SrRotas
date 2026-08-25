package com.srrotas.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class Strategy021Activity : Activity() {
    private val repo by lazy { SettingsRepository(this) }
    private lateinit var km: EditText
    private lateinit var minutes: EditText
    private lateinit var theme: Spinner
    private lateinit var hudTheme: Spinner
    private lateinit var collective: CheckBox
    private lateinit var summary: TextView
    private lateinit var presetBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@Strategy021Activity, 16), UiKit.dp(this@Strategy021Activity, 16), UiKit.dp(this@Strategy021Activity, 16), UiKit.dp(this@Strategy021Activity, 30))
            setBackgroundColor(UiKit.palette(this@Strategy021Activity).background)
        }
        scroll.addView(root)
        setContentView(scroll)
        UiKit.applySafeArea(scroll)

        root.addView(UiKit.title(this, "Estratégia", 28f))
        summary = UiKit.body(this, "", 13f)
        root.addView(UiKit.margin(summary, top = 8))

        presetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@Strategy021Activity, "Perfil de serviço"))
            addView(presetBox)
            addView(UiKit.margin(UiKit.secondaryButton(this@Strategy021Activity, "Personalizar métricas e Painel de Rota") {
                Strategy021Store.savePreset(this@Strategy021Activity, "custom")
                startActivity(Intent(this@Strategy021Activity, StrategyActivity::class.java))
            }, top = 8))
        }, top = 12))

        val s = repo.load()
        val x = Strategy021Store.load(this)
        km = UiKit.input(this, "Distância máxima para busca — km · 0 desativa", numeric = true).apply { setText(formatInput(s.maxPickupKm)) }
        minutes = UiKit.input(this, "Tempo máximo para busca — min · 0 desativa", numeric = true).apply { setText(x.maxPickupMinutes.toString()) }
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@Strategy021Activity, "Busca"))
            addView(km)
            addView(UiKit.margin(minutes, top = 7))
            addView(UiKit.margin(UiKit.body(this@Strategy021Activity, "Exceder quilômetros OU minutos classifica a busca como alta. Se só um dado estiver disponível, apenas ele é considerado.", 11f), top = 7))
        }, top = 12))

        theme = Spinner(this).apply {
            adapter = ArrayAdapter(this@Strategy021Activity, android.R.layout.simple_spinner_dropdown_item, listOf("Automático", "Claro", "Escuro"))
            setSelection(when (x.appTheme) { "light" -> 1; "dark" -> 2; else -> 0 })
        }
        hudTheme = Spinner(this).apply {
            adapter = ArrayAdapter(this@Strategy021Activity, android.R.layout.simple_spinner_dropdown_item, listOf("Seguir aplicativo", "Claro", "Escuro"))
            setSelection(when (x.hudThemeMode) { "light" -> 1; "dark" -> 2; else -> 0 })
        }
        collective = CheckBox(this).apply {
            text = "Participar da base coletiva"
            isChecked = s.collectiveStatsOptIn
            setTextColor(UiKit.palette(this@Strategy021Activity).ink)
        }
        root.addView(UiKit.margin(UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@Strategy021Activity, "Aparência e inteligência"))
            addView(UiKit.body(this@Strategy021Activity, "Tema do aplicativo")); addView(theme)
            addView(UiKit.margin(UiKit.body(this@Strategy021Activity, "Tema do HUD/menu"), top = 8)); addView(hudTheme)
            addView(UiKit.margin(collective, top = 8))
        }, top = 12))

        root.addView(UiKit.margin(UiKit.primaryButton(this, "Salvar") { save() }, top = 14))
        root.addView(UiKit.margin(UiKit.secondaryButton(this, "Abrir versão Web") { openWeb() }, top = 8))
        renderPresetButtons()
        refreshSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::summary.isInitialized) {
            refreshSummary()
            renderPresetButtons()
        }
    }

    private fun renderPresetButtons() {
        presetBox.removeAllViews()
        val active = Strategy021Store.load(this).strategyPreset
        listOf("popular" to "Popular", "comfort" to "Conforto", "premium" to "Premium").forEachIndexed { index, (key, label) ->
            val button = if (active == key) {
                UiKit.primaryButton(this, "✓ $label") { applyPreset(key) }
            } else {
                UiKit.secondaryButton(this, label) { applyPreset(key) }
            }
            presetBox.addView(if (index == 0) button else UiKit.margin(button, top = 6))
        }
    }

    private fun applyPreset(preset: String) {
        StrategyPresets021.apply(this, preset)
        renderPresetButtons()
        refreshSummary()
        Preference021Sync.sync(this)
        Toast.makeText(this, "${presetName(preset)} aplicado.", Toast.LENGTH_SHORT).show()
    }

    private fun save() {
        val s = repo.load()
        val maxKm = km.text.toString().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: s.maxPickupKm
        val maxMin = minutes.text.toString().toIntOrNull()?.coerceIn(0, 120) ?: Strategy021Store.load(this).maxPickupMinutes
        val appTheme = when (theme.selectedItemPosition) { 1 -> "light"; 2 -> "dark"; else -> "auto" }
        repo.save(s.copy(maxPickupKm = maxKm, collectiveStatsOptIn = collective.isChecked))
        Strategy021Store.saveMaxPickupMinutes(this, maxMin)
        Strategy021Store.saveAppTheme(this, appTheme)
        val hm = when (hudTheme.selectedItemPosition) { 1 -> "light"; 2 -> "dark"; else -> "follow_app" }
        Strategy021Store.saveHudThemeMode(this, hm)
        val resolvedHud = when (hm) { "light" -> "light"; "dark" -> "dark"; else -> appTheme }
        repo.save(repo.load().copy(hudTheme = resolvedHud))
        if (Strategy021Store.load(this).strategyPreset != "custom" && !matchesPreset(repo.load(), Strategy021Store.load(this).strategyPreset)) {
            Strategy021Store.savePreset(this, "custom")
        }
        BackendClient.syncPreferences(this)
        Preference021Sync.sync(this)
        Toast.makeText(this, "Estratégia salva.", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun refreshSummary() {
        val s = repo.load()
        var x = Strategy021Store.load(this)
        if (x.strategyPreset != "custom" && !matchesPreset(s, x.strategyPreset)) {
            Strategy021Store.savePreset(this, "custom")
            x = Strategy021Store.load(this)
        }
        summary.text = "${presetName(x.strategyPreset)}\nR$/km ${f(s.redPerKmBelow)} → ${f(s.minPerKm)}  ·  R$/min ${f(s.redPerMinuteBelow)} → ${f(s.minPerMinute)}  ·  R$/h ${f(s.redPerHourBelow)} → ${f(s.minPerHour)}\nBusca máxima: ${f(s.maxPickupKm)} km / ${x.maxPickupMinutes} min"
    }

    private fun matchesPreset(s: DriverSettings, p: String): Boolean = when (p) {
        "popular" -> close(s.redPerKmBelow, 1.2) && close(s.minPerKm, 1.5) && close(s.redPerMinuteBelow, .4) && close(s.minPerMinute, .5) && close(s.redPerHourBelow, 24.0) && close(s.minPerHour, 30.0)
        "comfort" -> close(s.redPerKmBelow, 1.5) && close(s.minPerKm, 1.8) && close(s.redPerMinuteBelow, .5) && close(s.minPerMinute, .65) && close(s.redPerHourBelow, 30.0) && close(s.minPerHour, 39.0)
        "premium" -> close(s.redPerKmBelow, 1.8) && close(s.minPerKm, 2.2) && close(s.redPerMinuteBelow, .65) && close(s.minPerMinute, .85) && close(s.redPerHourBelow, 39.0) && close(s.minPerHour, 51.0)
        else -> false
    }

    private fun close(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.0001
    private fun presetName(p: String) = when (p) { "popular" -> "Popular"; "comfort" -> "Conforto"; "premium" -> "Premium"; else -> "Personalizado" }
    private fun f(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f", v)
    private fun formatInput(v: Double) = f(v).trimEnd('0').trimEnd(',')
    private fun openWeb() { WebHandoff021.open(this, "/app/perfil") }
}
