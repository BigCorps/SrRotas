# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-26.3
DATA_CANÔNICA: 26/09/2026
CURRENT_HEAD_STAGE: 0.33.5-field / versionCode 82 — Assistente Ativo UX
CURRENT_FIELD_TEST: 0.33.2-field / versionCode 79 — Capture Continuity
HEAD_COMMIT_UPLOAD: 26ae4e3a8a564bf9d9609e546678927ee229fc15
LAST_GREEN_CI_CONFIRMED: Action #126 — 0.33.4-field / versionCode 81
ACTION_CURRENT_HEAD: #127 — FAILED no Architecture Guard por regra documental obsoleta; CI1 corrige o guard
SOURCE_OF_TRUTH: ROADMAP-CANONICO.md + README-CONTINUIDADE.md + código/build real + evidência de campo
STATUS: CANÔNICO PARA CONTINUIDADE DO DESENVOLVIMENTO

---

## 0. REGRA DOCUMENTAL OBRIGATÓRIA

A partir desta versão, TODO ZIP técnico do Sr. Rotas deve conter, no mínimo:

1. `ROADMAP-CANONICO.md` atualizado;
2. `README-CONTINUIDADE.md` atualizado;
3. `MANIFEST-<VERSAO>.json`;
4. arquivos completos modificados em caminhos relativos ao repositório;
5. LEIA-PRIMEIRO da entrega quando houver instrução específica.

Nenhum patch deve avançar sem atualizar os dois documentos canônicos acima.

Objetivo: permitir que outra instância/agente continue o projeto sem depender desta conversa.

---

## 1. MISSÃO

O Sr. Rotas já possui grande parte da estrutura funcional planejada.

A progressão de produto é:

**ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR**

Não reconstruir capacidades existentes apenas porque documentação antiga as apresentava como futuras.

---

## 2. PRINCÍPIO CENTRAL E PATRIMÔNIO

Contrato prioritário:

**horário → embarque/origem → busca (tempo/km) → corrida (tempo/km) → destino**

Pipeline de produto:

**captura → dados estruturados → decisão → corrida confirmada → jornada → histórico → modelo pessoal → coletivo opcional → predição → recomendação**

Tarifa e métricas financeiras apoiam a decisão, mas não substituem o patrimônio temporal/geográfico.

---

## 3. REGRAS DE PRESERVAÇÃO

- ATIVO + HOMOLOGADO: congelar.
- ATIVO + NÃO HOMOLOGADO: testar antes de alterar.
- PARCIAL: completar somente o gap.
- REGRESSÃO: restaurar o contrato correto.
- INATIVO: reativar somente após decisão explícita.
- NÃO LOCALIZADO: investigar antes de implementar.
- PLACEHOLDER: não completar automaticamente.
- Módulos independentes podem avançar em paralelo se não compartilham mutação crítica.
- Toda mudança deve ter rollback lógico e evitar efeitos silenciosos em outro módulo.

---

## 4. FOTOGRAFIA DA AUDITORIA A01–M08

Auditoria técnica consolidada:

- 172 capacidades auditadas;
- 134 `IMPLEMENTADO_ATIVO`;
- 28 `PARCIAL`;
- 2 `REGRESSÃO`;
- 3 `NÃO_LOCALIZADO`;
- 1 `IMPLEMENTADO_INATIVO`;
- 3 `FORA_DE_ESCOPO_ATUAL`;
- 1 `STUB_PLACEHOLDER`.

A matriz A01–M08 permanece inventário técnico detalhado. Este Roadmap é a sequência de continuidade.

---

## 5. ESTADO FACTUAL ATUAL

