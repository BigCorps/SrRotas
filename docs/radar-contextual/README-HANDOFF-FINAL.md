# Sr. Rotas — Radar Contextual / Continuidade no Destino
## Handoff final para a instância desenvolvedora

### Status
Módulo materializado e pronto para integração progressiva no repositório canônico `BigCorps/SrRotas`.

Este pacote NÃO foi aplicado ao repositório. Ele foi construído contra a arquitetura lida no canônico.


## Referência visual obrigatória
Antes de implementar a UI, abrir:
`REFERENCIA_VISUAL/MOCKUP-RADAR-CONTEXTUAL-4-FACETAS.png`
e ler:
`DOCS/REFERENCIA-VISUAL-MOCKUP.md`.

O mockup é o alvo visual das quatro facetas. O código do módulo define comportamento/contratos e pode conter fallback técnico; isso não autoriza descaracterizar o layout.

## Objetivo de produto
Transformar a posição Radar em uma ferramenta de inteligência contextual:
- analisar o DESTINO futuro da corrida;
- comparar permanecer no destino versus oportunidades próximas;
- combinar continuidade estatística + contexto temporal + custo/distância;
- explicar "Por que está aqui?";
- opcionalmente sugerir continuidade pouco antes da chegada;
- aprender de forma observacional com TTNR e qualidade da próxima oferta.

## Fronteiras
Collector/Scraper é trilha separada.
O Radar consome `/api/v1/radar/ingest` já existente e não incorpora scraping no Android.

## Fluxo
Collector -> radar/ingest existente -> eventos -> POI Resolver -> POIs/contexto
-> continuidade canônica -> Radar Contextual API -> Android -> telemetria
-> learning observacional.

## Regra de rollout
NÃO ativar tudo de uma vez.

Fase 0: migrations + backend, sem UI.
Fase 1: UI manual do Radar (`uiEnabled`).
Fase 2: runtime de refresh durante corrida (`runtimeEnabled`).
Fase 3: balão proativo (`assistantEnabled`).

Todos os flags Android nascem FALSE.

## Baselines que permanecem
- Radar atual continua disponível até homologação.
- ActiveAssistant026 continua intacto.
- Reader/OCR/OfferDispatcher/verdict não devem ser alterados.
- V7 permanece fonte histórica canônica, não é copiada para o módulo.
