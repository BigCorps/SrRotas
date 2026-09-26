# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-26.4
DATA_CANÔNICA: 26/09/2026
CURRENT_HEAD_STAGE: 0.33.5-field / versionCode 82 — Assistente Ativo UX / CI2
CURRENT_FIELD_TEST: 0.33.2-field / versionCode 79 — Capture Continuity
CURRENT_HEAD_COMMIT_BEFORE_CI2: c00eba3f2df7ab4e295539b6b7c350ecbdbffe30
LAST_GREEN_ANDROID_CI_CONFIRMED: Action #126 — 0.33.4-field / versionCode 81
ACTION_CURRENT_HEAD_BEFORE_CI2: #128 — FAILURE em compileDebugKotlin dentro do step Unit tests
SOURCE_OF_TRUTH: código atual da main + Action atual + JSON real de campo + Supabase atual + este ROADMAP-CANONICO.md + README-CONTINUIDADE.md
STATUS: CANÔNICO PARA CONTINUIDADE DO DESENVOLVIMENTO

---

## 0. REGRA DOCUMENTAL OBRIGATÓRIA

Todo ZIP técnico do Sr. Rotas deve conter:
- `ROADMAP-CANONICO.md` atualizado;
- `README-CONTINUIDADE.md` atualizado;
- `MANIFEST-<VERSAO>.json`;
- arquivos completos modificados em caminhos relativos;
- `LEIA-PRIMEIRO-<VERSAO>.md` quando houver instrução específica.

O agente pode ler GitHub, Actions, Vercel e Supabase para diagnóstico. Não deve escrever diretamente no GitHub. O usuário sobe manualmente na `main`. Supabase só recebe escrita mediante autorização explícita.

---

## 1. MISSÃO E PRINCÍPIO CENTRAL

Progressão:

**ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR**

Contrato patrimonial:

**horário → embarque/origem → busca (tempo/km) → corrida (tempo/km) → destino**

Pipeline:

**captura → dados estruturados → decisão → corrida confirmada → jornada → histórico → modelo pessoal → coletivo opcional → predição → recomendação**

Prioridade:
**integridade de dados > estabilidade > contrato funcional > compatibilidade > UX > feature nova**.

Módulos independentes podem avançar em paralelo se não compartilham mutação crítica.

---

## 2. ESTADO ATUAL DA MAIN / ANDROID CI

Antes do CI2:
- HEAD: `c00eba3f2df7ab4e295539b6b7c350ecbdbffe30`;
- `versionName = "0.33.5-field"`;
- `versionCode = 82`;
- Architecture Guard da Action #128: **SUCCESS**;
- step `Unit tests`: **FAILURE**;
- APK debug/release e uploads: **SKIPPED**.

A análise do log da Action #128 mostrou que o step não chegou às asserções JUnit. O Gradle parou em:

`app/src/main/java/com/bigcorps/driveraimvp/ActiveAssistantPolish0265.kt:171:44 Unresolved reference 'line'`

Causa factual:
- `ActiveAssistantPolish0265.kt` usa `SrUi023.palette(context)`;
- `SrUi023.Palette` possui `outline`, não `line`;
- `p.line` é inválido nesse tipo.

Correção CI2:
- alterar somente `p.line` → `p.outline` no contorno do botão secundário;
- preservar todo o restante do runtime 0.33.5;
- não fazer bump de versão.

Depois dessa correção, havia ainda um teste legado garantidamente obsoleto:
`Release033ContractTest.roadmapAllowsParallelProductTrack()`, que exigia literalmente textos da antiga 0.33.0.

O CI2 substitui esse teste por validação do contrato canônico atual:
- `CANONICAL_VERSION`;
- `CURRENT_HEAD_STAGE`;
- `0.33.5-field / versionCode 82`;
- módulos independentes em paralelo;
- referência ao `README-CONTINUIDADE.md`;
- separação entre HEAD e APK em campo.

---

## 3. APK REALMENTE EM CAMPO

