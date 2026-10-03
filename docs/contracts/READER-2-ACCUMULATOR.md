# Reader 2.0 — Contrato 0.32 · Accumulator / Promotion Readiness

## Objetivo

Reduzir os buracos ocasionais de leitura observados em campo sem relaxar o M1 e sem promover o Reader 2 prematuramente.

O Reader 2.0 0.32 combina evidências parciais de frames consecutivos da mesma oferta. Ele não inventa valores, não corrige números e não publica ofertas.

## Entrada

- mesmos frames do OCR espacial compartilhado;
- candidatos independentes produzidos por `Reader2Parallel031`;
- nenhuma nova captura;
- nenhum segundo ML Kit OCR.

## Janela

- memória curta: 4,5 s;
- máximo de 12 janelas ativas;
- identificação conservadora por tarifa compatível + posição vertical + campos já conhecidos;
- valores/labels conflitantes não sobrescrevem o estado anterior.

## Regra de merge

O accumulator **somente preenche campo ausente**.

Pode recuperar entre frames:

- km até embarque;
- minutos até embarque;
- km da corrida;
- minutos da corrida;
- tempo total;
- local de embarque;
- destino.

Não é permitido substituir valor observado já preenchido por outro conflitante. `totalKm` e `totalMinutes` são campos derivados: quando os dois trechos ficam disponíveis, são recalculados pela soma de busca + corrida.

## Promotion readiness

Um candidato é marcado apenas como `promotionReady=true` quando:

- está core-completo;
- foi observado pelo menos 2 vezes;
- a janela está sem conflito;
- confiança >= 0,70.

**Isso é somente telemetria.** Nesta versão:

- `promotion_effect=false`;
- M1 continua oficial;
- Reader 2 não grava banco/local store;
- Reader 2 não controla HUD;
- Reader 2 não chama backend;
- Reader 2 não influencia admissão.

## Diagnóstico

Nova seção: `reader2_accumulator_032`.

Principais contadores:

- `fields_recovered`;
- `core_completed_by_accumulation`;
- `promotion_ready_transitions`;
- `reader2_only_core_complete`;
- `promotion_ready_reader2_only`;
- `promotion_ready_agrees_with_m1`;
- `promotion_ready_disagrees_with_m1`.

Nenhum OCR bruto, endereço, coordenada ou screenshot é persistido pela telemetria.

## Decisão seguinte

O próximo estágio só pode habilitar **Controlled Hybrid** se o diagnóstico mostrar que o accumulator recupera candidatos core-completos de forma repetida e sem conflito relevante.