| Área | Estado canônico | Observação / próximo gate |
|---|---|---|
| Shell / navegação | IMPLEMENTADO / ESTÁVEL | congelado |
| Histórico Android | ESTÁVEL / FROZEN | não reabrir sem regressão factual |
| M1 | READER OFICIAL | mantém rollback |
| Integridade/admissão 0.28–0.30 | CONCLUÍDO | regressão permanente |
| Money Roles / Turbo Mais | CONCLUÍDO | 710/710 concordâncias no JSON 0.32 |
| Reader 2 Parallel | CONCLUÍDO SHADOW | sem efeito oficial |
| Reader 2 Accumulator | VALIDADO / SUPORTE | não promove sozinho |
| Reader 2 Consensus | EM SHADOW | Controlled Hybrid ainda desligado |
| Controlled Hybrid | PENDENTE CONDICIONAL | somente com evidência de campo |
| Reader 2 Primary | PENDENTE | depois de Hybrid + multiaparelho |
| Capture Resilience base | IMPLEMENTADO | mesma jornada preservada |
| Capture Continuity 0.33.2 | EM CAMPO | irmão/testador usando; aguardar JSON 3 |
| Crash observability 0.33.2 | IMPLEMENTADO | validar no JSON de campo |
| Screenshot Storage Guard | IMPLEMENTADO | manter monitoramento |
| Responsividade | IMPLEMENTADA / NÃO TOTALMENTE HOMOLOGADA | validar layouts reais |
| Dark Mode | PARCIAL / AUDITAR | não declarar concluído sem QA visual |
| Jornada compacta | IMPLEMENTADA | manter |
| Odômetro/energia 0.33.3 | CORREÇÃO IMPLEMENTADA / CI VERDE | Action #125; ainda não enviada a campo |
| Janela flutuante métricas 0.33.4 | CORREÇÃO IMPLEMENTADA / CI VERDE | Action #126; pronta para próxima consolidação de campo após JSON 3 |
| V7 processamento | CONCLUÍDO | ~40 mil screenshots já convertidos |
| V7 backend | CANÔNICO / ATIVO | legacy isolado |
| Base Pessoal → V7 | ATIVO | operacional + V7 do mesmo driver |
| Agora → V7 | ATIVO | via views corrigidas |
| Continuidade de destino → V7 | ATIVO | seed histórica como fallback explicável |
| Estatísticas / melhores horários → V7 | PATCH IMPLEMENTADO | backend usa fonte canônica analítica |
| IA analítica → V7 | PATCH IMPLEMENTADO | diferencia histórico de operacional |
| Base Coletiva | PRESERVADA | somente dados reais + opt-in |
| Radar contextual | PENDENTE / PARCIAL | P4 |
| Comercial/Admin web | PENDENTE | P5 |
| Release Hardening | PENDENTE | P6 |
| Play Store 1.0 | PENDENTE | gate final |

---

## 6. BUILD / CAMPO — NÃO CONFUNDIR

### 0.33.2-field / vc79
**É o APK atualmente enviado ao irmão/testador.**

Objetivo:
- validar Capture Continuity;
- confirmar aviso persistente quando MediaProjection cai;
- retomar com novo consentimento Android;
- validar crash observability.

Teste:
- uso normal;
- sem anotações durante direção;
- se captura cair, tocar `Retomar captura` quando seguro;
- ao fim, enviar JSON de diagnóstico.

### 0.33.3-field / vc80
**Não enviada ao irmão.**

Estado:
- commit `cca1888ce46bcd3a1380c20ee0d7ee3e5c578259`;
- Action #125 verde.

Correção:
- odômetro/energia espera o `SyncCoordinator` garantir a jornada;
- pendências antigas podem se recuperar;
- sync concorrente coalescido;
- diagnóstico `journey_metrics_sync_0333`.

### 0.33.4-field / vc81
**Não enviar ao irmão enquanto o JSON 0.33.2 não for analisado.**

Correção:
- restaura pílulas de métricas da janela flutuante;
- reutiliza `HudMetricEvaluation0221`;
- não cria cálculo financeiro paralelo;
- Reader, Histórico, V7 e persistência não mudam.

---

## 7. P0 — ESTABILIDADE

Prioridades obrigatórias:

- long-run;
- captura;
- Reader oficial;
- persistência;
- recovery;
- deduplicação;
- storage;
- memória;
- bateria/temperatura;
- atualização sem perda de dados;
- celular/tablet/split-screen;
- observabilidade de crashes e interrupções.

Estado atual:
- 0.33.2 está em validação real.
- Não consolidar 0.33.3/0.33.4 em novo APK de campo até analisar o JSON 3, salvo regressão bloqueante separada.

Gate P0:
- captura não pode morrer silenciosamente;
- se Android encerrar MediaProjection, usuário precisa perceber e conseguir retomar;
- mesma jornada deve ser preservada;
- nenhum restart global desnecessário;
- JSON suficiente para diagnosticar falhas.

---

## 8. P1 — REGRESSÕES REAIS

### Odômetro
Causa raiz encontrada:
- métrica podia ser salva localmente antes da jornada existir no backend;
- primeira tentativa retornava `journey_not_found`;
- o core depois criava a jornada, mas não retornava à fila de métricas.

Correção 0.33.3:
- `JourneyMetricsClient026.syncPending()` chama `SyncCoordinator.sync()` primeiro;
- envia odômetro/energia só depois do callback core;
- startup recupera pendências antigas;
- `SyncCoordinator` não foi reimplementado.

Próximo gate:
- testar campo;
- `pending_metrics=0` após sync;
- confirmar linhas em `journey_vehicle_metrics`.

### Janela flutuante
Regressão factual:
- métricas existiam no modelo e em “Mais detalhes”;
- primeiro nível expandido mostrava essencialmente Busca + Destino;
- pílulas financeiras/percurso desapareceram da decisão rápida.

