# Proposta administrativa Radar — rascunho 0.1

**Estado: PROPOSTA PENDENTE DE APROVAÇÃO. Nenhum endpoint ou migration ativado.**

ID proposto: `srrotas.radar-admin-import.draft.v0.1` · revisão documental 0.1.0.
Não é o contrato Radar v1.3 original, não implementa `radar.admin-import.v2` e não declara
compatibilidade v1. Nomes, limites, regras e rotas abaixo são decisões propostas para aprovação.
Se o original for recuperado, comparar explicitamente as diferenças; nunca renomear este draft
para uma versão dele sem análise e aprovação. Não enviar os exemplos sintéticos a produção.

## 1. Escopo e fluxo

Admin Radar autenticado → arquivo JSON → validação server-side sem gravação de negócio → prévia
por item → seleção humana só dos prontos → confirmação única transacional/idempotente → recibo
persistente `batch_id` → Envios → eventos em Recebidos / POIs em Lugares → revisão humana existente.

Criação apenas nesta versão proposta. Não atualizar, sobrescrever, aprovar, reativar, cancelar ou
assumir propriedade de registros existentes por upload. Atualizações continuam pelo processo
administrativo de revisão, com versão esperada e motivo. Não reutilizar `historical_import_*`,
`/admin/importacoes`, staging de Histórico nem `run_id`/credencial do coletor como lote administrativo.
Reutilizar somente o guard de sessão `requireRadarAdminV1`, as entidades Radar e a revisão já existentes.

## 2. Envelope e tipos (fechados)

JSON UTF-8, objeto raiz com **somente** `schema` e `items`. `schema` deve ser o ID deste draft.
`items`: array de 1–200 elementos. Cada elemento contém somente:

| Campo | Regra proposta |
| --- | --- |
| `item_id` | String ASCII 1–64, `[A-Za-z0-9_-]+`, única no arquivo; identidade de seleção, nunca PK do banco. |
| `kind` | `event` ou `poi`. |
| `provenance` | Objeto obrigatório descrito abaixo, sem propriedades extras. |
| `data` | Objeto correspondente ao tipo, sem propriedades extras. |

Proveniência obrigatória por item: `namespace` (slug 3–64 registrado/autorizado para curadoria
administrativa), `external_id` (string estável 1–200 por ocorrência/local), `official_url`
(HTTPS público válido, até 2.048 caracteres), `rights_basis` (`organizer_permission`,
`commercial_license`, `original_curation`), `rights_reference` (string 1–1.000, evidência documental
verificável), `coordinate_method` (`official_geodata`, `verified_original_survey`, `licensed_dataset`),
`verified_at` (timestamp com fuso), `verified_by` (referência de curador, string 1–100).
Os campos são declarações que exigem conferência, não prova automática de permissão. URLs não admitem credenciais/userinfo, hosts locais, nomes .local, IP literal privado/loopback/link-local ou esquemas file/data/http. O domínio oficial deve estar autorizado no namespace pelo curador. Não resolver DNS ou acessar a URL para tentar verificar o arquivo durante a importação.

Não admitir namespace reservado `ticketmaster`, `manual`, `admin`, `internal`, `admin_manual`
ou pertencente a coletor. Resolução de namespace é server-side em registro aprovado; se esse registro
não existir, item pendente, não fazer cadastro automático. API não aceita `collector_id`, ownership,
PK, `status`, `review_status`, `poi_id`, URL de callback, SQL, credencial, token ou metadata arbitrária.

`event.data`: `name` (3–200), `event_type` (subconjunto temporal proposto dos tipos aceitos pelo normalizador atual: `music`, `sports`, `fair_convention`,
`event`, `theatre`, `cultural`, `family`), `venue_name` (1–200), `address` (3–500),
`city` (2–100), `state` (UF brasileira), `lat`/`lng` (números finitos), `starts_at`, `expected_end_at`,
`egress_start_at`, `egress_end_at` (timestamps ISO-8601 explícitos `Z` ou offset), `end_time_basis`
(`official` ou `declared_estimate`). Todos obrigatórios. Uma estimativa declarada fica pendente e
nunca ganha horário inventado pelo servidor. Tipos não serão inferidos pelo nome.

