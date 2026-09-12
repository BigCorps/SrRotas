package com.srrotas.app

import com.google.mlkit.vision.text.Text
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs
import kotlin.math.round

/** Roteador multiplataforma com isolamento espacial por card/painel. */
object DriverPlatformOfferRouter {
    /**
     * RC3.1 — memória curta do painel da 99 em tela dividida.
     *
     * O OCR do tablet nem sempre lê "99Plus/99Pop/Escolher" em todos os frames.
     * Quando a identidade da 99 é vista com segurança, guardamos somente a posição
     * horizontal normalizada do painel por alguns segundos. Isso não guarda OCR,
     * endereço nem coordenadas e não altera o caminho da Uber.
     */
    private data class NinetyNinePaneMemory(
        val centerRatio: Double,
        val observedAt: Long,
        val landscape: Boolean,
    )

    private const val NINETY_NINE_PANE_MEMORY_MS = 30_000L
    private const val NINETY_NINE_PANE_TOLERANCE = 0.20
    @Volatile private var ninetyNinePaneMemory: NinetyNinePaneMemory? = null

    data class RoutedResult(
        val platform: String?,
        val offers: List<RideOffer>,
        val candidate: Boolean,
        val ownApp: Boolean = false,
        val reason: String = "contexto desconhecido",
    )

