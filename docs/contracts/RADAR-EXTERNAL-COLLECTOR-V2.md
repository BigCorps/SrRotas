# Sr. Rotas — Integração de coletor externo V2

Base de produção: **https://srrotas.com**. Data: 05/11/2026.

Este é o contrato completo para um desenvolvedor implementar um cliente independente. Não contém
um scraper e não solicita coletas. Reutiliza a infraestrutura vc110, suas tabelas, formato de chave,
escopos e endpoints. Não existe um segundo serviço de ingestão ou publicação.

**Ativação confirmada em 05/11/2026:** main `e8817573bd10580dc7a63ec374e2fe93002dc63c`,
deploy produtivo READY em `https://srrotas.com`. Migrações V1/V2 registradas no banco como
`20261105121037` / `20261105140014`; complemento de ACL de auditoria
`20261105144035_radar_audit_append_only_privileges_v2.sql` também aplicado, sem novo deploy.
Homologação HTTP confirmou pending → aprovação administrativa → Radar → despublicação,
retry idempotente e bloqueio da chave descartável após revogação. O coletor deve começar
com ping/dry-run e sua própria credencial limitada, conforme as seções abaixo.

## 1. Credencial individual

O administrador entra no login existente, abre **Administrativo → Radar → Chaves** (`/admin/radar`),
define nome, fontes, permissões, validade, lote máximo e envios/hora, e escolhe Criar chave.
A chave `srrk_<prefixo público>_<segredo aleatório>` aparece somente na criação/rotação. O segredo
usa 256 bits de aleatoriedade criptográfica. O banco guarda apenas SHA-256 da chave e metadados;
a chave original nunca é recuperada. Histórico de credenciais não contém nem o hash.

Guarde em secret manager ou `SR_ROTAS_COLLECTOR_KEY`. Nunca envie por logs, Git, Android, argumentos
públicos de comandos ou payloads. **Não usar nem compartilhar `RADAR_INGEST_SECRET`.** Esse segredo
continua exclusivo dos fluxos internos. O aplicativo usa sua própria autenticação de motorista.

Todas as chamadas usam HTTPS e:

```http
Authorization: Bearer <CHAVE_INDIVIDUAL>
Content-Type: application/json
```

| Escopo | Permissão |
|---|---|
| `events:ingest` | Enviar/atualizar/cancelar eventos apenas das fontes permitidas |
| `pois:propose` | Enviar lugares para revisão, inicialmente inativos |

Fontes `ticketmaster`, `manual`, `admin` e `internal` são reservadas. Não duplicar Ticketmaster.
Uma identidade canônica preexistente ou pertencente a outro coletor não pode ser assumida por uma
credencial diferente. A rotação preserva o ID do coletor e suas identidades, invalida imediatamente
a chave anterior e permite definir nova validade. Expiração impede ingestão e ping. Revogação é
administrativa; uma chave revogada não pode autenticar. A validade padrão é 365 dias quando omitida
na criação administrativa. Configure explicitamente uma validade adequada.

Não há escopo administrativo para coletores. Nem uma chave com os dois escopos pode consultar o
Admin, aprovar, despublicar ou acessar `radar/admin`, `poi/admin` ou `poi/reconcile`.

## 2. Endpoints e limites

| Método e caminho | Finalidade |
|---|---|
| `GET /api/v1/radar/collector/ping` | Validar conexão/credencial sem criar eventos |
| `GET /api/v1/radar/ingest` | Alias do ping |
| `POST /api/v1/radar/ingest` | Lote de eventos |
| `POST /api/v1/radar/collector/pois` | Lote de lugares |

Limite de corpo: **2.000.000 bytes**, verificado durante a leitura, inclusive sem Content-Length.
Eventos: padrão 200 por lote, administrador pode definir 1–500. Lugares: até 50 por lote.
Taxa: padrão 30 requisições/hora por coletor, configurável entre 1–1000. O orçamento é atômico e
persistente, por janela de 3600 s iniciada no primeiro acesso; ping, dry-run e tentativas autenticadas
consomem o mesmo orçamento. Rotacionar não reinicia o orçamento. Há orçamento global de 600
tentativas/minuto antes de autenticar. `429` inclui `Retry-After` (60 ou 3600 s).
Falha de autorização ou indisponibilidade do limitador fecha o acesso; nunca há fallback que remova a aprovação. Falha da auditoria HTTP **posterior a um commit confirmado** segue a política explícita da seção 7, preservando o recibo transacional.

