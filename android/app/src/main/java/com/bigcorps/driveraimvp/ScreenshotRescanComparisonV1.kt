package com.srrotas.app

object ScreenshotRescanComparisonV1 {
    fun status(old: String?, fresh: String?): String = when {
        fresh.isNullOrBlank() -> "ausente"
        old.isNullOrBlank() -> "recuperado"
        old.trim() == fresh.trim() -> "igual"
        else -> "diferente"
    }
    private fun fields(o: RideOffer?): List<Pair<String,String?>> = listOf(
            "Tarifa" to o?.fare?.toString(), "Origem / busca" to o?.context?.pickupLabel,
            "Busca km" to o?.pickupKm?.toString(), "Busca min" to o?.pickupMinutes?.toString(),
            "Destino" to o?.context?.destinationLabel, "Viagem km" to o?.tripKm?.toString(),
            "Viagem min" to o?.tripMinutes?.toString(), "Confiança do parser" to o?.confidence?.toString(),
            "Geocode" to o?.context?.geocodeStatus,
        )
    fun original(offer: RideOffer?): String = "Comparação manual com os dados oficiais (sem OCR):\n" +
        fields(offer).joinToString("\n") { (label, value) -> "$label: ${value ?: "—"}" }

    fun describe(old: RideOffer?, fresh: RideOffer?): String {
        val rows = fields(old).zip(fields(fresh)).joinToString("\n\n") { (a,b) ->
            "${a.first} · ${status(a.second,b.second)}\nOriginal: ${a.second ?: "—"}\nNova: ${b.second ?: "—"}"
        }
        val spec = fresh?.let(RadarDestinationContextV1::fromOffer)
        return rows + "\n\nRadarDestinationSpec: " +
            if(spec == null) "ainda insuficiente (${FieldPipelineFactsV1.specBlock(fresh)}). Labels não resolvem coordenadas por si só. Nenhum geocode consultado."
            else "campos presentes; identidade da corrida e atualidade do ETA ainda exigem validação. Nenhuma correção aplicada."
    }
}
