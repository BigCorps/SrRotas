# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-26.5
DATA_CANÔNICA: 26/09/2026
CURRENT_HEAD_STAGE: 0.33.5-field / versionCode 82 + Web P5-01
CURRENT_FIELD_TEST: 0.33.5-field / versionCode 82 — build consolidada enviada ao tester
CURRENT_HEAD_BEFORE_P5_01: 2c39373488362616380e09656178adb5944eaa98
LAST_GREEN_ANDROID_CI_CONFIRMED: Action #129 — 0.33.5-field / versionCode 82
WEB_SECURITY_BASELINE: Next.js 16.3.6 em produção
SOURCE_OF_TRUTH: código atual da main + Actions + JSON real de campo + Supabase atual + Vercel atual + este ROADMAP-CANONICO.md + README-CONTINUIDADE.md
STATUS: CANÔNICO PARA CONTINUIDADE

## 0. Regra documental
Todo ZIP técnico deve conter ROADMAP-CANONICO.md, README-CONTINUIDADE.md, manifest, arquivos completos modificados e LEIA-PRIMEIRO quando houver instrução específica. O agente pode ler GitHub/Actions/Vercel/Supabase; não escreve no GitHub. Supabase só recebe escrita mediante autorização explícita.

## 1. Missão
Progressão:
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.

Contrato patrimonial:
horário → embarque/origem → busca (tempo/km) → corrida (tempo/km) → destino.

Prioridade:
integridade de dados > estabilidade > contrato funcional > compatibilidade > UX > feature nova.

## 2. Android / campo
Build consolidada atual: 0.33.5-field / vc82.
Action #129: SUCCESS.
Inclui:
- Capture Continuity 0.33.2;
- Odometer Recovery 0.33.3;
- Floating Metrics Restore 0.33.4;
- Assistente Ativo UX 0.33.5.

O JSON final da 0.33.2 confirmou captura ativa, 24 ofertas locais, sync principal sem filas de ofertas/contextos/outcomes, crash observability funcional e storage hotfix preservado. A notificação 0.33.2 ainda precisa de um episódio controlado/real de interrupção para homologação visual. O tester já recebeu a 0.33.5.

Próximo JSON de campo deve validar:
- notificação de recovery e novo consentimento;
- same-journey resume;
- journey_vehicle_metrics / energia após 0.33.3;
- métricas flutuantes 0.33.4;
- IGNORAR | VER do Assistente 0.33.5;
- regressões de Reader/captura/storage.

## 3. Reader
M1 continua oficial.
Reader 2: Shadow → Parallel → Accumulator → Consensus → Controlled Hybrid → Primary → estabilização.
Controlled Hybrid permanece OFF. Não promover por intuição.
Money Roles / Turbo permanece congelado salvo regressão factual.
Histórico Android permanece FROZEN.

## 4. V7
Batch canônico: 48323962-cabd-497b-890f-8315e0d0753a.
Schema: srrotas-historical-offer-v1.
Extractor: V7.5.
Status live confirmado:
- 36.089 recebidos;
- 35.996 temporal-ready;
- 35.452 route-flow-ready;
- 34.129 financial-ready;
- 33.532 fully-ready;
- 0 invalid;
- 0 duplicate;
- canonical_for_intelligence=true;
- ownership resolvido.

Regras permanentes:
- ride_offers = operacional real;
- V7 = histórico analítico de ofertas observadas;
- V7 não prova aceite, conclusão, faturamento ou lucro realizado;
- Base Coletiva não recebe V7 silenciosamente.

Isolamento confirmado:
- lote V7 pertence ao driver 267c61ce-7d2c-4171-9ba2-226e3b61b923;
- nessa conta, sr_personal_offer_canonical_v1 retorna 35.996 historical_v7 + 32 operacionais;
- o driver do tester não recebe esse V7.

## 5. Intelligence QA
Views canônicas:
- sr_personal_offer_canonical_v1;
- sr_personal_offer_region_hour_v1.

Para o proprietário do V7, a view canônica mistura corretamente histórico e operacional, mantendo journey_id/outcomes somente no operacional.
A inteligência regional possui milhares de agregações reais do histórico canônico.

Próximos gates:
- sample size/confidence;
- fallback explícito;
- melhores horários/regiões;
- categorias;
- destinos/continuidade;
- explicabilidade;
- evitar precisão artificial.

## 6. Web / P5
Auditoria factual confirmou APIs existentes para:
- analytics;
- journeys;
- Agora / now-intelligence;
- destination continuity;
- account/devices;
- billing;
- MCP;
- preferences;
- messages;
- privacy/exclusão;
- importação histórica administrativa.

Antes de P5-01, /app, /app/agora e /app/historico eram redirects ou não expunham o produto.
P5-01 implementa a primeira superfície Web funcional:
- Início;
- Agora;
- Histórico;
- navegação principal com 5 destinos;
- reaproveitamento integral das APIs atuais;
- nenhuma migration;
- nenhuma nova regra de inteligência.

Próximos blocos Web:
P5-02 Configurações + IA;
P5-03 Admin operacional;
P5-04 Billing/entitlements/admin financeiro;
P5-05 privacidade/auditoria/saúde operacional.

1.0 não deve ser promovida sem Admin Web mínimo operacional.

## 7. Segurança / hardening
Next.js:
- 16.3.0 foi atualizado para 16.3.6 em 26/09/2026;
- deploy Vercel do commit 2c393734... ficou READY;
- nenhum erro de runtime foi encontrado após o deploy consultado.

Supabase:
- 37 tabelas possuem RLS habilitado sem policies: modelo atual é backend/service-role only e não deve receber policies em massa;
- views consultadas não possuem grants para anon/authenticated;
- SECURITY DEFINER sensíveis não estão expostas a anon/authenticated;
- 5 helpers de texto/região possuem search_path mutável e devem receber hardening em migration separada;
- leaked password protection permanece desabilitada;
- 10 FKs sem índice de cobertura devem ser avaliadas no P6;
- índices sem uso são INFO e não devem ser removidos cegamente.

## 8. Autenticação
Confirmado no código:
- device/MCP tokens persistidos por hash;
- cookies Web HttpOnly + Secure;
- admin import usa SameSite=Strict;
- login/registro/exclusão com rate limit;
- tokens MCP limitados por driver e retornados em claro apenas na criação.

## 9. Vercel
Projeto sr-rotas.
Produção do hotfix Next 16.3.6: READY.
Sem runtime errors na janela consultada após o deploy.

## 10. Prioridades
P0 — estabilidade de campo: em homologação na 0.33.5.
P1 — regressões reais: odômetro/métricas flutuantes em homologação.
P2 — V7 + Foundation: avançado.
P3 — Intelligence Analytics: QA + superfícies.
P4 — Radar Contextual.
P5 — Web/Admin: ativo; P5-01 é esta entrega.
P6 — hardening/release.

## 11. OpenAI
Evitar chamadas OpenAI para ranking, probabilidade, thresholds ou lógica resolvível deterministicamente. IA generativa permanece uma função opt-in/com créditos; MCP e analytics não dependem dela.

## 12. Regra final
IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
V7 PROCESSADO ≠ CORRIDA REALIZADA.
M2 MAIS NOVO ≠ M2 OFICIAL.

Ordem da verdade:
1. código atual da main;
2. Action;
3. JSON real de campo;
4. Supabase;
5. Vercel;
6. ROADMAP;
7. README;
8. documentação histórica.