## 3. Envelope de eventos e identificação da execução

```json
{
  "run_id": "agenda-sp-2030-11-10-lote-001",
  "dry_run": true,
  "events": []
}
```

`run_id`: string com 8–100 caracteres, regex `^[A-Za-z0-9._:-]{8,100}$`. **Sempre envie** um ID
novo para cada lote HTTP e conserve-o para retry. UUID também é válido. O servidor gera um UUID
quando omitido por um cliente legado; esse modo não permite retry previsível da execução.
`dry_run`: booleano opcional, padrão false. `events`: array não vazio, dentro do limite da chave.
Não enviar array diretamente. `snapshot_complete` é proibido (false é tolerado por compatibilidade).
A ausência de um evento em um lote não cancela nada. Não existe snapshot paginado nesta versão.

O mesmo coletor e run_id com o mesmo JSON semântico retorna o resultado anterior com
`idempotent_replay: true`, sem repetir writes. Reutilizar ID com corpo diferente gera `409
run_payload_conflict`. Ordem de propriedades JSON não importa; a ordem dos itens importa.
O run_id é compartilhado pelos endpoints: use IDs diferentes para lotes de eventos e lugares.
Dry-run não cria execução persistida; use outro ID para a submissão real.

## 4. Esquema completo de evento

Obrigatórios: `source`, `external_id`, `name`, `lat`, `lng`, `starts_at`, `source_url`.
Identidade: **(source, external_id)**. Não derive ID de nome/horário mutável.

| Campo | Tipo / regra |
|---|---|
| `source` | slug de 2–80 caracteres, letras minúsculas/números/`_`/`-`, autorizado na chave |
| `external_id` | string/ID estável de até 160 caracteres, sem controles; prefira string |
| `name` | string não vazia, até 240 caracteres |
| `event_type` | enum abaixo; ausente = `event`, valor desconhecido é rejeitado |
| `venue_name` | nome do local, até 240; enviar sempre que conhecido |
| `address` | endereço, até 500; enviar sempre que conhecido |
| `city`, `state` | cidade/estado, até 120/80; enviar sempre que conhecidos |
| `country_code` | código ISO de duas letras; padrão BR |
| `lat`, `lng` | coordenadas finitas −90…90 / −180…180; enviar números; nulo/vazio/booleano são inválidos |
| `starts_at` | ISO-8601 com `Z` ou offset explícito, obrigatório |
| `expected_end_at` | ISO com fuso, ≥ início; se ausente, estimativa por tipo |
| `end_time_source` | `source` ou `estimated`; declare `estimated` quando o fim enviado for estimado |
| `egress_start_at` | ISO com fuso; padrão fim−20 min |
| `egress_end_at` | ISO com fuso, ≥ início da janela; padrão fim+75 min |
| `source_url` | URL absoluta http(s), ≤1200, sem usuário/senha; obrigatória |
| `last_verified_at` | ISO com fuso, verificação real na fonte; se ausente usa recebimento; até 5 min no futuro |
| `confidence` | número 0…1; padrão 0,82 com fim explícito / 0,70 estimado; valores inválidos são rejeitados |
| `status` | `active` ou `cancelled`; padrão active; `expired` não é aceito do coletor |
| `metadata` | objeto JSON opcional, até 4096 bytes; sem dados pessoais/credenciais |

Tipos: `event`, `music`, `sports`, `theatre`, `fair_convention`, `family`, `airport`, `bus_terminal`,
`mall`, `cultural`, `mobility_hub`. Lugar permanente deve usar o endpoint de POIs.
Estimativa de duração quando o fim está ausente: sports 180 min, theatre/family 150, fair_convention
240, airport/bus_terminal/mall/mobility_hub 120, demais 180. Envie fim real quando disponível.
Confiança se refere à evidência do evento, nunca à probabilidade de passageiros.

Horários locais sem offset, datas inválidas (inclusive 30 de fevereiro), coordenadas inválidas,
links inseguros com credenciais e tipo/status desconhecidos são rejeitados por item. Não invente
campos para completar um registro. Ausência de local/endereço/confiança gera sinais de conferência
no Admin; o coletor deve preferir dados completos. O servidor não busca a source_url.

Exemplo válido (dados fictícios para dry-run; substitua a fonte por uma autorizada):

