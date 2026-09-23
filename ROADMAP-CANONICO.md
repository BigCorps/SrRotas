# Sr. Rotas — Roadmap Canônico

CANONICAL_VERSION: 2026-09-23.1  
CURRENT_STAGE: 0.32.0 Field — Reader 2 Accumulator  
SOURCE_OF_TRUTH: este arquivo versionado no repositório  
BUILT_FROM_MAIN: `3318c18c1a75ad7a84653d421edeb000c8f60bd4`  
LAST_GREEN_CI_BEFORE_STAGE: Action #115  
LAST_READER2_JSON_ANALYZED: `0.31.0-field / versionCode 73`  

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
| Money Roles / Turbo Mais | CONCLUÍDO | promoção/bônus não pode virar tarifa principal/card | teste de regressão permanente |
| Reader 2 Parallel 0.31 | CONCLUÍDO COMO SHADOW | candidate builder pré-M1, independente, mesmo OCR, sem side effects | alimenta accumulator |
| Capture Resilience 0.31.1 | IMPLEMENTADO / CI VERDE / CAMPO PENDENTE | perda da projeção não encerra jornada; retomada exige novo consentimento | validar por JSON do próximo teste |
| Reader 2 Accumulator 0.32 | IMPLEMENTADO NO PATCH / CAMPO PENDENTE — ETAPA ATUAL | junta somente campos ausentes de frames compatíveis em janela curta; sem publicação | medir recuperação dos buracos |
| Promoção controlada Reader 2 | PENDENTE | nenhuma promoção automática hoje | depende do 0.32 e evidência multiaparelho |
| Screenshot Storage Guard | PENDENTE | preservar recorte; evitar múltiplos arquivos por oferta; compressão/retenção | depois do Reader 2 mínimo estável |
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

### R2-B — Accumulator — ATUAL (0.32.0)

Objetivo: reduzir buracos ocasionais combinando evidências parciais da mesma oferta em poucos segundos.

Regras:
- somente campos ausentes são preenchidos;
- valores conflitantes não são sobrepostos;
- janela curta e somente memória;
- candidatos precisam de repetição para `promotion_ready`;
- `promotion_ready` é telemetria, não publicação.

### R2-C — Controlled Hybrid — PRÓXIMO, condicionado ao 0.32

Se o 0.32 mostrar candidatos core-completos recuperados sem conflito relevante:

- feature flag de rollback;
- M1 continua aceito quando produz oferta válida;
- Reader 2 pode resgatar oferta somente quando M1 não fecha o card e o candidato Reader 2 cumprir contrato de promoção;
- candidato resgatado passa pelos mesmos gates oficiais de integridade/admissão antes de persistir;
- telemetria identifica claramente `source_reader=m1|reader2_rescue`;
- nenhuma mudança silenciosa de Histórico/HUD.

### R2-D — Reader 2 Primary — PENDENTE

Somente após evidência de campo em mais de um aparelho:

- Reader 2 torna-se caminho principal Uber;
- M1 permanece temporariamente como rollback/compare;
- medir FN/FP, completude dos campos, duplicatas, latência e duração longa;
- retirar caminhos antigos somente depois de estabilidade comprovada.

### R2-E — Cleanup — PENDENTE

- remover código experimental substituído;
- manter adapters necessários;
- congelar contrato Reader 2 de produção;
- manter suíte permanente de regressão (Turbo Mais, cards longos, decimal, múltiplos cards, split-screen).

## 5. Evidência atual que autoriza o 0.32

Último JSON Reader 2 disponível antes desta etapa: `0.31.0-field / versionCode 73`.

- Reader 2 paralelo observou candidatos que o M1 não fechou;
- houve 48 candidatos `reader2_only` no diagnóstico disponível;
- nenhum desses 48 estava ainda core-completo naquele JSON;
- em frames pareados, Reader 2 apresentou mais completude core que M1 em parte relevante da amostra;
- bug report de campo relata redução dos erros e ofertas computadas de forma satisfatória, com buracos ocasionais menores;
- isso justifica accumulator, mas ainda não justifica promoção oficial direta.

## 6. Sequência após Reader 2

1. concluir Reader 2 até Controlled Hybrid / Primary conforme gates;
2. Screenshot Storage Guard;
3. UI + Dark Mode + responsividade + jornada compacta;
4. Radar + lugares salvos + rotas + screenshot UX;
5. janela flutuante como módulo próprio;
6. operação histórica V7.5;
7. release hardening;
8. Play Store / produção.

Capture Resilience 0.31.1 continua sendo validado em paralelo por diagnóstico e não precisa bloquear o trabalho interno do Reader 2, salvo se surgir P0/P1 de jornada/captura.

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
