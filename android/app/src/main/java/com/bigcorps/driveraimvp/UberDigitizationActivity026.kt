package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Entrada e revisão da digitalização manual da Uber. */
class UberDigitizationActivity026 : Activity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_SCAN_FILE = "scan_file"
        const val MODE_CHOOSER = "chooser"
        const val ACTION_RESULT = "com.srrotas.app.UBER_DIGITIZATION_RESULT"
        const val EXTRA_TEXT = "text"
        private const val REQ = 6207

        fun open(context: Context, mode: String = MODE_CHOOSER) {
            context.startActivity(
                Intent(context, UberDigitizationActivity026::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_MODE, mode),
            )
        }
    }

    private var mode = MODE_CHOOSER
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
            if (text.isBlank()) {
                toast("Não foi possível ler a tela.")
                finish()
                return
            }
            previewSingle(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CHOOSER
        val scanFile = intent.getStringExtra(EXTRA_SCAN_FILE)
        if (!scanFile.isNullOrBlank()) {
            reviewHistoryScan(scanFile)
            return
        }
        registerReceiverCompat()
        if (mode == MODE_CHOOSER) choose() else requestCapture()
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun choose() {
        AlertDialog.Builder(this)
            .setTitle("Digitalizar Uber")
            .setItems(arrayOf("Digitalizar jornada", "Digitalizar histórico")) { _, which ->
                mode = if (which == 0) UberDigitizationParser026.MODE_SESSION else UberDigitizationParser026.MODE_HISTORY
                requestCapture()
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQ)
    }

    @Deprecated("Compatibilidade sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        if (resultCode != RESULT_OK || data == null) {
            finish()
            return
        }
        val service = Intent(this, UberDigitizationCaptureService026::class.java)
            .putExtra(UberDigitizationCaptureService026.EXTRA_RESULT_CODE, resultCode)
            .putExtra(UberDigitizationCaptureService026.EXTRA_RESULT_DATA, data)
            .putExtra(EXTRA_MODE, mode)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)

        if (mode == UberDigitizationParser026.MODE_HISTORY) {
            toast("Digitalização iniciada. Role o Histórico da Uber e finalize pela câmera do Sr. Rotas ou pela notificação.")
            finish()
        }
    }

    private fun previewSingle(raw: String) {
        val parsed = runCatching { UberDigitizationParser026.parse(mode, raw) }.getOrElse {
            toast(it.message ?: "Tela não reconhecida.")
            finish()
            return
        }
        when (parsed) {
            is UberDigitizationResult026.Session -> previewSession(
                UberDigitizationResult026.Session(
                    UberJourneyLinker0262.attach(this, parsed.value),
                ),
            )
            is UberDigitizationResult026.Rides -> previewRides(parsed.values, 1, 1)
        }
    }

    private fun previewSession(parsed: UberDigitizationResult026.Session) {
        val value = parsed.value
        val journey = value.journeyId?.let { LocalStore.get(this).journey(it) }
        val association = when {
            journey != null -> "Jornada associada: ${dateTime(journey.startedAt)}"
            !value.journeyId.isNullOrBlank() -> "Jornada associada automaticamente"
            else -> "Jornada: não foi possível associar com segurança"
        }
        val message = buildString {
            append("Período: ${dateTime(value.startedAt)} – ${dateTime(value.endedAt)}\n")
            append("Faturamento: ${value.earnings?.let(::money) ?: "—"}\n")
            append("Viagens concluídas: ${value.completedTrips ?: "—"}\n")
            append("Viagens oferecidas: ${value.offeredTrips ?: "—"}\n")
            append("$association\n")
            append("Confiança: ${(value.confidence * 100).toInt()}%")
            value.observation?.let { append("\n\nObservação: $it") }
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmar jornada digitalizada")
            .setMessage(message)
            .setPositiveButton("Salvar") { _, _ -> save(parsed) }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun reviewHistoryScan(fileName: String) {
        val safeName = File(fileName).name
        if (!safeName.startsWith("uber-history-scan-") || !safeName.endsWith(".json")) {
            toast("Arquivo de digitalização inválido.")
            finish()
            return
        }
        val file = File(cacheDir, safeName)
        val frames = runCatching {
            val json = JSONObject(file.readText())
            val array = json.optJSONArray("frames")
            if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
        }.getOrDefault(emptyList())
        file.delete()
        if (frames.isEmpty()) {
            toast("Nenhum quadro do histórico foi capturado.")
            finish()
            return
        }
        val found = runCatching { UberHistoryScanAccumulator0262.parseFrames(frames) }.getOrElse {
            toast(it.message ?: "Nenhuma corrida reconhecida.")
            finish()
            return
        }
        previewRides(found.rides, found.framesRead, found.framesWithRides)
    }

    private fun previewRides(
        rides: List<UberCompletedRide026>,
        framesRead: Int,
        framesWithRides: Int,
    ) {
        val checked = BooleanArray(rides.size) { true }
        val labels = rides.map(::rideLabel).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Revisar histórico · ${rides.size} registro(s)")
            .setMessage("Foram analisados $framesRead quadro(s); $framesWithRides continham cartões reconhecíveis. Desmarque qualquer registro que não queira salvar.")
            .setMultiChoiceItems(labels, checked) { _, which, enabled -> checked[which] = enabled }
            .setPositiveButton("Salvar selecionadas") { _, _ ->
                val selected = rides.filterIndexed { index, _ -> checked[index] }
                if (selected.isEmpty()) {
                    toast("Nenhum registro selecionado.")
                    finish()
                } else {
                    save(UberDigitizationResult026.Rides(selected))
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun rideLabel(value: UberCompletedRide026): String = buildString {
        append(dateTime(value.occurredAt))
        append(" · ")
        append(if (value.rideStatus == UberCompletedRide026.STATUS_CANCELLED) "CANCELADA" else serviceLabel(value.serviceType))
        append(" · ${money(value.fare)}")
        value.durationSeconds?.let { append(" · ${duration(it)}") }
        value.distanceKm?.let { append(" · ${fmt(it)} km") }
        value.surgeAmount?.let { append(" · din. ${money(it)}") }
        value.extraAmount?.let { append(" · extra ${money(it)}") }
        if (!value.pickupLabel.isNullOrBlank() || !value.destinationLabel.isNullOrBlank()) {
            append("\n${value.pickupLabel ?: "Origem não lida"} → ${value.destinationLabel ?: "Destino não lido"}")
        }
    }

    private fun save(parsed: UberDigitizationResult026) {
        val store = UberDigitizationStore026.get(this)
        val message = when (parsed) {
            is UberDigitizationResult026.Session -> {
                if (store.saveSession(parsed.value)) "Jornada digitalizada salva/atualizada." else "Não foi possível salvar o resumo."
            }
            is UberDigitizationResult026.Rides -> {
                val (saved, duplicate) = store.saveRides(parsed.values)
                "$saved registro(s) salvo(s) · $duplicate duplicado(s)."
            }
        }
        UberDigitizationClient026.sync(this, parsed) { result ->
            if (parsed is UberDigitizationResult026.Session && result.isSuccess) {
                JourneyRealizedClient0262.refreshDays(this, 30) { }
            }
        }
        toast(message)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun registerReceiverCompat() {
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        receiverRegistered = true
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", value)
    private fun fmt(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
    private fun duration(seconds: Int): String = when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}min"
        else -> "${seconds / 60}min ${seconds % 60}s"
    }
    private fun serviceLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
        "uberx" -> "UberX"
        "comfort" -> "Comfort"
        "black" -> "Black"
        "electric" -> "Electric"
        "priority" -> "Priority"
        "moto" -> "Moto"
        else -> "Uber"
    }
    private fun dateTime(value: String?): String {
        if (value.isNullOrBlank()) return "horário não identificado"
        return runCatching {
            DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR"))
                .withZone(ZoneId.of("America/Sao_Paulo"))
                .format(Instant.parse(value))
        }.getOrDefault(value.take(16))
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