```json
{
  "run_id": "teste-integracao-2030-11-10-001",
  "dry_run": true,
  "events": [{
    "source": "agenda_sp",
    "external_id": "evento-123",
    "name": "Evento de homologação",
    "event_type": "music",
    "venue_name": "Arena de exemplo",
    "address": "Rua de exemplo, 100",
    "city": "São Paulo",
    "state": "SP",
    "country_code": "BR",
    "lat": -23.5505,
    "lng": -46.6333,
    "starts_at": "2030-11-10T20:00:00-03:00",
    "expected_end_at": "2030-11-10T23:00:00-03:00",
    "end_time_source": "source",
    "egress_start_at": "2030-11-10T22:40:00-03:00",
    "egress_end_at": "2030-11-11T00:15:00-03:00",
    "source_url": "https://example.org/evento-123",
    "last_verified_at": "2026-11-05T10:00:00Z",
    "confidence": 0.85,
    "status": "active",
    "metadata": {"organizer": "Exemplo"}
  }]
}
```

## 5. Atualização, cancelamento e revisão

Reenvie o objeto completo com a mesma identidade para atualizar. Dados alterados de um evento
aprovado voltam a pendente e deixam de alimentar o Radar. Rejeição, pedido de correção e
**despublicação são preservados** nos envios seguintes; um administrador deve revisar novamente.
Conteúdo idêntico é `unchanged`; a verificação pode ser atualizada sem perder a decisão.
Horários são comparados como instantes, não pela representação textual `Z`/`+00:00`.
Uma verificação mais antiga não pode substituir conteúdo diferente mais recente.

Para cancelar, envie o mesmo evento completo com `status: cancelled`. O cancelamento esconde o
evento imediatamente. Cancelamento e expiração são terminais: renomear/remarcar ou reenviar active
não os reativa. O servidor marca novos eventos já vencidos como expired. Um evento cancelado ou com
janela de saída encerrada não pode ser aprovado. Não crie outro ID para contornar esse estado.

Publicação é uma decisão humana independente do status operacional. No Admin, o revisor confere
fonte, Maps externo, horários, confiança, duplicidades e histórico, informa motivo e aprova/rejeita/
solicita correção/despublica. Cada decisão exige versão atual e grava dados + auditoria na mesma
transação. O histórico mantém antes/depois e ator. Escritas concorrentes/obsoletas falham com 409.
A chave de coleta não tem acesso a essas operações. Não há endpoint de aprovação do scraper.

Os leitores existentes do Radar continuam exigindo approved, active e as janelas geográficas/
temporais existentes. A aprovação não garante que o evento aparecerá para todos: raio, destino,
ETA, janela de saída e confiança continuam válidos. Não há probabilidade inventada de demanda.

## 6. Lugares

`POST /api/v1/radar/collector/pois`, escopo `pois:propose`:

```json
{
  "run_id": "teste-pois-2030-11-10-001",
  "dry_run": true,
  "pois": [{
    "source": "agenda_sp",
    "source_external_id": "arena_01",
    "canonical_name": "Arena de exemplo",
    "poi_type": "arena",
    "address": "Rua de exemplo, 100",
    "city": "São Paulo",
    "state": "SP",
    "lat": -23.5505,
    "lng": -46.6333,
    "source_url": "https://example.org/arena_01",
    "source_confidence": 0.85
  }]
}
```

Obrigatórios: source, source_external_id (slug de 2–100 caracteres), canonical_name (1–240), poi_type
(slug de 2–100), lat, lng e source_url. address/city/state opcionais; source_confidence 0…1, padrão
0,5; country_code é BR. Não enviar horários, aliases administrativos, status, review, collector_id
ou ingest_kind: o servidor controla esses campos e deriva normalized_name/geo_cell.

Identidade `(source, source_external_id)`; só o coletor proprietário pode atualizar. Novos lugares e
alterações aguardam aprovação como inactive, com metadata.review pending. Decisões negativas e
despublicação persistem. `poi/reconcile` é interno e não publica POIs: apenas propõe inativos ou
vincula lugares já ativos. Não é chamado pelo coletor.

## 7. Respostas e erros

Exemplo de sucesso HTTP 200:

```json
{
  "ok": true,
  "contract": "srrotas-radar-collector-api-v1",
  "kind": "event",
  "dry_run": false,
  "run_id": "agenda-sp-2030-11-10-lote-001",
  "received": 1,
  "accepted": 1,
  "unchanged": 0,
  "rejected": 0,
  "pending_review": 1,
  "expired": 0,
  "items": [{"index": 0, "source": "agenda_sp", "external_id": "evento-123",
             "status": "accepted", "code": "created_pending", "event_id": "UUID_DO_EVENTO"}]
}
```

A string contract preserva a versão wire vc110, mesmo com as garantias V2. HTTP 200 pode conter
rejeições: **inspecione items e rejected**. Cada item tem index da posição original, status accepted /
unchanged / rejected, code e ID quando salvo. Em POIs, poi_id identifica o lugar (event_id é alias
compatível). Dry-run devolve plano/contagens sem IDs novos persistidos, eventos, execuções ou publicação;
consome autenticação/taxa/auditoria. Rejeição em validação inclui código, fonte/ID, sem payload bruto.

Códigos por item incluem created_pending, updated_pending, updated_rejected, cancelled_kept,
expired_kept, no_change, duplicate_in_batch, owned_by_other_ingest, source_not_allowed,
stale_verification e nomes de campos inválidos (ex.: starts_at_offset_required, lat_invalid).
Um item inválido não impede os válidos; escritas válidas e execução são atômicas.

| HTTP | Significado / ação |
|---|---|
| 400 | JSON/envelope inválido; corrigir, não repetir cegamente |
| 401 | chave desconhecida/inválida/expirada; consultar responsável |
| 403 | chave revogada, falta de escopo, fonte recusada na transação ou uso de rota admin |
| 409 | run_payload_conflict ou revisão obsoleta; não mascarar com novo ID de retry |
| 413 | payload_too_large / batch_too_large; reduzir lote |
| 415 | json_required; corrigir Content-Type |
| 429 | collector_rate_limited / collector_auth_rate_limited; respeitar Retry-After |
| 503 | collector_store_unavailable / collector_http_audit_unavailable sem recibo confirmado; retry com mesmo ID/corpo e backoff |

Se uma execução antiga V1 não possuir hash de corpo, reutilizar seu ID retorna 409; use IDs novos
para novas execuções. Credenciais, logs e tabelas administrativas não são retornados ao coletor.

### Auditoria HTTP depois do commit

O recibo da execução (`sr_radar_source_runs`, incluindo hash/corpo lógico do lote) e o histórico de
alterações são confirmados **na mesma transação da ingestão**. O registro de uso HTTP e o último
acesso da chave são uma etapa posterior. Eles não podem desfazer um commit já confirmado.

Respostas de ingestão confirmada acrescentam `ingestion_committed: true` e
`http_audit: {"status": "recorded"}`. Isso significa **dados recebidos no banco**, não publicação:
o evento/lugar continua pendente de revisão humana.

Se a etapa HTTP falhar depois do commit, a resposta continua HTTP **200**, conserva o recibo,
contagens e IDs originais, e informa explicitamente:

```json
{
  "ingestion_committed": true,
  "run_id": "agenda-sp-2030-11-10-lote-001",
  "http_audit": {
    "status": "unconfirmed",
    "incident_id": "UUID_DO_INCIDENTE",
    "recovery": "retry_same_run_id_same_payload"
  }
}
```

Esse trecho é adicional ao resultado completo da seção 7, não um recibo substituto. **Não classifique
os itens como rejeitados nem gere outra execução para reenviá-los.** Guarde o recibo e o incident_id;
se desejar recuperar a auditoria, repita o mesmo endpoint, `run_id` e corpo original. O servidor
retorna `idempotent_replay: true`, preserva o recibo do lote e tenta registrar somente o novo acesso
HTTP; não repete as escritas de negócio. Quando a auditoria volta a funcionar, `http_audit.status`
passa a `recorded`. Se continuar falhando, permanece `unconfirmed` com um novo incident_id por tentativa.
Respeite a taxa/validade da chave; se não conseguir repetir, leve o recibo ao administrador.

Se um cliente legado omitir run_id, a resposta ainda informa o ID gerado e o commit confirmado,
mas a recuperação será `contact_admin_with_run_id`. Não acrescente esse ID a outro payload nem
faça retry gerando outro ID: o cliente não enviou originalmente uma identidade de execução repetível.

