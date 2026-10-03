# Contrato canônico — `srrotas-radar-poi-resolution-v1`

## Função
Resolver um `sr_event_opportunities` para um `sr_radar_pois` sem inventar identidade.

## Ordem de decisão
1. `source + source_external_id` conhecido -> match exato.
2. candidatos por célula g2 e células vizinhas;
3. nome normalizado / aliases;
4. distância geográfica;
5. endereço como evidência auxiliar;
6. tipo compatível.

## Thresholds v1
- `score >= 0.82` e `distance <= 350m` -> `linked`;
- sem candidato forte, fonte confiável + coordenadas válidas -> `created`;
- zona ambígua -> `review`;
- entrada inadequada -> `skipped`.

## Regra central
`review` NUNCA grava `sr_event_opportunities.poi_id` e o log de revisão também permanece sem `poi_id`.

Um evento pode existir perfeitamente sem POI resolvido. Isso preserva o Radar legado e evita fusões falsas.

## Não faz parte deste contrato
- previsão de demanda;
- score econômico;
- leitura V7;
- alteração de OCR;
- alteração do parser de oferta.