`poi.data`: `canonical_name` (3–200), `poi_type` (enum fechado proposto: `music`, `sports`, `theatre`,
`fair_convention`, `cultural`, `mall`, `hospital`, `hotel`, `airport`, `bus_terminal`, `mobility_hub`),
`address`, `city`, `state`, `lat`, `lng` com as mesmas regras. Todos obrigatórios. Sem horários de
evento, probabilidades, demanda ou ETA em POI permanente.

Strings normalizadas em Unicode NFC/trim para comparação, sem truncamento silencioso. Datas válidas são normalizadas ao instante UTC antes de canonicalização. Para comparação de conteúdo existente, ignorar item_id e verified_at/verified_by; comparar dados do evento/local e a evidência material (URL oficial, fundamento/referência de direitos e método de coordenada). Essa equivalência de negócio é diferente do hash da requisição idempotente, que vincula o envelope completo. Números não
aceitam strings/coerção, NaN ou infinito. Datas reais com timezone obrigatório; `verified_at` não pode
estar mais de 5 min no futuro. Coordenadas: lat −90..90/lng −180..180, dentro do recorte Brasil
−34..5.5 / −74.5..−28.5 e compatíveis com endereço/município verificados. O recorte sozinho não prova
exatidão: coordenadas aproximadas, sem método/evidência ou inconsistentes ficam pendentes ou inválidas
conforme a tabela seguinte. Nunca geocodificar/inventar coordenada silenciosamente.

Tempos: `starts_at < expected_end_at`, `starts_at <= egress_start_at < egress_end_at`,
`expected_end_at <= egress_end_at`; duração total ≤ 72 h e janela de saída ≤ 12 h (limites a aprovar).
Offset/Z equivalentes representam o mesmo instante. Evento com saída já encerrada fica pendente
(`EXPIRED_EVENT`), nunca é publicado. Evento em andamento pode ser recebido se a saída é futura,
mas a aba geral Android atual filtra eventos futuros; aprovação não promete visibilidade nessa aba.

Licenças ODbL ou outras com obrigações de atribuição/share-alike exigem validação jurídica e tratamento
compatível antes de namespace habilitado; uma URL, vídeo, pesquisa ou nome de licença não basta.
Sem extração de imagens/OCR, crawling, fetch da URL ou download de mídia durante preview/commit.

## 3. Classificação por item

Cada resultado inclui `item_id`, `kind`, `classification`, `reasons[]` (código estável + mensagem
segura em português + campo quando aplicável), resumo de dados normalizados e possíveis IDs Radar
relacionados (somente ao admin autorizado). Sem stacktrace, SQL, segredo ou resposta bruta de terceiros.

| Classe | Critério / ação |
| --- | --- |
| `invalid` — inválido | Campo/tipo desconhecido ou proibido, ID ausente/duplicado no arquivo, enum, número, URL ou timestamp inválido, coordenada fora dos limites, cronologia impossível. Não selecionável. |
| `duplicate` — duplicado | Mesma identidade estável e mesmo conteúdo normalizado já existentes, ou repetição equivalente de identidade no arquivo. Informa referência existente; não cria nem atualiza. Não selecionável. |
| `pending` — pendente | Evidência/direitos/namespace não confirmados, precisão/localização não comprovada, estimativa de horário, evento expirado, colisão provável entre fontes ou mesma identidade com conteúdo diferente. Exige esclarecimento fora do commit; não selecionável. |
| `ready` — pronto | Todos os campos e pré-requisitos verificados, sem bloqueio de proveniência, identidade ou duplicidade. Selecionável; **ainda não aprovado para publicação**. |

Prioridade: inválido > identidade equivalente duplicada > pendência > pronto. Item pode listar todos
os motivos; resumo conta cada item uma vez. IDs `item_id` repetidos tornam todos os envolvidos inválidos;
mesma identidade upstream repetida com conteúdo divergente torna todos pendentes. Identidade equivalente
repetida com conteúdo igual marca todos duplicados do arquivo; não escolher um arbitrariamente.

