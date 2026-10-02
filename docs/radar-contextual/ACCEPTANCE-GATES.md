# Gates de aceitação

## Gate A — compilação
- backend typecheck/test;
- Android assembleDebug;
- testes unitários do módulo.

## Gate B — dados
- migration limpa;
- ingest legado continua;
- POI reconcile sem fusões falsas em amostra revisada;
- REVIEW nunca grava poi_id.

## Gate C — Radar manual
- destino + ETA abre Radar;
- centro é destino, não GPS atual;
- sem amostra não mostra percentual inventado;
- ranking_score nunca aparece como probabilidade.

## Gate D — corrida
- zero request sem DOING_RIDE;
- refresh <= 1/min;
- resposta atrasada descartada se corrida mudou;
- OCR sem regressão/performance.

## Gate E — assistente
- somente janela de ETA configurada;
- cooldown funciona;
- `Ignorar | Ver`;
- não conflita com ActiveAssistant026.

## Gate F — learning
- TTNR usa primeira oferta operacional posterior;
- historical-import excluído;
- qualidade da próxima oferta disponível;
- relatórios marcados como observacionais.


## Gate G — fidelidade visual ao mockup
- comparar lado a lado com `MOCKUP-RADAR-CONTEXTUAL-4-FACETAS.png`;
- quatro facetas reconhecíveis;
- mapa centrado no destino;
- poucos POIs;
- detalhe com `Por que está aqui?`;
- balão compacto do mascote com `Ignorar | Ver`;
- UI integrada à identidade atual do Sr. Rotas.
