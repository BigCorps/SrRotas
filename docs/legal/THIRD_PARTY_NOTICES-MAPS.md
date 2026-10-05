# Map rendering — terceiros

O Radar Contextual vc89 usa componentes cartográficos de terceiros:

- **MapLibre Native (Android)** — projeto open source distribuído sob BSD 2-Clause.
- **OpenFreeMap** — estilo/tiles usados pelo mapa interno sem API key.
- Os dados cartográficos exibidos pelo estilo mantêm as atribuições aplicáveis, incluindo OpenStreetMap contributors.

O Sr.Rotas não envia credenciais de Google Maps para renderizar o mapa interno. Google Maps e Waze são usados apenas como destinos de intents/links externos quando o motorista escolhe navegar até um POI.

Referências oficiais:
- https://maplibre.org/
- https://openfreemap.org/
- https://www.openstreetmap.org/copyright
