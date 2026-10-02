# Gate 3 — Conciliação + Estatísticas + Pergunte + MCP

## Conciliação

O ranker compara somente dimensões disponíveis: valor, horário, categoria, origem, destino, duração, distância e a antiga pista `matched_ride_offer_id`. A pontuação normaliza pesos conforme a disponibilidade dos campos. Candidatos são apresentados ao motorista; somente a ação humana grava `source=reconciliation_v1`.

`no_match` também é uma decisão de conciliação: registra que a corrida importada foi revisada, mas não corresponde a uma oferta operacional identificável.

A antiga fonte `uber_history_ocr` é preservada para auditoria, mas:
- novas tentativas automáticas são bloqueadas por trigger;
- `listJourneyRealized0262` não a inclui;
- a view de aprendizado contextual do Radar não a inclui;
- ao revisar uma linha, o outcome legado correspondente é removido e, se confirmado, substituído por `reconciliation_v1`.

## Estatísticas

Cada aba chama intents do `srrotas-natural-question-v1`; não existe cálculo paralelo na UI.

- Resumo → PERIOD_SUMMARY + PROFITABILITY
- Produtividade → ACTUAL_DISTANCE + BEST_JOURNEY
- Horários → HOUR_PERFORMANCE
- Regiões → REGION_PERFORMANCE
- Corridas → COMPLETED_RIDES + REALIZED_EARNINGS
- Oportunidades → MISSED_OPPORTUNITIES + MARKET_QUALITY
- Custos → OPERATING_COST + PROFITABILITY

Oportunidades não realizadas contam apenas outcomes explícitos `NOT_COMPLETED`/`CANCELLED`. O valor das ofertas é exibido como valor observado, nunca como “dinheiro perdido”.

## Pergunte

A Web envia e reenvia `QuestionContext`, permitindo follow-ups curtos sem persistir a conversa inteira. A resposta mostra métricas, amostra, confiança, cobertura, completude, limitações e até três alternativas. Voz usa as APIs do navegador; não adiciona `RECORD_AUDIO` ao APK neste Gate.

## MCP

O toolset permanece com 12 nomes. Consultas analíticas gerais usam `askNaturalQuestion`, portanto compartilham a mesma fonte/semantic/EvidenceEnvelope de Estatísticas e Pergunte. Ferramentas operacionais específicas (busca de ofertas, estratégia e jornada) permanecem determinísticas e read-only.