Um item pronto para importação irá para revisão **pending**; isto é diferente da classe de prévia
pendente (que bloqueia importação). A UI explicita essa distinção e nunca pré-seleciona registros.

## 4. Identidade, ownership e duplicidades

Identidade upstream proposta: `(provenance.namespace, provenance.external_id, kind)`. Namespace e ID
são estáveis por ocorrência, nunca nome/data aproximada. Writer administrativo próprio (não coletor)
mapeará para `source=admin_manual`, ID derivado server-side de SHA-256 do JSON canônico da tupla e
proveniência original preservada. Prefixo/namespace administrativo é reservado, sem permitir ao upload
escolher ownership. O mapeamento é parte do projeto futuro e deve ser validado com os guards atuais.

Pesquisar tanto coletores quanto Ticketmaster/manuais, inclusive inativos, aprovados, rejeitados,
despublicados e cancelados. Encontrar mesmo upstream ID confiável/alias oficial exato implica
reutilizar a referência, não assumir ownership. Mesmo ID com alteração material fica pendente, não
sobrescreve nem cria cópia para contornar uma rejeição. Aprovação prévia nunca é herdada pelo lote.

Heurísticas são sinais para revisão, não prova de equivalência: eventos com nome/local normalizados,
início a até 15 min e distância até 300 m; POIs com nome/endereço compatíveis e distância até 100 m.
Os thresholds devem ser homologados. Mostrar motivos/IDs e marcar pendente; não fazer merge automático.
Mesmo local não significa mesmo evento; recorrências precisam de IDs distintos. Ausência de um sinal
não é garantia universal de que não há duplicata — a UI não pode prometer cobertura absoluta.

**Concorrência é um gate de ativação:** revalidar dentro da transação sob locks determinísticos de
identidade e candidatos/registro global de equivalências, com unicidade no banco. Todos os writers
relevantes (Ticketmaster, coletor e admin) precisam respeitar a coordenação comum ou mecanismo de
banco equivalente demonstrado em testes. Lock apenas no endpoint admin não resolve corrida com outro
writer. A infraestrutura atual não é aqui declarada suficiente: se a coordenação não puder ser
comprovada, confirmação falha fechada `503 DEDUP_COORDINATION_UNAVAILABLE`; não ativar gravação.

## 5. API proposta (INATIVA)

Rotas abaixo são desenhos, não endpoints existentes. Sessão admin atual em todas, recusando chave de
coletor mesmo se acompanhar cookie válido; admin não permitido recebe 403. Writes exigem Origin/Host
confiáveis, mesma origem, sem confiar em x-forwarded-host; CSRF não depende só de esconder botão.
Responses privadas `Cache-Control: no-store`; CORS não amplia origem. Sem login/segredo paralelo.

| Operação proposta | Requisição / resultado |
| --- | --- |
| `POST /api/v1/admin/radar/manual-import/preview` | Envelope JSON deste draft → 200 com prévia individual, resumo, hash canônico e token efêmero. Sem criar lote, itens, execução ou registros Radar. |
| `POST /api/v1/admin/radar/manual-import/commit` | Mesmo envelope, `selected_item_ids`, `preview_token`, cabeçalho `Idempotency-Key` UUID → 201 com recibo persistente; replay confirmado → 200, mesmo batch_id. |
| `GET /api/v1/admin/radar/manual-import/batches/{batch_id}` | Recibo imutável da confirmação + estado atual de revisão separado, 200 ou 404. Consultável após reload. |
| `GET /api/v1/admin/radar/manual-import/batches?client_request_id={key}` | Recuperação de resposta perdida, restrita ao ator/escopo da operação. Não consulta por credencial. |

Prévia aplica transação READ ONLY para consultas de negócio. Não cria staging/batch/run/decisão.
Throttling de gateway efêmero (sem persistir arquivo) e logs mínimos de segurança são separados dos
registros de negócio; nunca registrar payload, coordenadas, URLs privadas ou tokens. A inspeção local
atual do PR #22 continua ainda mais restrita: só sintaxe, sem chamar estas rotas.