    fun parse(
        result: Text,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): RoutedResult {
        val raw = result.text
        val lower = DriverOcrNormalizer.sanitize(raw).lowercase()
        if (lower.isBlank()) return RoutedResult(null, emptyList(), false, reason = "ocr vazio")

        // RC3: Uber e 99 continuam sendo avaliadas no MESMO frame.
        // RC3.1 acrescenta continuidade de painel: se a identidade textual da 99
        // falhar em um frame, o card ainda pode ser encaminhado pelo painel que
        // acabou de ser confirmado como 99.
        val spatialLines = OfferSpatialIsolation0221.lines(result)
        rememberNinetyNinePane(
            lines = spatialLines,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        invalidateNinetyNinePaneIfUberTookOver(
            lines = spatialLines,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )

        val ninetyNineScreen = looksLike99(lower)
        val rememberedPaneAvailable =
            activeNinetyNinePane(frameWidth, frameHeight) != null
        val ninetyNineCandidate =
            FlexibleDriverOfferParser.primaryFare(raw) != null &&
                (ninetyNineScreen || rememberedPaneAvailable)

        var ninetyNineOffers = if (ninetyNineScreen) {
            FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
        } else {
            emptyList()
        }

        // Se o texto identificador da 99 caiu naquele frame, tentamos somente o
        // painel memorizado. Não liberamos o frame inteiro e rejeitamos clusters
        // que contenham âncora Uber.
        var ninetyNineUsedPaneMemory = false
        if (ninetyNineOffers.isEmpty() && rememberedPaneAvailable) {
            val remembered = parseNinetyNineFromRememberedPane(
                lines = spatialLines,
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (remembered.isNotEmpty()) {
                ninetyNineOffers = remembered
                ninetyNineUsedPaneMemory = true
                refreshNinetyNinePane(frameWidth, frameHeight)
            }
        }

        if (
            ninetyNineOffers.isEmpty() &&
            ninetyNineScreen &&
            FlexibleDriverOfferParser.looksLikeCandidate(raw) &&
            safeWholeFrameFallback(raw)
        ) {
            FlexibleDriverOfferParser.parseText(
                rawText = raw,
                platform = "99",
                sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                captureMethod = "media-projection-ocr/99-text-fallback-0265",
                settings = settings,
            )?.let { ninetyNineOffers = listOf(it) }
        }

        // Caminho Uber preservado: a RC3 não muda UberSpatialParser nem
        // OfferParser. Apenas deixa de retornar cedo por causa da 99.
        var uberOffers = UberSpatialParser0221.parse(
            result = result,
            settings = settings,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        // Se um cluster que saiu pelo caminho Uber contém identidade forte da 99
        // e nenhuma ação inequívoca da Uber, não deixamos o rótulo Uber contaminar
        // HUD/banco. Esse caso ocorre quando os painéis se encostam no split-screen.
        uberOffers = uberOffers.filterNot { offer ->
            val offerLower = DriverOcrNormalizer.sanitize(offer.rawText).lowercase()
            looksLike99(offerLower) && !explicitUberOfferAction(offer.rawText)
        }
        val uberAnchored = OfferSpatialIsolation0221.hasUberOfferAnchor(raw)
        if (uberOffers.isEmpty() && explicitUberOfferAction(raw)) {
            // RC3.2: o fallback textual só abre com ação inequívoca de oferta.
            // Nome de categoria (Comfort/Black/etc.) não prova sozinho que há card.
            if (FlexibleDriverOfferParser.primaryFareCount(raw) == 1) OfferParser.parse(
                rawText = raw,
                sourcePackage = AppSignals.UBER_PACKAGE,
                captureMethod = "media-projection-ocr/uber-text-fallback-0270",
                settings = settings,
            )?.let { uberOffers = listOf(it) }
        }

        var crossPlatformCollisionResolved = false
        if (ninetyNineOffers.isNotEmpty() && uberOffers.isNotEmpty()) {
            val resolved = CrossPlatformOfferArbitrator0270.resolve(
                ninetyNine = ninetyNineOffers,
                uber = uberOffers,
            )
            crossPlatformCollisionResolved = resolved.resolvedCollisions > 0
            ninetyNineOffers = resolved.ninetyNine
            uberOffers = resolved.uber
        }

        if (ninetyNineOffers.isNotEmpty() || uberOffers.isNotEmpty()) {
            val combined = (ninetyNineOffers + uberOffers)
                .distinctBy(OfferDeduplicator::semanticKey)
            val platform = when {
                ninetyNineOffers.isNotEmpty() && uberOffers.isNotEmpty() -> "multi"
                ninetyNineOffers.isNotEmpty() -> "99"
                else -> "uber"
            }
            val reason = when {
                crossPlatformCollisionResolved &&
                    ninetyNineOffers.isNotEmpty() && uberOffers.isEmpty() ->
                    "colisão Uber/99 resolvida: prevaleceu 99"
                crossPlatformCollisionResolved &&
                    uberOffers.isNotEmpty() && ninetyNineOffers.isEmpty() ->
                    "colisão Uber/99 resolvida: prevaleceu Uber"
                ninetyNineOffers.isNotEmpty() && uberOffers.isNotEmpty() ->
                    "candidatos Uber + 99 isolados no mesmo frame"
                ninetyNineOffers.isNotEmpty() && ninetyNineUsedPaneMemory ->
                    "candidato 99 recuperado por memória de painel"
                ninetyNineOffers.isNotEmpty() && uberAnchored ->
                    "candidato 99 isolado + Uber aguardando frame completo"
                ninetyNineOffers.isNotEmpty() &&
                    ninetyNineOffers.any { it.parserVersion == "sr-rotas-multi-v0.27.0-99-flex" } ->
                    "candidato 99 flex em tela dividida"
                ninetyNineOffers.isNotEmpty() -> "candidato 99"
                ninetyNineCandidate -> "candidato Uber isolado + 99 pendente"
                else -> "candidato Uber isolado"
            }
            return RoutedResult(platform, combined, true, reason = reason)
        }

        // Se nenhuma plataforma fechou um card, preservamos o motivo específico
        // para que o diagnóstico diferencie 99 incompleta de Uber incompleta.
        if (ninetyNineCandidate) {
            return RoutedResult(
                "99",
                emptyList(),
                true,
                reason = if (ninetyNineScreen) {
                    "candidato 99 aguardando geometria completa"
                } else {
                    "painel 99 memorizado aguardando card completo"
                },
            )
        }
        if (uberAnchored) {
            return RoutedResult(
                platform = "uber",
                offers = emptyList(),
                candidate = true,
                reason = "candidato Uber aguardando frame completo",
            )
        }

        val inferred = inferGenericPlatform(lower)
        if (FlexibleDriverOfferParser.looksLikeCandidate(raw)) {
            val offers = FlexibleDriverOfferParser.parseSpatial(
                result = result,
                platform = inferred,
                sourcePackage = AppSignals.inferredPackage(inferred),
                captureMethod = "media-projection-ocr/$inferred",
                settings = settings,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (offers.isNotEmpty()) {
                return RoutedResult(
                    inferred,
                    offers,
                    true,
                    reason = "candidato genérico/$inferred",
                )
            }
            if (safeWholeFrameFallback(raw)) FlexibleDriverOfferParser.parseText(
                rawText = raw,
                platform = inferred,
                sourcePackage = AppSignals.inferredPackage(inferred),
                captureMethod = "media-projection-ocr/$inferred-text-fallback-0265",
                settings = settings,
            )?.let {
                return RoutedResult(
                    inferred,
                    listOf(it),
                    true,
                    reason = "fallback textual/$inferred",
                )
            }
        }

        val gate = UberScreenGate.classify(raw)
        if (gate == UberScreenGate.Kind.OWN_APP) {
            return RoutedResult(
                null,
                emptyList(),
                false,
                ownApp = true,
                reason = "interface Sr. Rotas",
            )
        }

        val candidate = ninetyNineCandidate || FlexibleDriverOfferParser.looksLikeCandidate(raw)
        val reason = when (gate) {
            UberScreenGate.Kind.IDLE_OR_HOME -> "home/ocioso"
            UberScreenGate.Kind.FOREIGN_UI -> "outra interface"
            else -> if (
                OfferSpatialIsolation0221.navigationNoise(
                    OfferSpatialIsolation0221.lines(result),
                )
            ) {
                "tela dividida sem card isolado"
            } else {
                "contexto desconhecido"
            }
        }
        return RoutedResult(null, emptyList(), candidate, reason = reason)
    }

    private fun rememberNinetyNinePane(
        lines: List<SpatialOcrLine>,
        frameWidth: Int,
        frameHeight: Int,
    ) {
        if (frameWidth <= 0 || frameHeight <= 0 || lines.isEmpty()) return
        val ratios = lines
            .filter { FlexibleDriverOfferParser.primaryFare(it.text) != null }
            .mapNotNull { fareLine ->
                val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                    lines = lines,
                    fareLine = fareLine,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                )
                if (cluster.isEmpty()) return@mapNotNull null
                val clusterText = cluster.joinToString("\n") { it.text }
                if (!looksLike99(DriverOcrNormalizer.sanitize(clusterText).lowercase())) {
                    return@mapNotNull null
                }
                fareLine.box.centerX().toDouble() / frameWidth.toDouble()
            }
            .sorted()

        if (ratios.isEmpty()) return
        ninetyNinePaneMemory = NinetyNinePaneMemory(
            centerRatio = ratios[ratios.size / 2].coerceIn(0.0, 1.0),
            observedAt = System.currentTimeMillis(),
            landscape = frameWidth >= frameHeight,
        )
    }

    /**
     * Se o mesmo painel passar a exibir uma âncora Uber explícita, a memória da
     * 99 é invalidada imediatamente. Assim uma troca de app no mesmo lado da
     * tela dividida não transforma um card Uber em 99.
     */
    private fun invalidateNinetyNinePaneIfUberTookOver(
        lines: List<SpatialOcrLine>,
        frameWidth: Int,
        frameHeight: Int,
    ) {
        val memory = activeNinetyNinePane(frameWidth, frameHeight) ?: return
        val uberInRememberedPane = lines
            .filter { FlexibleDriverOfferParser.primaryFare(it.text) != null }
            .any { fareLine ->
                if (!sameRememberedPane(fareLine, memory, frameWidth)) {
                    false
                } else {
                    val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                        lines = lines,
                        fareLine = fareLine,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    )
                    val clusterText = cluster.joinToString("\n") { it.text }
                    OfferSpatialIsolation0221.hasUberOfferAnchor(clusterText)
                }
            }
        if (uberInRememberedPane) ninetyNinePaneMemory = null
    }

    private fun parseNinetyNineFromRememberedPane(
        lines: List<SpatialOcrLine>,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val memory = activeNinetyNinePane(frameWidth, frameHeight) ?: return emptyList()
        val strict = OfferSpatialIsolation0221.navigationNoise(lines)

        return lines
            .filter { FlexibleDriverOfferParser.primaryFare(it.text) != null }
            .filter { sameRememberedPane(it, memory, frameWidth) }
            .sortedBy { it.box.centerY() }
            .mapNotNull { fareLine ->
                val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                    lines = lines,
                    fareLine = fareLine,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                )
                if (cluster.isEmpty()) return@mapNotNull null
                val clusterText = cluster.joinToString("\n") { it.text }
                if (OfferSpatialIsolation0221.hasUberOfferAnchor(clusterText)) {
                    return@mapNotNull null
                }

                FlexibleDriverOfferParser.parse99FlexibleText(
                    rawText = clusterText,
                    sourcePackage = AppSignals.NINETY_NINE_PACKAGE,
                    captureMethod = "media-projection-ocr/99-pane-memory-0270",
                    settings = settings,
                    navigationNoise = strict,
                    trustedPane = true,
                )?.let { OfferContextExtractor0221.attach(it, cluster) }
            }
            .distinctBy(OfferDeduplicator::semanticKey)
    }

    private fun activeNinetyNinePane(
        frameWidth: Int,
        frameHeight: Int,
    ): NinetyNinePaneMemory? {
        val memory = ninetyNinePaneMemory ?: return null
        val now = System.currentTimeMillis()
        if (
            now - memory.observedAt > NINETY_NINE_PANE_MEMORY_MS ||
            memory.landscape != (frameWidth >= frameHeight)
        ) {
            ninetyNinePaneMemory = null
            return null
        }
        return memory
    }

    private fun sameRememberedPane(
        line: SpatialOcrLine,
        memory: NinetyNinePaneMemory,
        frameWidth: Int,
    ): Boolean {
        if (frameWidth <= 0) return false
        val ratio = line.box.centerX().toDouble() / frameWidth.toDouble()
        return abs(ratio - memory.centerRatio) <= NINETY_NINE_PANE_TOLERANCE
    }

    private fun refreshNinetyNinePane(
        frameWidth: Int,
        frameHeight: Int,
    ) {
        val current = activeNinetyNinePane(frameWidth, frameHeight) ?: return
        ninetyNinePaneMemory = current.copy(observedAt = System.currentTimeMillis())
    }

    private fun looksLike99(lower: String): Boolean {
        val strong = listOf(
            "perfil essencial",
            "plus nova",
            "99pop",
            "99 pop",
            "99plus",
            "99 plus",
            "99moto",
            "99 moto",
            "99táxi",
            "99taxi",
            "99electric",
            "99 entrega",
        ).any(lower::contains)
        if (strong) return true

        // "Escolher" é distintivo da oferta da 99. Em split-screen o OCR pode
        // separar "4 min" e "680 m" em linhas diferentes; por isso a detecção
        // usa a geometria flexível somente quando também existe tarifa principal.
        val action = lower.contains("escolher")
        val rideContext =
            lower.contains("corridas") ||
                lower.contains("solicitações") ||
                lower.contains("solicitacoes")
        val fare = FlexibleDriverOfferParser.primaryFare(lower) != null
        val looseMetrics = FlexibleDriverOfferParser.geometryCount99Flexible(lower) >= 2
        val advertised = lower.contains("/km")
        return action && fare && (rideContext || looseMetrics || advertised)
    }

    private fun explicitUberOfferAction(raw: String): Boolean {
        val lower = DriverOcrNormalizer.sanitize(raw).lowercase()
        return listOf("aceitar", "selecionar", "exclusivo", "radar de viagens")
            .any(lower::contains)
    }

    private fun safeWholeFrameFallback(raw: String): Boolean =
        FlexibleDriverOfferParser.primaryFareCount(raw) == 1 &&
            FlexibleDriverOfferParser.geometryCount(raw) in 2..3

    private fun inferGenericPlatform(lower: String): String = when {
        lower.contains("indrive") || lower.contains("in drive") -> "indrive"
        lower.contains("maxim") -> "maxim"
        else -> "other"
    }
}

object DriverOcrNormalizer {
    fun sanitize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .lines()
        .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()
}

/** Extrator financeiro para 99 e fallback genérico. */
object FlexibleDriverOfferParser {
    private val moneyRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val advertisedRegex = Regex(
        "(?:R\\$|\\$)\\s*([0-9OSoIlL]{1,4}(?:[.,][0-9OSoIlL]{1,2})?)\\s*/\\s*km",
        RegexOption.IGNORE_CASE,
    )

