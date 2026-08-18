package com.srrotas.app

import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

class PushSettingsView(context: Context) : LinearLayout(context) {
    private val status: TextView = UiKit.body(context, "", 12f)
    private val operational = check("Atualizações e compatibilidade")
    private val journey = check("Resumo ao encerrar uma jornada")
    private val sync = check("Avisos importantes de sincronização")
    private val product = check("Novidades do produto")
    private var loading = true

    init {
        orientation = VERTICAL
        addView(UiKit.sectionTitle(context, "Notificações"))
        addView(UiKit.body(context, "Alertas operacionais do Sr. Rotas. Ofertas em tempo real e o HUD continuam locais e não dependem de push.", 12f))
        addView(UiKit.margin(status, top = 8))
        addView(UiKit.margin(UiKit.primaryButton(context, "Ativar notificações") {
            PushManager.requestPermission(context) { result ->
                status.text = result.fold(
                    { if (it) "Notificações autorizadas neste aparelho." else "Permissão não concedida." },
                    { if (it.message == "onesignal_not_configured") "OneSignal ainda não configurado neste build." else "Falha: ${it.message}" }
                )
                refresh()
            }
        }, top = 9))
        addView(UiKit.margin(operational, top = 8))
        addView(journey)
        addView(sync)
        addView(product)
        addView(UiKit.margin(UiKit.secondaryButton(context, "Enviar notificação de teste") {
            status.text = "Enviando teste..."
            BackendClient.sendTestPush(context) { result ->
                status.text = result.fold({ it }, { "Falha no teste: ${it.message}" })
            }
        }, top = 9))
        bind(operational); bind(journey); bind(sync); bind(product)
        refresh()
    }

    fun refresh() {
        if (SettingsRepository(context).load().deviceToken.isBlank()) {
            loading = true
            status.text = "Conecte sua conta para configurar notificações."
            operational.isEnabled = false; journey.isEnabled = false; sync.isEnabled = false; product.isEnabled = false
            loading = false
            return
        }
        val configured = PushManager.isConfigured(context)
        status.text = when {
            !configured -> "Integração pronta no app; falta ONESIGNAL_APP_ID neste build."
            PushManager.permissionGranted(context) -> "Push ativo neste aparelho."
            else -> "Push configurado, mas a permissão ainda não foi concedida."
        }
        BackendClient.fetchNotificationPreferences(context) { result ->
            result.onSuccess { prefs ->
                loading = true
                operational.isEnabled = true; journey.isEnabled = true; sync.isEnabled = true; product.isEnabled = true
                operational.isChecked = prefs.operationalEnabled
                journey.isChecked = prefs.journeySummaryEnabled
                sync.isChecked = prefs.syncAlertsEnabled
                product.isChecked = prefs.productUpdatesEnabled
                loading = false
                PushManager.sync(context, prefs)
            }.onFailure {
                status.text = "Não foi possível carregar preferências: ${it.message}"
            }
        }
    }

    private fun check(label: String) = CheckBox(context).apply {
        text = label
        setTextColor(UiKit.palette(context).ink)
    }

    private fun bind(view: CheckBox) {
        view.setOnCheckedChangeListener { _, _ ->
            if (loading) return@setOnCheckedChangeListener
            save()
        }
    }

    private fun save() {
        status.text = "Salvando preferências..."
        val prefs = BackendClient.NotificationPreferences(
            operationalEnabled = operational.isChecked,
            journeySummaryEnabled = journey.isChecked,
            syncAlertsEnabled = sync.isChecked,
            productUpdatesEnabled = product.isChecked,
        )
        BackendClient.saveNotificationPreferences(context, prefs) { result ->
            status.text = result.fold(
                { if (PushManager.permissionGranted(context)) "Preferências salvas · push ativo." else "Preferências salvas." },
                { "Falha ao salvar: ${it.message}" }
            )
        }
    }
}