`0.33.2-field / versionCode 79`

Commit:
`9fa6571c0eb319db7b3b82759fa98bfbf60d7d58`

Action:
`#124 — SUCCESS`

Objetivo:
- validar Capture Continuity;
- verificar perda de MediaProjection;
- garantir aviso visível `Sr. Rotas — leitura pausada`;
- ação `Retomar captura`;
- novo consentimento Android continua obrigatório;
- mesma jornada deve ser preservada;
- validar crash observability.

**Aguardar o JSON 3 do irmão.**

Não pedir anotações enquanto dirige.

---

## 4. BUILDS VERDES AINDA NÃO ENVIADAS AO IRMÃO

### 0.33.3-field / vc80
Commit `cca1888ce46bcd3a1380c20ee0d7ee3e5c578259`
Action #125 — SUCCESS

Escopo: Odometer Sync Recovery.
- sync core garante a jornada antes de métricas;
- pendências antigas podem ser recuperadas;
- chamadas concorrentes são coalescidas;
- telemetria `journey_metrics_sync_0333`.

### 0.33.4-field / vc81
Commit `7161902957cf6903dd0ac26f748da8de31bc228c`
Action #126 — SUCCESS

Escopo: Floating Metrics Restore.
- R$/km;
- R$/min;
- R$/h;
- km;
- minutos;
- lucro estimado;
- demais métricas habilitadas quando disponíveis.

Reutiliza `HudMetricEvaluation0221`; não cria outro motor financeiro.

---

## 5. 0.33.5 — ASSISTENTE ATIVO UX

O Assistente Ativo já existia; 0.33.5 não reconstrói o motor.

Contrato:
- balão pequeno ancorado ao mascote/janela;
- texto curto;
- `IGNORAR | VER`;
- `VER` abre Agora;
- `IGNORAR` fecha;
- toque fora somente fecha;
- toque fora não marca “estou em corrida”;
- duração configurada controla timeout;
- remover supressão silenciosa legada;
- sem LLM para decidir deslocamento;
- Reader, captura, Histórico, V7, backend e Supabase preservados.

Arquivos centrais:
- `ActiveAssistant026.kt`;
- `ActiveAssistantPolish0265.kt`;
- `ActiveAssistant0335ContractTest.kt`.

CI2 corrige somente o símbolo de paleta que impedia compilação e um teste documental obsoleto. Não muda regra de negócio.

---

## 6. DEPOIS QUE 0.33.5 FICAR VERDE

**Não enviar automaticamente ao irmão.**

Primeiro analisar o JSON 3 da 0.33.2:
1. `capture_resilience_0311`;
2. `capture_recovery_notification_0332`;
3. `crash_observability_0332`;
4. OCR/M1;
5. Reader2;
6. Money Roles/Turbo;
7. Screenshot Storage;
8. crashes/restarts;
9. captura silenciosamente parada;
10. fechamento do P0.

Se P0 estiver aprovado, a 0.33.5 verde será a primeira candidata consolidada contendo:
- Capture Continuity 0.33.2;
- Odometer Recovery 0.33.3;
- Floating Metrics 0.33.4;
- Assistente UX 0.33.5.

Só então dizer explicitamente:

**“Agora pode enviar este APK ao seu irmão.”**

---

## 7. SCREENSHOT STORAGE / CRASH OBSERVABILITY

Não reintroduzir poda massiva de MediaStore no caminho quente OCR/captura.

Estado conhecido:
- private cap 30;
- JPEG 72;
- dedupe preservado;
- automatic visible prune desligado;
- backlog legado pode existir.

Crash observability 0.33.2 deve exportar somente diagnóstico sanitizado:
- classe;
- mensagem limitada;
- stack limitado;
- thread;
- versão;
- idade.

Não incluir OCR bruto, screenshot, endereço ou coordenadas.

---

## 8. READER

M1 continua Reader oficial.

Reader 2:
**Shadow → Parallel → Accumulator → Consensus → Controlled Hybrid → Primary → estabilização**

