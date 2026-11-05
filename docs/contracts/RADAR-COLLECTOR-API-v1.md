# Radar — API do coletor externo v1 (`srrotas-radar-collector-api-v1`)

Status: **contrato normativo de integração (vc110)**. Destinado ao agente coletor independente
(scraper) que entrega eventos e candidatos a lugares ao Sr. Rotas com **credencial própria**.
Tudo o que o coletor envia fica **retido para revisão humana** e só chega ao Radar do app depois de
aprovado no Admin (`/admin/radar`). Este documento substitui, para coletores externos, as instruções
de autenticação de `RADAR-INGEST.md` (que continua válido apenas para o caminho interno/admin com
`RADAR_INGEST_SECRET`).

Pré-requisito de servidor: migration `supabase/migrations/20261105120000_radar_collectors_review_v1.sql`
aplicada. Sem ela, todas as rotas de coletor respondem `503 {"error":"collector_store_unavailable"}` e
o Radar do app continua funcionando com a consulta legada.

## 1. Base, autenticação e escopos

- Base URL: `https://<host>` (fornecida pelo admin). Sempre HTTPS.
- Header obrigatório em todas as chamadas:
  `Authorization: Bearer srrk_<prefixo>_<segredo>`
- `Content-Type: application/json` nos `POST`. Corpo máximo: 2 MB.
- A chave é **individual por coletor**, gerada pelo admin e exibida uma única vez. O servidor guarda
  somente o SHA-256 da chave; compara em tempo constante.
- Escopos possíveis (definidos pelo admin):
  - `events:ingest` — enviar eventos (`POST /api/v1/radar/ingest`);
  - `pois:propose` — propor lugares (`POST /api/v1/radar/collector/pois`).
- `allowed_sources`: lista de `source` (slugs) que o coletor pode usar. Fontes internas
  (`ticketmaster`, `manual`, `admin`, `internal`) nunca são permitidas a coletores.
- A chave de coletor **não** acessa nenhuma rota administrativa (`/api/v1/radar/admin`,
  `/api/v1/radar/poi/admin`, `/api/v1/radar/poi/reconcile`, `/api/v1/admin/**`) nem rotas do app.
- Nunca coloque a chave em Git, logs, URLs, payloads ou no Android. Use variável de ambiente/cofre.

## 2. Endpoints

| Método | Caminho | Escopo | Uso |
|---|---|---|---|
| `GET` | `/api/v1/radar/collector/ping` | qualquer chave ativa | Teste de conexão (sem escrita além da auditoria). |
| `GET` | `/api/v1/radar/ingest` | qualquer chave ativa | Alias do ping. |
| `POST` | `/api/v1/radar/ingest` | `events:ingest` | Lote de eventos. |
| `POST` | `/api/v1/radar/collector/pois` | `pois:propose` | Lote de candidatos a lugar (sempre inativos). |

### 2.1 Ping

Resposta `200`:

```json
{
  "ok": true,
  "collector": { "name": "Coletor semanal SP", "key_prefix": "srrk_1a2b3c4d", "status": "active" },
  "scopes": ["events:ingest"],
  "allowed_sources": ["prefeitura_sp", "sympla"],
  "limits": { "max_batch": 200, "rate_limit_per_hour": 30, "max_pois_per_batch": 50 },
  "server_time": "2026-11-05T12:00:00.000Z",
  "contract": "srrotas-radar-collector-api-v1"
}
```

O ping conta para o limite por hora. Use-o no início de cada execução.

## 3. Envio de eventos — `POST /api/v1/radar/ingest`

