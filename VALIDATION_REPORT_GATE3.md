# Validation Report — Gate 3/7

## Base
- GitHub `main`: `638b8c1dfe4ea4c527e417da8b13a37e8ecb3440` no início da construção.
- Pacote é um overlay; não contém Reader/HUD/Radar Android nem APK.

## TypeScript
- Checagem `strict/noEmit` executada sobre todos os `.ts/.tsx` novos/alterados.
- Contratos `srrotas-natural-question-v1` e `McpContext` canônicos foram usados temporariamente durante a checagem e NÃO fazem parte do overlay final.
- Resultado final: PASS.

## MCP
- `registerTool`: 12 ocorrências.
- 12 nomes únicos e exatamente os mesmos do Gate 1.
- Nenhum import OpenAI nos arquivos novos/alterados.
- Annotations permanecem read-only / non-destructive / idempotent / closed-world.

## PostgreSQL / Supabase
Validações executadas em transações com `ROLLBACK`:
- criação de `sr_ride_reconciliations`, FKs, checks e índices: PASS;
- função/trigger que impede novas promoções automáticas `uber_history_ocr`: PASS;
- substituição da view `sr_radar_contextual_learning_v1` excluindo semântica automática legada: PASS.

Nenhuma migration nova do Gate 3 foi persistida durante a validação.

A migration `20261002145358_radar_contextual_fk_indexes_v1.sql` já existe no histórico real do Supabase e entra apenas para sincronizar o repositório; seus índices já estão em produção.

## Ranker de Conciliação
Smoke determinístico:
- candidato compatível → 94,84/100, confiança `high`;
- candidato incompatível → 0/100, `insufficient`.
- confirmação continua obrigatoriamente humana.

## Semântica realizada
- `listJourneyRealized0262` passa a excluir `source=uber_history_ocr`.
- `reconciliation_v1` é a nova fonte criada após confirmação humana.
- registros `uber_history_ocr` antigos não são apagados em massa; ficam para auditoria/revisão.
- ofertas `NOT_COMPLETED/CANCELLED` podem sustentar “oportunidades não realizadas”, mas o valor ofertado nunca é chamado de receita perdida.

## Próxima validação obrigatória
Após upload:
1. GitHub Actions completo;
2. Vercel READY;
3. aplicar as duas migrations novas do Gate 3;
4. smoke autenticado das três telas e endpoints;
5. QA de uma confirmação + um `no_match`;
6. conferir paridade Estatísticas/Pergunte/MCP.