    internal val geometryRegex = Regex(
        "(?:\\(\\s*)?([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\s*(?:[·•\\-–—]?\\s*)?(?:\\(\\s*)?([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\s*\\)?",
        RegexOption.IGNORE_CASE,
    )

    // RC3 — somente 99. O ML Kit frequentemente entrega, no tablet/split-screen,
    // tempo e distância como linhas independentes. Mantemos o cluster espacial e
    // reconstruímos apenas as duas geometrias dentro dele.
    private val durationTokenRegex = Regex(
        "\\b([0-9OSoIlL]{1,3})\\s*(?:min|minuto|minutos)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val distanceTokenRegex = Regex(
        "\\b([0-9OSoIlL]{1,5}(?:[.,][0-9OSoIlL]{1,3})?)\\s*(km|m)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val rating99Regex = Regex(
        "\\b([45](?:[.,][0-9]{1,2})?)\\s*[·•]\\s*[0-9]{1,6}\\s*(?:corridas?|viagens?)",
        RegexOption.IGNORE_CASE,
    )
    private val ratingClassicRegex = Regex(
        "\\b([45](?:[.,][0-9]{1,2})?)\\s*\\(\\s*[0-9]{1,6}\\s*\\)",
        RegexOption.IGNORE_CASE,
    )