```json
{
  "run_id": "sp-2026-11-05T12-00Z",
  "dry_run": false,
  "events": [
    {
      "source": "prefeitura_sp",
      "external_id": "agenda-98765",
      "event_type": "music",
      "name": "Show na Praça Central",
      "venue_name": "Praça Central",
      "address": "Praça Central, 100 - Centro",
      "city": "São Paulo",
      "state": "SP",
      "country_code": "BR",
      "lat": -23.5505,
      "lng": -46.6333,
      "starts_at": "2026-11-20T20:00:00-03:00",
      "expected_end_at": "2026-11-20T23:00:00-03:00",
      "egress_start_at": "2026-11-20T22:40:00-03:00",
      "egress_end_at": "2026-11-21T00:15:00-03:00",
      "source_url": "https://agenda.exemplo.gov.br/eventos/98765",
      "confidence": 0.9,
      "status": "active",
      "metadata": { "organizer": "Secretaria de Cultura" }
    }
  ]
}
```

O corpo deve ser um **objeto** com `events` (array direto não é aceito para coletores).

### 3.1 Campos do lote

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `events` | array | sim | 1 … `max_batch` do coletor (padrão 200, teto 500). Acima: `413 batch_too_large`. |
| `run_id` | string | recomendado | `^[A-Za-z0-9._:-]{8,100}$`. Chave de idempotência da execução (seção 5). |
| `dry_run` | boolean | não | `true` valida e mostra o resultado por item **sem gravar nada**. |
| `snapshot_complete` | — | proibido | Qualquer valor diferente de `false` → `400 snapshot_not_allowed_for_collectors`. |

### 3.2 Campos do evento

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `source` | string | sim | Slug `^[a-z0-9][a-z0-9_-]{1,79}$` presente em `allowed_sources`. |
| `external_id` | string | sim | ID estável na fonte; ≤ 160 caracteres, sem caracteres de controle. Nunca derivado de nome/data. |
| `name` | string | sim | 1–240 caracteres. |
| `lat`, `lng` | number | sim | WGS84; `-90..90` / `-180..180`. Coordenadas fora do Brasil são sinalizadas na revisão. |
| `starts_at` | string | sim | ISO-8601 **com offset** (ver 3.3). |
| `expected_end_at` | string | recomendado | ISO-8601 com offset; ≥ `starts_at`. Ausente → estimado por tipo (`end_time_source=estimated`). |
| `egress_start_at`, `egress_end_at` | string | não | ISO-8601 com offset; fim ≥ início. Ausentes → `fim−20 min … fim+75 min`. |
| `event_type` | string | recomendado | Um de `event, music, sports, theatre, fair_convention, family, airport, bus_terminal, mall, cultural, mobility_hub`. Ausente → `event`. Desconhecido → rejeitado (`event_type_unknown`). |
| `status` | string | recomendado | `active` ou `cancelled`. Ausente → `active`. `expired` → `status_not_allowed`; outro → `status_unknown`. |
| `venue_name` | string | recomendado | ≤ 240. |
| `address`, `city`, `state` | string | recomendado | ≤ 500 / 120 / 80. |
| `country_code` | string | não | 2 letras; padrão `BR`. |
| `source_url` | string | recomendado | `http(s)` sem usuário/senha; ≤ 1200. Ausente é sinalizado na revisão. |
| `confidence` | number | não | `0..1` (número, não string). Ausente → 0,82 com fim explícito, 0,70 estimado. |
| `metadata` | object | não | JSON ≤ 4 KB. Somente dados públicos; nunca dados pessoais. |

### 3.3 Regra de fuso horário

Todo horário deve ser ISO-8601 com **offset explícito**: `2026-11-20T20:00:00-03:00`,
`2026-11-20T23:00:00Z` ou `2026-11-20T20:00-03:00`. Horário sem offset (`2026-11-20T20:00:00`),
formatos locais (`20/11/2026 20:00`), timestamps numéricos e datas inexistentes são rejeitados com
`<campo>_offset_required` ou `<campo>_invalid`. O servidor converte para UTC; o Admin exibe em
Brasília com o offset.

### 3.4 Resposta de sucesso (`200`)

`200` significa que o lote foi processado; cada item tem seu próprio resultado.

