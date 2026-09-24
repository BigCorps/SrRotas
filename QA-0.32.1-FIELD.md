# QA de campo — Sr. Rotas 0.32.1 Field · Reader 2 Consensus

## Objetivo

Validar o consenso temporal do Reader 2 sem exigir anotações durante a direção.

## Como testar

Use o aplicativo normalmente durante a jornada. Não é necessário anotar horários, tarifas, endereços ou erros enquanto dirige.

Se perceber algo claramente errado e conseguir lembrar depois, inclua no bug report final. Screenshot é opcional e somente se já estiver disponível naturalmente.

## Ao terminar

Envie apenas:

1. um bug report geral com a percepção da jornada;
2. o JSON de diagnóstico exportado pelo Sr. Rotas.

## O que a instância desenvolvedora vai conferir no JSON

- versão `0.32.1-field` / `versionCode 76`;
- saúde OCR e captura;
- `reader2_parallel_031`;
- `reader2_accumulator_032`;
- `reader2_consensus_0321`;
- `capture_resilience_0311`;
- `reader2_money_shadow_030_field2`;
- `offer_admission_030`.

### Sinal principal para avanço

A seção `reader2_consensus_0321` deve mostrar se ofertas core-completas encontradas apenas pelo Reader 2 reapareceram em frames distintos e estáveis.

Os campos principais são:

- `reader2_only_core_seen`;
- `confirmations_accepted`;
- `duplicate_frame_suppressed`;
- `conflicts_detected`;
- `consensus_ready_reader2_only`.

## Critério de avanço

O Controlled Hybrid só será considerado se houver `consensus_ready_reader2_only > 0` em campo, sem conflito recorrente relevante e sem regressão nas funções congeladas.

Até essa decisão, o M1 continua sendo o leitor oficial e o Reader 2 não publica oferta.