Correção 0.33.4:
- restaurar R$/km, R$/min, R$/h, km, min, lucro estimado;
- respeitar enabled metrics/ordem/thresholds existentes;
- usar `HudMetricEvaluation0221`;
- preservar Busca, Destino, Combinado, continuidade e controles.

Próximo gate:
- CI verde — Action #126;
- depois validação visual em campo/build consolidada.

---

## 9. V7 / DATA — ESTADO CANÔNICO

O processamento dos ~40 mil screenshots está CONCLUÍDO.

Lote canônico:
- batch `48323962-cabd-497b-890f-8315e0d0753a`;
- extractor `V7.5`;
- 36.089 registros recebidos;
- 33.532 fully-ready;
- 35.996 temporal-ready;
- 35.452 route-flow-ready;
- 34.129 financial-ready.

Correções já aplicadas no Supabase:
- lote V7 marcado `ready` e canônico;
- 9 batches legacy arquivados;
- seed regional restringida a V7 canônico;
- 4.459 grupos / 35.957 amostras V7-only na seed;
- ownership V7 resolvido para o driver correto;
- Base Pessoal agrega V7 + operacional real;
- `sr_personal_offer_canonical_v1` criada;
- 38.756 registros sintéticos legacy removidos de `ride_offers`;
- staging legacy preservado para auditoria/rollback;
- nenhum outcome/import Uber dependia desses 38.756 registros.

Regra permanente:
- não misturar legacy inferior com V7 em inteligência;
- `ride_offers` permanece fonte operacional real;
- V7 entra por contrato analítico canônico;
- Base Coletiva não recebe V7.

Progressão:
**V7 canônico → agregações temporal/geográficas → Base Pessoal → Agora → continuidade → Estatísticas/IA → probabilidade/recomendação**

---

## 10. INTELLIGENCE FOUNDATION

Já conectado:
- Base Pessoal;
- inteligência temporal/geográfica regional;
- Agora;
- seed histórica;
- continuidade/destino;
- Estatísticas históricas;
- melhores horários;
- contexto analítico da IA.

Ainda precisa de QA/hardening:
- confiança/amostra explícitas;
- comportamento por horário/dia/região/categoria;
- análise de destinos;
- probabilidade de próxima corrida;
- tempo esperado até próxima oferta;
- explicabilidade;
- evitar precisão artificial em amostra pequena;
- Replay Inteligente da Jornada;
- métricas avançadas P3.

---

## 11. READER — M1 / M2

M1 permanece oficial.

Sequência:
**Shadow → Consensus → Controlled Hybrid → Primary → estabilização → cleanup**

Gate de Controlled Hybrid:
- `consensus_ready_reader2_only > 0` em uso real;
- conflitos não recorrentes/materialmente relevantes;
- sem regressão Turbo/M1/Histórico/captura;
- CI/testes verdes;
- rollback explícito;
- rescue somente quando M1 não fecha a oferta;
- rescue precisa passar gates oficiais de integridade/admissão.

A versão 1.0 exige Reader confiável; não exige obrigatoriamente M2 Primary.

---

## 12. P2 — V7 + FUNDAÇÃO DA INTELIGÊNCIA

Estado: EM ANDAMENTO AVANÇADO.

Concluído:
- V7 processado;
- V7 canônico;
- legacy isolado;
- views/seed;
- Base Pessoal/Agora;
- continuidade;
- Estatísticas/AI conectadas ao contrato canônico.

Falta:
- validar resultados no produto;
- revisar amostras/confiança;
- ampliar inteligência de destinos;
- probabilidade de continuidade;
- comportamento temporal/geográfico por categoria;
- evitar misturar semântica “oferta observada” com “corrida realizada”.

---

## 13. P3 — INTELIGÊNCIA ANALÍTICA

Pendente/parcial:
- comparativos;
- gráficos/evolução;
- IA algorítmica com explicabilidade;
- metas/projeções;
- custo de oportunidade;
- Replay Inteligente da Jornada;
- recomendação personalizada;
- melhores horários por região;
- heatmap próprio de rentabilidade quando suportado pela base;
- recomendação por categoria.

Não usar heatmap visual da Uber como fundamento da inteligência.

---

## 14. P4 — RADAR CONTEXTUAL

Objetivo:
- eventos;
- polos de mobilidade;
- hospitais;
- hotéis;
- rodoviárias;
- aeroportos;
- centros de eventos;
- shoppings;
- contexto temporal;
- lugares marcados;
- fonte/evidência/timestamp;
- integração com Agora.

Separar:
- Radar de ofertas da plataforma;
- Sr. Rotas Radar de contexto externo.

---

## 15. P5 — COMERCIAL / ADMIN WEB