Token de prévia: envelope opaco com criptografia autenticada (AEAD), vinculado ao ator e sessão, ID/revisão desta proposta,
versão de política, hash do envelope normalizado (JCS/RFC 8785), fingerprint das identidades/versões
consultadas e IDs elegíveis; prazo 10 min. Não serve como autorização independente. Nenhum payload
sensível em token legível. Dados completos são reenviados na confirmação e revalidados server-side.

Limites propostos: arquivo e corpo de preview até 2.000.000 bytes; corpo de commit até 2.100.000
bytes (inclui envelope + token até 64 KiB + seleção), todos limitados em streaming sem confiar em Content-Length;
1–200 itens, seleção de 1–200 prontos; sem arquivo comprimido/base64/binário/URL de arquivo remoto;
profundidade máxima 12, textos limitados como §2; JSON com chaves repetidas rejeitado (não aceitar
last-key-wins). Content-Type application/json; campos extras recusados. Preview 30/15 min e commit
10/15 min por ator + origem, GET 60/min; rate-limit indisponível falha fechado, sem fallback aberto.
Não delegar validação somente ao cliente. Operações de commit/read-receipt exigem trilha administrativa.

## 6. Confirmação, transação, auditoria e recibo

1. Revalidar sessão/origem/limites. Consultar chave idempotente já confirmada ANTES de exigir token
   novo: mesma requisição pode ser repetida depois que o token de preview expirou.
2. Para operação nova, validar token/ator/expiração e hash; seleção é conjunto explícito de IDs
   únicos, não vazio, subconjunto dos prontos. Campo repetido ou não pronto → 422, nenhum write.
3. Canonical request hash vincula schema/envelope + IDs selecionados ordenados. Mudar arquivo ou
   seleção com mesma chave → 409. Reordenar IDs selecionados não muda a operação; ordem do envelope
   é preservada pelo hash. Chave de outro ator/escopo → 409, sem revelar seu recibo.
4. Iniciar uma transação no banco: reservar chave única, obter locks em ordem consistente, refazer
   validação/duplicidades/proveniência/versões/validade temporal e recusar qualquer alteração relevante
   desde a prévia com 409. Nenhum lote parcial: ou todos os selecionados ou nenhum.
5. Gravar somente os selecionados, seus IDs, lote/itens/recibo/auditoria na MESMA transação. Evento:
   `status=active`, `review_status=pending`, sem `poi_id` herdado. POI: `status=inactive`, revisão
   pending. Relacionamento autorizado com POI existente só pelo fluxo de revisão/resolução já aprovado.
   Não gravar itens não selecionados como eventos/POIs; recibo pode registrar IDs/status de exclusão,
   sem reter payload completo desnecessário. Nenhuma aprovação automática ou acesso de coletor ao lote.
6. Falha de qualquer write/auditoria core → rollback integral, 5xx seguro. Não usar várias chamadas
   PostgREST independentes como se fossem transação; projeto futuro exige RPC transacional revisada.
7. Commit confirmado → recibo 201 e Location. Falha posterior de telemetria HTTP não converte commit
   conhecido em um falso rollback; retry da mesma chave devolve o recibo original. Commit incerto por
   transporte: orientar repetir MESMA chave/corpo, nunca gerar lote novo silenciosamente.

`batch_id` UUID gerado pelo servidor, independente de run_id/collector_id. Recibo persistente contém
ator, timestamp UTC real, ID/revisão da política/contrato, chave de requisição não secreta, hash,
contagens de prévia/selecionados/criados/não selecionados, IDs por item, target kind/id, estado de
review inicial e links internos para Envios/Recebidos/Lugares. Status inicial do batch: `committed`;
replay não cria novos itens/decisões/revisões. Rejeições/rollback não viram lotes committed.
Consulta separa snapshot original imutável de estado atual de revisão/despublicação.

Proposta de persistência específica (ainda sem DDL): lotes administrativos + itens administrativos,
chave idempotente única por escopo/ator, FKs para entidades Radar e auditoria append-only. Deve reusar
as decisões/revisões existentes, sem escrever histórico de importações de corridas. RLS/ACL fechados
para anon/authenticated/coletores; RPC somente service_role após autenticação administrativa no backend.
DDL, índices/locks, privilégios, backup/rollback e retenção exigem revisão antes de migration futura.

