package com.srrotas.app.r3lab

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.srrotas.app.BuildConfig
import com.srrotas.app.DriverOcrNormalizer
import com.srrotas.app.DriverPlatformOfferRouter
import com.srrotas.app.SettingsRepository
import com.srrotas.app.SpatialOcrLine
import com.srrotas.app.SrUi023
import com.srrotas.app.UiKit
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** Verificação de coexistência com o app operacional (serviços de Acessibilidade habilitados). */
object R3LabConflicts {
    data class Report(val ownServiceEnabled: Boolean, val operationalServicesEnabled: List<String>, val m1Active: Boolean) {
        val lines get() = buildList {
            add(if (ownServiceEnabled) "Acessibilidade do R3-Lab: ATIVA" else "Acessibilidade do R3-Lab: desativada (necessária para capturar)")
            if (operationalServicesEnabled.isEmpty()) add("Acessibilidade do app operacional: desativada — sem conflito")
            else add("Acessibilidade do app operacional ATIVA (${operationalServicesEnabled.joinToString()}): no modo M1 ela fica inerte; não use M2/Comparar nele durante o ensaio")
            add(if (m1Active) "Leitura M1 deste APK ativa: o R3 fica bloqueado (sem dois OCRs)" else "Leitura M1 deste APK: inativa")
            add("A Acessibilidade do R3-Lab só enxerga Uber/99: ela NÃO consegue provar se o app operacional está lendo a tela. Antes de cada sessão C/D, encerre a jornada/leitura M1 do app operacional e confirme abaixo (restrição de protocolo, não gate automático).")
        }
    }

    fun parseEnabled(raw: String?, ownPackage: String): Pair<Boolean, List<String>> {
        val services = raw.orEmpty().split(':').map { it.trim() }.filter { it.isNotBlank() }
        val own = services.any { it.startsWith("$ownPackage/") }
        val operational = services.filter { s -> R3AllowedPackages.OPERATIONAL.any { s.startsWith("$it/") } }.map { it.substringBefore('/') }
        return own to operational
    }

    fun check(context: Context): Report {
        val (own, operational) = parseEnabled(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), context.packageName)
        return Report(own, operational, runCatching { SettingsRepository(context).isProjectionActive() }.getOrDefault(false))
    }
}

/**
 * Tela do laboratório R3 (somente APK `r3lab`). Diagnóstico, consentimentos, motivos de rejeição,
 * rótulos de verdade-terreno (TP/FP/FN) e, por oferta, "Ver imagem utilizada" (sem OCR) separado
 * de "Reprocessar OCR" (replay C × D do mesmo quadro, sem gravar nada).
 */