```json
{
  "ok": true,
  "contract": "srrotas-radar-collector-api-v1",
  "dry_run": false,
  "run_id": "sp-2026-11-05T12-00Z",
  "received": 3,
  "expired": 0,
  "accepted": 1,
  "unchanged": 1,
  "rejected": 1,
  "pending_review": 1,
  "items": [
    { "index": 0, "external_id": "agenda-98765", "source": "prefeitura_sp", "status": "accepted", "code": "created_pending", "event_id": "8f0c…" },
    { "index": 1, "external_id": "agenda-11111", "source": "prefeitura_sp", "status": "unchanged", "code": "cancelled_kept", "event_id": "1b2c…" },
    { "index": 2, "external_id": null, "source": "prefeitura_sp", "status": "rejected", "code": "external_id_required" }
  ]
}
```

`status` por item: `accepted` (gravado), `unchanged` (nada relevante mudou) ou `rejected` (não gravado).

Códigos por item:

| status | code | Significado |
|---|---|---|
| accepted | `created_pending` | Novo evento, aguardando revisão. |
| accepted | `created_cancelled` | Novo registro já cancelado (nunca visível). |
| accepted | `requeued_for_review` | Mudança material num evento aprovado/expirado → voltou a `pending` e saiu do app até nova aprovação. |
| accepted | `updated_pending` | Evento ainda pendente teve conteúdo atualizado. |
| accepted | `resubmitted_for_review` | Evento rejeitado/com correção solicitada foi reenviado alterado → `pending`. |
| accepted | `cancelled_applied` | Cancelamento aplicado imediatamente. |
| unchanged | `no_material_change` | Evento aprovado reenviado sem mudança material (só a verificação é atualizada). |
| unchanged | `no_change` | Pendente/rejeitado reenviado idêntico. |
| unchanged | `cancelled_kept` | Registro cancelado permanece cancelado (cancelamento é pegajoso). |
| unchanged | `already_cancelled` | Cancelamento repetido. |
| unchanged | `expired_kept` | Evento expirado reenviado sem mudança material. |
| rejected | `source_invalid`, `source_not_allowed` | `source` inválido ou fora de `allowed_sources`. |
| rejected | `external_id_required`, `external_id_invalid` | Identidade ausente/inválida. |
| rejected | `name_required`, `name_too_long`, `lat_invalid`, `lng_invalid` | Campos obrigatórios. |
| rejected | `starts_at_required`, `<campo>_offset_required`, `<campo>_invalid` | Horários. |
| rejected | `expected_end_before_start`, `egress_end_before_start` | Janela inconsistente. |
| rejected | `event_type_unknown`, `status_unknown`, `status_not_allowed` | Enumerações. |
| rejected | `source_url_invalid`, `confidence_invalid`, `country_code_invalid`, `metadata_invalid`, `metadata_too_large`, `event_invalid` | Formato. |
| rejected | `duplicate_in_batch` | Mesma `source + external_id` mais de uma vez no lote (todas as ocorrências são rejeitadas). |
| rejected | `owned_by_other_ingest` | O registro pertence a outra origem (interna ou outro coletor); o coletor não pode alterá-lo. |
| rejected | `write_conflict`, `write_failed` | Falha de gravação daquele item; reenvie no próximo ciclo. |

### 3.5 Erros do lote

| HTTP | `error` | Ação do coletor |
|---|---|---|
| 400 | `invalid_json`, `payload_must_be_object`, `events_required`, `run_id_invalid`, `dry_run_invalid`, `snapshot_not_allowed_for_collectors` | Corrigir o payload. |
| 401 | `collector_unauthorized` | Chave ausente/errada. Não insistir. |
| 403 | `collector_revoked` | Chave revogada; pedir nova ao admin. |
| 403 | `scope_missing` (`required_scope`) | Pedir o escopo ao admin. |
| 413 | `batch_too_large` (`max_batch`, `received`), `payload_too_large` (`max_bytes`) | Dividir o lote. |
| 429 | `collector_rate_limited` (`limit_per_hour`, `retry_after_seconds`) / `collector_auth_rate_limited` | Esperar `Retry-After`. |
| 500 | `collector_ingest_failed` | Repetir mais tarde com o mesmo `run_id`. |
| 503 | `collector_store_unavailable` | Servidor ainda sem a migration; tentar no próximo ciclo. |