    data class TimeDistance(val minutes: Int, val km: Double)
    private data class OrderedDuration(val line: Int, val minutes: Int)
    private data class OrderedDistance(val line: Int, val km: Double)

    fun looksLikeCandidate(rawText: String): Boolean {
        val text = DriverOcrNormalizer.sanitize(rawText)
        if (primaryFare(text) == null) return false
        if (geometryCount(text) < 2) return false
        val lower = text.lowercase()
        return listOf(
            "aceitar", "escolher", "selecionar", "pegar",
            "corrida", "corridas", "viagem", "viagens", "oferta", "solicitação", "solicitacao",
        ).any(lower::contains) || advertisedRegex.containsMatchIn(text)
    }

    internal fun geometryCount(rawText: String): Int =
        geometryRegex.findAll(DriverOcrNormalizer.sanitize(rawText)).count()

    internal fun geometryCount99Flexible(rawText: String): Int =
        geometryPairs99Flexible(DriverOcrNormalizer.sanitize(rawText)).size

    fun parseSpatial(
        result: Text,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        frameWidth: Int,
        frameHeight: Int,
    ): List<RideOffer> {
        val lines = OfferSpatialIsolation0221.lines(result)
        if (lines.isEmpty()) return emptyList()
        val fareLines = lines.filter { primaryFare(it.text) != null }
        if (fareLines.isEmpty()) return emptyList()

        val strict = OfferSpatialIsolation0221.navigationNoise(lines)
        return fareLines.sortedBy { it.box.centerY() }.mapNotNull { fareLine ->
            val cluster = OfferSpatialIsolation0221.clusterAroundFare(
                lines = lines,
                fareLine = fareLine,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
            if (cluster.isEmpty()) return@mapNotNull null
            val text = cluster.joinToString("\n") { it.text }

            val parsed = if (platform == "99") {
                parse99FlexibleText(
                    rawText = text,
                    sourcePackage = sourcePackage,
                    captureMethod = captureMethod,
                    settings = settings,
                    navigationNoise = strict,
                )
            } else {
                if (!clusterCandidate(platform, text, strict, geometryCount(text))) {
                    null
                } else {
                    parseText(
                        rawText = text,
                        platform = platform,
                        sourcePackage = sourcePackage,
                        captureMethod = captureMethod,
                        settings = settings,
                    )
                }
            }

            parsed?.let { OfferContextExtractor0221.attach(it, cluster) }
        }.distinctBy(OfferDeduplicator::semanticKey)
    }

    /**
     * Parser 99 específico para o cluster já isolado. Primeiro tenta a geometria
     * clássica. Só se ela vier incompleta reconstrói tempo/distância entre linhas.
     */
    internal fun parse99FlexibleText(
        rawText: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        navigationNoise: Boolean = false,
        trustedPane: Boolean = false,
    ): RideOffer? {
        val text = DriverOcrNormalizer.sanitize(rawText)
        val strictPairs = geometryPairs(text)
        val pairs = if (strictPairs.size >= 2) strictPairs else geometryPairs99Flexible(text)
        if (!clusterCandidate(
                platform = "99",
                text = text,
                strict = navigationNoise,
                geometryPairs = pairs.size,
                trusted99Pane = trustedPane,
            )
        ) return null
        val version =
            if (strictPairs.size >= 2) "sr-rotas-multi-v0.22.1"
            else "sr-rotas-multi-v0.27.0-99-flex"
        return buildOffer(
            text = text,
            platform = "99",
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            settings = settings,
            pairs = pairs,
            parserVersion = version,
        )
    }

    private fun clusterCandidate(
        platform: String,
        text: String,
        strict: Boolean,
        geometryPairs: Int,
        trusted99Pane: Boolean = false,
    ): Boolean {
        if (geometryPairs < 2 || primaryFare(text) == null) return false
        if (platform == "99") {
            // Memória de painel é uma confiança espacial temporária, não uma
            // liberação geral: ainda exigimos tarifa + 2 geometrias e recusamos
            // qualquer cluster que passe a carregar âncora Uber.
            if (trusted99Pane) {
                return !OfferSpatialIsolation0221.hasUberOfferAnchor(text)
            }

            val lower = text.lowercase()
            val choose = lower.contains("escolher")
            val service = listOf(
                "plus nova", "99plus", "99 plus", "99pop", "99 pop",
                "99moto", "99 moto", "99taxi", "99táxi", "99electric", "99 entrega",
            ).any(lower::contains)
            val profile = lower.contains("perfil essencial")
            val request = lower.contains("solicitações") || lower.contains("solicitacoes") || lower.contains("corridas")
            val advertised = advertisedRegex.containsMatchIn(text)
            if (!(choose || service || profile)) return false

            // Quando Waze/Maps também está no frame, não aceitamos um card só
            // porque existem números. Exigimos a ação da 99 OU identidade de
            // serviço acompanhada de R$/km. Isso preserva isolamento entre apps.
            return if (strict) {
                choose || (service && advertised) || (profile && advertised)
            } else {
                choose || request || advertised || service || profile
            }
        }
        val lower = text.lowercase()
        val action = listOf("aceitar", "escolher", "selecionar", "pegar").any(lower::contains)
        val platformAnchor = when (platform) {
            "indrive" -> lower.contains("indrive") || lower.contains("in drive")
            "maxim" -> lower.contains("maxim")
            else -> false
        }
        return action || platformAnchor
    }

    fun parseText(
        rawText: String,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
    ): RideOffer? {
        val text = DriverOcrNormalizer.sanitize(rawText)
        val pairs = geometryPairs(text)
        if (pairs.size < 2) return null
        return buildOffer(
            text = text,
            platform = platform,
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            settings = settings,
            pairs = pairs,
            parserVersion = "sr-rotas-multi-v0.22.1",
        )
    }

    private fun buildOffer(
        text: String,
        platform: String,
        sourcePackage: String,
        captureMethod: String,
        settings: DriverSettings,
        pairs: List<TimeDistance>,
        parserVersion: String,
    ): RideOffer? {
        val fare = primaryFare(text) ?: return null
        if (pairs.size < 2) return null

        val pickup = pairs[0]
        val trip = pairs[1]
        val totalKm = pickup.km + trip.km
        val totalMinutes = pickup.minutes + trip.minutes
        if (totalKm <= 0.0 || totalMinutes <= 0) return null

        val perKm = fare / totalKm
        val perHour = fare / (totalMinutes / 60.0)
        val perMinute = fare / totalMinutes
        if (!OfferValidator.isPlausible("exclusive", fare, totalKm, totalMinutes, perKm, perHour, perMinute)) {
            return null
        }

        val advertised = advertisedRegex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        if (advertised != null && advertised > 0.0) {
            val delta = abs(advertised - perKm) / advertised
            if (delta > 0.25) return null
        }

        val rating = rating99Regex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
            ?: ratingClassicRegex.find(text)?.groupValues?.getOrNull(1)?.let(OfferParser::parseNumberCandidate)
        val service = serviceType(text, platform)

        val estimatedCost = totalKm * settings.costPerKm
        val estimatedProfit = fare - estimatedCost
        val profitPerHour = estimatedProfit / (totalMinutes / 60.0)
        val profitPercent = if (fare > 0.0) estimatedProfit / fare * 100.0 else null

        var confidence = if (platform == "99") 0.84 else 0.74
        if (advertised != null) confidence += 0.05
        if (rating != null) confidence += 0.03
        if (service != "unknown") confidence += 0.03
        if (parserVersion.endsWith("99-flex")) confidence -= 0.02

        val observed = Instant.now()
        val bucket = observed.epochSecond / 120L
        val dedupe = sha256(
            listOf(
                platform,
                round2(fare),
                round2(pickup.km),
                round2(trip.km),
                round2(totalKm),
                totalMinutes,
                bucket,
            ).joinToString("|"),
        ).take(40)

        return RideOffer(
            observedAt = observed.toString(),
            platform = platform,
            sourcePackage = sourcePackage,
            captureMethod = captureMethod,
            rawText = text.take(12000),
            fare = round2(fare),
            pickupKm = round2(pickup.km),
            tripKm = round2(trip.km),
            totalKm = round2(totalKm),
            pickupMinutes = pickup.minutes,
            tripMinutes = trip.minutes,
            totalMinutes = totalMinutes,
            perKm = round2(perKm),
            perHour = round2(perHour),
            perMinute = round2(perMinute),
            estimatedCost = round2(estimatedCost),
            estimatedProfit = round2(estimatedProfit),
            profitPerHour = round2(profitPerHour),
            profitPercent = profitPercent?.let(::round2),
            passengerRating = rating?.let(::round2),
            advertisedPerKm = advertised?.let(::round2),
            serviceType = service,
            verdict = "regular",
            confidence = confidence.coerceIn(0.50, 0.97),
            offerType = "exclusive",
            parserVersion = parserVersion,
            dedupeKey = dedupe,
        )
    }

    internal fun primaryFareCount(text: String): Int {
        val normalized = DriverOcrNormalizer.sanitize(text)
        var count = 0
        for (match in moneyRegex.findAll(normalized)) {
            val start = match.range.first
            val before = normalized.substring(maxOf(0, start - 2), start).trim()
            if (before.endsWith("+")) continue
            val afterStart = match.range.last + 1
            val after = normalized.substring(afterStart, minOf(normalized.length, afterStart + 10))
            if (Regex("^\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(after)) continue
            val value = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: continue
            if (value in 2.0..1000.0) count++
        }
        return count
    }

    internal fun primaryFare(text: String): Double? {
        val normalized = DriverOcrNormalizer.sanitize(text)
        for (match in moneyRegex.findAll(normalized)) {
            val start = match.range.first
            val before = normalized.substring(maxOf(0, start - 2), start).trim()
            if (before.endsWith("+")) continue
            val afterStart = match.range.last + 1
            val after = normalized.substring(afterStart, minOf(normalized.length, afterStart + 10))
            if (Regex("^\\s*/\\s*km", RegexOption.IGNORE_CASE).containsMatchIn(after)) continue
            val value = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: continue
            if (value in 2.0..1000.0) return value
        }
        return null
    }

    private fun geometryPairs(text: String): List<TimeDistance> =
        geometryRegex.findAll(text).mapNotNull { match ->
            val minutes = OfferParser.parseNumberCandidate(match.groupValues[1])?.toInt() ?: return@mapNotNull null
            val rawDistance = OfferParser.parseNumberCandidate(match.groupValues[2]) ?: return@mapNotNull null
            val unit = match.groupValues[3].lowercase()
            val km = if (unit == "m") rawDistance / 1000.0 else rawDistance
            if (minutes !in 1..360 || km !in 0.05..500.0) null else TimeDistance(minutes, km)
        }.toList()

    /**
     * Reconstrói pares somente dentro do texto de um cluster 99. Cada duração é
     * associada à distância livre mais próxima (mesma linha ou até três linhas),
     * impedindo que endereços distantes e o R$/km sejam tratados como geometria.
     */
    private fun geometryPairs99Flexible(text: String): List<TimeDistance> {
        val lines = DriverOcrNormalizer.sanitize(text).lines()
        val durations = mutableListOf<OrderedDuration>()
        val distances = mutableListOf<OrderedDistance>()

        lines.forEachIndexed { index, line ->
            durationTokenRegex.findAll(line).forEach { match ->
                val value = OfferParser.parseNumberCandidate(match.groupValues[1])?.toInt() ?: return@forEach
                if (value in 1..360) durations += OrderedDuration(index, value)
            }

            // Linhas monetárias nunca representam distância da rota. Isso evita
            // interpretar "R$ 3,83 km" como distância caso o OCR perca a barra.
            if (line.contains("R$", ignoreCase = true) || line.contains('$')) {
                return@forEachIndexed
            }
            distanceTokenRegex.findAll(line).forEach { match ->
                val raw = OfferParser.parseNumberCandidate(match.groupValues[1]) ?: return@forEach
                val unit = match.groupValues[2].lowercase()
                val km = if (unit == "m") raw / 1000.0 else raw
                if (km in 0.05..500.0) distances += OrderedDistance(index, km)
            }
        }

        if (durations.size < 2 || distances.size < 2) return emptyList()

        val used = BooleanArray(distances.size)
        val pairs = mutableListOf<Pair<Int, TimeDistance>>()
        durations.forEach { duration ->
            val best = distances.indices
                .asSequence()
                .filter { !used[it] }
                .map { index ->
                    val distance = distances[index]
                    val lineDelta = abs(distance.line - duration.line)
                    Triple(index, distance, lineDelta)
                }
                .filter { it.third <= 3 }
                .minWithOrNull(
                    compareBy<Triple<Int, OrderedDistance, Int>> { it.third }
                        .thenBy { if (it.second.line < duration.line) 1 else 0 }
                        .thenBy { it.second.line },
                )
                ?: return@forEach

            used[best.first] = true
            pairs += duration.line to TimeDistance(duration.minutes, best.second.km)
        }

        return pairs.sortedBy { it.first }.map { it.second }.take(3)
    }

    private fun serviceType(text: String, platform: String): String {
        val lower = text.lowercase()
        if (platform == "99") {
            return when {
                lower.contains("plus nova") || lower.contains("99plus") || lower.contains("99 plus") -> "99plus"
                lower.contains("99pop") || lower.contains("99 pop") -> "99pop"
                lower.contains("99moto") || lower.contains("99 moto") -> "99moto"
                lower.contains("99táxi") || lower.contains("99taxi") -> "99taxi"
                lower.contains("99electric") -> "99electric"
                lower.contains("99 entrega") || lower.contains("99entrega") -> "99entrega"
                else -> "99"
            }
        }
        return when {
            lower.contains("indrive") || lower.contains("in drive") -> "indrive"
            lower.contains("maxim") -> "maxim"
            else -> "unknown"
        }
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
