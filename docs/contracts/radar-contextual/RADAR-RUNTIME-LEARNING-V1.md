# Radar Runtime + Learning v1

Runtime ativo somente com jornada ativa + corrida DOING_RIDE + destino geocodificado + ETA.

Cadência de rede:
- >30 min: 5 min
- 18–30 min: 3 min
- 8–18 min: 90 s
- <=8 min: 60 s

Telemetria permitida: ride_radar_armed, radar_opened, opportunity_viewed, navigation_opened, assistant_eligible, assistant_shown, assistant_ignored, assistant_viewed.

Não enviar OCR bruto, endereço, latitude/longitude nem trilha GPS.

TTNR = primeira ride_offer operacional da mesma jornada após ride_outcomes.completed_at.
A análise é observacional e não prova causalidade.