Proposta de retenção: descartar bytes do upload após resposta; nunca reter arquivo no navegador,
localStorage, logs ou bucket. Auditoria/recibo mínimo de rastreabilidade por 365 dias (a aprovar),
com identidade administrativa protegida, acesso restrito e política de descarte/backup auditada.
Dados de evento/POI/proveniência seguem a política própria do Radar, sem exclusão em cascata pelo
fim do prazo do recibo. Hash não anonimiza informação; acesso ao digest também é restrito.

## 7. Erros e estados da UI

| HTTP / código | Significado / recuperação |
| --- | --- |
| 400 `INVALID_JSON`, `DUPLICATE_JSON_KEY`, `INVALID_ENVELOPE` | Corrigir arquivo; nenhum registro gravado. |
| 401 `UNAUTHORIZED` | Reautenticar; não perder seleção apenas em memória sem expor payload. |
| 403 `ADMIN_REQUIRED`, `ORIGIN_REQUIRED`, `COLLECTOR_KEY_NOT_ALLOWED` | Não repetir com credencial/escopo incorreto. |
| 413 `PAYLOAD_TOO_LARGE` | Reduzir arquivo; server também limita streaming. |
| 415 `JSON_REQUIRED` | Content-Type/UTF-8 correto. |
| 422 `SELECTION_NOT_READY`, `EMPTY_SELECTION` | Voltar à prévia; nada confirmado. |
| 409 `PREVIEW_STALE`, `PREVIEW_EXPIRED` | Refazer prévia e seleção consciente. |
| 409 `IDEMPOTENCY_CONFLICT`, `IDEMPOTENCY_SCOPE_MISMATCH` | Não gerar chave nova automaticamente nem vazar recibo alheio. |
| 429 `RATE_LIMITED` | Retry-After, sem loops automáticos. |
| 503 `CONTRACT_NOT_APPROVED`, `STORE_UNAVAILABLE`, `DEDUP_COORDINATION_UNAVAILABLE` | Operação indisponível, fail-closed. |
| 500 `BATCH_ROLLED_BACK` | Rollback conhecido; retry seguro da mesma chave se apropriado. |
| 503 `COMMIT_OUTCOME_UNKNOWN` | Não afirmar rollback. Repetir mesma chave/corpo/consultar recibo. |

Erro: `{ "error": { "code": "...", "message": "...", "retryable": false, "correlation_id": "UUID" } }`.
Sem SQL, stack, credenciais, caminho ou payload bruto. Mensagens por campo não ecoam entrada sensível.

UI: pronto/duplicado/pendente/inválido com motivos, fonte/local/horários com fuso; seleção só por clique
nos prontos, resumo de confirmação e revisão humana antes de publicar. Mudança de arquivo invalida
prévia/seleção; resposta assíncrona antiga nunca restaura a seleção de outro arquivo. Reenvio não é
aprovação. Batch não é fonte de demanda/ETA fictícia. No estado atual, mostrar bloqueio do contrato,
não anunciar que v1/v2 está implementado. Navegação e ações acessíveis em desktop/mobile/fonte 150%.

## 8. Exemplos e matriz de testes (a executar somente após implementação)

Arquivos em [examples](examples/) são **sintéticos e somente proposta**. Não são fixtures oficiais de
v1.3 nem dados para ingestão. IDs, hashes e tokens de respostas são ilustrativos; nenhum recibo real.
O manifesto de exemplos explicita quais itens dependem de aprovação de namespace/direitos.

