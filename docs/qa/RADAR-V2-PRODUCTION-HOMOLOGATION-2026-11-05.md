# Radar V2 — homologação produtiva em 05/11/2026

Resultado: os nove cenários HTTP solicitados passaram em https://srrotas.com.
A verificação adicional de ACL encontrou grants excedentes de auditoria, corrigidos e
verificados nesta sessão, sem alterar o código runtime nem criar deploy.

## Versão e infraestrutura observadas

- Main/backend publicado: `e8817573bd10580dc7a63ec374e2fe93002dc63c`.
- Vercel: `sr-rotas`, deploy `dpl_HAgmF7kz2zLkcrhCuARnnEqdsKU1`, production/READY,
  SHA acima e alias srrotas.com confirmados pelo conector.
- Supabase: `gheymrttmfdxnjdbgvgl`.
- Migrações produtivas: V1 `20261105121037`; V2 `20261105140014`;
  correção de ACL `20261105144035_radar_audit_append_only_privileges_v2`.
- RPCs V2 SECURITY INVOKER, execução somente postgres/service_role. RLS nas tabelas
  de coletores, uso, decisões e histórico. Anon/authenticated sem leitura das chaves.
- O ambiente local bloqueou a saída HTTP. Os testes HTTP foram executados em um
  Vercel Sandbox não persistente, limitado aos domínios srrotas.com e supabase.com.
  Nenhuma proteção foi desabilitada ou configuração de deploy alterada.

## Credencial e contexto dos testes

A credencial fornecida pelo usuário pertencia ao próprio “Coletor Radar Teste”.
Para cumprir a preservação dessa chave, ela não foi usada nem armazenada nos comandos.
Outra chave de 256 bits foi gerada para o coletor descartável
`81b5abe5-bb82-40c1-8f62-6de15a39f28a`, provisionado pela conexão administrativa:
fonte exclusiva `radar_homolog_20261105`, escopo events:ingest, lote 5,
30 requisições/hora e validade de 20 minutos.

Os segredos dos testes existiram apenas em arquivos 0600 no tmpfs /dev/shm do
sandbox, sem impressão nos logs ou gravação no repositório; foram removidos.
Nenhuma chamada usou o segredo global de ingestão.

Para testar os endpoints sem participação do usuário, foram provisionadas sessões
de homologação pelo banco: uma sessão curta da identidade administrativa já existente
e uma sessão de consumidor vinculada a um motorista sintético separado.
Isso valida a autorização/decisões por sessão e o feed HTTP; não constitui teste de
login interativo/OAuth. As sessões e o consumidor sintético foram removidos ao final.

## Resultados HTTP observados

| Cenário | Resultado |
|---|---|
| Ping autenticado | 200, fonte/escopo/limites corretos |
| Chave inválida | 401 collector_unauthorized |
| Dry-run | 200, accepted=1, dry_run=true; banco permaneceu com 93 eventos e sem execução dry-run |
| Ingestão real sintética | 200, ingestion_committed=true, http_audit.status=recorded, pending_review=1 |
| Conferência administrativa | GET 200, review_status=pending, row_version=1 |
| Radar com evento pendente | GET 200, evento ausente |
| Aprovação administrativa | PATCH 200, approved |
| Radar após aprovação | GET 200, evento presente |
| Retry mesmo endpoint/run_id/corpo | 200, idempotent_replay=true, mesmo ID; uma linha/execução; aprovação preservada |
| Despublicação administrativa | PATCH 200, unpublished, row_version=3 |
| Radar após despublicação | GET 200, evento ausente |
| Revogação administrativa da chave descartável | PATCH 200; status revoked |
| Ping e ingestão com chave revogada | 403 collector_revoked em ambos |
| Coletor tentando Admin | 403 collector_key_not_allowed |
| Admin sem sessão | 401 |
| Página antiga | 308 para /admin/radar, sem formulário de segredo |
| Sessões de homologação após remoção | Admin e consumidor retornaram 401 |

O roteiro inicial esperava 401 para a chave revogada e teve uma asserção restritiva.
O contrato implementa 403 collector_revoked; a verificação específica foi repetida e
confirmou esse resultado em GET e POST. Nenhuma alteração no aplicativo foi necessária.

## Identificadores de evidência

- Evento: `2f3eee2e-f531-4864-a811-7ca8909886e3`.
- run_id: `homolog-v2-8e14e9e5-da91-4075-8c47-2588eac4a92a`.
- Execução: `sr_radar_source_runs.id=759`, status ok, saved_count=1, request_hash presente.
- Histórico: IDs 14/16/18, versões 1/2/3: pending → approved → unpublished.
- Decisão approve: `186e4f0c-4a68-490e-8465-89b83921ebdb`.
- Decisão unpublish: `325dd105-8600-4d22-ac63-0a98611a8e33`.
- Uso HTTP do coletor: IDs 16, 18–23; inclui ping/dry-run/ingestão/replay e acessos negados.
- Há registros 401 de falhas de autenticação sem coletor identificado.
- Não foi apagado nenhum registro legítimo de uso, execução, decisão ou histórico.

Amostras dos request IDs Vercel:

