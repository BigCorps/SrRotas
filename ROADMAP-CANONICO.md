# Sr. Rotas — Roadmap Canônico

CANONICAL_VERSION: 2026-09-24.2  
CURRENT_STAGE: 0.33.0 Field — Release Prep Pack 1  
SOURCE_OF_TRUTH: este arquivo versionado no repositório  
BUILT_FROM_MAIN: `cc7895937acc54fa010b7914d55e6b653054492f`  
LAST_GREEN_CI_BEFORE_STAGE: Action #118  
LAST_READER2_JSON_ANALYZED: `0.32.0-field / versionCode 75`  

> Regra nova de ritmo: módulos independentes podem avançar em trilhas paralelas. Um módulo experimental só bloqueia outro quando existe dependência técnica real ou P0/P1 compartilhado.

## 1. Objetivo permanente

O Sr. Rotas deve produzir inteligência temporal e geográfica a partir de ofertas reais. O contrato prioritário é:

**horário → embarque → busca (tempo/km) → corrida (tempo/km) → destino**.

Tarifa e métricas ajudam a decisão do motorista, mas não substituem esses dados patrimoniais.

## 2. Regras arquiteturais

- desenvolvimento modular e contratos explícitos;
- módulo estável fica congelado até mudança deliberada;
- um único OCR pesado compartilhado sempre que possível;
- Reader 2 deve manter rollback durante migração;
- diagnóstico deve permitir QA sem anotações enquanto o motorista dirige;
- evidência de campo = bug report + JSON + screenshots quando naturalmente disponíveis;
- trilhas independentes podem avançar no mesmo APK desde que não compartilhem mutação crítica.

## 3. Estado atual

| Módulo / objetivo | Estado canônico | Próximo passo |
|---|---|---|
| Shell/navegação consolidada | CONCLUÍDO | congelado |
| Histórico | ESTÁVEL / CONGELADO | mudança visual responsiva permitida; sem acoplamento Reader |
| M1 | BASELINE OFICIAL | rollback durante migração |
| Integridade/admissão 0.28–0.30 | CONCLUÍDO | regressão permanente |
| Money Roles / Turbo Mais | CONCLUÍDO | 710/710 concordâncias no JSON 0.32 |
| Capture Resilience 0.31.1 | CONCLUÍDO / ESTÁVEL | monitorar; 2/2 retomadas bem-sucedidas |
| Reader 2 Parallel | CONCLUÍDO SHADOW | base do pipeline 2.0 |
| Reader 2 Accumulator | VALIDADO / SUPORTE | 12 Reader2-only core-completos; não promove |
| Reader 2 Consensus 0.32.1 | EM CAMPO / PARALELO | medir `consensus_ready_reader2_only` |
| Controlled Hybrid | PENDENTE CONDICIONAL | ativar só com consenso real e rollback |
| Reader 2 Primary | PENDENTE | após Hybrid multiaparelho |
| Screenshot Storage Guard | IMPLEMENTADO 0.33 | validar via JSON |
| Responsividade | IMPLEMENTADA 0.33 | validar celular/tablet |
| Dark Mode | CONTRATO/REGRESSÃO 0.33 | validar visualmente build atual |
| Jornada compacta | IMPLEMENTADA 0.33 | `OK ✓ — M1/M2/2.0` |
| Radar/lugares/rotas | PRÓXIMO PACOTE | 0.34 Navigation Pack |
| Screenshot UX oferta→imagem | PRÓXIMO PACOTE | índice + preview em 0.34 |
| Janela flutuante modular | PENDENTE | 0.35 |
| Base histórica V7.5 | PRONTA / NÃO EXECUTADA | operação Supabase independente |
| 99/demais plataformas | M1 preservado | decisão antes de release |
| Release hardening | PENDENTE | após pacotes funcionais |
| Play Store/produção | PENDENTE | gate final |

## 4. Trilhas paralelas vigentes

### Trilha Reader 2

`Parallel → Accumulator → Consensus → Controlled Hybrid → Primary → Cleanup`

Consensus continua sem efeito oficial na 0.33. O restante do produto não fica parado esperando essa telemetria.

### Trilha Produto

`0.33 Storage/UI → 0.34 Radar/Rotas/Screenshot UX → 0.35 Janela flutuante`

### Trilha Dados

`V7.5 histórico → validação de inteligência temporal/geográfica → hardening de consultas`

As três trilhas convergem no Release Hardening.

## 5. Evidência que autorizou acelerar

JSON 0.32:

- OCR 12.991/12.991, zero falhas;
- Reader 2: 242 candidatos, 166 core-completos;
- 74 Reader2-only, sendo 12 core-completos;
- Reader 2 à frente em completude em 31 frames contra 1 do M1;
- Turbo Mais: 710/710 concordâncias;
- Capture Resilience: 4 interrupções, 2 retomadas solicitadas e 2 concluídas;
- nenhum P0/P1 que obrigue storage/UI a esperar Consensus.

## 6. Sequência acelerada até lançamento

1. **0.33.0 Release Prep Pack 1** — storage + responsividade + dark contract + jornada compacta;
2. **0.34.x Navigation Pack** — Radar, lugares, Buscar Destino/Combinado, oferta→screenshot;
3. **0.35.x Floating Window Module** — refatoração isolada;
4. **Reader 2 Controlled Hybrid/Primary** entra assim que a telemetria atingir o gate, sem esperar os pacotes acima;
5. **V7.5** pode ser executado como operação independente de dados;
6. **Release Hardening** — longa duração, multiaparelho, permissões, upgrade sem limpar dados, regressões e rollback;
7. **Play Store / produção**.

## 7. Gate de lançamento

- Reader oficial com rollback e sem P0/P1 recorrente;
- captura longa + retomada validadas;
- cinco campos patrimoniais estáveis em mais de um aparelho/layout;
- Turbo Mais/múltiplos valores em regressão permanente;
- storage limitado;
- Dark Mode/responsividade utilizáveis;
- Histórico estável;
- Radar/rotas sem quebra crítica;
- permissões/onboarding testados;
- CI/contratos verdes;
- atualização sobre instalação existente sem perda de dados;
- checklist Play Console e rollback documentados.

## 8. Atualização do roadmap

Qualquer mudança deliberada deve atualizar `CANONICAL_VERSION`, `CURRENT_STAGE`, estado do módulo, dependências e guard automatizável. Git continua sendo a fonte primária do contrato; Supabase pode espelhar o status futuramente.
