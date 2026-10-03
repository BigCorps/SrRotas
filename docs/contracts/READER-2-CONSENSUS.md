# Reader 2.0 — Contrato 0.32.1 · Temporal Consensus / Promotion Readiness

## Objetivo

Transformar os casos `Reader2-only core-complete` observados em campo em evidência temporal confiável antes de qualquer Controlled Hybrid.

A 0.32.0 provou que o Reader 2 consegue fechar ofertas que o M1 não fechou, mas não provou repetição suficiente dessas ofertas. A 0.32.1 mede exatamente essa repetição.

## Entrada

- mesmo OCR espacial compartilhado com M1;
- candidatos produzidos por `Reader2Parallel031` e consolidados por `Reader2Accumulator032`;
- somente candidatos core-completos entram na avaliação de consenso;
- nenhuma nova captura e nenhum segundo OCR.

## Reader2-only

O consenso de resgate só é construído quando não existe uma oferta M1 compatível no mesmo frame.

O candidato precisa conter:

- tarifa;
- local de embarque;
- km e minutos até embarque;
- km e minutos da corrida;
- destino;
- tempo total.

## Consenso temporal

- janela máxima: 8 s;
- duas confirmações mínimas;
- observações separadas por pelo menos 450 ms;
- repetições abaixo de 450 ms são contabilizadas como `duplicate_frame_suppressed` e não confirmam a oferta;
- tarifa, busca, corrida, total e labels precisam permanecer compatíveis;
- qualquer conflito invalida a janela;
- confiança mínima: 0,70.

## Hard gates

Nesta versão:

- `controlled_hybrid_effect=false`;
- `official_persistence=false`;
- `backend_effect=false`;
- `hud_effect=false`;
- `admission_influence=false`;
- M1 continua oficial;
- Histórico permanece congelado.

`consensus_ready_reader2_only` significa somente que existe evidência suficiente para avaliar o Controlled Hybrid na próxima versão.

## Diagnóstico

Nova seção: `reader2_consensus_0321`.

Contadores principais:

- `reader2_only_core_seen`;
- `consensus_windows_started`;
- `confirmations_accepted`;
- `duplicate_frame_suppressed`;
- `conflicts_detected`;
- `consensus_ready_transitions`;
- `consensus_ready_reader2_only`;
- `matched_m1_agrees` / `matched_m1_disagrees`.

Nenhum OCR bruto, endereço, coordenada ou screenshot é persistido pelo módulo.

## Gate para Controlled Hybrid

O próximo estágio pode ativar Controlled Hybrid somente se o diagnóstico de campo mostrar:

1. `consensus_ready_reader2_only > 0` em uso real;
2. conflitos desses candidatos não forem recorrentes/materialmente altos;
3. nenhuma regressão em Turbo Mais, M1 oficial, Histórico ou captura;
4. CI e testes de contrato verdes;
5. rollback explícito mantido.

Se esse gate não for atingido, permanece em Reader 2 shadow e corrige-se a camada de consenso antes de qualquer publicação oficial.
