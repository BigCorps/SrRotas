# Sr. Rotas — Roadmap Canônico

CANONICAL_VERSION: 2026-09-24.1  
CURRENT_STAGE: 0.32.1 Field — Reader 2 Consensus  
SOURCE_OF_TRUTH: este arquivo versionado no repositório  
BUILT_FROM_MAIN: `2aaf0a078e5a2a5d6380a9537b07254274bbc92f`  
LAST_GREEN_CI_BEFORE_STAGE: Action #116  
LAST_READER2_JSON_ANALYZED: `0.32.0-field / versionCode 75`  

> Este documento define o canônico vigente. Changelog registra o que mudou; este roadmap registra o que vale agora. Qualquer alteração deliberada de arquitetura, contrato, ordem de módulos ou critério de promoção deve atualizar este arquivo no mesmo patch.

## 1. Objetivo permanente

O Sr. Rotas deve produzir inteligência temporal e geográfica a partir de ofertas reais. O contrato de captura prioritário é:

**horário → embarque → busca (tempo/km) → corrida (tempo/km) → destino**.

Tarifa e métricas financeiras são importantes para decisão do motorista, mas não substituem esses dados patrimoniais.

## 2. Regras arquiteturais permanentes

- desenvolvimento modular;
- módulos estabilizados ficam congelados até mudança explícita de contrato;
- correção de um módulo não pode alterar silenciosamente outro;
- um único OCR pesado compartilhado sempre que possível;
- nenhum patch-on-patch como arquitetura permanente;
- Reader 2 deve poder ser revertido para a baseline oficial durante a migração;
- persistência/HUD/Histórico só recebem dados pelo fluxo oficial definido para a etapa;
- diagnóstico deve permitir validar sem exigir que o motorista anote detalhes durante a condução;
- evidência de campo = bug report + JSON diagnóstico + screenshots quando forem naturalmente disponíveis.

## 3. Estado atual por módulo

| Módulo / objetivo | Estado canônico | Contrato vigente | Dependência / próximo passo |
|---|---|---|---|
| Shell consolidado / navegação | CONCLUÍDO | `ConsolidatedMainActivity027037`; sem cadeia de polishes runtime | congelado |
| Histórico | ESTÁVEL / CONGELADO | ofertas oficiais + correção de corrida realizada preservadas | não acoplar Reader experimental |
| M1 | BASELINE OFICIAL | continua sendo fonte oficial até promoção explícita do Reader 2 | serve de rollback/comparação |
| Integridade/admissão 0.28–0.30 | CONCLUÍDO | gates de integridade, conflitos x10 e admissão conservadora | manter no fluxo oficial |
| Money Roles / Turbo Mais | CONCLUÍDO | 710/710 concordâncias monetárias no JSON 0.32; promoção/bônus não vira tarifa principal | regressão permanente |
| Reader 2 Parallel 0.31 | CONCLUÍDO COMO SHADOW | candidate builder pré-M1, independente, mesmo OCR, sem side effects | fornece candidatos ao pipeline Reader 2 |
| Capture Resilience 0.31.1 | CONCLUÍDO / ESTÁVEL | 4 interrupções observadas; 2 retomadas solicitadas e 2 concluídas na mesma jornada; novo consentimento preservado | congelado, monitorar regressão |
| Reader 2 Accumulator 0.32 | CAMPO VALIDADO / NÃO PROMOVIDO | 182 merges; 0 campos recuperados; 12 Reader2-only core-completos; nenhuma publicação | consenso temporal substitui readiness simples |
| Reader 2 Consensus 0.32.1 | ETAPA ATUAL | confirma Reader2-only core-completo em frames distintos; suppressa repetição imediata; sem publicação | medir `consensus_ready_reader2_only` |
| Controlled Hybrid | PRÓXIMO, CONDICIONADO | Reader 2 poderá resgatar apenas quando M1 não fechar e consenso estiver aprovado | depende do JSON 0.32.1 |
| Reader 2 Primary | PENDENTE | Reader 2 como caminho principal Uber, M1 rollback temporário | depende do Hybrid multiaparelho |
| Screenshot Storage Guard | PENDENTE | preservar recorte; evitar múltiplos arquivos por oferta; compressão/retenção | após Reader 2 mínimo estável |
| UI / Dark Mode / responsividade | PENDENTE | corrigir contraste/margens; Develop Mode compacto | depois de storage guard |
| Jornada compacta | PENDENTE | `OK ✓ — M1/M2/2.0` ou equivalente | junto da etapa UI |
| Radar / lugares salvos / rotas | PENDENTE | resolver destino dos locais salvos e Buscar Destino | depois de UI |
| Screenshot UX oferta→imagem | PENDENTE | índice durável e preview exato | junto de Radar/rotas |
| Janela flutuante | PENDENTE / MÓDULO PRÓPRIO | refatoração separada | após Radar/rotas |
| Base histórica V7.5 | PRONTA PARA OPERAÇÃO / NÃO EXECUTADA | preservar capturas físicas; retirar promoção sintética antiga; promover fully-ready V7.5 com proveniência | operação Supabase independente |
| 99 / demais plataformas | M1 ATUAL PRESERVADO | Reader 2 atual é priorizado no caminho Uber | decidir antes de release se generaliza ou mantém adapter estável |
| Release hardening | PENDENTE | regressão, longa duração, multiaparelho, permissões, storage, crash e rollback | depende dos módulos P0/P1 |
| Play Store / produção | PENDENTE | somente após release gate | etapa final |

## 4. Reader 2.0 — sequência canônica de conclusão

