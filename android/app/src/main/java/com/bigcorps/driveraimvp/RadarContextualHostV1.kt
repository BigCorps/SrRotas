package com.srrotas.app

/**
 * Contrato fino para a MainActivity/host canônico.
 * Evita o módulo depender diretamente de ConsolidatedMainActivity027037.
 */
interface RadarContextualHostV1 {
    fun openRadarDestination(spec:RadarDestinationSpecV1)
    fun focusRadarOpportunity(opportunityId:String?=null)
}
