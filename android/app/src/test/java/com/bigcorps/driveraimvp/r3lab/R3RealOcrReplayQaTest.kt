package com.srrotas.app.r3lab

import android.app.Application
import android.graphics.Rect
import com.srrotas.app.DriverOcrNormalizer
import com.srrotas.app.DriverSettings
import com.srrotas.app.SpatialOcrLine
import com.srrotas.app.UberSpatialParser0221
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * QA privada (opt-in): replay do OCR REAL (texto + caixas) de capturas Uber autorizadas, mantidas FORA
 * do Git (`SR_READER_OCR_DIR`, `SR_CONTEXT_EXPECTED`). Compara, no MESMO OCR espacial, o caminho oficial
 * (modo C) com o perfil R3 (modo D) e o resultado do interpretador primário do R3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class R3RealOcrReplayQaTest {
    @Test fun replayRealOcr() {
        val dir = System.getenv("SR_READER_OCR_DIR")?.let(::File)
        val expectedFile = System.getenv("SR_CONTEXT_EXPECTED")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory && expectedFile != null && expectedFile.isFile)
        val expected = JSONObject(expectedFile!!.readText())
        val settings = DriverSettings(costPerKm = 0.82)
        val profile = R3ProfileManager().also(R3DefaultProfiles::installDefaults).activeFor(R3AllowedPackages.UBER)!!
        val report = JSONArray()
        expected.keys().forEach { case ->
            val exp = expected.getJSONObject(case)
            val all = File(dir, "$case.lines").readLines().mapNotNull { row ->
                val p = row.split('\t'); if (p.size < 5) null else DriverOcrNormalizer.sanitize(p[4]).takeIf { it.isNotBlank() }
                    ?.let { SpatialOcrLine(it, Rect(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())) }
            }
            val (w, h) = File(dir, "$case.size").readText().trim().split("x").let { it[0].toInt() to it[1].toInt() }
            linkedMapOf(
                "completo" to all,
                "sem_trecho_busca" to all.filterNot { it.text.contains(exp.getString("pickup_leg")) },
                "sem_trecho_viagem" to all.filterNot { it.text.contains(exp.getString("trip_leg")) },
                "sem_cabecalho" to all.filterNot { it.text.contains(exp.getString("header")) },
            ).forEach { (variant, lines) ->
                val raw = lines.joinToString("\n") { it.text }
                val official = runCatching { UberSpatialParser0221.parseLines(lines, raw, settings, w, h) }.getOrDefault(emptyList())
                val c = R3OfficialInterpretation.fromOffers(official, "uber", "f", "o")
                val d = R3ProfileExtractor.extract(profile, lines, "f", "o", settings, w)
                val primary = R3PrimaryInterpreter.interpret(profile, lines, official, settings, w, "f", "o")
                fun s(list: List<R3Candidate>) = list.joinToString(";") { x -> if (x.accepted) "OK ${x.offer!!.fare}/${x.offer.pickupMinutes}/${x.offer.tripMinutes}/${x.offer.pickupKm}/${x.offer.tripKm}" else "REJ ${x.rejection}" }.ifBlank { "none" }
                report.put(JSONObject().put("case", case.takeLast(6)).put("variant", variant).put("C", s(c)).put("D", s(d)).put("R3", s(primary.candidates))
                    .put("R3_source", primary.source))
            }
        }
        File(System.getenv("SR_R3_OUT") ?: "build/r3-replay.json").writeText(report.toString(1))
    }
}
