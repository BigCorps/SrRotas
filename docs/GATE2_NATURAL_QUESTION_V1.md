# Gate 2 — Fundação Premium / Natural Question v1

Contrato: `srrotas-natural-question-v1`.

## Escopo deste patch

- AnalyticsDomain único no backend.
- EvidencePolicy universal e semântico.
- NaturalQuestionEngine determinístico, sem chamada a OpenAI e sem consumo de crédito por pergunta.
- 18 intents canônicos.
- QuestionContext curto para follow-up como `E ontem?` e `E por hora?`.
- Separação obrigatória entre oferta observada, corrida/faturamento realizado, custo estimado, gasto registrado e distância real.
- Reuso de `listJourneyRealized0262`, `listJourneyMetrics`, `nowIntelligence`, `destinationContinuity`, perfil de custos e tabelas atuais.
- `/api/v1/ask` mantém campos de compatibilidade (`answer`, `range`, `offer_count`, `model`, `usage`) e adiciona `StructuredAnswer`.

## Guardrails

- V7 nunca vira corrida concluída/faturamento realizado.
- Ranking regional/horário/categoria exige amostra mínima de 20.
- Base Coletiva continua dependente do opt-in e das views agregadas existentes.
- `MISSED_OPPORTUNITIES` é reconhecido, porém retorna `not_ready` até a Conciliação do Gate 3; não estima receita perdida sem confirmação humana.
- Nenhuma alteração em Reader, MediaProjection, HUD, Radar, captura automática ou Android.
- Nenhuma migration necessária neste Gate 2 inicial.

## Smoke canônico

`backend/src/intelligence/self-check.ts` cobre os 18 intents, `E ontem?`, `E por hora?` e normalização controlada de erros simples.
