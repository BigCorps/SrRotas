package com.srrotas.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Revisão posterior 0.26.4 dos dados digitalizados da Uber.
 *
 * Não cria schema. Reabre os registros já persistidos no SQLite 0.26.2,
 * atualiza o mesmo source_key e reenvia a correção ao endpoint existente.
 */
object UberCapturedHistoryEditor0264 {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    private sealed class Entry {
        data class Session(val value: UberSessionSummary026) : Entry()
        data class Ride(val value: UberCompletedRide026) : Entry()
    }

    fun show(context: Context) {
        val entries = loadEntries(context)
        if (entries.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Editar histórico capturado")
                .setMessage("Ainda não há jornadas ou corridas digitalizadas salvas neste aparelho.")
                .setPositiveButton("Fechar", null)
                .show()
            return
        }
        val labels = entries.map(::label).toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Editar histórico capturado")
            .setItems(labels) { _, which ->
                when (val selected = entries[which]) {
                    is Entry.Session -> editSession(context, selected.value)
                    is Entry.Ride -> editRide(context, selected.value)
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun loadEntries(context: Context): List<Entry> {
        val store = UberDigitizationStore026.get(context)
        val owner = ownerKey(context)
        val sessions = store.readableDatabase.query(
            "session_imports",
            arrayOf(
                "source_key", "captured_at", "started_at", "ended_at", "earnings",
                "completed_trips", "offered_trips", "confidence", "journey_id", "observation",
            ),
            "owner_key=?",
            arrayOf(owner),
            null,
            null,
            "captured_at desc",
            "50",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun string(name: String): String? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getString(i)
                    }
                    fun double(name: String): Double? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getDouble(i)
                    }
                    fun int(name: String): Int? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getInt(i)
                    }
                    add(
                        Entry.Session(
                            UberSessionSummary026(
                                sourceKey = cursor.getString(cursor.getColumnIndexOrThrow("source_key")),
                                capturedAt = cursor.getString(cursor.getColumnIndexOrThrow("captured_at")),
                                startedAt = string("started_at"),
                                endedAt = string("ended_at"),
                                earnings = double("earnings"),
                                completedTrips = int("completed_trips"),
                                offeredTrips = int("offered_trips"),
                                confidence = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")),
                                journeyId = string("journey_id"),
                                observation = string("observation"),
                            ),
                        ),
                    )
                }
            }
        }

        val rides = store.readableDatabase.query(
            "ride_imports",
            arrayOf(
                "source_key", "captured_at", "occurred_at", "fare", "service_type",
                "pickup_label", "destination_label", "confidence", "duration_seconds",
                "distance_km", "surge_amount", "extra_amount", "ride_status",
            ),
            "owner_key=?",
            arrayOf(owner),
            null,
            null,
            "coalesce(occurred_at,captured_at) desc",
            "100",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun string(name: String): String? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getString(i)
                    }
                    fun double(name: String): Double? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getDouble(i)
                    }
                    fun int(name: String): Int? {
                        val i = cursor.getColumnIndexOrThrow(name)
                        return if (cursor.isNull(i)) null else cursor.getInt(i)
                    }
                    add(
                        Entry.Ride(
                            UberCompletedRide026(
                                sourceKey = cursor.getString(cursor.getColumnIndexOrThrow("source_key")),
                                capturedAt = cursor.getString(cursor.getColumnIndexOrThrow("captured_at")),
                                occurredAt = string("occurred_at"),
                                fare = cursor.getDouble(cursor.getColumnIndexOrThrow("fare")),
                                serviceType = cursor.getString(cursor.getColumnIndexOrThrow("service_type")),
                                pickupLabel = string("pickup_label"),
                                destinationLabel = string("destination_label"),
                                confidence = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")),
                                durationSeconds = int("duration_seconds"),
                                distanceKm = double("distance_km"),
                                surgeAmount = double("surge_amount"),
                                extraAmount = double("extra_amount"),
                                rideStatus = cursor.getString(cursor.getColumnIndexOrThrow("ride_status")),
                            ),
                        ),
                    )
                }
            }
        }
        return (sessions + rides).sortedByDescending { capturedAt(it) }.take(120)
    }

    private fun editSession(context: Context, value: UberSessionSummary026) {
        val start = input(context, "Início · dd/MM/aaaa HH:mm", displayTime(value.startedAt))
        val end = input(context, "Fim · dd/MM/aaaa HH:mm", displayTime(value.endedAt))
        val earnings = decimalInput(context, "Faturamento (R$)", value.earnings)
        val completed = integerInput(context, "Viagens concluídas", value.completedTrips)
        val offered = integerInput(context, "Viagens oferecidas", value.offeredTrips)
        val observation = input(context, "Observação", value.observation.orEmpty(), singleLine = false)
        val form = form(context, listOf(start, end, earnings, completed, offered, observation))

        val dialog = AlertDialog.Builder(context)
            .setTitle("Editar jornada capturada")
            .setView(form)
            .setPositiveButton("Salvar correção", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val startIso = parseTime(start.text?.toString())
                val endIso = parseTime(end.text?.toString())
                if (start.text?.toString()?.isNotBlank() == true && startIso == null) {
                    toast(context, "Confira a data/hora inicial.")
                    return@setOnClickListener
                }
                if (end.text?.toString()?.isNotBlank() == true && endIso == null) {
                    toast(context, "Confira a data/hora final.")
                    return@setOnClickListener
                }
                if (startIso != null && endIso != null && Instant.parse(endIso).isBefore(Instant.parse(startIso))) {
                    toast(context, "O fim da jornada não pode ser anterior ao início.")
                    return@setOnClickListener
                }
                val earningsValue = decimal(earnings)
                val completedValue = integer(completed)
                val offeredValue = integer(offered)
                if (earnings.text?.toString()?.isNotBlank() == true && (earningsValue == null || earningsValue < 0.0)) {
                    toast(context, "Confira o faturamento.")
                    return@setOnClickListener
                }
                if (completed.text?.toString()?.isNotBlank() == true && completedValue == null) {
                    toast(context, "Confira a quantidade de viagens concluídas.")
                    return@setOnClickListener
                }
                if (offered.text?.toString()?.isNotBlank() == true && offeredValue == null) {
                    toast(context, "Confira a quantidade de viagens oferecidas.")
                    return@setOnClickListener
                }
                val updated = value.copy(
                    startedAt = startIso,
                    endedAt = endIso,
                    earnings = earningsValue,
                    completedTrips = completedValue,
                    offeredTrips = offeredValue,
                    observation = observation.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                )
                UberDigitizationStore026.get(context).saveSession(updated)
                UberDigitizationClient026.sync(context, UberDigitizationResult026.Session(updated))
                dialog.dismiss()
                toast(context, "Jornada capturada atualizada.")
            }
        }
        dialog.show()
    }

    private fun editRide(context: Context, value: UberCompletedRide026) {
        val occurred = input(context, "Data/hora · dd/MM/aaaa HH:mm", displayTime(value.occurredAt))
        val fare = decimalInput(context, "Valor da corrida (R$)", value.fare)
        val service = input(context, "Categoria", value.serviceType)
        val pickup = input(context, "Origem", value.pickupLabel.orEmpty())
        val destination = input(context, "Destino", value.destinationLabel.orEmpty())
        val duration = decimalInput(context, "Duração (minutos)", value.durationSeconds?.div(60.0))
        val distance = decimalInput(context, "Distância (km)", value.distanceKm)
        val surge = decimalInput(context, "Dinâmica (R$) · opcional", value.surgeAmount)
        val extra = decimalInput(context, "Extra (R$) · opcional", value.extraAmount)
        val status = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Concluída", "Cancelada"),
            )
            setSelection(if (value.rideStatus == UberCompletedRide026.STATUS_CANCELLED) 1 else 0)
            background = fieldBackground(context)
            minimumHeight = SrUi023.dp(context, 44)
        }
        val fields = listOf<View>(occurred, fare, service, pickup, destination, duration, distance, surge, extra, status)
        val dialog = AlertDialog.Builder(context)
            .setTitle("Editar corrida capturada")
            .setView(form(context, fields))
            .setPositiveButton("Salvar correção", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val whenIso = parseTime(occurred.text?.toString())
                if (occurred.text?.toString()?.isNotBlank() == true && whenIso == null) {
                    toast(context, "Confira a data/hora da corrida.")
                    return@setOnClickListener
                }
                val fareValue = decimal(fare)
                if (fareValue == null || fareValue < 0.0) {
                    toast(context, "Informe um valor válido para a corrida.")
                    return@setOnClickListener
                }
                val durationMinutes = decimal(duration)
                val distanceValue = decimal(distance)
                val surgeValue = decimal(surge)
                val extraValue = decimal(extra)
                if (duration.text?.toString()?.isNotBlank() == true && (durationMinutes == null || durationMinutes !in 0.0..1440.0)) {
                    toast(context, "A duração deve ficar entre 0 e 1.440 minutos.")
                    return@setOnClickListener
                }
                if (distance.text?.toString()?.isNotBlank() == true && (distanceValue == null || distanceValue !in 0.0..2000.0)) {
                    toast(context, "A distância deve ficar entre 0 e 2.000 km.")
                    return@setOnClickListener
                }
                if (surge.text?.toString()?.isNotBlank() == true && (surgeValue == null || surgeValue < 0.0)) {
                    toast(context, "Confira o valor da dinâmica.")
                    return@setOnClickListener
                }
                if (extra.text?.toString()?.isNotBlank() == true && (extraValue == null || extraValue < 0.0)) {
                    toast(context, "Confira o valor extra.")
                    return@setOnClickListener
                }
                val updated = value.copy(
                    occurredAt = whenIso,
                    fare = fareValue,
                    serviceType = service.text?.toString()?.trim()?.ifBlank { "unknown" } ?: "unknown",
                    pickupLabel = pickup.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                    destinationLabel = destination.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                    durationSeconds = durationMinutes?.let { (it * 60.0).toInt() },
                    distanceKm = distanceValue,
                    surgeAmount = surgeValue,
                    extraAmount = extraValue,
                    rideStatus = if (status.selectedItemPosition == 1) UberCompletedRide026.STATUS_CANCELLED else UberCompletedRide026.STATUS_COMPLETED,
                )
                updateRide(context, updated)
                UberDigitizationClient026.sync(context, UberDigitizationResult026.Rides(listOf(updated)))
                dialog.dismiss()
                toast(context, "Corrida capturada atualizada.")
            }
        }
        dialog.show()
    }

    private fun updateRide(context: Context, value: UberCompletedRide026) {
        val cv = ContentValues().apply {
            putNullable("occurred_at", value.occurredAt)
            put("fare", value.fare)
            put("service_type", value.serviceType)
            putNullable("pickup_label", value.pickupLabel)
            putNullable("destination_label", value.destinationLabel)
            putNullable("duration_seconds", value.durationSeconds)
            putNullable("distance_km", value.distanceKm)
            putNullable("surge_amount", value.surgeAmount)
            putNullable("extra_amount", value.extraAmount)
            put("ride_status", value.rideStatus)
            put("sync_state", 0.toInt())
        }
        UberDigitizationStore026.get(context).writableDatabase.update(
            "ride_imports",
            cv,
            "owner_key=? and source_key=?",
            arrayOf(ownerKey(context), value.sourceKey),
        )
    }

    private fun label(entry: Entry): String = when (entry) {
        is Entry.Session -> buildString {
            append("Jornada · ${displayTime(entry.value.startedAt).ifBlank { displayTime(entry.value.capturedAt) }}")
            entry.value.earnings?.let { append(" · ${money(it)}") }
        }
        is Entry.Ride -> buildString {
            append("Corrida · ${displayTime(entry.value.occurredAt).ifBlank { displayTime(entry.value.capturedAt) }}")
            append(" · ${money(entry.value.fare)}")
            append(" · ${entry.value.serviceType}")
        }
    }

    private fun capturedAt(entry: Entry): String = when (entry) {
        is Entry.Session -> entry.value.capturedAt
        is Entry.Ride -> entry.value.capturedAt
    }

    private fun form(context: Context, fields: List<View>): ScrollView = ScrollView(context).apply {
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    SrUi023.dp(context, 18),
                    SrUi023.dp(context, 8),
                    SrUi023.dp(context, 18),
                    SrUi023.dp(context, 8),
                )
                fields.forEachIndexed { index, field ->
                    addView(
                        field,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { if (index > 0) topMargin = SrUi023.dp(context, 7) },
                    )
                }
            },
        )
    }

    private fun input(context: Context, hintText: String, value: String, singleLine: Boolean = true): EditText = EditText(context).apply {
        hint = hintText
        setText(value)
        setSingleLine(singleLine)
        if (!singleLine) minLines = 2
        setTextColor(SrUi023.palette(context).ink)
        setHintTextColor(SrUi023.palette(context).muted)
        background = fieldBackground(context)
        setPadding(SrUi023.dp(context, 11), SrUi023.dp(context, 9), SrUi023.dp(context, 11), SrUi023.dp(context, 9))
    }

    private fun decimalInput(context: Context, hintText: String, value: Double?): EditText =
        input(context, hintText, value?.let { String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') }.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

    private fun integerInput(context: Context, hintText: String, value: Int?): EditText =
        input(context, hintText, value?.toString().orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

    private fun fieldBackground(context: Context) = SrUi023.rounded(
        SrUi023.palette(context).surface,
        11,
        SrUi023.palette(context).outline,
        1,
        context,
    )

    private fun decimal(field: EditText): Double? =
        JourneyFlowRules026.decimalFlexible(field.text?.toString())

    private fun integer(field: EditText): Int? =
        field.text?.toString()?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    private fun displayTime(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching { dateFormat.format(Instant.parse(value).atZone(zone)) }.getOrDefault(value.take(16))
    }

    private fun parseTime(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        runCatching { Instant.parse(raw) }.getOrNull()?.let { return it.toString() }
        return runCatching {
            LocalDateTime.parse(raw, dateFormat).atZone(zone).toInstant().toString()
        }.getOrNull()
    }

    private fun ownerKey(context: Context): String {
        val token = SettingsRepository(context).load().deviceToken.ifBlank { "anonymous" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", value)
    private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun ContentValues.putNullable(key: String, value: Any?) {
        if (value == null) putNull(key) else when (value) {
            is String -> put(key, value)
            is Double -> put(key, value)
            is Int -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}
