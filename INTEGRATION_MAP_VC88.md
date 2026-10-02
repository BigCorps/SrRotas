# Integration Map — Radar Contextual vc88

Base GitHub usada: `925078db920ac56649b2b116a9b3e0e60054a76c`.

## Já existente no main e preservado
Estes arquivos NÃO são sobrescritos pelo patch vc88:
- `RadarDestinationContextV1.kt` — contém currentRide.localOfferId → oferta persistida → OfferContext;
- `RadarContextualClientV1.kt`;
- `RadarContextualModelsV1.kt`;
- `RadarContextualPresenterV1.kt`;
- `RadarContextualTelemetryV1.kt`;
- `RadarContextualHostV1.kt`;
- `RadarMiniMapViewV1.kt`;
- `DestinationRadarAssistantBridgeV1.kt`;
- `DestinationRadarAssistantUiV1.kt`;
- `DestinationRadarInteractionV1.kt`;
- backend contextual/POI resolver;
- migrations Radar já aplicadas;
- `RadarPanel027035.kt` rollback.

## Modificado no vc88
- `ConsolidatedMainActivity027037.kt`: costura UI/rota/CTA/host/ações do assistente;
- `RadarContextualFlagsV1.kt`: rollout ordenado;
- `RadarContextualIntegrationV1.kt`: lifecycle runtime;
- `RadarDestinationEntryV1.kt`: CTA só com UI flag e corrida elegível;
- `DestinationRadarRuntimeV1.kt`: expõe applicationContext para bridge;
- `RadarContextualPanelV1.kt`: demo, foco de oportunidade e telemetria preservada;
- `SrRotasApplication.kt`: instala runtime dormente;
- `MainActivity.kt`: ações do assistente.

## Novo no vc88
- `DestinationRadarRuntimeBridgeV1.kt`;
- `DestinationRadarAssistantRendererV1.kt`;
- `RadarContextualDemoV1.kt`;
- `RadarContextualHomologationV1.kt`;
- testes Android de integração;
- testes backend puros;
- guard CI do Radar Contextual.

## Facetas
1. `RadarDestinationEntryV1` — entrada na corrida;
2. `RadarContextualPanelV1 + RadarMiniMapViewV1` — mapa contextual;
3. `RadarContextualPanelV1` — detalhe e "Por que está aqui?";
4. `DestinationRadarAssistantRendererV1` — assistente no host existente do HUD.