Estado:
- Parallel implementado;
- Accumulator implementado;
- Consensus implementado;
- Controlled Hybrid OFF;
- Primary pendente.

Não promover por intuição. A 1.0 exige Reader confiável; não exige M2 Primary obrigatoriamente.

---

## 9. MONEY ROLES / TURBO MAIS

Estado: **CONCLUÍDO / CONGELADO COM REGRESSÃO PERMANENTE**.

`MoneyRoleResolver030` distingue tarifa principal, R$/km anunciado, promoção/bônus, valor secundário e desconhecido.

Não reabrir sem regressão real.

---

## 10. HISTÓRICO

**FROZEN.**

Não redesenhar/refatorar sem regressão factual.
Não acoplar Reader experimental.
Oferta → screenshot é gap separado.

---

## 11. V7 — BASE HISTÓRICA

O lote histórico já foi processado. Não propor novo OCR/reconversão/reprocessamento.

Batch canônico:
`48323962-cabd-497b-890f-8315e0d0753a`

Schema:
`srrotas-historical-offer-v1`

Extractor:
`V7.5`

Conferência live em 26/09/2026:
- status: `ready`;
- received/rows: 36.089;
- fully ready: 33.532;
- partial: 2.557;
- invalid: 0;
- duplicate: 0;
- temporal-ready: 35.996;
- route-flow-ready: 35.452;
- financial-ready: 34.129;
- `canonical_for_intelligence = true`;
- ownership canônico resolvido.

Regras permanentes:
- `ride_offers` = operacional real;
- V7 = contrato analítico;
- V7 representa oferta histórica observada, nunca corrida aceita/concluída;
- não repopular `ride_offers` com histórico sintético;
- Base Coletiva não recebe V7 silenciosamente.

Views canônicas confirmadas:
- `sr_personal_offer_canonical_v1`;
- `sr_personal_offer_region_hour_v1`.

---

## 12. INTELLIGENCE FOUNDATION

Já consome V7:
- seed temporal/geográfica;
- Base Pessoal;
- Agora;
- continuidade de destino;
- Estatísticas;
- melhores horários;
- contexto analítico da IA.

Próximo foco:
- QA da inteligência;
- confiança/amostra;
- probabilidade;
- recomendação;
- destinos;
- comportamento temporal/geográfico;
- explicabilidade.

Não derivar de V7 sem base factual:
- lucro realizado;
- taxa de conclusão;
- aceitação;
- outcome.

---

## 13. PRIORIDADES P0–P6

### P0 — ESTABILIDADE
Long-run, captura, Reader oficial, persistência, recovery, dedupe, storage, memória, bateria/temperatura, atualização sem perda, celular/tablet/split-screen e observabilidade.

P0 atual depende do JSON 3 da 0.33.2.

### P1 — REGRESSÕES REAIS
Odômetro e janela flutuante estão corrigidos nas 0.33.3/0.33.4 e aguardam futura homologação.

### P2 — V7 + FUNDAÇÃO DA INTELIGÊNCIA
Estado avançado. Falta validar no produto, confiança/amostra, destinos, continuidade, comportamento horário/dia/região/categoria e probabilidades sem misturar oferta observada com corrida realizada.

### P3 — INTELIGÊNCIA ANALÍTICA
Comparativos, gráficos/evolução, IA algorítmica, metas, projeções, custo de oportunidade, Replay Inteligente, recomendação, melhores regiões/horários e heatmap próprio quando suportado pelos dados.

### P4 — RADAR CONTEXTUAL
Eventos, polos, hospitais, hotéis, rodoviárias, aeroportos, centros de eventos, shoppings, contexto temporal, lugares marcados, fonte/evidência/timestamp e integração com Agora.

### P5 — COMERCIAL + ADMIN WEB
Trial, planos, entitlement, assinatura, usuários, sessões, financeiro, dados, privacidade/LGPD, suporte, termos, exclusão, operação administrativa, status V7 e saúde operacional.

