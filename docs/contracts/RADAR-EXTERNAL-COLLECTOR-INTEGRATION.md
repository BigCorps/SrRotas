# Radar — integração de um coletor externo de eventos e lugares (contrato, vc109)

Status: **documentação de integração**. Nenhum coletor foi desenvolvido ou executado nesta versão. Este
contrato descreve como um agente separado (coletor semanal) publica eventos e candidatos a lugares no
backend **existente**, sem segundo serviço de eventos, sem credenciais no Android e preservando a revisão
humana de lugares. Complementa `RADAR-INGEST.md` (formato do evento), que continua válido.

Auditoria do código em 03/11/2026 (`backend/app/api/v1/radar/**`, `backend/src/radar-*.ts`,
`backend/src/event-radar-026.ts`, migrations `20260902_event_radar_026.sql`,
`20261001210000_radar_poi_context_v1.sql` e resolver v1).

## 1. O que já está operacional, o que depende de configuração e o que falta

| Endpoint | Uso | Autenticação | Estado |
|---|---|---|---|
| `POST /api/v1/radar/ingest` | Coletor publica eventos (lote até 500) | `Authorization: Bearer <RADAR_INGEST_SECRET>` | Operacional **se** `RADAR_INGEST_SECRET` estiver configurado no servidor (sem ele: 503 `radar_ingest_secret_missing`). |
| `POST/GET/PATCH /api/v1/radar/poi/admin` | Coletor envia candidatos a lugar; revisor publica | mesmo Bearer | Código presente; sem o segredo responde 401. README registra que o endpoint ainda dependia de deploy/QA — **confirmar em produção antes de usar**. |
| `POST /api/v1/radar/poi/reconcile` | Vincula eventos a lugares (`poi_id`) | mesmo Bearer | Operacional com o segredo; **não é chamado automaticamente** pelo ingest. |
| `GET/POST/PATCH/DELETE /api/v1/radar/admin` | CRUD manual de eventos (DELETE = cancelar) | mesmo Bearer | Operacional com o segredo. |
| `GET /api/v1/radar/events` | **Consumo pelo app** (motorista) | sessão do motorista + `can_operate` | Operacional; não é para o coletor. |
| `GET /api/v1/radar/refresh` | Cron interno Ticketmaster (`17 */2 * * *`) | `Bearer CRON_SECRET` | Depende de `CRON_SECRET` e `TICKETMASTER_API_KEY`; sem a chave retorna `skipped`. |

Variáveis de servidor (somente nomes): `RADAR_INGEST_SECRET`, `CRON_SECRET`, `TICKETMASTER_API_KEY`,
`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`. **Nunca** colocar segredos no Android, em logs, no Git ou no
payload; o app usa apenas a sessão do motorista nos endpoints de consumo.

## 2. Fluxo recomendado do coletor (1–2 vezes por semana)

1. Coletar das fontes aprovadas (seção 5) somente dados públicos; nunca dados pessoais.
2. Normalizar cada evento para o formato de `RADAR-INGEST.md`, **sempre** com `external_id` estável
   da fonte (ex.: id do evento/ingresso). Sem `external_id`, o backend usa hash de
   `source|name|venue|starts_at`, que muda com renomeação/remarcação e deixa o registro antigo ativo.
3. Enviar em lotes ≤ 500, um `source` por lote quando usar `snapshot_complete=true`.
4. Reenviar eventos cancelados com o mesmo `source + external_id` e `status: "cancelled"` (todos os
   campos obrigatórios presentes). Eventos fora do snapshot completo da mesma fonte expiram.
5. Enviar candidatos a lugar (estádios, arenas, terminais, aeroportos, shoppings, pavilhões) para
   `poi/admin` com `dry_run: true` primeiro; depois sem `dry_run`. Eles entram **inativos** com
   `metadata.review = "pending"`.
6. Revisão humana publica cada lugar com `PATCH {id, status: "active"}`.
7. Opcional: `POST /poi/reconcile` para vincular eventos a lugares já publicados.
8. Registrar a execução localmente no coletor (início, fim, contagens, erros) — ver lacunas.

## 3. Garantias existentes (verificadas no código)

- **Idempotência de eventos:** upsert por `(source, external_id)` com índice único no banco.
- **Idempotência de lugares:** chave `source + source_external_id`; duplicatas no mesmo lote rejeitadas;
  lugar já `active` nunca é sobrescrito pelo coletor; atualização só enquanto `inactive`.
