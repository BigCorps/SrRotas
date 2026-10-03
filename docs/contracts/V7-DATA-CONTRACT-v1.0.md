# Sr. Rotas — Contrato definitivo de reprocessamento histórico V7

Contrato: `srrotas-historical-offer-v1`
Timezone canônico: `America/Sao_Paulo`
Formato preferido: JSONL/NDJSON, uma oferta por linha.

## Regra central
A unidade de dados é UMA OFERTA, não uma imagem. Uma imagem pode gerar 0, 1 ou várias ofertas.
Nunca inventar valores. Campo não legível ou não presente deve ser `null` e produzir flag de qualidade.

## Arquivos obrigatórios
1. `srrotas_offers_v7_1.jsonl` — uma linha por oferta.
2. `srrotas_images_audit_v7_1.jsonl` — uma linha por imagem processada, inclusive `no_offer` e erro.
3. `srrotas_processing_manifest_v7_1.json` — totais, versão, datas, SHA-256 e contagens de qualidade.

## Rastreabilidade
- `schema_version`
- `extractor_version`
- `record_id`
- `source_file_name`
- `source_file_sha256`
- `offer_index`
- `crop_sha256` opcional/recomendado.

`record_id` deve ser SHA-256 determinístico de:
`source_file_sha256|offer_index|observed_at|fare|pickup_km|trip_km|pickup_minutes|trip_minutes|pickup_text_normalized|destination_text_normalized`

## Horário
- `observed_at`: ISO-8601; preferir UTC/Z.
- `observed_timezone`: `America/Sao_Paulo`.
- `time_source`: filename, image_metadata, visible_screen, external_metadata, inferred, unknown.
- `time_confidence`: 0..1.

Nunca usar o horário do reprocessamento como horário da oferta.

## Geometria — regra crítica
Extrair separadamente:
- `pickup_minutes`: motorista → passageiro
- `pickup_km`: motorista → passageiro
- `trip_minutes`: passageiro → destino
- `trip_km`: passageiro → destino

Calcular:
- `total_minutes = pickup_minutes + trip_minutes`
- `total_km = pickup_km + trip_km`

Os totais só existem quando as duas parcelas correspondentes existirem.
Nunca usar `trip_*` como `total_*`.
Nunca preencher pickup ausente com zero.
Nunca considerar financeiramente completa uma oferta com apenas uma perna.

## Localização
Preservar:
- `pickup_text`
- `destination_text`
- `driver_location_text` somente quando houver evidência real.

Não inferir localização do motorista a partir do pickup.
Opcional/recomendado: candidatos de região + confiança.

Bloquear textos de UI como região: `Área`, `Desloque-se até`, `Escolher`, `Aceitar`, `Destino`, `Buscar`, `Entrada principal` e similares.

## Econômico
- fare
- passenger_rating opcional
- advertised_per_km opcional
- bonus_amount opcional
- surge_multiplier opcional
- dynamic_signal: explicit, bonus, surge_like, none, unknown

Bônus e R$/km anunciado nunca substituem a tarifa principal.

## Plataforma/categoria
- platform: uber, 99, unknown
- platform_confidence
- service_type
- offer_type: exclusive, radar, unknown

Se não houver evidência suficiente, use unknown.

## Qualidade
Manter `quality_flags[]`, `ocr_confidence` e `field_confidence`.

Flags mínimas:
missing_observed_at, low_time_confidence, missing_fare,
missing_pickup_minutes, missing_pickup_km,
missing_trip_minutes, missing_trip_km,
missing_pickup_text, missing_destination_text,
generic_pickup_region, generic_destination_region,
leg_pairing_ambiguous, geometry_conflict,
advertised_rate_mismatch, platform_ambiguous,
multiple_cards_same_image, ocr_low_confidence, outlier_suspect.

## Quatro usos independentes
`demand_temporal_ready`: horário confiável + pickup/região utilizável.
`route_flow_ready`: horário + pickup + destination utilizáveis.
`financial_ready`: tarifa + 4 campos de geometria coerentes.
`fully_ready`: as três anteriores e sem conflito crítico.

Não usar um único `valid/invalid` para todos os motores.

## Validação
- valores não negativos;
- tarifa plausível;
- total_minutes exatamente igual à soma das pernas quando completo;
- total_km igual à soma com tolerância de arredondamento;
- advertised_per_km serve como validação cruzada;
- não misturar números de cards diferentes;
- cada card Radar/tela dividida é cluster independente.

## Multi-pass V7
1. leitura normal;
2. crop individual do card/painel;
3. re-OCR somente dos campos ausentes/ambíguos;
4. pré-processamento alternativo se ainda faltar dado.

Merge somente entre leituras do mesmo card.

## Deduplicação
Não deduplicar somente por `source_file_sha256`.
Uma imagem pode conter várias ofertas.
Usar `record_id` como idempotência principal e `source_file_sha256 + offer_index` como rastreabilidade.

## Auditoria da imagem
Para cada imagem registrar: nome, SHA-256, status (processed/no_offer/error/review), quantidade de cards, record_ids, número de passes e erro técnico.

## Manifesto final
Informar total de imagens, processadas, no_offer, erros, ofertas extraídas, valid/partial/invalid,
demand_temporal_ready, route_flow_ready, financial_ready, fully_ready,
Uber/99/unknown, distribuição por mês e hashes dos arquivos de saída.

## Preservação
Manter prints originais, saída antiga, saída nova, versão do extrator e original/normalizado.
Nunca apagar a versão anterior.

## Execução recomendada
Primeiro rodar 500–1.000 imagens estratificadas e auditar manualmente uma amostra.
Se aprovado, rodar as ~40 mil durante a madrugada.
O lote completo pode ser gerado antes da atualização do backend, mas não deve ser promovido ao motor estatístico até o importador novo validar este contrato.
