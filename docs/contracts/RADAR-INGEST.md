# Contrato de ingestão — Sr. Rotas Radar 0.26.1

## Endpoint

`POST /api/v1/radar/ingest`

Header obrigatório:

`Authorization: Bearer <RADAR_INGEST_SECRET>`

`Content-Type: application/json`

## Payload recomendado

```json
{
  "snapshot_complete": false,
  "events": [
    {
      "source": "prefeitura_sp",
      "external_id": "evento-123",
      "event_type": "music",
      "name": "Nome do evento",
      "venue_name": "Local do evento",
      "address": "Endereço",
      "city": "São Paulo",
      "state": "SP",
      "country_code": "BR",
      "lat": -23.5505,
      "lng": -46.6333,
      "starts_at": "2026-09-05T20:00:00-03:00",
      "expected_end_at": "2026-09-05T23:00:00-03:00",
      "egress_start_at": "2026-09-05T22:40:00-03:00",
      "egress_end_at": "2026-09-06T00:15:00-03:00",
      "source_url": "https://fonte.exemplo/evento-123",
      "confidence": 0.90,
      "status": "active",
      "metadata": {
        "organizer": "Exemplo"
      }
    }
  ]
}
```

Também é aceito um array JSON diretamente; nesse caso `snapshot_complete` fica falso.

## Obrigatórios por evento

- `source`
- `name`
- `lat`
- `lng`
- `starts_at`

O backend consegue derivar:
- `external_id`, se a fonte não fornecer;
- `event_type=event`, se ausente/desconhecido;
- `expected_end_at`, se ausente;
- `egress_start_at` e `egress_end_at`, se ausentes;
- `confidence`, se ausente;
- `country_code=BR`;
- `status=active`.

Para qualidade do Radar, o scraper deve preferir enviar `external_id` estável da própria fonte e horário de término real sempre que disponível.

## Tipos aceitos

`event`, `music`, `sports`, `theatre`, `fair_convention`, `family`, `airport`, `bus_terminal`, `mall`, `cultural`, `mobility_hub`.

## Dedupe

A chave é `(source, external_id)`.

Se `external_id` não for enviado, o backend gera um SHA-256 determinístico usando fonte + nome + local + início.

## Snapshot completo

Use `"snapshot_complete": true` apenas quando o payload representa a lista COMPLETA e atual daquela única fonte. Nesse modo, eventos ativos daquela fonte que não vierem mais no snapshot são marcados `expired`.

Não use `snapshot_complete=true` em paginações parciais.

## Atualização / cancelamento

Para um evento existente, envie o mesmo `source + external_id` com os campos novos. O upsert atualiza o registro.

Para cancelamento pelo scraper:

```json
{
  "source": "prefeitura_sp",
  "external_id": "evento-123",
  "name": "Nome do evento",
  "lat": -23.5505,
  "lng": -46.6333,
  "starts_at": "2026-09-05T20:00:00-03:00",
  "status": "cancelled"
}
```

## Frequência recomendada

- eventos comuns: a cada 1–2 horas;
- fontes que mudam pouco: 3–6 horas;
- próximo do horário do evento, a fonte pode ser consultada com mais frequência, desde que respeite os termos/limites do site/API original.

O APK não consulta o scraper: ele lê a base normalizada do Sr. Rotas.