Pendente:
- trial;
- planos;
- entitlement;
- assinatura;
- gestão de usuários;
- sessões;
- financeiro;
- dados;
- privacidade/LGPD;
- suporte/termos;
- exclusão de dados;
- operação administrativa.

Separar perfil de estratégia de plano comercial.

---

## 16. P6 — HARDENING / RELEASE

Antes da 1.0:
- long-run;
- bateria/temperatura/memória;
- multiaparelho;
- celular/tablet/split-screen;
- upgrade sem perda;
- rollback;
- permissões;
- onboarding;
- observabilidade;
- LGPD;
- Data Safety;
- Play Store;
- RC final.

---

## 17. FORA DO CORE ATUAL / PÓS-CORE

- importação histórica por galeria/pasta;
- referral;
- cupons;
- gamificação/moedas/comunidade/anúncios;
- automação de aceitar/recusar corrida;
- score automático de área perigosa/tráfico/milícia;
- leitura visual de zonas de calor da Uber como base de inteligência.

---

## 18. PROTOCOLO DE CAMPO

O motorista/testador NÃO deve anotar horários, valores ou falhas enquanto dirige.

Fonte de validação:
1. uso normal;
2. bug report quando seguro;
3. JSON de diagnóstico no fim;
4. screenshot somente quando surgir naturalmente e for seguro.

Próximo artefato esperado:
**JSON 3 da 0.33.2-field**.

Ao recebê-lo:
1. validar P0 Capture Continuity;
2. validar crash observability;
3. conferir Reader/Money/Storage regressions;
4. decidir se 0.33.3 + 0.33.4 podem ser consolidadas no próximo APK de campo;
5. só então avisar explicitamente qual APK enviar ao irmão.

---

## 19. REGRA DE CONTINUIDADE PARA OUTRO AGENTE

Antes de qualquer alteração, o novo agente deve:

1. ler `README-CONTINUIDADE.md`;
2. ler `ROADMAP-CANONICO.md`;
3. conferir `main` e última Action;
4. identificar qual APK está realmente em campo;
5. não assumir que HEAD = build testada;
6. não reabrir Histórico, Reader oficial ou V7 sem evidência factual;
7. preservar GitHub como leitura para o agente e entregar ZIP para upload manual;
8. usar Supabase somente conforme autorização explícita do usuário;
9. atualizar Roadmap + README em TODO novo ZIP.



---

## 20. ATUALIZAÇÃO DOCUMENTAL — 26/09/2026 / DOC2

Confirmação factual posterior ao DOC1:

- `0.33.4-field / versionCode 81`;
- commit de código `7161902957cf6903dd0ac26f748da8de31bc228c`;
- workflow `Android CI + Field APK`;
- **Action #126 concluída com SUCCESS**;
- 0.33.4 está tecnicamente pronta, mas **não substitui ainda a 0.33.2 em campo**;
- 0.33.2 continua com o irmão/testador até chegar o JSON 3;
- 0.33.3 + 0.33.4 ficam candidatas à próxima build consolidada após análise do P0.

Nenhuma mudança funcional nova foi feita neste DOC2.


---

## 21. 0.33.5-FIELD — ASSISTENTE ATIVO UX

Estado funcional: IMPLEMENTADO.
Estado CI do primeiro upload: Action #127 FALHOU antes de unit tests/build.

Causa da falha:
- `android/scripts/check-architecture.sh` ainda exigia literalmente
  `CANONICAL_VERSION: 2026-09-24.2` e
  `CURRENT_STAGE: 0.33.0 Field — Release Prep Pack 1`;
- o Roadmap novo e correto foi rejeitado pelo guard antigo;
- não há evidência desta Action de erro de compilação/runtime, pois ela parou
  antes de `Unit tests` e antes de `Build field release APK`.

### Contrato 0.33.5

- motor/ranking do Assistente permanece existente;
- sem LLM para decidir deslocamento;
- balão pequeno ancorado ao mascote;
- ações `IGNORAR | VER`;
- `VER` abre Agora;
- `IGNORAR` fecha;
- toque fora apenas fecha;
- não usar toque fora como “estou em corrida”;
- preferência de duração do balão passa a controlar a exibição;
- Reader, captura, Histórico, V7, backend e Supabase não mudam.

### CI1

`0.33.5-CI1` altera somente:
- Architecture Guard;
- Roadmap;
- README;
- manifest.

O guard deixa de fixar uma versão histórica do Roadmap e passa a validar:
- presença dos documentos canônicos;
- HEAD técnico 0.33.5/vc82;
- separação HEAD x APK em campo;
- regra documental obrigatória;
- contrato do Assistente Ativo.

APK em campo continua 0.33.2/vc79 até análise do JSON 3.

Próximo bloco independente após CI verde:
**Web/Admin factual audit + Intelligence QA/hardening**.