**1.0 não deve ser promovida sem Admin Web mínimo operacional.**

### P6 — HARDENING / RELEASE
Long-run, multiaparelho, upgrade, rollback, observabilidade, permissões, onboarding, LGPD, Data Safety, Play Store, RC e 1.0.

---

## 14. AUDITORIA LIVE DE AMBIENTES — 26/09/2026

### GitHub
- `main` em `c00eba3f...` antes do CI2;
- Action #128 confirmou Architecture Guard verde;
- falha real: `compileDebugKotlin`, símbolo `p.line` inexistente;
- nenhum APK da 0.33.5 foi produzido pela #128.

### Vercel
- projeto `sr-rotas`;
- deploy de produção do commit `c00eba3f...`: `READY`;
- nenhum erro de runtime encontrado na janela de 24h consultada.

### Supabase
Projeto `gheymrttmfdxnjdbgvgl`:
- status `ACTIVE_HEALTHY`;
- Postgres 17;
- V7 canônico conferido;
- dados operacionais continuam separados do histórico V7.

Advisors atuais para futura frente Web/Admin/hardening:
- 37 tabelas com RLS habilitado e sem policies;
- 5 funções com `search_path` mutável;
- proteção de senha vazada desabilitada;
- 10 foreign keys sem índice de cobertura;
- índices sem uso reportados como INFO.

Esses avisos devem ser classificados antes de qualquer mudança. Tabelas RLS sem policy podem ser intencionalmente backend-only; não alterar em massa.

---

## 15. PRÓXIMO BLOCO INDEPENDENTE APÓS CI VERDE

**Web/Admin factual audit + Intelligence QA/hardening**

Essas frentes podem avançar sem esperar M2 Primary.

Abordagem Admin:
1. auditar o que já existe;
2. mapear users/devices/sessions/journeys/V7/offers/analytics/trial/plans/billing/entitlements/privacy/admin auth;
3. classificar existente/parcial/ausente/placeholder;
4. implementar somente gaps.

Abordagem Intelligence:
- Base Pessoal;
- Agora;
- continuidade;
- melhor horário;
- regiões;
- categorias;
- destinos;
- tamanho de amostra;
- confidence;
- fallback;
- V7 vs operacional;
- explicabilidade.

---

## 16. REGRAS DE CORREÇÃO DE CI

Quando Action falhar:
1. descobrir o step e a falha real;
2. distinguir guard/test/compile/package/signing;
3. corrigir o menor escopo;
4. não fazer bump de versão para correção exclusiva de CI/compile sem mudança funcional;
5. atualizar Roadmap + README;
6. manter rollback simples;
7. ZIP sempre inclui docs.

No caso da #128:
- o nome do step era `Unit tests`;
- a falha real ocorreu em `:app:compileDebugKotlin`;
- o teste legado ainda precisa ser corrigido porque seria o próximo bloqueio.

---

## 17. OPENAI/API/LLM

Evitar novas chamadas pagas de OpenAI para funções resolvíveis algoritmicamente.

Para probabilidade, threshold, ranking e contexto temporal-geográfico, preferir inteligência algorítmica explicável quando possível.

---

## 18. REGRA FINAL / FONTE DE VERDADE

**IMPLEMENTADO ≠ HOMOLOGADO.**
**HEAD ≠ BUILD DE CAMPO.**
**CI VERDE ≠ TESTE DE CAMPO APROVADO.**
**V7 PROCESSADO ≠ CORRIDA REALIZADA.**
**M2 MAIS NOVO ≠ M2 PRONTO PARA SER OFICIAL.**

Ordem da fonte de verdade:
1. código atual da `main`;
2. Action atual;
3. JSON real de campo;
4. Supabase atual;
5. `ROADMAP-CANONICO.md`;
6. `README-CONTINUIDADE.md`;
7. auditoria técnica;
8. documentação histórica antiga.

Se documentação antiga divergir do código atual, investigar antes de alterar.