| Caso | Resultado exigido |
| --- | --- |
| Evento e POI válidos com evidência aprovada | ready na prévia, sem writes; seleção explícita. |
| v1.3/v1/v2 antigo recebido | Rejeitar versão não definida; sem conversão tácita. |
| JSON inválido, repetição de chave, raiz extra, enum/propriedade proibida | 400 envelope ou invalid por item; sem aprovação/owner injetado. |
| Arquivo > limite com Content-Length ausente/falso, stream excessivo, profundidade | Recusa limitada em memória, sem write. |
| Sem sessão, sessão revogada/não admin, chave coletor + cookie, Origin forjado | 401/403, nenhuma mutação. |
| URL loopback/private IP/credencial/data/file, URL pública | Recusa URL insegura; nenhuma requisição de saída, mesmo pública. |
| Evidência de direitos ausente/não confirmada, namespace desconhecido, ODbL não revisada | pending; vídeo/site sozinho não torna pronto. |
| Coordenada aproximada, fora Brasil/intervalo, incompatível com cidade | pending ou invalid conforme causa; sem geocode inventado. |
| Datas sem offset, data impossível, cronologia errada, offset equivalente | Invalid ou equivalência correta por instante. |
| Horário estimado, evento expirado/em andamento, POI com ETA | Pending informado; POI com campo proibido invalid; sem alterar leitor Android. |
| item_id repetido, mesma identidade igual/divergente no arquivo | Todos invalid/duplicate/pending conforme §3; nunca escolher arbitrariamente. |
| Mesma identidade já cadastrada, inclusive aprovada/rejeitada/cancelada | Duplicate ou pending; nenhuma sobrescrita/reativação. |
| Colisão provável de POI/evento com Ticketmaster/coletor/manual | Pending + motivo; não assumir ownership nem deduplicar por chute. |
| Mesmo POI, ocorrências distintas / eventos recorrentes | Não fundir eventos somente pelo local; ID estável por ocorrência. |
| Lote misto + selecionar apenas parte dos prontos | Só selecionados criados; excluídos sem entidade gravada. |
| ID não pronto, seleção vazia/desconhecida/duplicada | 422, nenhuma mutação. |
| Hash/ator/token/política/row_version alterados, token expirado | 409/403 conforme causa; revalidar, sem write parcial. |
| Replay mesma chave/mesma seleção, seleção reordenada, token expirado após commit | Mesmo batch/IDs/recibo, sem nova auditoria de negócio. |
| Mesma chave com outro corpo/seleção/ator | 409, sem vazamento ou segunda operação. |
| Duas confirmações paralelas, chaves iguais ou distintas | Uma identidade final; replay/conflict determinístico. |
| Corrida admin × Ticketmaster × coletor; coordenação indisponível | Sem duplicar; testar banco real. Fail-closed se garantia não demonstrada. |
| Falha após N inserts ou na auditoria; POST timeout antes/depois de commit | Rollback integral ou recibo idempotente recuperável, nunca estado ambíguo tratado como rollback. |
| GET batch após reload, id inexistente/de outro escopo, paginação de Envios | Autorizado/restrito, snapshot estável e status atual separado. |
| Aprovação/rejeição/despublicação concorrente | row_version/409 e auditoria existentes; cliente coletor não revisa. |
| Leitores antes/depois de aprovação; POI inativo | Pending/inactive invisíveis; somente aprovação humana permite leitura legítima. |
| RLS/ACL anon/authenticated/coletor/service_role, auditoria append-only | Privilégios mínimos, nenhuma rota pública de write/UPDATE/DELETE de audit. |
| UI desktop/mobile/fonte 150%, teclado, leitor de tela, rede/offline/arquivo trocado | Sem sobreposição, foco acessível, sem seleção antiga/reenvio cego/publicação automática. |

## 9. Gates de aprovação (nenhum marcado automaticamente)

- [ ] Responsável aprova ID/versão NOVOS e ausência de compatibilidade presumida.
- [ ] Aprovar campos, enums, direitos/licenças, limites/retencão, criação apenas e regras de pendência.
- [ ] Aprovar namespace/ownership e desenho de dedupe entre todos os writers.
- [ ] Revisar autenticação/CSRF, transação/RPC, auditoria, concorrência, RLS/ACL e rollback.
- [ ] Aprovar proposta de persistência; só depois preparar migration revisável e testar isoladamente.
- [ ] Implementar validators/endpoints/UI/recibo e executar matriz em PostgreSQL descartável/navegador.
- [ ] Homologar preview e fluxo de revisão sem dados reais; produção exige autorização posterior.

Esta proposta e sua aprovação documental não autorizam migration Supabase, deploy produtivo,
importação real, aprovação de registros, merge ou publicação. Não há endpoints de gravação neste PR.
