# Contrato — `srrotas-radar-contextual-v1`

## Semânticas congeladas
- `continuity_probability_pct`: probabilidade estatística em até 10 minutos SOMENTE quando a amostra já é elegível no motor canônico.
- `baseline_probability_pct`: mesma métrica para permanecer na célula do destino.
- `delta_probability_pct`: diferença em pontos percentuais entre candidato e baseline.
- `ranking_score`: índice interno para ordenação. **Não é probabilidade.**
- `confidence`: confiança da evidência apresentada. Não é chance de corrida.
- `potential`: rótulo operacional derivado de score + suficiência estatística.
- `evidence[]`: justificativas auditáveis para o card "Por que está aqui?".

## Assistente de destino
O backend só marca `assistant.eligible=true` quando:
- potencial alto;
- confiança >= 0,70;
- score >= 68;
- candidato a <= 3,5 km;
- vantagem >= 5 p.p. quando baseline comparável;
- ETA entre 4 e 18 minutos.

A UI ainda deve respeitar cooldown/local state.

## Guardrail
Contexto externo (evento/hub/POI) pode aumentar relevância temporal/ranking, mas NUNCA preencher `continuity_probability_pct`.
