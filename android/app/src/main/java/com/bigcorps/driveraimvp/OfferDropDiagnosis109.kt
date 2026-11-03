package com.srrotas.app

/**
 * vc109 — por que uma tela Uber com cara de oferta não virou oferta.
 *
 * Instrumentação SEM alterar o Reader: a leitura oficial (`MediaProjectionOcrService`, congelado)
 * já chama `RadarHudTrace024.recordOcr` → `OfferSpatialIsolation0221.lines` → `recordRoute` na
 * mesma thread principal. As linhas do quadro ficam guardadas só dentro desse escopo e, quando a
 * rota termina como "candidato Uber sem oferta", as MESMAS funções puras da cadeia oficial são
 * reavaliadas para apontar a primeira etapa que descartou o card. Só códigos e contagens são
 * registrados (nunca texto do OCR). Reader2 e gates continuam intocados.
 */
internal object OfferDropDiagnosis109 {
    enum class Stage { FARE, ANCHOR, SCREEN_GATE, LEGS, DETECTOR, PARSER, INTEGRITY, NONE }

    data class Result(val stage: Stage, val reason: String, val fareLines: Int, val moneyLines: Int, val geometryLines: Int, val clusterLines: Int) {
        fun fields(): Map<String, Any?> = mapOf(
            "drop_stage" to stage.name, "drop_reason" to reason.take(80), "fare_lines" to fareLines,
            "money_lines" to moneyLines, "geometry_lines" to geometryLines, "cluster_lines" to clusterLines,
        )
    }

    private const val MIN_INTERVAL_MS = 1_000L
    @Volatile private var scopeOpen = false
    @Volatile private var lines: List<SpatialOcrLine>? = null
    @Volatile private var lastAt = 0L
    @Volatile private var lastFingerprint = 0

    /** Chamado no início do processamento oficial de um quadro (recordOcr). */
    fun openFrame() { scopeOpen = true; lines = null }

    /** Linhas espaciais do quadro oficial atual; ignora outros chamadores fora do escopo. */
    fun observeLines(value: List<SpatialOcrLine>) { if (scopeOpen) lines = value }

    /** Fecha o escopo no fim da classificação do quadro; retorna o diagnóstico quando aplicável. */
    fun closeFrame(platform: String?, candidate: Boolean, offers: Int, settings: () -> DriverSettings): Result? {
        val captured = lines
        scopeOpen = false; lines = null
        if (platform != "uber" || !candidate || offers > 0 || captured.isNullOrEmpty()) return null
        val now = System.currentTimeMillis()
        val fp = captured.sumOf { it.text.hashCode() }
        if (now - lastAt < MIN_INTERVAL_MS || fp == lastFingerprint) return null
        lastAt = now; lastFingerprint = fp
        return runCatching { diagnose(captured, settings()) }.getOrNull()
    }

    /** Reavalia as etapas puras do caminho Uber na ordem oficial. */
    fun diagnose(lines: List<SpatialOcrLine>, settings: DriverSettings): Result {
        val width = (lines.maxOfOrNull { it.box.right } ?: 1).coerceAtLeast(1)
        val height = (lines.maxOfOrNull { it.box.bottom } ?: 1).coerceAtLeast(1)
        val money = lines.count { it.text.contains("R$") || it.text.contains("$") }
        val geometry = lines.count { OfferContextEngine.isGeometry(it.text) }
        val fares = MoneyRoleResolver030.primarySpatialFareLines(lines)
        fun result(stage: Stage, reason: String, cluster: Int = 0) = Result(stage, reason, fares.size, money, geometry, cluster)
        if (fares.isEmpty()) return result(Stage.FARE, if (money == 0) "no_money_line" else "money_not_primary_fare")
        val fare = fares.first()
        val cluster = OfferSpatialIsolation0221.clusterAroundFare(lines, fare, width, height)
        if (cluster.isEmpty()) return result(Stage.ANCHOR, "empty_cluster")
        val text = cluster.joinToString("\n") { it.text }
        val strict = OfferSpatialIsolation0221.navigationNoise(lines)
        val anchored = if (strict) OfferSpatialIsolation0221.hasExplicitUberCardAnchor(text) else OfferSpatialIsolation0221.hasUberOfferAnchor(text)
        if (!anchored) return result(Stage.ANCHOR, if (strict) "strict_without_explicit_action" else "no_uber_anchor", cluster.size)
        val gate = UberScreenGate.classify(text)
        if (gate != UberScreenGate.Kind.OFFER_CANDIDATE) return result(Stage.SCREEN_GATE, "gate_${gate.name.lowercase()}", cluster.size)
        when (val legs = UberLegResolver106.resolve(text)) {
            is UberLegResolver106.Resolution.Ambiguous -> return result(Stage.LEGS, "legs_${legs.reason}", cluster.size)
            else -> Unit
        }
        if (UberOfferDetector.detect(BRUberLineSanitizer.sanitize(text)) == null) return result(Stage.DETECTOR, "detector_rejected", cluster.size)
        val offer = OfferParser.parse(text, AppSignals.UBER_PACKAGE, "diagnostic-drop", settings)
            ?: FlexibleDriverOfferParser.parseText(text, "uber", AppSignals.UBER_PACKAGE, "diagnostic-drop", settings)
            ?: return result(Stage.PARSER, "parser_rejected", cluster.size)
        val integrity = OfferIntegrityGate027033.assess(offer)
        if (!integrity.ready) return result(Stage.INTEGRITY, "integrity_" + integrity.flags.take(3).joinToString("+"), cluster.size)
        return result(Stage.NONE, "parse_ok_in_shadow", cluster.size)
    }
}
