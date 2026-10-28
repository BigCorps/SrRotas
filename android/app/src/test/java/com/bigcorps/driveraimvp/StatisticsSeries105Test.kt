package com.srrotas.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StatisticsSeries105Test {
    private val nbsp = ' '

    @Test fun moneyIsOneUnbreakableUnitWithCents() {
        assertEquals("R$${nbsp}9,90", StatsFormat105.money(9.9))
        assertEquals("R$${nbsp}999,99", StatsFormat105.money(999.99))
        assertEquals("R$${nbsp}1.236,79", StatsFormat105.money(1236.79))
        assertEquals("R$${nbsp}12.345,67", StatsFormat105.money(12345.67))
        assertEquals("R$${nbsp}123.456,78", StatsFormat105.money(123456.78))
        assertEquals("—", StatsFormat105.money(null))
        assertEquals("—", StatsFormat105.money(Double.NaN))
        assertEquals("R$${nbsp}1.236,79", StatsFormat105.unbreakable("R$ 1.236,79"))
        assertFalse(StatsFormat105.money(1236.79).contains(' '))
    }

    private fun bucket(key: String, value: Any?, status: String) = JSONObject().put("key", key).put("label", "Seg").put("weekday", "Seg")
        .put("date", "20/10").put("partialWindow", false).put("value", value ?: JSONObject.NULL).put("status", status).put("coverage", JSONObject.NULL)

    private fun series(metric: String, semantic: String, buckets: JSONArray, status: String = "complete") = JSONObject().put("metric", metric)
        .put("semantic", semantic).put("unit", "x").put("status", status).put("completeness", "complete").put("coverage", 1.0)
        .put("sampleCount", 3).put("total", 10.0).put("limitations", JSONArray()).put("buckets", buckets)

    private fun body(series: JSONObject) = JSONObject().put("schema", StatisticsSeries105.SCHEMA).put("time_zone", "America/Sao_Paulo")
        .put("period", JSONObject().put("from", "2026-10-21T18:00:00.000Z").put("to", "2026-10-28T18:00:00.000Z")).put("series", series)

    @Test fun parsesSeriesAndKeepsGapsAsNull() {
        val parsed = StatisticsSeries105.parse(body(JSONObject().put("realized_revenue", series("realized_revenue", "REALIZED_REVENUE",
            JSONArray().put(bucket("a", 10.0, "complete")).put(bucket("b", null, "unavailable")).put(bucket("c", 0, "no_activity")).put(bucket("d", 99.0, "unavailable"))))))
        val b = parsed["realized_revenue"]!!.buckets
        assertEquals(10.0, b[0].value!!, 0.0)
        assertNull(b[1].value)
        assertTrue(b[2].drawable)
        assertNull("value under unavailable is never trusted", b[3].value)
        assertTrue(parsed.matches("2026-10-21T18:00:00Z", "2026-10-28T18:00:00Z"))
        assertFalse(parsed.matches("2026-10-20T18:00:00Z", "2026-10-28T18:00:00Z"))
    }

    @Test fun offersCanNeverBeReadAsRevenue() {
        val parsed = StatisticsSeries105.parse(body(JSONObject()
            .put("realized_revenue", series("realized_revenue", "OBSERVED_OFFER", JSONArray().put(bucket("a", 500.0, "complete"))))
            .put("observed_offers", series("observed_offers", "OBSERVED_OFFER", JSONArray().put(bucket("a", 12, "complete"))))))
        assertNull(parsed["realized_revenue"])
        assertNotNull(parsed["observed_offers"])
    }

    @Test fun rejectsWrongSchemaOrPeriod() {
        assertThrows(Exception::class.java) { StatisticsSeries105.parse(body(JSONObject()).put("schema", "other")) }
        val inverted = body(JSONObject()).put("period", JSONObject().put("from", "2026-10-28T18:00:00Z").put("to", "2026-10-21T18:00:00Z"))
        assertThrows(Exception::class.java) { StatisticsSeries105.parse(inverted) }
    }

    @Test fun comparisonOnlyWhenEligible() {
        val parsed = StatisticsSeries105.parse(body(JSONObject()).put("comparison", JSONObject().put("eligible", false).put("changePct", 12.0)))
        assertNull(parsed.comparison!!.changePct)
    }

    @Test fun crossSummaryStatesOnlyMaximaAndCoverage() {
        val points = listOf(
            CrossSummary105.Point("Seg", "segunda-feira", 100.0, 4.0),
            CrossSummary105.Point("Ter", "terça-feira", 250.0, 8.0),
            CrossSummary105.Point("Qua", "quarta-feira", null, 3.0),
        )
        val text = CrossSummary105.facts(points, "tarifas", "horas", { "R$ $it" }, { "$it h" })!!
        assertTrue(text.contains("mesmo dia: terça-feira"))
        assertTrue(text.contains("2 de 3 dias"))
        assertNull(CrossSummary105.facts(points.take(1), "a", "b", { "" }, { "" }))
    }

    @Test fun compactGridKeepsTwoColumnsOnPhones() {
        assertEquals(2, StatsUi105.columns(360, 1f))
        assertEquals(2, StatsUi105.columns(411, 1.3f))
        assertEquals(1, StatsUi105.columns(360, 1.3f))
        assertEquals(1, StatsUi105.columns(320, 1f))
    }
}
