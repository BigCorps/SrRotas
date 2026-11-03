package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * vc109 — replay PRIVADO de OCR real (texto + caixas) para busca/destino, com variantes de OCR
 * parcial (trecho ausente, cabeçalho ausente) e o texto do mapa acima do card. Entradas e
 * expectativas ficam fora do repositório (SR_READER_OCR_DIR, SR_CONTEXT_EXPECTED); opt-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderContextReplayQa105Test {
    private fun norm(s: String?) = s.orEmpty().lowercase()

    @Test fun replayContext() {
        val dir = System.getenv("SR_READER_OCR_DIR")?.let(::File)
        val expectedFile = System.getenv("SR_CONTEXT_EXPECTED")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory && expectedFile != null && expectedFile.isFile)
        val expected = JSONObject(expectedFile!!.readText())
        val out = File(System.getenv("SR_CONTEXT_OUT") ?: "build/context-replay.json")
        val report = JSONArray()
        val settings = DriverSettings(costPerKm = 0.82)
        expected.keys().forEach { case ->
            val exp = expected.getJSONObject(case)
            val all = File(dir, "$case.lines").readLines().mapNotNull { row ->
                val p = row.split('\t'); if (p.size < 5) null else {
                    val text = DriverOcrNormalizer.sanitize(p[4])
                    if (text.isBlank()) null else SpatialOcrLine(text, Rect(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt()))
                }
            }
            val (w, h) = File(dir, "$case.size").readText().trim().split("x").let { it[0].toInt() to it[1].toInt() }
            val variants = linkedMapOf(
                "completo" to all,
                "sem_trecho_busca" to all.filterNot { it.text.contains(exp.getString("pickup_leg")) },
                "sem_trecho_viagem" to all.filterNot { it.text.contains(exp.getString("trip_leg")) },
                "sem_cabecalho" to all.filterNot { it.text.contains(exp.getString("header")) },
                "so_cabecalho_e_busca" to all.filterNot { it.text.contains(exp.getString("trip_leg")) },
                "sem_endereco_destino" to all.filterNot { it.text.startsWith(exp.getString("destination")) },
            )
            variants.forEach { (variant, lines) ->
                val raw = lines.joinToString("\n") { it.text }
                val offers = runCatching { UberSpatialParser0221.parseLines(lines, raw, settings, w, h) }.getOrDefault(emptyList())
                    .map(OfferContextCompletion109::complete) // what OfferDispatcher stages for the HUD
                val fare = MoneyRoleResolver030.primarySpatialFareLines(lines).firstOrNull()
                val cluster = fare?.let { OfferSpatialIsolation0221.clusterAroundFare(lines, it, w, h) }.orEmpty()
                val ctx = OfferContextExtractor0221.extract(cluster.ifEmpty { lines }, "2026-11-03T12:00:00Z", 20)
                val legacy = OfferContextEngine.extract(lines.map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) },
                    "2026-11-03T12:00:00Z", 20)
                fun verdict(label: String?, role: String): String {
                    val want = exp.getString(role); val other = exp.getString(if (role == "pickup") "destination" else "pickup")
                    return when {
                        label.isNullOrBlank() -> "indisponivel"
                        norm(label).startsWith(norm(want)) -> "correto"
                        norm(label).startsWith(norm(other)) -> "INVERTIDO"
                        else -> "ERRADO"
                    }
                }
                val missingDest = variant == "sem_trecho_viagem" || variant == "so_cabecalho_e_busca" || variant == "sem_endereco_destino"
                val missingPickup = variant == "sem_trecho_busca"
                report.put(JSONObject().apply {
                    put("case", case); put("variant", variant)
                    put("offers", offers.size)
                    put("official_pickup", verdict(offers.firstOrNull()?.context?.pickupLabel, "pickup"))
                    put("official_destination", verdict(offers.firstOrNull()?.context?.destinationLabel, "destination"))
                    put("cluster_pickup", verdict(ctx?.pickupLabel, "pickup"))
                    put("cluster_destination", verdict(ctx?.destinationLabel, "destination"))
                    put("legacy_pickup", verdict(legacy?.pickupLabel, "pickup"))
                    put("legacy_destination", verdict(legacy?.destinationLabel, "destination"))
                    put("expect_pickup", if (missingPickup) "correto|indisponivel" else "correto")
                    put("expect_destination", if (missingDest) "indisponivel" else "correto")
                    put("roles", OfferContextEngine.routeRoles(lines.sortedBy { it.box.top }.map { ContextOcrLine(it.text, it.box.top, it.box.left, it.box.bottom, it.box.right) }).evidence)
                    put("official_pickup_text", offers.firstOrNull()?.context?.pickupLabel ?: JSONObject.NULL)
                    put("official_destination_text", offers.firstOrNull()?.context?.destinationLabel ?: JSONObject.NULL)
                    put("drop", if (offers.isEmpty()) OfferDropDiagnosis109.diagnose(lines, settings).let { "${it.stage}:${it.reason}" } else JSONObject.NULL)
                    put("times", offers.firstOrNull()?.let { "${it.pickupMinutes}/${it.tripMinutes}/${it.totalMinutes}" } ?: JSONObject.NULL)
                })
            }
        }
        out.parentFile?.mkdirs(); out.writeText(report.toString(2))
    }
}
