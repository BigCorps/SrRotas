package com.srrotas.app

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Shadow monetário independente do Field2.
 *
 * Reinterpreta apenas o papel dos valores R$ já presentes no rawText do card
 * oficial. Não captura imagem, não executa OCR, não grava oferta, não altera HUD
 * e não participa da admissão.
 */
internal object Reader2MoneyShadow030 {
    private var observations = 0
    private var multiMoneyCards = 0
    private var promotionCards = 0
    private var shadowFareAvailable = 0
    private var fareMatches = 0
    private var fareDisagreements = 0
    private var largeFareDisagreements = 0
    private var lastM1Fare: Double? = null
    private var lastShadowFare: Double? = null
    private var lastCandidateCount = 0
    private var lastPromotionCount = 0

    @Synchronized
    fun observe(offers: List<RideOffer>) {
        offers.forEach(::observeOne)
    }

    private fun observeOne(offer: RideOffer) {
        observations++
        val candidates = MoneyRoleResolver030.inspectText(offer.rawText)
        if (candidates.size > 1) multiMoneyCards++
        val promotions = candidates.count { it.role == MoneyRoleResolver030.Role.PROMOTION_BONUS }
        if (promotions > 0) promotionCards++

        val shadowFare = MoneyRoleResolver030.shadowPrimaryFare(offer.rawText)
        lastM1Fare = offer.fare
        lastShadowFare = shadowFare
        lastCandidateCount = candidates.size
        lastPromotionCount = promotions

        if (shadowFare != null) {
            shadowFareAvailable++
            val tolerance = max(0.05, max(abs(offer.fare), abs(shadowFare)) * 0.01)
            if (abs(offer.fare - shadowFare) <= tolerance) {
                fareMatches++
            } else {
                fareDisagreements++
                val ratio = if (minOf(offer.fare, shadowFare) > 0.0) {
                    max(offer.fare, shadowFare) / minOf(offer.fare, shadowFare)
                } else {
                    1.0
                }
                if (ratio >= 1.8) largeFareDisagreements++
            }
        }
    }

    @Synchronized
    fun resetRuntime() {
        observations = 0
        multiMoneyCards = 0
        promotionCards = 0
        shadowFareAvailable = 0
        fareMatches = 0
        fareDisagreements = 0
        largeFareDisagreements = 0
        lastM1Fare = null
        lastShadowFare = null
        lastCandidateCount = 0
        lastPromotionCount = 0
    }

    @Synchronized
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "sr-reader2-money-shadow-030-field2-v1")
        put("mode", "shadow")
        put("source", "official_card_raw_text_after_shared_m1_ocr")
        put("second_ocr", false)
        put("official_persistence", false)
        put("backend_effect", false)
        put("hud_effect", false)
        put("admission_influence", false)
        put("observations", observations)
        put("multi_money_cards", multiMoneyCards)
        put("promotion_cards", promotionCards)
        put("shadow_fare_available", shadowFareAvailable)
        put("fare_matches", fareMatches)
        put("fare_disagreements", fareDisagreements)
        put("large_fare_disagreements", largeFareDisagreements)
        put("last_m1_fare", lastM1Fare ?: JSONObject.NULL)
        put("last_shadow_fare", lastShadowFare ?: JSONObject.NULL)
        put("last_money_candidate_count", lastCandidateCount)
        put("last_promotion_candidate_count", lastPromotionCount)
        put(
            "policy",
            "Reader 2 monetary shadow classifica papéis dos valores R$ de forma independente; nunca substitui a tarifa oficial nesta fase.",
        )
        put(
            "privacy",
            "Somente contadores e últimos valores numéricos; sem OCR bruto, endereço, coordenada ou screenshot.",
        )
    }
}