## 4. Fluxo de aprovação

1. Todo evento novo, ou com mudança material, entra com `review_status = pending`.
2. `pending`, `rejected` e `changes_requested` **nunca** aparecem no app (`GET /api/v1/radar/events`,
   oportunidades contextuais e contexto de lugares leem apenas `approved`).
3. O admin revisa em `/admin/radar` → Recebidos: Aprovar, Rejeitar ou Solicitar correção (nota
   obrigatória nos dois últimos). Toda decisão é registrada com autor, status anterior e data.
4. O admin vê sinais automáticos: possível duplicado (mesmo nome normalizado com início em até 3 h e
   ≤ 300 m, ou mesmo local com o mesmo início), término antes do início, início já passado ainda
   pendente, coordenadas fora do Brasil, sem `source_url`, confiança < 0,5.
5. O coletor **não** recebe callback; ele vê o efeito no próximo envio (`no_material_change`,
   `resubmitted_for_review` etc.). Após "Solicitar correção", reenvie o mesmo `source + external_id`
   com os dados corrigidos.

## 5. Idempotência

- **Identidade do evento:** `(source, external_id)`. Reenviar a mesma identidade atualiza o mesmo
  registro segundo as regras da seção 6; nunca cria duplicata.
- **Execução:** `run_id` por coletor. Se um lote com o mesmo `run_id` já foi processado (não
  `dry_run`), o servidor devolve o **resumo anterior** com `"idempotent_replay": true` e
  `replayed_from`, sem reprocessar. Use um `run_id` novo para cada execução real e o mesmo `run_id`
  apenas para repetir após timeout/erro de rede. Lotes paginados: um `run_id` por página
  (ex.: `sp-2026-11-05T12-00Z-p2`).
- `dry_run: true` nunca grava e não consome o `run_id`.

## 6. Atualizações e cancelamentos

- **Cancelamento:** envie o evento completo com `"status": "cancelled"`. É aplicado imediatamente
  (ocultar é seguro), sem nova revisão.
- **Cancelamento é pegajoso:** um reenvio `active` de um registro cancelado não o reativa
  (`cancelled_kept`). Se o evento foi remarcado de verdade, publique-o com um **novo** `external_id`
  (ou peça ao admin).
- **Mudança material** — `name`, `starts_at`, `expected_end_at`, `egress_start_at`, `egress_end_at`,
  `lat`, `lng`, `venue_name`, `address` — em evento aprovado: o evento volta a `pending` e sai do app até
  nova aprovação (`requeued_for_review`).
- **Mudança não material** (`source_url`, `confidence`, `event_type`, `city`, `state`, `country_code`,
  `metadata`) em evento aprovado: não é aplicada automaticamente (`no_material_change`); se for
  importante, avise o admin.
- **Expiração** é do servidor: eventos ativos expiram 2 h após `egress_end_at`. O coletor não envia
  `expired` e não usa `snapshot_complete`; eventos que somem da fonte devem ser enviados como
  `cancelled` se a fonte indicar cancelamento, ou simplesmente expiram pelo horário.
- O coletor só altera registros que ele mesmo criou (`owned_by_other_ingest` caso contrário).

## 7. Lugares — `POST /api/v1/radar/collector/pois` (escopo `pois:propose`)

```json
{
  "dry_run": true,
  "pois": [
    {
      "source": "prefeitura_sp",
      "source_external_id": "arena-central",
      "canonical_name": "Arena Central",
      "poi_type": "sports",
      "lat": -23.5450,
      "lng": -46.6400,
      "address": "Av. Exemplo, 1000",
      "city": "São Paulo",
      "state": "SP",
      "source_url": "https://agenda.exemplo.gov.br/locais/arena-central",
      "source_confidence": 0.85
    }
  ]
}
```

