# Sr. Rotas — V7 Intelligence Foundation

Base do patch: `main` commit `bae0a699743415d334f4d7926d07ddadf8635953`.

## O que já foi aplicado no Supabase

- lote V7.5 `48323962-cabd-497b-890f-8315e0d0753a` marcado como canônico/ready;
- 9 batches legacy arquivados e preservados para rollback/auditoria;
- `sr_refresh_region_seed_v1()` restrita a V7 canônico;
- seed reconstruída com 4.459 grupos / 35.957 amostras V7-only;
- propriedade do V7 resolvida para o driver correto;
- Base Pessoal passou a agregar operacional + V7;
- view `sr_personal_offer_canonical_v1` criada;
- 38.756 promoções históricas sintéticas antigas removidas de `ride_offers`;
- nenhuma oferta operacional real foi removida.

## Patch de backend

### `backend/src/analytics.ts`

- mantém `fetchOffers()` como caminho operacional puro;
- adiciona `fetchCanonicalOffers()` para análises;
- Estatísticas passam a usar `sr_personal_offer_canonical_v1`;
- `get_best_hours` passa a usar V7 + operacional no período;
- source counts (`operational` / `historical_v7`) passam a acompanhar os agregados;
- custos/lucro continuam calculados apenas quando os campos existem;
- jornadas continuam exclusivamente operacionais.

### `backend/src/ai.ts`

- a amostra analítica usada pelo assistente passa a vir da fonte canônica;
- cada linha leva `source`;
- instrução diferencia explicitamente operacional de `historical_v7`;
- histórico V7 nunca é tratado como corrida realizada.

## O que NÃO muda

- `ride_offers` continua sendo a tabela operacional real;
- `journeySummary`, `costBreakdown` e `strategyProgress` continuam operacionais;
- Base Coletiva continua sem V7, preservando opt-in e dados reais;
- Histórico Android permanece FROZEN;
- nenhuma alteração no Reader/MediaProjection;
- nenhuma alteração na versão do APK.

## Aplicação

1. Subir os arquivos mantendo os caminhos relativos.
2. A migration representa o estado já aplicado no Supabase de produção; ela é idempotente para esse lote e serve para manter o repositório sincronizado.
3. Deixar o CI/Vercel validar o backend.
4. Testar `/api/v1/analytics` e IA em períodos de 90 dias, além do `get_best_hours` com 90/180 dias.
