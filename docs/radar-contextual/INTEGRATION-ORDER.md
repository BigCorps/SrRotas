# Ordem exata de integração

## 1 — Banco
Aplicar nesta ordem:
1. `20261001210000_radar_poi_context_v1.sql`
2. `20261001210100_radar_poi_resolver_v1.sql`
3. `20261001210200_radar_contextual_learning_v1.sql`

Validar RLS/privileges e rollback antes de seguir.

## 2 — Backend sem ativar Android
Adicionar:
- opportunity engine/contracts/adapters;
- POI service/resolver;
- rotas `/radar/poi-*`;
- `/radar/contextual`;
- `/radar/contextual/events`.

Não substituir `/api/v1/radar/ingest` inicialmente.
Rodar POI reconcile manual/admin.

## 3 — Testar backend
Casos mínimos:
- destino sem amostra;
- destino com baseline;
- POI com estatística própria;
- contexto externo sem estatística;
- evento ambíguo -> REVIEW;
- dedupe de telemetria.

## 4 — Android UI em shadow
Adicionar classes do módulo, flags todas FALSE.
Ativar somente `uiEnabled` em tester.
Manter `RadarPanel027035` como rollback.

## 5 — Runtime
Após UI estável, ativar `runtimeEnabled`.
Confirmar cadência adaptativa e ausência de efeito no OCR.

## 6 — Assistente
Somente depois:
`assistantEnabled=true`.
Reutilizar renderer/ancoragem visual do mascote, NÃO o loop do ActiveAssistant026.

## 7 — Learning
Validar rows de `sr_radar_contextual_learning_v1`.
Não usar a view como prova causal.