### R2-A — Parallel independente — CONCLUÍDO

- observa OCR espacial antes do M1;
- candidate builder próprio;
- compara M1 × Reader 2;
- identifica `reader2_only`;
- nenhum segundo OCR;
- nenhum efeito oficial.

### R2-B — Accumulator — CAMPO VALIDADO (0.32.0)

O JSON 0.32 mostrou que o Reader 2 já consegue produzir ofertas completas que o M1 não produz, mas o accumulator não recuperou campos nesta amostra:

- `reader2_only_candidates = 74`;
- `reader2_only_core_complete = 12`;
- `windows_merged = 182`;
- `fields_recovered = 0`;
- `core_completed_by_accumulation = 0`;
- `promotion_ready_reader2_only = 0`.

Conclusão canônica: manter o accumulator como componente de suporte, mas não usar `observations>=2` dentro da janela curta como único critério de promoção.

### R2-C — Consensus temporal — ATUAL (0.32.1)

Objetivo: descobrir se os 12 casos Reader2-only core-completos reaparecem de forma estável em frames realmente distintos.

Regras:
- somente candidatos Reader2-only e core-completos entram no consenso de resgate;
- repetição com menos de 450 ms é tratada como duplicata de frame;
- janela máxima de 8 s;
- todos os campos core e tarifa precisam permanecer compatíveis;
- qualquer conflito invalida a janela;
- confiança mínima 0,70;
- `consensus_ready_reader2_only` é telemetria, não publicação;
- `controlled_hybrid_effect=false`.

### R2-D — Controlled Hybrid — PRÓXIMO, condicionado ao 0.32.1

O Controlled Hybrid só será ativado se o JSON 0.32.1 mostrar candidatos Reader2-only confirmados por consenso temporal sem conflito material.

Quando ativado:
- feature flag/rollback explícito;
- M1 continua aceito quando produz oferta válida;
- Reader 2 só resgata quando M1 não fecha o card;
- candidato resgatado precisa ser core-completo, confirmado pelo consenso e passar pelos gates oficiais de integridade/admissão;
- telemetria identifica `source_reader=m1|reader2_rescue`;
- Histórico/HUD recebem apenas a oferta já aprovada no fluxo oficial.

### R2-E — Reader 2 Primary — PENDENTE

Somente após evidência de campo em mais de um aparelho:
- Reader 2 torna-se caminho principal Uber;
- M1 permanece temporariamente como rollback/compare;
- medir FN/FP, completude dos campos, duplicatas, latência e duração longa;
- retirar caminhos antigos somente depois de estabilidade comprovada.

### R2-F — Cleanup — PENDENTE

- remover código experimental substituído;
- manter adapters necessários;
- congelar contrato Reader 2 de produção;
- manter suíte permanente de regressão: Turbo Mais, cards longos, decimal, múltiplos cards, split-screen e retomada de captura.

## 5. Evidência de campo que autoriza a 0.32.1

Último JSON: `0.32.0-field / versionCode 75`.

- OCR: 12.991 iniciados / 12.991 concluídos / 0 falhas;
- Reader 2: 242 candidatos, 166 core-completos;
- 74 candidatos Reader2-only;
- 12 Reader2-only core-completos;
- Reader 2 ficou à frente em completude em 31 frames contra 1 do M1;
- Turbo Mais: 710/710 concordâncias de tarifa, 0 divergências;
- Capture Resilience: 4 interrupções, 2 pedidos de retomada, 2 retomadas concluídas, 0 falhas;
- accumulator: 182 merges, mas 0 campos recuperados;
- readiness pareado ao M1: 67 concordâncias e 7 divergências, o que impede promoção cega.

Isso autoriza consenso temporal, não publicação oficial.

## 6. Sequência após Reader 2

1. concluir Consensus 0.32.1;
2. Controlled Hybrid;
3. Reader 2 Primary / rollback multiaparelho;
4. Screenshot Storage Guard;
5. UI + Dark Mode + responsividade + jornada compacta;
6. Radar + lugares salvos + rotas + screenshot UX;
7. janela flutuante como módulo próprio;
8. operação histórica V7.5;
9. release hardening;
10. Play Store / produção.

## 7. Gate de lançamento

Uma versão apta para lançamento exige, no mínimo:

- Reader oficial com rollback definido e sem P0/P1 recorrente;
- captura de longa duração e retomada de MediaProjection validadas;
- cinco campos patrimoniais com estabilidade suficiente em mais de um aparelho/layout;
- Turbo Mais e múltiplos valores monetários em regressão permanente;
- storage de screenshots limitado;
- Dark Mode e responsividade utilizáveis;
- Histórico estável;
- Radar/rotas sem fluxo quebrado crítico;
- permissões/onboarding testados em Android suportado;
- CI verde e suíte de contrato/regressão verde;
- teste de atualização sobre instalação existente sem limpar dados;
- política de rollback documentada;
- checklist Play Console/produção concluído.

## 8. Como atualizar este roadmap

Toda mudança canônica deve alterar, no mesmo patch:

1. `CANONICAL_VERSION` quando houver mudança deliberada de direção/contrato;
2. `CURRENT_STAGE`;
3. estado do módulo afetado;
4. dependências e próximo passo;
5. changelog correspondente;
6. Architecture Guard quando o contrato puder ser verificado automaticamente.

O banco pode futuramente espelhar o status para painel administrativo, mas **a fonte primária durante o desenvolvimento é este arquivo versionado no repositório**, porque ele acompanha exatamente o código/commit ao qual o contrato se aplica.