| Requisição | x-vercel-id |
|---|---|
| Ingestão | iad1::iad1::grzrn-1793889117377-c98b6ce46216 |
| Radar pendente | iad1::iad1::rngjk-1793889119618-87685c804047 |
| Aprovação | iad1::iad1::p7l6j-1793889120566-3ef383f617f2 |
| Radar aprovado | iad1::iad1::x4pp9-1793889121166-f91188cbc96c |
| Replay | iad1::iad1::qf2cp-1793889121910-21e303ea2d3f |
| Despublicação | iad1::iad1::sl5jr-1793889124509-0ec8abb75453 |
| Ingestão revogada | iad1::iad1::wjmbg-1793889157658-e19f015623a2 |
| Radar após limpeza | iad1::iad1::wscg5-1793889158452-a4d58e0fb3a4 |

## Correção de permissões encontrada durante a homologação

O Supabase atribuiu ALL a service_role por default privileges na criação das tabelas.
GRANT SELECT, INSERT na migração anterior não removeu os privilégios já existentes:
o histórico permitia UPDATE/DELETE/TRUNCATE; decisões e uso também permitiam TRUNCATE.

O teste isolado passou a reproduzir esses defaults e falhou antes da correção.
A migração adicional redefine a ACL somente das três tabelas de auditoria e concede
apenas SELECT/INSERT à service_role. Não altera linhas, sequências, políticas RLS,
eventos ou contratos de API. Não altera os default privileges globais do projeto.

Após 21/21 testes isolados com navegador passarem, a migração foi aplicada via MCP.
A leitura produtiva confirmou SELECT/INSERT=true e UPDATE/DELETE/TRUNCATE/TRIGGER=false
nas três tabelas. Uma chamada real à sr_radar_review_v2 sob SET LOCAL ROLE service_role
e um INSERT de uso funcionaram dentro de transação revertida com ROLLBACK; os registros
sintéticos continuaram com três versões e nenhum dado transacional desse check persistiu.

O rollback do aplicativo deve manter esses grants restritos. Restaurar ALL reabre a falha.
A correção e a regressão ficam registradas na branch de revisão, sem merge automático
em main e sem novo deploy. Produção já recebeu o ajuste SQL validado.

## Preservação e limpeza

- Os 93 eventos Ticketmaster foram comparados por agregação de todas as linhas ordenadas
  antes/depois: fingerprint `4052fc08b97b0163cebdb6fceb34b9fe`, idêntico após HTTP e ACL.
- A linha inteira do “Coletor Radar Teste”, ID `90e10ae0-68c1-4670-8c75-8fc9b5838d6d`,
  manteve fingerprint `b31c1460ec7f64c77f0ef0ff5ae639d6`; active, row_version=1,
  last_used_at=null, sem alteração de chave/limites/fontes/validade.
- O único evento sintético criado ficou unpublished; zero sintéticos aprovados/publicados.
- A chave descartável foi revogada; não foi removida sua proveniência.
- Sessões temporárias e motorista sintético removidos; acessos posteriores negados.
- Arquivos temporários de segredo: zero; sandbox stopped e persistent=false.
- Nenhuma alteração Android, scraper, build Next, deploy Vercel ou novo commit na main.

## Verificações locais e limites

- 50/50 Radar e 72/72 Estatísticas, reexecutados nesta sessão.
- 21/21 isolados PostgreSQL 17.7/PostgREST 13.0.7/Chromium, incluindo grants reais,
  tentativas UPDATE/DELETE/TRUNCATE negadas, fluxo completo, falhas de auditoria HTTP,
  concorrência, expiração e rollback de contenção.
- TypeScript sem emissão e diff-check limpos.
- Não foi necessário recompilar o runtime Next ou disparar Actions Android: só SQL,
  teste e documentação mudaram. O backend publicado continuou no SHA confirmado.
- Nenhuma falha de auditoria foi induzida em produção; esses cenários foram exercitados
  no banco descartável. O login interativo e a interface Android não foram homologados aqui.
- Não há etapa bloqueada do roteiro HTTP solicitado. A revisão do registro SQL na branch
  e sua sincronização futura em main são pendências de repositório, não de operação HTTP.

## Prompt para o desenvolvedor do coletor

Implemente um scraper externo e independente para o Sr. Rotas lendo integralmente
docs/contracts/RADAR-EXTERNAL-COLLECTOR-V2.md de BigCorps/SrRotas.
Produção: https://srrotas.com; integração V2 homologada, com revisão humana obrigatória.

Não altere backend, Supabase ou Android. Não reutilize credenciais da homologação.
Use somente uma nova chave individual limitada, obtida por configuração segura,
em SR_ROTAS_COLLECTOR_KEY; nunca use o segredo global de ingestão ou acesso administrativo.

Comece por ping e dry-run. Implemente somente fontes permitidas pela chave e cuja coleta
seja autorizada, sem duplicar Ticketmaster. Não invente campos ou probabilidade de demanda.
Implemente events:ingest e, se autorizado, pois:propose conforme o contrato.

Mantenha identidade estável (source, external_id), run_id por lote e persistência do
mesmo run_id/corpo entre retries. Respeite 409, 429/Retry-After e a política http_audit:
commit confirmado com auditoria unconfirmed não é rejeição e não autoriza novo run_id.
Use datas com fuso, URLs originais, coordenadas válidas e proveniência real.

Envie atualizações e cancelamentos explicitamente, sem snapshot_complete. Eventos e POIs
devem permanecer pendentes/inativos até revisão humana; o coletor nunca aprova ou publica.

Entregue projeto separado, dependências fixadas, testes com mocks, checkpoints de execução,
logs sem segredos e guia de instalação/agendamento 1–2 vezes por semana.
Não ative agenda nem envie dados reais sem configurar a credencial e concluir dry-run.