- 1–50 lugares por lote; `source` em `allowed_sources`; `source_external_id` e `poi_type` no formato
  `^[a-z0-9][a-z0-9_-]{1,99}$`; coordenadas válidas; URL `http(s)` sem credenciais.
- Todo lugar entra **inativo** com `metadata.review = "pending"` e só é publicado por aprovação humana
  (`/admin/radar` → Lugares). Um lugar já ativo nunca é sobrescrito pelo coletor.
- Resposta `200`: `{ ok, dry_run, received, created, updated, pending_review }` (em `dry_run`,
  `validated`). Erros: `400` com `error` (`pois_required`, `poi_invalid` + `index`,
  `source_not_allowed` + `index`, `<campo>_invalid`, `duplicate_source_external_id_in_batch`),
  `403 scope_missing`, `413 batch_too_large`, além de 401/403/429/503 da seção 3.5.
- O coletor nunca chama `poi/reconcile`; a vinculação evento↔lugar é decisão interna.

## 8. Limites

| Limite | Padrão | Teto | Erro |
|---|---|---|---|
| Eventos por lote | 200 | 500 | `413 batch_too_large` |
| Lugares por lote | 50 | 50 | `413 batch_too_large` |
| Corpo | 2 MB | — | `413 payload_too_large` |
| Chamadas por hora por coletor (ping + envios) | 30 | 1000 | `429 collector_rate_limited` |
| Tentativas com chave inválida (por IP) | 20 / 15 min | — | `429 collector_auth_rate_limited` |

## 9. Teste seguro de conexão

1. `GET /api/v1/radar/collector/ping` → confirme `scopes`, `allowed_sources` e `limits`.
2. `POST /api/v1/radar/ingest` com `"dry_run": true` → confira os códigos por item; nada é gravado.
3. Só então envie o lote real com `run_id` novo.

Exemplo de cliente: `docs/contracts/examples/radar-collector-client.example.mjs`
(`node radar-collector-client.example.mjs --ping`, `--dry-run arquivo.json`, `arquivo.json`), lendo
`SR_ROTAS_BASE_URL` e `SR_ROTAS_COLLECTOR_KEY` do ambiente.

## 10. Cadência recomendada

- Execução completa **1–2 vezes por semana** (ex.: segunda e quinta), cobrindo as próximas 2–3
  semanas de eventos.
- Reenvie cancelamentos assim que detectados (mesma `source + external_id`).
- Respeite os termos de uso e limites de cada fonte; colete apenas dados públicos e nunca dados
  pessoais.

## 11. Gestão de chaves (somente admin)

Em `/admin/radar` → Chaves (sessão administrativa BigCorps; nunca por chave de coletor):

- **Criar:** nome, fontes permitidas, escopos, lote máximo e limite/hora. A chave
  `srrk_<prefixo>_<segredo>` aparece **uma única vez** com aviso de cópia; depois só o prefixo é
  exibido.
- **Rotacionar:** gera nova chave (exibida uma vez) e invalida a anterior imediatamente.
- **Revogar:** desativa a chave definitivamente (`403 collector_revoked`).
- **Editar:** fontes, escopos e limites, sem trocar a chave.
- **Envios:** últimas execuções (`run_id`, contagens) e auditoria de uso (endpoint, HTTP, contagens;
  sem payload, IP apenas como hash truncado).

## 12. Regras invioláveis

- Nada enviado por coletor fica visível antes da aprovação humana.
- Chave por coletor, escopo mínimo, só as fontes permitidas; sem acesso administrativo.
- Sem segredo no Android, no Git ou em logs.
- O caminho interno `RADAR_INGEST_SECRET` (Ticketmaster/admin) continua separado e inalterado.
