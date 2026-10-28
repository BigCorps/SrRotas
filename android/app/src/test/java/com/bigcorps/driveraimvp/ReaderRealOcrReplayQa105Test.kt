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
 * Private replay of REAL OCR lines (text + boxes) through the official Uber M1
 * route. Inputs live outside the repository (SR_READER_OCR_DIR); nothing from
 * the screenshots is versioned. Opt-in with -PqaScreenshots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderRealOcrReplayQa105Test {
    @Test fun replayPrivateOcr() {
        val dir = System.getenv("SR_READER_OCR_DIR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(System.getenv("SR_READER_OUT") ?: "build/reader-replay.json")
        val report = JSONArray()
        val settings = DriverSettings(costPerKm = 0.82)
        dir!!.listFiles { f -> f.name.endsWith(".lines") }!!.sortedBy { it.name }.forEach { file ->
            val (w, h) = file.name.substringBefore(".lines").let { name ->
                File(dir, "$name.size").takeIf { it.isFile }?.readText()?.trim()?.split("x")?.let { it[0].toInt() to it[1].toInt() } ?: (1188 to 1599)
            }
            val lines = file.readLines().mapNotNull { row ->
                val parts = row.split('\t')
                if (parts.size < 5) return@mapNotNull null
                val text = DriverOcrNormalizer.sanitize(parts[4])
                if (text.isBlank()) null else SpatialOcrLine(text, Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt()))
            }
            val raw = lines.joinToString("\n") { it.text }
            var offers = UberSpatialParser0221.parseLines(lines, raw, settings, w, h)
            var route = "uber_spatial"
            val explicitAction = listOf("aceitar", "selecionar", "exclusivo", "radar de viagens", "tenho interesse").any(raw.lowercase()::contains)
            if (offers.isEmpty() && explicitAction && FlexibleDriverOfferParser.primaryFareCount(raw) == 1) {
                offers = listOfNotNull(OfferParser.parse(raw, AppSignals.UBER_PACKAGE, "replay/text-fallback", settings))
                route = "uber_text_fallback"
            }
            val reader2 = Reader2Parallel031.inspectFrame(lines, w, h).candidates.firstOrNull()
            val fares = MoneyRoleResolver030.primarySpatialFareLines(lines)
            val cluster = fares.firstOrNull()?.let { OfferSpatialIsolation0221.clusterAroundFare(lines, it, w, h) }.orEmpty()
            report.put(JSONObject().apply {
                put("case", file.name.substringBefore(".lines"))
                put("route", if (offers.isEmpty()) "none" else route)
                put("primary_fares", JSONArray(fares.map { it.text }))
                put("cluster_geometry_lines", JSONArray(cluster.map { it.text }.filter { Regex("(?i)min|km|R\\$").containsMatchIn(it) }))
                put("offers", JSONArray(offers.map { o ->
                    JSONObject().put("fare", o.fare).put("pickup_minutes", o.pickupMinutes).put("pickup_km", o.pickupKm)
                        .put("trip_minutes", o.tripMinutes).put("trip_km", o.tripKm).put("total_minutes", o.totalMinutes).put("total_km", o.totalKm)
                        .put("per_minute", o.perMinute).put("per_hour", o.perHour).put("per_km", o.perKm)
                        .put("advertised_per_km", o.advertisedPerKm).put("service", o.serviceType).put("confidence", o.confidence)
                        .put("integrity", JSONArray(OfferIntegrityGate027033.assess(o).flags)).put("ready", OfferIntegrityGate027033.assess(o).ready)
                }))
                put("reader2_shadow", reader2?.let { c ->
                    JSONObject().put("pickup_minutes", c.pickupMinutes).put("pickup_km", c.pickupKm).put("trip_minutes", c.tripMinutes)
                        .put("trip_km", c.tripKm).put("total_minutes", c.totalMinutes).put("total_km", c.totalKm)
                } ?: JSONObject.NULL)
            })
        }
        out.parentFile?.mkdirs()
        out.writeText(report.toString(2))
    }
}
