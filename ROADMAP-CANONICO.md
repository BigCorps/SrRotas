# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-26.7
DATA_CANÔNICA: 26/09/2026
CURRENT_HEAD_STAGE: 0.33.5-field / versionCode 82 + Web P5-02 Admin Ops V1
CURRENT_FIELD_TEST: 0.33.5-field / versionCode 82 — tester ativo
CURRENT_HEAD_BEFORE_P5_02: ee1ef53e4c9409c876fdc22fa141f4ac3e56165e
LAST_GREEN_ANDROID_CI_CONFIRMED: Action #130 — SUCCESS
ACTION_CURRENT_HEAD_BEFORE_P5_02: #131 — FAILURE somente no Architecture regression guard documental
WEB_PRODUCTION: P5-01 READY na Vercel, sem runtime errors na janela consultada
SOURCE_OF_TRUTH: código atual da main + Actions + JSON real de campo + Supabase atual + Vercel atual + este ROADMAP-CANONICO.md + README-CONTINUIDADE.md
STATUS: CANÔNICO PARA CONTINUIDADE

## 0. Regra de entrega
Todo ZIP técnico deve conter:
- ROADMAP-CANONICO.md atualizado;
- README-CONTINUIDADE.md atualizado;
- MANIFEST da entrega;
- arquivos completos modificados;
- LEIA-PRIMEIRO quando houver instruções.

O agente pode ler GitHub/Actions/Vercel/Supabase. Não escreve diretamente no GitHub. Supabase só recebe escrita por ferramenta mediante autorização explícita.

## 1. Gate novo para APK de campo
Decisão de 26/09/2026:
**não enviar uma nova APK apenas porque existe uma versão/build nova.**

A próxima APK só será enviada ao tester quando:
1. houver mudança Android nova, perceptível e com hipótese de validação clara;
2. o Admin Web estiver pronto para ser testado em conjunto;
3. CI estiver verde;
4. houver roteiro de teste objetivo.

Até lá, builds geradas automaticamente por commits Web não devem ser enviadas.

## 2. JSON real da 0.33.5
Arquivo de diagnóstico recebido em 26/09/2026:
- versionName 0.33.5-field;
- versionCode 82;
- Samsung SM-X626B / Android 16;
- MediaProjection ativa;
- captura realizou milhares de OCRs e 165 ofertas parseadas no diagnóstico;
- jornada estava ativa.

O bloco journey_metrics_sync_0333 mostrou:
- runs: 4;
- pending_metrics: 0;
- pending_energy: 0;
- metric_attempts: 0;
- energy_attempts: 0;
- core_sync_before_metrics: true.

Conclusão: a correção 0.33.3 não falhou; ela simplesmente não foi exercitada porque nenhum odômetro/energia entrou nessa jornada.
Supabase confirmou a jornada d1a1c866... com vehicle_metrics=0 e energy_entries=0.

A notificação 0.33.2 também ainda não foi exercitada na 0.33.5:
- posted_episodes=0;
- visible_requested=false;
- fresh_consent_required=true;
- silent_projection_restart=false.

O Assistente 0.33.5 não possui bloco de telemetria próprio no diagnóstico atual; portanto ausência visual não pode ser diferenciada com precisão entre "regra não disparou" e "UX não apareceu". Isso deve ser corrigido na próxima mudança Android significativa antes de novo APK.

## 3. Reader / captura
M1 continua oficial.
Reader2: Shadow → Parallel → Accumulator → Consensus → Controlled Hybrid → Primary → estabilização.
Controlled Hybrid OFF.
Histórico Android FROZEN.
Money Roles/Turbo congelado salvo regressão factual.

No JSON 0.33.5:
- Reader2 Parallel observou 10.508 frames;
- backend_effect=false;
- hud_effect=false;
- admission_influence=false;
- Consensus continuou telemetry-only;
- Money Shadow teve 165/165 correspondências de tarifa e 0 divergências.

Não promover Reader2 por esses dados.

## 4. V7
Batch canônico:
48323962-cabd-497b-890f-8315e0d0753a

Estado:
- 36.089 recebidos;
- 35.996 temporal-ready;
- 35.452 route-flow-ready;
- 34.129 financial-ready;
- 33.532 fully-ready;
- 0 invalid;
- 0 duplicate;
- canonical_for_intelligence=true;
- ownership resolvido.

Regras:
- ride_offers = operacional real;
- V7 = histórico analítico;
- V7 não prova aceite/conclusão/faturamento/lucro realizado;
- Base Coletiva não recebe V7 silenciosamente.

## 5. Web P5-01
P5-01 está em produção:
- /app — Início;
- /app/agora — inteligência histórica;
- /app/historico — jornadas/analytics;
- Vercel READY;
- sem runtime errors encontrados.

A Action #131 ficou vermelha por guard legado que ainda procura literalmente a antiga 0.33.2 no README. Não houve compile/test Android nessa Action porque o guard parou antes.

## 6. Web P5-02 — Admin Ops V1
Objetivo desta entrega:
Admin operacional real, inicialmente somente leitura, com acesso completo para exatamente dois administradores BigCorps.

Módulos:
- Visão geral;
- Motoristas;
- aparelhos;
- jornadas abertas/recentes;
- ofertas/outcomes agregados;
- odômetro/energia por motorista;
- V7 canônico e qualidade;
- trials;
- assinaturas;
- pagamentos;
- entitlements;
- créditos;
- sessões Web;
- MCP;
- notificações;
- uso de IA;
- saúde/privacidade.

Segurança:
- allowlist full-admin exata: contato@bigcorps.com.br + jadielalmeida@gmail.com;
- os dois recebem o mesmo acesso ao /admin;
- outros importadores autorizados continuam restritos a /admin/importacoes;
- Admin Ops não retorna raw_text de ofertas;
- não retorna coordenadas;
- nenhuma ação destrutiva habilitada no V1.

## 7. Supabase / hardening
Estado conhecido:
- RLS sem policy é majoritariamente backend/service-role only; não criar policies em massa;
- 5 helpers possuem search_path mutável;
- leaked password protection desabilitada;
- 10 FKs sem índice de cobertura;
- índices sem uso são INFO.

Nenhuma migration é necessária para P5-02.

## 8. CI documental
Action #131 falhou porque check-architecture.sh e Release033ContractTest ainda têm uma expectativa literal de 0.33.2 no README.

Para não misturar alteração de scripts Android com esta entrega Web, P5-02 mantém um marcador de compatibilidade CI explicitamente identificado como legado no README.
A limpeza do guard/teste será feita junto da próxima mudança Android significativa, antes da próxima APK enviada ao tester.

## 9. Prioridades
P0 — estabilidade de campo: 0.33.5 em observação.
P1 — odômetro/métricas flutuantes: ainda precisam ser realmente exercitados.
P2 — V7/Foundation: avançado.
P3 — Intelligence Analytics: QA.
P4 — Radar Contextual.
P5 — Web/Admin: P5-02 ativo.
P6 — hardening/release.

## 9.1. Paralelismo seguro
Módulos independentes podem avançar em paralelo desde que não quebrem os contratos congelados, a integridade dos dados nem os gates de homologação.

## 10. Regra final
IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
V7 PROCESSADO ≠ CORRIDA REALIZADA.
M2 NOVO ≠ M2 OFICIAL.

A próxima APK só será enviada quando houver algo novo e objetivo para o tester validar junto com o Admin Web.
