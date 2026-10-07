# Sr. Rotas — Continuidade Canônica

SINGLE_SOURCE_OF_TRUTH: true
CANONICAL_CONTINUITY_VERSION: 2026-10-07.3
CURRENT_HEAD_STAGE: 0.33.18-field / versionCode 95 — Field vc95 — Map render + Ride turnover + Fresh Projection Recovery
GREEN_BASELINE_BEFORE_DOC_CLEANUP: 35885221f36b7f048af229745d75f1e483bc6524
Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.8-field / versionCode 85`
HEAD atual: `0.33.18-field / versionCode 95`

> Este arquivo é a única fonte operacional de verdade para agentes e continuidade do desenvolvimento.
> Histórico de fases, QA, handoffs, manifests de ZIP e roadmaps antigos foram removidos do branch principal e permanecem recuperáveis pelo Git.
> Antes de qualquer alteração, valide também o código e os serviços atuais. Se houver conflito entre este arquivo e documentação histórica de commits antigos, este arquivo + estado atual do código/serviços prevalecem.

## 1. Objetivo atual

Levar o Sr. Rotas 1.0 à Play Store preservando primeiro:
1. integridade de dados;
2. estabilidade de captura;
3. contratos funcionais;
4. compatibilidade;
5. UX;
6. novas funcionalidades.

Próximo objetivo imediato:
- revisar somente o diff vc95 com Claude antes de Actions; depois homologar render cartográfico, troca explícita de corrida e reinício de captura com consentimento novo na mesma jornada;
- depois executar um teste de NOVO USUÁRIO ponta a ponta;
- somente então fechar Gate 6/7 e Gate 7/7 para Play Store.

## 2. Arquitetura congelada

Android:
- `ConsolidatedMainActivity027037` é o shell principal.
- Reader M1/MediaProjection é o leitor oficial.
- Reader 2 permanece shadow/observacional.
- Controlled Hybrid permanece OFF.
- não criar segundo OCR pesado concorrente;
- não alterar Reader/HUD/parser/dedupe/fórmulas em correções puramente visuais/comerciais.
- `JourneyBubbleController` é o host principal da janela flutuante/HUD.

Dados:
- `ride_offers` = observações operacionais; não representam automaticamente corridas concluídas ou receita.
- `ride_outcomes` confiável exclui legado `uber_history_ocr` das métricas realizadas fortes.
- V7 é histórico canônico observado; NÃO reprocessar.
- nunca inventar valores ausentes.
- pseudonimizado não significa anonimizado.
- não persistir OCR bruto, screenshots, trilha GPS arbitrária, tokens ou Android ID bruto em telemetria analítica.

Web/backend:
- Next.js + Supabase + Vercel.
- AnalyticsDomain/Evidence Policy é a base compartilhada por Estatísticas/Pergunte/Agora/MCP.
- Pergunte é determinístico; não depende de API externa de IA e não consome créditos por consulta.

## 3. Estado dos Gates 1.0

### Gate 1 — MCP OAuth
Estado: funcional/homologado tecnicamente.
- OAuth 2.1 + DCR + PKCE S256.
- refresh rotation, revogação e reautorização validadas.
- sem chave manual.
- 12 ferramentas read-only:
  - `get_srrotas_capabilities`
  - `get_history_dashboard`
  - `get_driver_summary`
  - `get_driver_strategy`
  - `get_strategy_progress`
  - `search_offers`
  - `compare_periods`
  - `get_best_hours`
  - `get_cost_breakdown`
  - `get_current_journey`
  - `list_journeys`
  - `get_journey_summary`
- identidade duplicada de tester foi consolidada: Android operacional + Web/OAuth + V7 agora convergem para um único driver.
- smoke com dados recentes deve ser repetido sempre que houver alteração de identidade/auth, mas não reconstruir OAuth sem evidência.

### Gate 2 — Fundação Premium
Estado: ✅ fechado.
- AnalyticsDomain/Evidence/NQE compartilhados.
- 18 intents determinísticos.
- EvidenceEnvelope e políticas de suficiência/confiança.
- sem OpenAI/API externa.

### Gate 3 — Conciliação + Estatísticas + Pergunte + MCP
Estado: ✅ fechado.
- `/app/estatisticas`
- `/app/ia`
- `/app/conciliacao`
- reconciliação é decisão humana; nunca auto-confirma.
- métricas realizadas fortes ignoram outcomes `source=uber_history_ocr`.
- `microphone=(self)` homologado para SpeechRecognition.
- MCP continua read-only.

### Gate 4 — Comercial
Estado: ✅ fechado.
Modelo:
- **Sr. Rotas Copiloto** = gratuito/permanente para contas não bloqueadas.
- **Sr. Rotas Inteligência** = R$ 9,90 / 30 dias.
- trial = 7 dias de Inteligência a partir da primeira oferta operacional válida.
- sem cobrança automática.
- Pergunte determinístico não usa créditos.
- tabelas/carteiras antigas de créditos ficam apenas como legado/auditoria.

Pix Banco Inter:
- Pix real de R$ 9,90 homologado.
- retorno bancário `CONCLUIDA`.
- assinatura ativada por 30 dias.
- checkout deve mostrar:
  - `Intermediações de Pagamentos BigCorps`
  - `Sr.Rotas | Desenvolvido por BigCorps`

### Gate 5 — Android / Radar Contextual
Estado: vc92 homologou satisfatoriamente o Reader em campo. Radar chegou a R3 com corrida/destino/ETA válidos e HTTP 200, mas zero POIs/oportunidades; cobertura pequena do catálogo será tratada separadamente. vc93 provou R4, currentRide, ETA/geocode, runtime e backend HTTP 200 com 1 POI/oportunidade; PendingIntent DEMO chegou à Activity, mas mapa ficou liberado/inativo. Navegação/mapa falhou na homologação; Reader não é a causa. vc94 homologou abertura da superfície/DEMO, mas falhou na cartografia e turnover; vc95 implementado, pendente de revisão Claude, Actions e campo.


**FIELD VC94 — 07/10/2026 — report operacional real**

Status encontrado: **ABERTO antes do vc95**.
1. Radar/Continuidade: superfície e Prévia DEMO abrem; header/cards/POIs/botões e espaço físico do mapa aparecem. Navegação/surface lifecycle melhoraram, mas MapLibre/cartografia permanece totalmente branco; renderização cartográfica segue aberta.
2. Corrida ativa: após currentRide/DOING_RIDE, ofertas seguintes perdem a seleção operacional normal e podem ficar em “OUTRA CORRIDA ATIVA”; a nova corrida não assume currentRide e prejudica o destino seguinte.
3. Reader: qualidade/leitura satisfatórias; perda após aproximadamente 1h30–2h. Reinício técnico existente não recuperou naquela ocorrência. Preservar interpretação/parser; atuar somente na resiliência MediaProjection/capture lifecycle.
4. Requisito operacional: botão direto no HUD solicita NOVA MediaProjection, reabre o seletor Android e preserva a mesma jornada, sem reutilizar silenciosamente token antigo.

Evidência JSON vc94: `surface_open_state=rendered`, panel attached/shown=true, stage=666×780, surface_render_completed=true, map_created_count=4/map_ready_count=4, mas cartografia branca. `map_ready` não prova frame cartográfico. Captura: pending_recovery=true, interruptions=21, projection_stopped_by_system=21, resume_requested=7/resume_success=7 e manual_recovery_unavailable. Sessão atual: 5043 OCRs concluídos, 0 failures; Reader não é causa de parser/gates.

vc95 — Field vc95 — Map render + Ride turnover + Fresh Projection Recovery:
- MapLibre observa style/loading/first frame/fully rendered/error. Fallback só desaparece após fully-rendered em superfície ativa com destino submetido; load tem timeout de 15 s a partir do attach; render tem 8 s somente após style+destino submetido. Timeouts mantêm explicação e são recuperáveis por fully-rendered tardio; erros reais continuam fatais. Atualizar recria o mapa pelo fluxo existente. MapLibre 13.6.1/OpenGL/OpenFreeMap/STYLE_URI preservados.
- Primeira seleção permanece em um toque. Troca no HUD exige TROCAR PARA ESTA CORRIDA → CONFIRMAR TROCA em até 7 s, somente em memória e vinculada à jornada/corrida atual/oferta. Coordinator nega replacement por padrão: notificação antiga não substitui currentRide. Seleção confirmada permite substituir corrida na mesma jornada ACTIVE, com anterior NOT_COMPLETED/replaced_by_new_ride e nova DOING_RIDE em transação; toque na própria corrida é idempotente. CORRIDA ATIVA, REALIZADA/NÃO REALIZADA e Radar ficam no card normal. Checkmark continua somente ReportSelection/relatório.
- Reiniciar captura no HUD M1 pede consentimento Android novo; force fresh projection desmonta a sessão antiga antes de usar resultData recém-autorizado. Jornada/histórico preservados; callbacks antigos não liberam/encerram a nova sessão. M2 não oferece o botão.
- Pós-review: fresh inválido preserva projection existente; sem sessão, registra fresh_projection_rejected_no_session e chama stopSelf, sem subir foreground artificialmente, inclusive jornada encerrada entre Activity e serviço. Diagnóstico mapa distingue load_timeout/render_timeout e map_late_render_recovered sem dados sensíveis.
- Recovery técnico ACTION_RECOVER continua usando a projection existente, sem seletor automático. Perda real mantém recovery pendente/journey aberta. Novos counters locais distinguem recovery técnico/fresh/stale callback.
- Semântica Reader congelada no baseline vc92 `1421f512d966101cc6bbd0dfda52cf0626a9c4dd`: 17 arquivos byte a byte; MediaProjectionOcrService é exceção somente lifecycle/consentimento/callback/diagnóstico. OCR/parser/gates/sampling/dedupe/M1 temporal preservados pelo guard estrito. Reader 2 shadow, Controlled Hybrid OFF, single-heavy-OCR.
- Nenhum auto-launch de mapa. Backend/Supabase/workflows/provider permanecem inalterados. Não houve deploy/Actions/merge. Implementação local não homologa o render GPU nem captura longa: revisão Claude, APK e campo continuam necessários.

vc94 — Radar Contextual Field 7:
- `0.33.17-field / versionCode 94`; Reader permanece byte a byte no baseline vc92 `1421f512d966101cc6bbd0dfda52cf0626a9c4dd` (guard v6 e manifesto JVM). Reader 2 shadow, Controlled Hybrid OFF, single-heavy-OCR.
- Um coordenador local da Activity recebe REAL/DEMO de Agora, HUD, Assistente e Field; consumo único após onPostResume, seleção da aba Radar, auxiliares ocultos e superfície anexada/visível/medida. No máximo 6 tentativas por abertura; nenhuma nova Activity ou persistência do pending.
- CTA Agora resolve current spec no clique. REAL revalida currentRide antes de abrir e renderizar. DEMO sobrevive a resume/refresh automático; saída explícita, estágio/Rollback, refresh operacional ou REAL abandonam DEMO.
- Resultado válido oculto fica armazenado para render pendente; geração/corrida continuam protegidas. Nenhum MapLibre em superfície hidden/zero-size; provider, URI OpenFreeMap e BAL preservados. Field diagnóstico recolhível com scroll limitado para reservar área ao mapa.
- DISCOVERY/REGION: ETA de -10 a +18 minutos (tolerância de chegada); STRONG permanece backend. REGION = uma vez por corrida/localOfferId, sem expiração após shown; duas preferences fixas e migração lazy da chave antiga da corrida atual. DISCOVERY conserva cooldown de 20 min por oportunidade; STRONG inalterado. REGION mantém ID nulo e metadata.variant=region.
- Diagnóstico apenas memória: origem/kind/estado/medidas/tentativas/defer/render, DEMO e contadores/erros/release do mapa fora do ring; eta_delta_seconds assinado, sem endereço/coordenadas/OCR/screenshot.
- PendingIntent chega à Activity; vc94 corrige lifecycle da superfície e ETA boundary. Nenhum mapa autoaberto pelo runtime: exige toque. Backend/Supabase/workflows/ranking/radius não alterados.
- Revisão Claude somente leitura antes de Actions; nenhum merge. Testes JVM/guards não substituem validação física.

vc93 — Radar Contextual Field 6:
- `0.33.16-field / versionCode 93`.
- **READER VC92 CONGELADO** no commit funcional `1421f512d966101cc6bbd0dfda52cf0626a9c4dd`; guard v5 compara conteúdo/inventário e teste JVM compara SHA-256. Nenhuma mudança em M1/OCR/parser/dedupe/admission/fórmulas, Reader 2 shadow, Controlled Hybrid OFF e single-heavy-OCR.
- Migração local única, somente Field vc93+: R3 com UI/runtime ativos promove assistente para R4; flags gerais continuam false. Mesmo sem promoção, migração é consumida; escolha manual e Rollback também a consomem e nunca são revertidos pelo próximo sync.
- STRONG/DISCOVERY conservam comportamento vc92. REGION, somente após resposta bem-sucedida da corrida atual, zero oportunidades e ETA 0–18 min: “Radar analisou sua região de chegada. Quer ver?” / “Ver região”. Sem oportunidade/POI sintético, ID de oportunidade nulo, cooldown de 20 min por corrida separado das oportunidades reais.
- HUD informa região analisada sem oportunidade e oferece VER REGIÃO DO DESTINO. Mapa abre somente após toque pelo launcher BAL existente, centralizado no destino mesmo sem marcadores, com empty state explícito e atualização manual.
- Diagnóstico inclui field_r4_migration_applied persistido, assistant_kind e region_signal_generated/rendered/view_clicked; não exporta destino textual, coordenadas, OCR ou screenshots.
- Backend/Supabase/ranking/radius/MapLibre provider/workflows/permissões permanecem iguais.
- Hardening pós-review: falha de WindowManager limpa host/estado visual; renderer exige host e card anexados antes de shown/cooldown. REGION envia metadata.variant=region mantendo ID nulo; diagnóstico separa decisão atual e último tipo renderizado e preserva seleção válida em decisões nulas. Versão vc93 mantida.
- Após commit/push: nova revisão Claude somente do patch, somente leitura; nenhum Actions nesta tarefa e nenhum merge. Homologação física de overlay, lifecycle, BAL e migração ainda necessária.

vc92 (Radar Contextual Field 5 sobre vc91):
- `0.33.15-field / versionCode 92`.
- STRONG preserva elegibilidade/headline/action do backend; DISCOVERY usa oportunidades existentes, texto neutro, ETA 0–18 min e cooldown de 20 min da mesma oportunidade.
- Nenhum mapa aberto automaticamente; callback atualiza somente HUD existente e motorista toca Ver para abrir Radar.
- Feedback Radar no HUD é vinculado à identidade currentRide; latest é limpo na troca de corrida.
- Prévia DEMO local perceptível: visibilidade, scroll ao topo, feedback Field e eventos demo_preview_opened/rendered, sem backend ou telemetria operacional.
- M1 temporal single-OCR recovery usa dois OCRs recebidos em memória por até 2 s: tarifa única igual, dimensões e posição de card estáveis, âncora explícita Uber, sem navegação/99 e dois pares complementares. Frames isolados ou conflitantes não formam candidato.
- Candidato passa por OfferParser/contexto/OfferIntegrityGate027033, dispatcher/admission/dedupe existentes; sem relaxar gates, fórmulas ou 99.
- Diagnóstico exporta contadores m1_temporal_* e uber_anchor_geometry_0/1/2plus, sem OCR bruto/endereço/coordenada/screenshot. recovered_offers conta recuperados prontos no Integrity Gate enviados ao dispatcher; admissão/dedupe ainda podem suprimi-los.
- Reader 2 continua shadow; Controlled Hybrid OFF; único OCR pesado; ShadowOfferRecovery027033 permanece desativado.
- Backend/migrations/workflows/provider MapLibre/permissões não alterados.

vc91 (Radar Contextual Field 4 sobre vc90):
- `0.33.14-field / versionCode 91`.
- crash de reentrada corrigido: View de loading nova a cada abertura.
- fetch UI protegido contra callback obsoleto por geração, currentRide.localOfferId e superfície visível; DEMO, idle, ocultação e detach invalidam consultas.
- MapLibre permanece lazy e releaseMap preservado.
- diagnóstico M1 separa `candidate_no_offer` (`blocked_offers=0`) de `integrity_reject` (`blocked_offers>0`), com contadores explícitos e reason original preservado.
- eventos legados sem contagem permanecem não classificados; métricas espaciais preservadas, sem OCR bruto/endereço/coordenada/screenshot.
- sem alteração funcional do Reader M1; Reader 2 continua shadow; Controlled Hybrid OFF.
- nenhum backend/migration alterado.

vc90 (Field 3 sobre vc89):
- `0.33.13-field / versionCode 90`.
- o motorista confirma explicitamente **ESTOU NESSA CORRIDA** no card da oferta; somente essa ação cria `DOING_RIDE`/`currentRide`.
- corrida ativa mostra **VER OPORTUNIDADES NO DESTINO** quando destino + ETA estão disponíveis.
- Runtime é acordado imediatamente após `markDoingRide` e o diagnóstico prova `ride_mark_requested → ride_mark_success → spec_resolved → backend_query`.
- Assistente real e DEMO abrem a Activity por `PendingIntent` com opt-in BAL para Android 14+/15+; diagnóstico prova `open_attempt → open_sent → intent_received`.
- MapLibre é lazy: nenhum `MapView` é criado no bootstrap do shell; ao sair da superfície Radar o mapa é destruído e `map_active=false`.
- Reader M1, router, Uber gate/spatial parser, OfferParser, OfferDeduplicator e backend permanecem idênticos ao vc89.
- nenhuma migration.

vc89 (Field 2 sobre vc88):
- `0.33.12-field / versionCode 89`.
- feature flags do Radar Contextual começam `false`.
- `RadarPanel027035` permanece como rollback.
- módulo contextual está ligado ao fluxo real, não apenas presente no repositório.
- destino da corrida é resolvido por:
  `currentRide.localOfferId → LocalStore → RideOffer.context`
- não voltar a usar `latestOffer` como pressuposto da corrida ativa.

Radar R1 backend homologado:
- 4 POIs.
- 4 aliases.
- 7 eventos ativos ligados.
- 4 `created`.
- 3 `linked` por identidade exata.
- 0 eventos ativos sem POI.
- `review` nunca grava `poi_id`.
- migrations Radar já estão aplicadas; NÃO reaplicar.

Teste Field do vc89:
1. instalar por cima da build atual; nunca limpar dados;
2. R0: validar Radar legado;
3. `Prévia DEMO`;
4. ativar `1 · UI`;
5. testar corrida real e CTA `Ver oportunidades no destino`;
6. ativar `2 · Runtime` somente após UI aprovada;
7. ativar `3 · Assistente` somente após Runtime aprovado;
8. validar `Assistente DEMO`;
9. usar `Rollback` a qualquer regressão;
10. conferir telemetria/diagnóstico antes de promover flags.

Correções P1 do primeiro Field:
- DEMO é estritamente uma prévia explícita e não sobrevive a refresh operacional, CTA real, troca de corrida ou mudança R2/R3/R4;
- mapa interno passa a usar MapLibre Native + OpenFreeMap, sem API key paga;
- POIs do backend Sr.Rotas são os marcadores do mapa; Google Maps/Waze ficam como navegação externa;
- painel Field mostra fonte `currentRide.localOfferId → LocalStore → RideOffer.context`, presença de lat/lng/ETA, última consulta, HTTP/backend, POIs, oportunidades, decisão do assistente e cooldown;
- JSON combinado exporta `radar_contextual_v1` sem endereço textual ou coordenadas exatas;
- gate R4: não avançar até R2/R3 provarem visualmente a identidade da corrida real.

Runtime:
- somente em corrida válida com destino resolvido;
- runtime não captura/OCR; vc92 somente atualiza o HUD existente;
- cadência contextual nunca mais frequente que 1/min;
- contexto externo pode alterar relevância/ranking, nunca fabricar `continuity_probability_pct`.

## 4. Access Resolver

Estados:
- `TRIAL_PENDING`
- `TRIAL_ACTIVE`
- `PAID_ACTIVE`
- `EXPIRED_READ_ONLY`
- `BLOCKED`

Configuração de homologação:
- `enforcement_mode=observe`
- `max_active_devices=2`
- `require_device_identity=false`

Contrato:
- Copiloto: `can_operate=true` e `can_history=true` para contas não bloqueadas.
- Inteligência: `can_analytics`, `can_ai`, `can_mcp` dependem de trial/assinatura quando enforcement for promovido.
- TRIAL_PENDING deve permitir a primeira oferta.
- fim do trial/assinatura nunca deve bloquear o Copiloto.
- não promover `observe → enforce` antes do teste de novo usuário ponta a ponta.

Device identity:
- `Settings.Secure.ANDROID_ID`.
- raw ID não é armazenado no servidor.
- HMAC SHA-256 server-side.
- fallback por instalação quando necessário.

## 5. V7 — congelado

Não reprocessar.

Referência normativa:
`docs/contracts/V7-DATA-CONTRACT-v1.0.md`

Métricas do lote consolidado:
- recebidas: 36.089
- fully-ready: 33.532
- partial: 2.557
- invalid: 0
- duplicate: 0
- temporal-ready: 35.996
- route-flow-ready: 35.452
- financial-ready: 34.129

O ownership foi consolidado para o driver canônico do tester sem reprocessar nenhuma oferta.

## 6. Pergunte / Estatísticas / Conciliação

Filtros Estatísticas:
- Ontem
- 7 dias
- 30 dias
- Personalizado

Seções:
- Resumo
- Produtividade
- Horários
- Regiões
- Corridas
- Oportunidades
- Custos

Conciliação:
- compara histórico Uber x ofertas Sr. Rotas;
- candidato pode usar tempo, valor, origem, destino, duração, sequência e delta;
- usuário confirma/nega;
- nunca auto-confirmar;
- downstream usa cobertura de conciliação e qualidade da evidência.

Semântica de oportunidades:
- oferta não concluída ≠ receita perdida garantida;
- previsões exibem amostra/confiança;
- nunca prometer corrida futura.

## 7. Contratos normativos preservados

Os únicos documentos técnicos permanentes devem estar em `docs/contracts/`:
- Capture Resilience
- MCP OAuth
- Radar ingest
- Reader 2 base/parallel/accumulator/consensus
- V7 Data Contract
- Radar Contextual POI / Contextual / Runtime Learning

Documentos de publicação/referência permitidos:
- `docs/DATA-SAFETY.md`
- `docs/PLAY-STORE.md`
- `docs/FIELD-SIGNING.md`
- `docs/ENVIRONMENT.md`
- `docs/DIAGNOSTICS.md`
- `docs/PARSER-REAL-WORLD-FIXTURES.md`

Todo o restante de handoffs, QA antigo, fases, manifests e instruções de ZIP é histórico do Git.

## 8. Supabase / segurança

Princípios:
- tabelas server-only podem ter RLS habilitado sem policy de anon/auth; não criar policy falsa apenas para silenciar advisor.
- funções `SECURITY DEFINER` expostas em `public` precisam ter `EXECUTE` revogado de `public/anon/authenticated` quando forem server-only.
- não executar migrations antigas apenas porque o nome de arquivo difere do versionamento gerado pelo MCP.
- não mass-delete backlog de screenshots.
- não reprocessar V7.

Pendência manual conhecida para Gate 6:
- confirmar `Leaked Password Protection` no Auth/Supabase.

## 9. Play Store — antes da publicação

Antes da RC:
- validar vc88 no tester;
- executar novo usuário ponta a ponta;
- validar offline → online;
- soak/bateria/sync/crash;
- zero P0/P1;
- promover Access Resolver de `observe` para `enforce` somente depois dos testes;
- Play Integrity soft/observe;
- Data Safety;
- declaração MediaProjection;
- foreground service;
- localização;
- Accessibility: produção NÃO usa Accessibility como leitor oficial de ofertas;
- assetlinks com SHA-256 da assinatura final;
- AAB final;
- política de privacidade, termos, suporte e exclusão de conta;
- instalação RC sobre Field sem desinstalar/limpar dados.

## 10. Teste obrigatório de NOVO USUÁRIO antes da Play

Executar instalação limpa com uma pessoa/conta nova e acompanhar banco:
1. instalar;
2. onboarding;
3. cadastro;
4. vincular primeiro aparelho;
5. verificar exatamente 1 `driver`;
6. primeira oferta;
7. trial de Inteligência inicia;
8. Reader/HUD/Copiloto;
9. Web;
10. Pix R$ 9,90;
11. confirmação Banco Inter;
12. PAID_ACTIVE;
13. Estatísticas/Pergunte;
14. MCP OAuth;
15. logout/login;
16. reinício;
17. segundo aparelho;
18. confirmar que ainda existe um único driver canônico.

Falha que crie segundo `driver` é P0 para publicação.

## 11. Regras que NÃO podem regredir

- M1 continua oficial.
- Reader2 continua shadow.
- Controlled Hybrid OFF.
- V7 não reprocessar.
- Money/fórmulas não reabrir sem regressão comprovada.
- `RadarPanel027035` fica como rollback enquanto Radar Contextual não for homologado.
- Radar flags default false.
- destino contextual = `currentRide.localOfferId → LocalStore → RideOffer.context`.
- `review` do POI Resolver nunca escreve `poi_id`.
- MCP nunca executa ações em corridas/mobilidade.
- reconciliação nunca auto-confirma.
- não expor OCR bruto/screenshots/coordenadas/tokens/device HMAC.
- não criar segundo sistema concorrente de HUD/WindowManager.
- não usar documentação histórica como requisito atual.

## 12. Regra documental para agentes

Toda mudança que altere versão, arquitetura, gate, feature flag, contrato comercial, schema/semântica ou estado de homologação deve atualizar **este arquivo no mesmo PR**.

Não criar novos arquivos na raiz com prefixos:
- `APLICAR-`
- `FASE-`
- `PHASE-`
- `QA-`
- `TESTE-`
- `VALIDACAO-`
- `VALIDATION_REPORT`
- `LEIA-PRIMEIRO`
- `LEIA_PRIMEIRO`
- `FIX_`
- `HANDOFF-`
- `ROADMAP-`
- `MANIFEST-`
- `PATCH-MANIFEST`
- `PATCH_MANIFEST`
- `SRROTAS-*-MANIFEST`

Não criar manifests de ZIP como documentação operacional.

Use:
- `README-CONTINUIDADE.md` para estado atual;
- `CHANGELOG.md` para histórico resumido;
- `docs/contracts/` somente para contratos normativos permanentes.

## 13. Próxima ação canônica

1. revisar somente o patch vc95 com Claude antes de Actions; depois validar render real/timeout/erros em aparelho, turnover A→B→C explícito e fresh MediaProjection na mesma jornada, inclusive callback stale e sessão longa. Semântica Reader vc92 permanece congelada; catálogo POI tratado em outra tarefa;
2. conferir telemetria Radar após uso real;
3. corrigir somente regressões demonstradas;
4. executar teste completo de novo usuário;
5. fechar Gate 6;
6. gerar RC/AAB e fechar Gate 7.

IMPLEMENTADO ≠ HOMOLOGADO.
CI VERDE ≠ TESTE DE CAMPO.
HEAD ≠ BUILD EM CAMPO.
