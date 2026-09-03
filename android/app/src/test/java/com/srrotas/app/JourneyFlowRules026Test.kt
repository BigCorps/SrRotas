package com.srrotas.app

object JourneyFlowRules026Test {
    @JvmStatic
    fun main(args: Array<String>) {
        check(JourneyFlowRules026.decimalFlexible("52.310,5") == 52310.5)
        check(JourneyFlowRules026.decimalFlexible("52310.5") == 52310.5)
        check(JourneyFlowRules026.decimalFlexible("52.310") == 52310.0)
        check(JourneyFlowRules026.decimalFlexible("") == null)
        check(JourneyFlowRules026.validEnd(52310.0, 52401.2))
        check(!JourneyFlowRules026.validEnd(52310.0, 52200.0))
        check(JourneyFlowRules026.draftIsFresh(1_000L, 1_000L + 60_000L))
        check(!JourneyFlowRules026.draftIsFresh(1_000L, 1_000L + JourneyFlowRules026.DRAFT_TTL_MS + 1L))
        check(JourneyFlowRules026.energyKind("fuel") == JourneyMetricsRules026.KIND_FUEL)
        check(JourneyFlowRules026.unitFor(JourneyMetricsRules026.KIND_ELECTRIC) == JourneyMetricsRules026.UNIT_KWH)
        println("JOURNEY_FLOW_026_OK")
    }
}
