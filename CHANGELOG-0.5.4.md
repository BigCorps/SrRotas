# Changelog — Sr. Rotas 0.5.4 Alpha

## Card Stabilizer

Novo estágio entre parser e persistência. O HUD continua imediato; histórico/backend aguardam no máximo 750 ms para consolidar leituras do mesmo card.

A seleção prioriza geometria completa, tempos completos, R$/km anunciado coerente, categoria reconhecida, avaliação presente e confiança maior.

Tarifa não identifica o card, pois foi justamente o campo que oscilou nos testes. Dois cards semelhantes no mesmo frame Radar continuam buckets separados.

## Mantido da 0.5.3

OCR 250 ms, bitmap máximo 2100 px, dois frames pendentes, correção `1l/ll`, Electric, bloqueio de UI estrangeira e dedupe já calibrado.
