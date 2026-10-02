# Validation report — Gate 2 + Radar Contextual

## Correções feitas durante a consolidação

1. Corrigido `DestinationRadarAssistantUiV1`: helper de ação agora recebe `Context` explicitamente.
2. Corrigido reuso de Views em `RadarContextualPanelV1` para evitar `already has a parent` após refresh/seleção.
3. Card selecionado alinhado ao tom suportado por `SrUi023`.
4. Loop do runtime reduzido para 30 s para respeitar janelas efetivas de 60/90/180/300 s sem aumentar chamadas de rede.
5. Continuidade do Radar ganhou adapter/cache curto e proteção de opt-in coletivo; o cálculo P10 continua vindo do motor canônico existente.
6. Busca de POIs limitada geograficamente antes do Haversine e continuidade limitada a até 12 células em lotes.
7. Rotas de inteligência/telemetria contextual alinhadas a `can_analytics`.
8. POI resolver passou a usar `source + external_id` quando disponível, validar latitude/longitude e manter `review` sem associação automática.
9. Alias de POI ficou conservador para não transformar título de evento em nome permanente de local sem evidência.
10. Telemetria backend aceita apenas metadata escalar whitelisted, sem OCR bruto/endereço/coordenadas.
11. Grants de sequences das migrations foram reduzidos às sequences novas específicas.
12. TTNR da view observacional ficou limitado à mesma jornada da corrida concluída.
13. Testes TypeScript de referência foram retirados de `backend/src` para não entrarem no `tsconfig` sem Jest.
14. Nomes das migrations receberam timestamps únicos e ordem inequívoca.
15. Confirmada a convenção existente: diretório físico `com/bigcorps/driveraimvp`, package Kotlin `com.srrotas.app`; não foi alterada.

## Estado

- Gate 1: estrutura OAuth verde; homologação real do cliente ainda em andamento separadamente.
- Gate 2: implementação consolidada pronta para upload e CI.
- Radar Contextual: implementação incluída, mas dormente por flags até rollout controlado.
