# PROMPT — Atualização e reprocessamento V7 do Sr. Rotas

Atualize o V7 para obedecer integralmente ao contrato `srrotas-historical-offer-v1`.

Objetivo: reprocessar aproximadamente 40 mil screenshots históricos e novos screenshots futuros, gerando dados rastreáveis e adequados para inteligência temporal/geográfica e análise financeira.

Regras obrigatórias:
1. Uma linha representa UMA OFERTA; uma imagem pode gerar 0, 1 ou várias ofertas.
2. Calcule SHA-256 real da imagem, `offer_index` por card e `record_id` determinístico por oferta.
3. Extraia o horário real da oferta e registre `time_source` + `time_confidence`. Nunca use o horário do reprocessamento.
4. Separe obrigatoriamente:
   - pickup_minutes = motorista → passageiro
   - pickup_km = motorista → passageiro
   - trip_minutes = passageiro → destino
   - trip_km = passageiro → destino
5. Só calcule total quando as duas pernas existirem. Nunca use trip como total e nunca preencha pickup ausente com zero.
6. Preserve `pickup_text` e `destination_text`; textos de UI não são regiões.
7. Não invente plataforma, região, km ou minutos. Use `null`/`unknown` + flags quando incerto.
8. Em Radar/tela dividida, segmente cada card e associe números somente ao próprio cluster.
9. Faça até quatro passes: normal, crop, re-OCR de campos faltantes e pré-processamento alternativo.
10. Gere:
   - demand_temporal_ready
   - route_flow_ready
   - financial_ready
   - fully_ready
11. Gere `quality_flags[]`, `ocr_confidence` e confiança por campo.
12. Entregue:
   - `srrotas_offers_v7_1.jsonl`
   - `srrotas_images_audit_v7_1.jsonl`
   - `srrotas_processing_manifest_v7_1.json`
13. Não sobrescreva a saída anterior e não apague os prints originais.
14. Antes do lote completo, rode 500–1.000 imagens estratificadas e gere relatório de qualidade.
15. Use o JSON Schema fornecido como contrato formal.

Qualquer campo não confiável deve permanecer nulo e explicitamente sinalizado. Nunca complete por adivinhação.