class R3LabActivity : Activity() {
    private lateinit var body: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@R3LabActivity, 14), UiKit.dp(this@R3LabActivity, 16), UiKit.dp(this@R3LabActivity, 14), UiKit.dp(this@R3LabActivity, 26))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(SrUi023.palette(this@R3LabActivity).background); addView(body); isFillViewport = true }
        setContentView(scroll)
        UiKit.applySafeArea(scroll)
        render()
    }

    override fun onResume() { super.onResume(); if (::body.isInitialized) render() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    private fun text(value: String, size: Float = 11f) = SrUi023.body(this, value, size)

    private fun render() {
        body.removeAllViews()
        val p = SrUi023.palette(this)
        if (!BuildConfig.R3_LAB) { renderPrimary(); return }
        body.addView(SrUi023.title(this, "R3-Lab · experimental", 22f))
        body.addView(text("Build ${BuildConfig.VERSION_NAME} · ${packageName}. Laboratório isolado: M1 continua oficial, Reader2 segue shadow; nada aqui altera histórico, receita, HUD ou backend."))
        val consent = R3LabSettings.consent(this)
        val conflicts = R3LabConflicts.check(this)

        body.addView(UiKit.margin(SrUi023.card(this, 12, 16).apply {
            addView(SrUi023.title(this@R3LabActivity, "Estado", 15f))
            addView(text("Captura: ${R3LabTelemetry.state}" + (R3LabTelemetry.lastBlock?.let { " · bloqueio: ${blockText(it)}" } ?: "")))
            addView(text("Janelas: ${R3LabTelemetry.lastTargets.ifBlank { "nenhuma Uber/99 elegível" }} · API ${android.os.Build.VERSION.SDK_INT}" +
                if (android.os.Build.VERSION.SDK_INT < 30) " (R3 exige Android 11+)" else ""))
            addView(text("Modo: " + if (R3LabSettings.mode(this@R3LabActivity) == R3LabSettings.MODE_D) "D — captura R3 + perfil R3 (validado pelo parser oficial)" else "C — captura R3 + parser oficial"))
            conflicts.lines.forEach { addView(text("• $it", 10.5f)) }
        }, top = 10))

        body.addView(UiKit.margin(SrUi023.title(this, "Consentimentos", 15f), top = 12))
        body.addView(toggle("Aviso de Acessibilidade lido e aceito", consent.accessibilityDisclosureAccepted) { on ->
            if (on) disclosure() else { R3LabSettings.setDisclosure(this, false); R3LabSettings.setEnabled(this, false); render() }
        })
        body.addView(toggle("Laboratório ativo (captura R3)", consent.labEnabled) { on ->
            if (on && !R3LabSettings.consent(this).accessibilityDisclosureAccepted) { disclosure(); return@toggle }
            R3LabSettings.setEnabled(this, on); render()
        })
        body.addView(toggle("Confirmo: a leitura M1 do app operacional Sr. Rotas está ENCERRADA (vale 12 h)", R3LabSettings.protocolConfirmed(this)) { on ->
            R3LabSettings.confirmProtocol(this, on); render()
        })
        body.addView(toggle("Guardar a imagem de cada oferta (privado, só neste aparelho)", consent.imageRetentionConsent) { on ->
            R3LabSettings.setImages(this, on); render()
        })
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(R3LabSettings.MODE_C to "Modo C (oficial)", R3LabSettings.MODE_D to "Modo D (perfil R3)").forEachIndexed { i, (m, label) ->
            modes.addView(chip(label, R3LabSettings.mode(this) == m) { R3LabSettings.setMode(this, m); render() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(this@R3LabActivity, 6) })
        }
        body.addView(UiKit.margin(modes, top = 6))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Abrir configurações de Acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, top = 6))

        renderMetrics()
        renderOffers()

        body.addView(UiKit.margin(SrUi023.title(this, "Ferramentas", 15f), top = 12))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Registrar oferta NÃO detectada (Uber)") { missed("uber") }, top = 4))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Registrar oferta NÃO detectada (99)") { missed("99") }, top = 4))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Exportar diagnóstico (sem dados pessoais)") { export() }, top = 4))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Zerar contadores da sessão") { R3LabTelemetry.reset(this); render() }, top = 4))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Apagar todas as imagens do laboratório") {
            confirm("Apagar as imagens guardadas? As ofertas do laboratório continuam, com imagem PURGED.") { R3EvidenceStore(this).deleteAll(); render() }
        }, top = 4))
        body.addView(UiKit.margin(text("Desinstalar este APK remove tudo do laboratório. O app operacional não é afetado.", 10f), top = 6))
        p.hashCode()
    }

    /** vc112 — APK principal: escolha do leitor, aviso, imagens, status, diagnóstico e rollback para o M1. */
    private fun renderPrimary() {
        body.addView(SrUi023.title(this, "Leitor de ofertas", 22f))
        body.addView(text("Build ${BuildConfig.VERSION_NAME}. ${R3Mode.statusText(this)}", 11.5f))
        val primary = R3Mode.selected(this) == R3Mode.PRIMARY
        body.addView(UiKit.margin(SrUi023.title(this, "Escolha", 15f), top = 12))
        body.addView(toggle("R3 — Acessibilidade (principal, sem MediaProjection; M1 como reserva)", primary) { R3Mode.select(this, R3Mode.PRIMARY); render() })
        body.addView(toggle("M1 — MediaProjection (forçar / voltar ao leitor anterior)", !primary) { R3Mode.select(this, R3Mode.M1_FORCE); render() })
        body.addView(UiKit.margin(text("A troca vale a partir da próxima jornada. Para voltar ao M1 basta escolher M1 aqui — não é preciso reinstalar.", 10.5f), top = 4))
        val consent = R3LabSettings.consent(this)
        body.addView(UiKit.margin(SrUi023.title(this, "Permissões do R3", 15f), top = 12))
        body.addView(toggle("Aviso de Acessibilidade lido e aceito", consent.accessibilityDisclosureAccepted) { on ->
            if (on) primaryDisclosure() else { R3LabSettings.setDisclosure(this, false); render() }
        })
        body.addView(toggle("Guardar a imagem de cada oferta (privado, só neste aparelho)", consent.imageRetentionConsent) { on -> R3LabSettings.setImages(this, on); render() })
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Abrir configurações de Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, top = 6))
        R3Mode.fallback(this)?.let { fb ->
            body.addView(UiKit.margin(text("Último fallback para o M1: ${R3FallbackNotice.reasonText(fb.reason)} (jornada ${fb.journeyId.take(8)}).", 10.5f), top = 8))
        }
        renderMetrics()
        renderOffers()
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Exportar diagnóstico do R3 (sem dados pessoais)") { export() }, top = 10))
    }

    private fun renderMetrics() {
        val c = R3LabTelemetry.snapshot()
        fun n(k: String) = c[k] ?: 0L
        fun sum(prefix: String) = c.filterKeys { it.startsWith(prefix) }.values.sum()
        val db = R3LabDb.get(this); val labels = db.labels(); val cov = db.coverage()
        body.addView(UiKit.margin(SrUi023.card(this, 12, 16).apply {
            addView(SrUi023.title(this@R3LabActivity, "Diagnóstico", 15f))
            addView(text("Screenshots: ${n("screenshot_success")} ok / ${sum("screenshot_fail:")} falhas / ${n("screenshot_attempt")} tentativas · watchdog ${n("watchdog_capture")}"))
            addView(text("Quadros: ${n("frame_changed") + n("frame_first")} novos · ${n("frame_forced_refresh")} refresh · ${n("frame_unchanged")} iguais (sem OCR)"))
            addView(text("OCR: ${n("ocr_ok")} ok / ${n("ocr_fail")} falhas · ${n("ocr_superseded")} substituídos · watchdog ${n("watchdog_ocr")} · p50 ${R3LabTelemetry.percentile("ocr", .5) ?: "–"} ms / p95 ${R3LabTelemetry.percentile("ocr", .95) ?: "–"} ms"))
            addView(text("Candidatos por quadro: ${n("candidate_accepted")} aceitos · ${n("candidate_rejected")} rejeitados · ${n("frame_without_candidate")} quadros sem card"))
            addView(text("Ofertas distintas (estabilizadas): ${n("offers_accepted")} aceitas · ${n("offers_rejected")} rejeitadas · commit falhou ${n("commit_failed")}"))
            if (!BuildConfig.R3_LAB) addView(text("Caminho oficial: ${n("dispatch_submitted")} enviadas ao dispatcher · ${n("official_persisted")} gravadas no histórico · ${n("official_evidence_linked")} com imagem vinculada · fallbacks ${sum("fallback_m1:")}"))
            addView(text("Verdade-terreno: TP ${labels.tp} · FP ${labels.fp} · FN ${labels.fn} · sem rótulo ${labels.unlabeled}" +
                (if (labels.tp + labels.fp > 0) " · precisão ${pct(labels.tp, labels.tp + labels.fp)}" else "") +
                (if (labels.tp + labels.fn > 0) " · recall ${pct(labels.tp, labels.tp + labels.fn)}" else "")))
            addView(text("Imagens: ${cov.withReadyPrimary}/${cov.acceptedOffers} ofertas aceitas com imagem · READY ${cov.framesReady} · FAILED ${cov.framesFailed} · PURGED ${cov.framesPurged}"))
            val reasons = R3LabTelemetry.recentReasons("rejection:")
            if (reasons.isNotEmpty()) addView(text("Motivos de rejeição: " + reasons.joinToString(" · ") { "${it.first.removePrefix("rejection:")} ${it.second}" }, 10f))
            val blocks = R3LabTelemetry.recentReasons("blocked:", 5) + R3LabTelemetry.recentReasons("screenshot_fail:", 4)
            if (blocks.isNotEmpty()) addView(text("Bloqueios/falhas: " + blocks.joinToString(" · ") { "${it.first} ${it.second}" }, 10f))
        }, top = 12))
    }

    private fun renderOffers() {
        val offers = R3LabDb.get(this).offers(30)
        body.addView(UiKit.margin(SrUi023.title(this, "Ofertas do laboratório (${offers.size})", 15f), top = 12))
        if (offers.isEmpty()) body.addView(text("Nenhuma oferta estabilizada ainda."))
        val p = SrUi023.palette(this)
        offers.forEach { o ->
            val f = runCatching { JSONObject(o.fieldsJson) }.getOrDefault(JSONObject())
            fun v(k: String) = f.optJSONObject(k)?.optString("value")?.takeIf { it.isNotBlank() && it != "null" }
            body.addView(UiKit.margin(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(context, 10), UiKit.dp(context, 8), UiKit.dp(context, 10), UiKit.dp(context, 8))
                background = SrUi023.rounded(p.surface, 12, if (o.accepted) UiKit.palette(context).good else p.orange, 2, context)
                addView(SrUi023.title(context, "${o.platform.uppercase()} · ${v("fare")?.let { "R$ $it" } ?: "sem tarifa"} · ${if (o.accepted) "ACEITA" else "REJEITADA"} · modo ${o.mode}", 12.5f))
                addView(text("Busca ${v("pickup_min") ?: "?"} min / ${v("pickup_km") ?: "?"} km · Viagem ${v("trip_min") ?: "?"} min / ${v("trip_km") ?: "?"} km" +
                    (v("service_type")?.let { " · $it" } ?: ""), 10.5f))
                o.rejection?.let { addView(text("Motivo: $it", 10.5f).apply { setTextColor(p.orange) }) }
                addView(text("${SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(o.createdAtMs))} · rótulo ${o.label ?: "—"}", 10f))
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(chip("Ver imagem utilizada", false) { startActivity(Intent(this@R3LabActivity, R3EvidenceViewerActivity::class.java).putExtra(R3EvidenceViewerActivity.EXTRA_OFFER, o.labOfferId)) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(chip("Reprocessar OCR", false) { replay(o.labOfferId) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 6) })
                addView(UiKit.margin(row, top = 6))
                if (o.accepted) {
                    val labels = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    listOf("TP" to "Oferta real (TP)", "FP" to "Falso positivo (FP)").forEachIndexed { i, (k, label) ->
                        labels.addView(chip(label, o.label == k) { R3LabDb.get(this@R3LabActivity).setLabel(o.labOfferId, if (o.label == k) null else k); render() },
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(context, 6) })
                    }
                    addView(UiKit.margin(labels, top = 4))
                }
            }, top = 6))
        }
    }

    /** Replay determinístico do MESMO quadro guardado: C (oficial) × D (perfil), sem gravar nada. */
    private fun replay(labOfferId: String) {
        val primary = R3LabDb.get(this).links(labOfferId).firstOrNull { it.role == R3EvidenceRole.PRIMARY } ?: return
        val (file, status) = R3EvidenceStore(this).verifiedFile(primary.frameId)
        if (file == null) { info("Reprocessar OCR", "Imagem indisponível: $status. Nada foi reprocessado."); return }
        val frame = R3LabDb.get(this).frame(primary.frameId) ?: return
        val pkg = frame.sourcePackage
        info("Reprocessar OCR", "Aguardando a leitura ao vivo ficar ociosa e reprocessando o quadro guardado…")
        worker.execute {
            val message = runCatching {
                val bmp = BitmapFactory.decodeFile(file.path) ?: error("decode")
                try {
                    // Exclusividade atômica (issue #6): bloqueia screenshot/OCR ao vivo, espera o que estiver
                    // em voo e roda UM OCR; libera sempre, inclusive em falha/cancelamento.
                    val text = R3Supervisor.replayRecognize(bmp).getOrElse { return@runCatching "Reprocessamento não executado: ${it.message}" }
                    val settings = SettingsRepository(this).load()
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { SpatialOcrLine(DriverOcrNormalizer.sanitize(l.text), it) } }
                    val platform = R3AllowedPackages.platform(pkg) ?: "uber"
                    val obs = "replay-" + UUID.randomUUID()
                    val c = R3OfficialInterpretation.fromOffers(DriverPlatformOfferRouter.parse(text, settings, bmp.width, bmp.height).offers, platform, frame.frameId, obs)
                    val d = R3Supervisor.profiles.activeFor(pkg)?.let { R3ProfileExtractor.extract(it, lines, frame.frameId, obs, settings, bmp.width) }.orEmpty()
                    "Mesmo quadro (${frame.width}×${frame.height}), sem gravar nada.\n\nC — parser oficial:\n${summary(c)}\n\nD — perfil R3:\n${summary(d)}"
                } finally { bmp.recycle() }
            }.getOrElse { "Falha no reprocessamento: ${it.javaClass.simpleName}" }
            runOnUiThread { if (!isFinishing) info("Reprocessar OCR", message) }
        }
    }

    private fun summary(list: List<R3Candidate>) = if (list.isEmpty()) "nenhum card" else list.joinToString("\n") { c ->
        fun v(k: String) = c.fields[k]?.value ?: "?"
        "• R$ ${v("fare")} · busca ${v("pickup_min")} min/${v("pickup_km")} km · viagem ${v("trip_min")} min/${v("trip_km")} km · " +
            (if (c.accepted) "ACEITA" else "REJEITADA (${c.rejection})")
    }

    private fun missed(platform: String) {
        R3LabDb.get(this).addMissedOffer(platform)
        android.widget.Toast.makeText(this, "Oferta não detectada registrada ($platform).", android.widget.Toast.LENGTH_SHORT).show()
        render()
    }

    private fun export() {
        val json = R3LabTelemetry.export(this, "${BuildConfig.VERSION_NAME}/${packageName}").toString(2)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json)
            .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico R3-Lab"), "Exportar diagnóstico R3-Lab"))
    }

    /** Aviso do leitor R3 no app principal (difere do aviso do laboratório). */
    private fun primaryDisclosure() {
        AlertDialog.Builder(this).setTitle("Leitor R3 usa Acessibilidade")
            .setMessage("Com o leitor R3, o Sr. Rotas usa o serviço de Acessibilidade do Android para capturar a tela SOMENTE quando a janela do Uber Driver ou do 99 Motorista está visível durante uma jornada, e lê as ofertas por OCR no próprio aparelho.\n\n" +
                "• As ofertas lidas aparecem no HUD e entram no seu Histórico, como no leitor M1.\n• Não toca, aceita ou recusa corridas.\n• Não envia imagens para a nuvem; guardar a imagem de cada oferta é uma escolha separada.\n" +
                "• Outras janelas não são lidas; a captura para com a tela desligada e fora da jornada.\n\nVocê pode voltar ao M1 a qualquer momento nesta tela ou desligar a Acessibilidade nas configurações do Android.")
            .setPositiveButton("Aceito") { _, _ -> R3LabSettings.setDisclosure(this, true); render() }
            .setNegativeButton("Agora não", null).show()
    }

    private fun disclosure() {
        AlertDialog.Builder(this).setTitle("Aviso — R3-Lab usa Acessibilidade")
            .setMessage("Este APK experimental usa o serviço de Acessibilidade do Android para capturar a tela SOMENTE quando a janela do Uber Driver ou do 99 Motorista está visível, ler as ofertas por OCR e medir a qualidade da leitura.\n\n" +
                "• Não toca, aceita ou recusa corridas.\n• Não envia imagens nem textos para a nuvem.\n• Outras janelas não são lidas; a captura para com a tela desligada.\n" +
                "• Guardar imagens é uma escolha separada.\n• O app operacional e seu histórico não são alterados.\n\nVocê pode desativar a qualquer momento nesta tela ou nas configurações de Acessibilidade.")
            .setPositiveButton("Aceito") { _, _ -> R3LabSettings.setDisclosure(this, true); render() }
            .setNegativeButton("Agora não", null).show()
    }

    private fun confirm(message: String, action: () -> Unit) =
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("Confirmar") { _, _ -> action() }.setNegativeButton("Cancelar", null).show()

    private fun info(title: String, message: String) = AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()

    private fun pct(a: Int, b: Int) = String.format(Locale("pt", "BR"), "%.1f%%", 100.0 * a / b)

    private fun blockText(code: String) = when (code) {
        "LAB_DISABLED" -> "laboratório desligado"; "DISCLOSURE_REQUIRED" -> "aviso não aceito"; "ACCESSIBILITY_DISABLED" -> "Acessibilidade desligada"
        "API_UNSUPPORTED" -> "Android < 11"; "SCREEN_OFF" -> "tela desligada"; "OFFICIAL_READER_ACTIVE" -> "leitura M1 ativa"
        "OPERATIONAL_APP_ACTIVE" -> "app operacional visível"; "OPERATIONAL_M1_UNCONFIRMED" -> "confirme que a leitura M1 do app operacional está encerrada"
        "OCR_STUCK_RESTART_REQUIRED" -> "OCR travado: feche o R3-Lab nas configurações do Android (Forçar parada) e reabra"; "OCR_BUSY" -> "outro OCR (M2/Comparar)"; "TARGET_UNVERIFIED" -> "sem janela Uber/99"
        "NOT_LAB_BUILD" -> "não é o APK R3-Lab"; else -> code
    }

    private fun toggle(label: String, on: Boolean, action: (Boolean) -> Unit) = UiKit.margin(TextView(this).apply {
        val tone = UiKit.palette(context).good
        text = (if (on) "✓ " else "○ ") + label; textSize = 11.5f; gravity = Gravity.CENTER_VERTICAL; minHeight = UiKit.dp(context, 40)
        setPadding(UiKit.dp(context, 10), 0, UiKit.dp(context, 10), 0)
        setTextColor(if (on) android.graphics.Color.WHITE else tone)
        background = SrUi023.rounded(if (on) tone else android.graphics.Color.TRANSPARENT, 10, tone, if (on) 0 else 1, context)
        setOnClickListener { action(!on) }
    }, top = 5)

    private fun chip(label: String, active: Boolean, action: () -> Unit) = TextView(this).apply {
        val p = SrUi023.palette(context)
        text = label; textSize = 10.5f; gravity = Gravity.CENTER; minHeight = UiKit.dp(context, 34); isSingleLine = true
        setTextColor(if (active) android.graphics.Color.WHITE else p.blue)
        background = SrUi023.rounded(if (active) p.blue else android.graphics.Color.TRANSPARENT, 9, p.blue, if (active) 0 else 1, context)
        setOnClickListener { action() }
    }
}
