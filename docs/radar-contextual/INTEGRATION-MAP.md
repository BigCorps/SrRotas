# Integration Map

## REUTILIZAR
- `sr_event_opportunities`
- `/api/v1/radar/ingest`
- `continuityEstimate()`
- `sr_collective_region_hour_v1`
- `zone_exposures`
- `ride_outcomes`
- `ride_offers`
- `JourneyCoordinator`
- `RideOffer.context`
- `SrUi023`
- autenticação/acessos do backend
- renderer visual do balão do mascote (somente visual)

## ADICIONAR
- `sr_radar_pois`
- aliases + resolution log
- contextual telemetry
- contextual learning view
- Contextual API
- Android client/models/presenter
- mini-mapa Canvas
- destination runtime
- feature flags

## NÃO TOCAR
- Reader/OCR
- parser
- OfferDispatcher admission
- verdict financeiro
- dedupe das ofertas
- importação V7
- semântica de JourneyCoordinator
- ActiveAssistant026 idle rules
- `RadarPanel027035` até a nova tela ser homologada

## SUBSTITUIÇÃO FINAL
Somente depois dos gates, a posição de navegação `Radar` pode instanciar o novo
`RadarContextualPanelV1` em vez do painel legado.
