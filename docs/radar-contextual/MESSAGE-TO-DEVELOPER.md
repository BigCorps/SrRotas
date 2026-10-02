# Mensagem sugerida para a instância desenvolvedora

Este pacote implementa o novo Radar Contextual/Continuidade no Destino sobre a arquitetura canônica do BigCorps/SrRotas.

IMPORTANTE:
- não reconstruir o Radar do zero;
- não alterar Reader/OCR/OfferDispatcher/verdict;
- manter RadarPanel027035 como rollback;
- manter `/api/v1/radar/ingest` atual durante a primeira homologação;
- aplicar migrations na ordem documentada;
- feature flags começam FALSE;
- integrar em fases: backend -> UI manual -> runtime -> assistente;
- `ranking_score` não é probabilidade;
- contexto externo nunca fabrica demanda;
- `continuity_probability_pct` só existe com evidência estatística suficiente;
- Learning/TTNR é observacional e não causal.

Começar por `DOCS/README-HANDOFF-FINAL.md` e `DOCS/INTEGRATION-ORDER.md`.


## Referência visual
O arquivo `REFERENCIA_VISUAL/MOCKUP-RADAR-CONTEXTUAL-4-FACETAS.png` é referência obrigatória.
Use-o lado a lado durante a integração das quatro facetas. O mini-mapa Canvas atual é fallback de homologação, não objetivo visual final quando for viável reproduzir o mapa contextual do mockup sem regressão.