- **Proveniência:** `source`, `source_url`, `external_id`, `metadata`, `last_verified_at`; uma linha
  por fonte em `sr_radar_source_runs` após sucesso.
- **Horários:** `starts_at` obrigatório; `expected_end_at` estimado por tipo quando ausente
  (`end_time_source = estimated`); janela de saída padrão `fim−20 min … fim+75 min`.
- **Validade:** eventos ativos expiram 2 h após `egress_end_at`; o app só lista `active` e com saída
  ainda relevante.
- **Confiança:** 0–1 (padrão 0,82 com fim explícito; 0,70 estimado); o app exibe a confiança e não
  promete probabilidade sem amostra (contrato `radar-contextual`).
- **Acesso ao banco:** RLS habilitado, somente `service_role` via backend.

## 4. Lacunas que o coletor precisa respeitar (extensões futuras, não implementadas)

| Lacuna | Risco | Extensão proposta |
|---|---|---|
| Comparação do Bearer não é de tempo constante; um único segredo libera ingest, admin e POI | Vazamento dá acesso total de escrita | Segredo por coletor/escopo (`ingest:write`, `poi:candidate`), comparação constante, assinatura HMAC do corpo com timestamp (anti-replay) |
| Sem limite de taxa nos endpoints do Radar | Abuso/erro de loop | Aplicar `security-rate-limit` existente |
| Sem id de execução; `source_runs` só grava sucesso por fonte | Sem rastreabilidade por execução | `run_id` (idempotency key) no payload, início/fim, rejeitados/expirados, retorno do `run_id` |
| Lote inteiro falha por um item inválido; duplicata no mesmo lote derruba o upsert | Perda do lote | Validação por item com relatório, deduplicação no lote, transação |
| `snapshot_complete` exige um lote ≤ 500 | Fontes grandes não expiram corretamente | Snapshot paginado por `run_id` |
| Reenvio sem `status` reativa evento cancelado/expirado | Evento cancelado volta | Coletor deve SEMPRE enviar `status`; backend: status terminal "pegajoso" |
| `event_type`/`status` desconhecidos viram `event`/`active` silenciosamente | Classificação errada | Rejeitar com erro por item |
| Horário sem fuso é interpretado no fuso do servidor | Evento deslocado | Coletor envia ISO-8601 **com** offset (`-03:00`) |
| `poi/reconcile` cria lugar a partir de evento como `active` (padrão do banco) | **Contorna a revisão humana** | Antes de usar `reconcile` em produção: criar lugares como `inactive` + `review: pending` (mudança de backend a validar) |
| Sem auditoria de quem aprovou o lugar | Revisão não auditável | Registrar revisor e data na aprovação |
| `GET /events` mostra saúde só do Ticketmaster | Saúde do coletor invisível | `source_status` por fonte |

Até essas extensões existirem, o coletor **não deve** chamar `poi/reconcile` em produção e deve sempre
enviar `external_id`, `status` e horários com fuso.

## 5. Fontes avaliadas

| Fonte | Tipo | Observação |
|---|---|---|
| Ticketmaster (Discovery API) | shows, esportes | Já integrado pelo cron interno; não duplicar pelo coletor (mesmo `source` = `ticketmaster`). |
| Sympla | eventos culturais/negócios | Verificar termos de uso/API pública antes de coletar; `external_id` = id do evento. |
| Fontes municipais (agenda cultural/prefeituras) | feiras, festas, eventos públicos | Preferir dados abertos oficiais; `source` por município (`prefeitura_sp`). |
| Estádios e arenas | jogos e shows | Melhor como **lugares** (POI) revisados + eventos com horário oficial. |
| Feiras e pavilhões | feiras/convenções | `event_type = fair_convention`; horários de fechamento são o dado útil (saída). |
| Shoppings | lugar recorrente | Como POI revisado; horários de funcionamento não são eventos. |
| Aeroportos e terminais rodoviários | mobilidade | Como POI revisado; picos por voos/partidas exigem fonte oficial e regras próprias (não implementado). |

## 6. Regras invioláveis

- Nenhum segredo no Android, no Git ou em logs públicos.
- Sem segundo serviço de eventos: somente os endpoints acima.
- Lugares só são publicados após revisão humana.
- O app nunca anuncia alta probabilidade sem amostra suficiente; eventos mostram origem, horário,
  confiança e validade.
- Coleta pública, sem dados pessoais; respeitar termos de cada fonte.
