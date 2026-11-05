package com.srrotas.app

/**
 * vc111 — texto do Histórico Uber a partir da GEOMETRIA do OCR oficial.
 *
 * No Histórico, o horário da viagem ("7:35") é um bloco isolado na coluna direita, na altura da
 * linha "Serviço · duração · distância". No texto concatenado do ML Kit ele vinha fora do card e,
 * sozinho na linha, ainda era descartado como relógio da barra de status
 * (`UberDigitizationText0265`). Aqui cada horário isolado é ligado à linha visual que ocupa a mesma
 * faixa vertical à sua esquerda e reinserido logo após ela como "às h:mm". Valores isolados
 * seguidos, na mesma faixa, de "Preço dinâmico"/"Valor extra" viram uma linha só (não são tarifa).
 * Nada é inventado: sem linha correspondente à esquerda, o horário fica de fora.
 */
internal object UberHistoryLayout111 {
    private val TIME_ONLY = Regex("^\\d{1,2}\\s*[:hH]\\s*[0-5]\\d$")
    private val MONEY_ONLY = Regex("^[⊕•·+\\s]*R\\$\\s*\\d{1,5}(?:[.,]\\d{2})\\s*$", RegexOption.IGNORE_CASE)
    private val MONEY_LABEL = Regex("^(preço dinâmico|preco dinamico|valor extra|valor a mais|gorjeta)", RegexOption.IGNORE_CASE)

    fun text(lines: List<SpatialOcrLine>): String {
        if (lines.isEmpty()) return ""
        val frameWidth = (lines.maxOf { it.box.right }).coerceAtLeast(1)
        val texts = lines.map { it.text.trim() }.toMutableList()
        val drop = BooleanArray(lines.size)
        val after = HashMap<Int, MutableList<String>>()

        // Rótulo de dinâmico/extra na mesma faixa visual de um valor isolado: uma linha só.
        lines.forEachIndexed { i, money ->
            if (!MONEY_ONLY.matches(texts[i])) return@forEachIndexed
            val label = lines.indices.firstOrNull { j ->
                j != i && !drop[j] && MONEY_LABEL.containsMatchIn(texts[j]) && sameRow(money, lines[j]) &&
                    lines[j].box.left >= money.box.left && lines[j].box.left - money.box.right < frameWidth / 3
            } ?: return@forEachIndexed
            texts[i] = texts[i] + " " + texts[label]
            drop[label] = true
        }

        lines.forEachIndexed { i, time ->
            if (!TIME_ONLY.matches(texts[i])) return@forEachIndexed
            // Linhas à esquerda na mesma faixa vertical (mesmo card), na ordem nativa.
            val row = lines.indices.filter { j ->
                j != i && !drop[j] && !TIME_ONLY.matches(texts[j]) && lines[j].box.right <= time.box.left + time.box.width() / 2 &&
                    time.box.left - lines[j].box.right < frameWidth * 0.6 && verticalOverlap(time, lines[j]) > 0.35
            }
            if (row.isEmpty()) return@forEachIndexed // relógio/ruído: sem linha do card à esquerda
            drop[i] = true
            // A linha "Serviço · duração · 5.87" pode quebrar ("km" na linha de baixo): o horário entra
            // depois da continuação, sem separar número e unidade.
            var end = row.max()
            while (end + 1 < lines.size && end + 1 != i && !TIME_ONLY.matches(texts[end + 1]) &&
                lines[end + 1].box.top <= time.box.bottom + time.box.height() &&
                lines[end + 1].box.right <= time.box.left && kotlin.math.abs(lines[end + 1].box.left - lines[end].box.left) < frameWidth / 10
            ) end++
            after.getOrPut(end) { mutableListOf() } += "às " + texts[i].replace(Regex("\\s+"), "")
        }
        return buildList {
            texts.forEachIndexed { i, t ->
                if (!drop[i]) add(t)
                after[i]?.let(::addAll)
            }
        }.joinToString("\n")
    }

    private fun sameRow(a: SpatialOcrLine, b: SpatialOcrLine) = verticalOverlap(a, b) > 0.5

    /** Fração da menor altura que se sobrepõe verticalmente. */
    private fun verticalOverlap(a: SpatialOcrLine, b: SpatialOcrLine): Double {
        val top = maxOf(a.box.top, b.box.top); val bottom = minOf(a.box.bottom, b.box.bottom)
        val h = minOf(a.box.height(), b.box.height()).coerceAtLeast(1)
        return (bottom - top).coerceAtLeast(0).toDouble() / h
    }
}
