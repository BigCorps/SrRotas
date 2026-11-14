# Admin Radar — protótipo administrativo 0.2 isolado (issue #20 / PR #22)

Branch `feature/admin-radar-json-import`, base main `e9f7982`, HEAD anterior `008347c`.
Main, PR #19, vc114, Android e coletor externo preservados. Não há migration produtiva nem endpoint
de commit publicado. Não há compatibilidade presumida com v1.3/v1/v2. O documento desaparecido não
bloqueia mais o desenvolvimento do **novo** [draft 0.2](../../contracts/admin-radar-import-proposal/PROPOSAL.md).

## Fluxo implementado e limites reais

- Upload para **prévia HTTP autenticada**, sessão administrativa/Origin/recusa de chave coletor e
  rate-limit existentes. Streaming/UTF-8/JSON estritos, chaves repetidas/profundidade/limites, campos
  fechados, timezone/cronologia, UF/coordenadas, URL pública sem fetch, proveniência e fonte autorizada.
- Classificação individual ready/duplicate/pending/invalid, motivos e referências; nenhuma
  pré-seleção. Mudança de arquivo limpa seleção e invalida respostas antigas. Prévia nunca grava
  eventos/POIs/batches/decisões; somente contador mínimo de segurança existente pode mudar.
- Sem registro aprovado no ambiente publicado, campos válidos ficam pendentes. Não cadastrar fontes
  a partir do arquivo. Coordenadas/município/direitos/horários exigem atestação server-side do hash
  material exato, além de namespace/domínio/fundamento permitidos. Exemplos SINTETICO nunca são
  autorização de produção. A versão antiga é rejeitada, sem conversão tácita.
- Registro de namespaces **experimental** versionado, aprovação/revogação, conflito por versão,
  auditoria e ACL/RLS; slug de coletor/reservado não pode ser usurpado. Apenas no banco descartável.
- `Sandbox` em `backend/tests/radar-isolated/admin-import` implementa transação única, seleção só
  dos prontos, token AEAD, idempotência, batch_id persistente, recibo/itens/auditoria append-only.
  Host loopback + nome `sr_radar_test_*` + nonce obrigatório. Nenhuma rota Next importa o writer.
- Reutiliza normalizador POI: normalized_name/geo_cell/BR/source_url/confidence. Evento força
  pending/active e end_time_source=source somente com evidência; POI força inactive/review=pending.
  Apenas revisão humana existente permite publicação; novo lote não aprova nada.
- Área de lotes em Envios, consulta batch_id e links persistentes após reload; eventos administrativos
  em Recebidos pela origem manual; POIs em Lugares inativos. O teste de navegador usa adaptadores
  locais para registro/consulta do experimento. **Não há armazenamento de batches ativado no Admin
  publicado:** consulta retorna indisponível, sem recibo fictício. Commit da UI desabilitado.

## Bloqueio de ativação reproduzido

O teste negativo mantém o advisory lock administrativo retido, mas o RPC real do coletor e uma
inserção no formato Ticketmaster aceitam a mesma ocorrência em outras sources. Foram comprovadas
**três fontes para a mesma ocorrência** no banco descartável. A unicidade atual é por source; ela
não garante dedupe global. Serializar só o Admin não resolve. O teste passa ao demonstrar o defeito
estrutural e verificar `commit_enabled=false` / `DEDUP_COORDINATION_UNAVAILABLE`; **não é evidência
de dedupe resolvido**.

Para ativar: desenho/revisão de identidades/aliases globais e coordenação obrigatória em todos os
writers ou no banco, com conflitos ambíguos bloqueados sem fusão automática; repetir concorrência e
revisão de ACL/RLS/RPC/rollout/rollback. Em seguida revisar o registro real de fontes e persistência,
preparar migration revisável e obter autorização específica. Nada aplicado no Supabase produtivo.

## Testes executados

Node **22.23.5**, Next **16.3.6**, PostgreSQL **17.7**, PostgREST **13.0.7**, Chromium nativo local.

| Verificação | Resultado local |
| --- | --- |
| `npm run check` | aprovado |
| `npm run test:radar` | 59/59 |
| `npm run test:statistics` | 72/72 |
| `npm run build` | aprovado |
| `SR_RADAR_BROWSER_EXECUTABLE=/usr/bin/chromium npm run test:radar:isolated` | 31/31, sem skips |

Regressões novas abrangem JSON estrito/stream excessivo, datas/coordenadas/URL/campos proibidos,
fonte não autorizada, estimativa, identidade duplicada/divergente e colisões entre fontes; prévia
sem writes; seleção parcial; defaults seguros; recibo depois do commit/replay; outra chave/corpo/ator;
token expirado/adulterado/sessão diferente; revogação desde a prévia; concorrência mesma chave e
chaves distintas; falha depois de inserts/auditoria e falha SQL real da auditoria com rollback integral;
ACL/RLS e imutabilidade; aprovação/rejeição concorrente/despublicação com versão esperada e leitores.
Hash material é recalculado das colunas atuais: metadata antiga não prova conteúdo que foi editado.

HTTP **real no Next local**, com sessão de teste: preview 200 com pending (registro não ativado),
versão antiga/chave JSON repetida 400, Content-Type errado 415 e POST commit **404**. Handler
compartilhado também testado com sessão ausente 401, Origin ausente 403 e chave coletor+cookie 403.
Nenhuma resposta de commit HTTP produtivo é alegada: commits 201/replay 200 são resultados internos
do experimento PostgreSQL, não endpoints publicados. Recibo no navegador usa adaptador local de
consulta autenticada desse mesmo banco, não resposta estática inventada.

31 testes reaproveitam a suíte anterior de coletores/revisão, sem reconstruir o backend. O runner
cria e destrói containers/banco únicos; nenhuma variável/secreto produtivo. CI foi ajustado para
build antes do navegador, exigir Chrome e guardar imagens privadas. Nenhum teste Android nesta branch.

## Capturas reais do protótipo

Fixtures exclusivamente sintéticas no banco descartável: um pronto autorizado **somente pelo setup
de QA**, um duplicado já semeado, um pendente sem atestação e um inválido. Nenhum dado real ou
aprovação produtiva. Prévia montada com adaptador isolado que reutiliza guard/serviço real. O teste
separado acima percorre a rota Next sem adaptador e comprova fail-closed.

- [Desktop 1365×900](desktop.png)
- [Celular 390×844](mobile.png)
- [Desktop fonte 150%](desktop-font150.png)
- [Celular fonte 150%](mobile-font150.png)
- [Recibo em Envios](receipt.png)
- [POI em Lugares](places.png)

Fontes computadas aumentadas em 150%, sem substituir por zoom do viewport. Navegação participa do
fluxo; sem interseção com formulário, sem overflow horizontal e com hit-testing das ações após
scroll. Estados de arquivo inválido/excessivo, reset de seleção e confirmação bloqueada testados.

## Revisão necessária antes de ativação

Claude Review Handoff (somente leitura): contrato 0.2, segurança da prévia, limites e proveniência,
registro versionado, transação/idempotência/recibo/ACL, testes negativos de coordenação e ausência
de endpoint de commit no aplicativo. Findings voltam ao Codex; não fazer merge, migrations ou
deploy produtivo. Proposta aceita apenas como base de desenvolvimento isolado; ativação ainda bloqueada.
