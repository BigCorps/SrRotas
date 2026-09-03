package com.srrotas.app

import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Isolamento espacial para não misturar Uber/99 com Waze/Maps ou cards vizinhos.
 *
 * 0.26.1 acrescenta divisores verticais entre valores principais. Quando duas
 * ofertas aparecem simultaneamente, cada preço passa a possuir sua própria faixa
 * vertical e não herda tempo/distância do card de cima ou de baixo.
 */
internal object OfferSpatialIsolation0221 {
    fun lines(result: Text): List<SpatialOcrLine> =
        result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = DriverOcrNormalizer.sanitize(line.text)
            if (text.isBlank()) null else SpatialOcrLine(text, box)
        }

    fun navigationNoise(lines: List<SpatialOcrLine>): Boolean {
        val lower = lines.joinToString("\n") { it.text }.lowercase()
        val navNames = listOf(
            "waze",
            "google maps",
            "maps",
            "iniciar trajeto",
            "rotas",
            "recentralizar",
        )
        return navNames.any(lower::contains)
    }

    fun clusterAroundFare(
        lines: List<SpatialOcrLine>,
        fareLine: SpatialOcrLine,
        frameWidth: Int,
        frameHeight: Int,
        strictHorizontal: Boolean = navigationNoise(lines),
    ): List<SpatialOcrLine> {
        val cx = fareLine.box.centerX()
        val cy = fareLine.box.centerY()
        val horizontalRadius = (
            frameWidth * if (strictHorizontal) 0.20 else 0.42
        ).toInt().coerceAtLeast(180)
        val verticalRadius = (frameHeight * 0.36).toInt().coerceAtLeast(260)

        val fareCandidates = lines
            .filter { line ->
                UberOfferDetector.isPrimaryFareLine(line.text) ||
                    FlexibleDriverOfferParser.primaryFare(line.text) != null
            }
            .filter { line ->
                val lineCx = line.box.centerX()
                abs(lineCx - cx) <= horizontalRadius ||
                    (
                        line.box.left <= fareLine.box.right + horizontalRadius / 2 &&
                            line.box.right >= fareLine.box.left - horizontalRadius / 2
                        )
            }
            .sortedBy { it.box.centerY() }

        val currentIndex = fareCandidates.indexOfFirst { sameLine(it, fareLine) }
        val previousFareY = if (currentIndex > 0) {
            fareCandidates[currentIndex - 1].box.centerY()
        } else {
            null
        }
        val nextFareY = if (currentIndex >= 0 && currentIndex < fareCandidates.lastIndex) {
            fareCandidates[currentIndex + 1].box.centerY()
        } else {
            null
        }

        val naturalTop = (cy - verticalRadius).coerceAtLeast(0)
        val naturalBottom = min(frameHeight, cy + verticalRadius)
        val band = HudReliabilityRules0261.verticalBand(
            currentY = cy,
            previousFareY = previousFareY,
            nextFareY = nextFareY,
            naturalTop = naturalTop,
            naturalBottom = naturalBottom,
        )

        return lines.filter { line ->
            val lineCx = line.box.centerX()
            val centerNear = abs(lineCx - cx) <= horizontalRadius
            val overlapsFareColumn =
                line.box.left <= fareLine.box.right + horizontalRadius / 2 &&
                    line.box.right >= fareLine.box.left - horizontalRadius / 2
            val centerY = line.box.centerY()
            val yNear = centerY in band
            yNear && (centerNear || overlapsFareColumn)
        }.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })
    }

    fun paneForFares(
        lines: List<SpatialOcrLine>,
        fares: List<SpatialOcrLine>,
        frameWidth: Int,
        frameHeight: Int,
    ): List<SpatialOcrLine> {
        if (fares.isEmpty()) return emptyList()
        val centerX = fares.map { it.box.centerX() }.sorted()[fares.size / 2]
        val strict = navigationNoise(lines)
        val radius = (frameWidth * if (strict) 0.21 else 0.44).toInt().coerceAtLeast(190)
        val fareTop = fares.minOf { it.box.top }
        val fareBottom = fares.maxOf { it.box.bottom }
        val extraY = (frameHeight * 0.28).toInt().coerceAtLeast(220)
        val top = max(0, fareTop - extraY)
        val bottom = min(frameHeight, fareBottom + extraY)
        return lines.filter { line ->
            line.box.centerY() in top..bottom && abs(line.box.centerX() - centerX) <= radius
        }.sortedWith(compareBy<SpatialOcrLine> { it.box.top }.thenBy { it.box.left })
    }

    fun hasExplicitUberCardAnchor(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("aceitar", "selecionar", "exclusivo", "radar de viagens")
            .any(lower::contains)
    }

    fun hasUberOfferAnchor(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "aceitar",
            "selecionar",
            "exclusivo",
            "radar de viagens",
            "uberx",
            "comfort",
            "priority",
            "uber moto",
            "ubermoto",
            "black",
            "electric",
        ).any(lower::contains)
    }

    fun has99OfferAnchor(text: String): Boolean {
        val lower = text.lowercase()
        val strong = listOf(
            "escolher",
            "perfil essencial",
            "plus nova",
            "99pop",
            "99 pop",
            "99plus",
            "99 plus",
            "99moto",
            "99 moto",
            "99taxi",
            "99táxi",
        ).any(lower::contains)
        val requestContext =
            lower.contains("solicitações") ||
                lower.contains("solicitacoes") ||
                lower.contains("corridas")
        return strong &&
            (requestContext || FlexibleDriverOfferParser.geometryCount(text) >= 2)
    }

    private fun sameLine(a: SpatialOcrLine, b: SpatialOcrLine): Boolean =
        a.box == b.box && a.text == b.text

}