Falhas de auditoria em ping/dry-run ou sem recibo de commit confirmado retornam **503** controlado,
`error: collector_http_audit_unavailable`, incident_id e request_status. Não afirmam commit nem rollback.
Quando run_id está disponível, a orientação continua sendo repetir o mesmo corpo/ID. Uma falha
transitória da RPC também pode deixar o resultado desconhecido: conservar o mesmo ID é obrigatório.

O evento de log `sr_radar_collector_http_audit_unconfirmed` registra incident_id, coletor, endpoint,
SHA-256 do run_id, estado de confirmação e status planejado. Não registra chave, corpo ou erro SQL.
O operador correlaciona o incident_id com o aviso do cliente e consulta o recibo/histórico pelo
run_id na área administrativa. Linhas de uso HTTP contam tentativas: mais de uma linha para o mesmo
run_id após retries não significa duplicação de eventos ou de execuções.

## 8. Cliente HTTP mínimo e teste sem dados reais

```python
import json, os, urllib.request, uuid
BASE = "https://srrotas.com"
KEY = os.environ["SR_ROTAS_COLLECTOR_KEY"]

def call(path, payload=None):
    raw = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=raw,
        method="GET" if payload is None else "POST",
        headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)

capabilities = call("/api/v1/radar/collector/ping")
# Eventos completos conforme seção 4, usando fonte exclusiva de homologação.
# request = {"run_id": str(uuid.uuid4()), "dry_run": True, "events": validated_events}
# result = call("/api/v1/radar/ingest", request)
# assert result["rejected"] == 0, result["items"]
```

Capture HTTPError para aplicar a tabela de erros, sem registrar Authorization/KEY. Mantenha o mesmo
JSON e run_id em falhas transitórias. O exemplo não busca eventos na internet.

1. Admin cria fonte/chave exclusiva de teste. Faça ping: só valida conexão e registra uso.
2. Envie os exemplos sintéticos como dry_run; confira accepted/rejected, sem gravação de eventos.
3. Em homologação isolada, use novo run_id e dry_run false; confira pending e ausência no Radar.
4. Confira/aprove na tela com motivo; consulte o Radar no raio e horário do teste.
5. Despublique e confira ausência. Cancele e reenvie active: deve continuar terminal.
6. Rotacione/revogue/expire: chave anterior deve falhar.

Não aprove eventos sintéticos na produção. Testes locais repetíveis: `npm ci`, `npm run test:radar`,
`npm run test:radar:isolated` (Docker). Para incluir o fluxo visual no Chromium, execute `npm run build`
e `SR_RADAR_BROWSER_EXECUTABLE=/usr/bin/chromium npm run test:radar:isolated`.

## 9. Frequência e operação

Sugestão: segunda e quinta 09:00 America/Sao_Paulo (ou só segunda). Configure explicitamente o fuso
do agendador. Respeite fontes autorizadas, termos e limites de origem. Mantenha IDs estáveis e um
registro local de lotes/retries. Colete alterações/cancelamentos explícitos; ausência em página parcial
não é cancelamento. Uma ou duas verificações semanais não garantem atualidade de eventos próximos.

A URL antiga `/radar-admin` foi descontinuada: redireciona permanentemente (308) para `/admin/radar`, que exige a sessão administrativa existente. Nenhuma tela solicita o segredo global; os endpoints internos de servidor permanecem compatíveis.

Admin usa as rotas existentes `/api/v1/admin/radar/collectors`, `/events`, `/pois`, `/decisions`, `/runs`.
Mutações exigem sessão administrativa, Origin do destino HTTP, motivo e expected_version atual nas
decisões; alterações de credenciais também usam expected_version. `GET events?id=UUID` consulta até
100 snapshots do histórico. O banco retém todas as revisões futuras, sem reprocessar passado canônico.

Ordem de implantação: consultar estado real/migrations/credenciais do projeto correto, revisão
independente de segurança/schema, backup, aplicar apenas a nova migração em ambiente isolado,
testar, aplicar após homologação no projeto correto, fast-forward coordenado da main, deploy único
e smoke ping/dry-run/pendente/aprovação/retirada. Não reaplicar migrations anteriores em produção.

Rollback de contenção: `RADAR-EXTERNAL-COLLECTOR-V2.rollback.sql`. Revoga chaves, oculta dados externos
e desativa RPCs V2, mantendo schema, decisões, histórico e guards. Não remover review_status nem
restaurar fallback sem aprovação. Retenção/arquivo de auditoria é configuração operacional futura;
nesta versão não há exclusão automática da evidência.
