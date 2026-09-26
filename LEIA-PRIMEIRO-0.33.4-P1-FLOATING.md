# Sr. Rotas 0.33.4 Field — P1 Floating Metrics Restore

Base: `main` commit `cca1888ce46bcd3a1380c20ee0d7ee3e5c578259` — Action #125 verde.

## Regressão factual

A janela flutuante atual preservava as métricas no objeto `RideOffer`, porém no primeiro nível expandido exibia apenas sinais de Busca/Destino. R$/km, R$/min, R$/h, km, minutos e lucro ficaram escondidos no segundo nível de detalhes.

## Correção

- restaura no primeiro nível as pílulas: R$/km, R$/min, R$/h, km, min e Lucro est.* quando os dados existem;
- Nota, Lucro/h e Margem podem entrar como extras quando habilitados na configuração já existente do HUD;
- classificação financeira usa exclusivamente `HudMetricEvaluation0221`;
- km/min totais são informativos e neutros, pois não possuem threshold canônico próprio;
- modo daltônico usa a mesma semântica de cores do HUD;
- composição em duas colunas, com adaptação ao modo compacto/text size;
- Busca, Destino, Combinado, continuidade, mensagens, controles e segundo nível permanecem intactos.

## Preservado

- Reader/M1/M2: sem alteração;
- parser/admission/integrity: sem alteração;
- Histórico: FROZEN;
- persistência/backend/V7: sem alteração;
- Capture Continuity 0.33.2: sem alteração;
- Odometer Sync 0.33.3: sem alteração.

## Campo

NÃO enviar ao irmão enquanto ele estiver validando a 0.33.2. Subir na main apenas para CI. Depois do JSON P0 decidimos qual build consolidada será o próximo APK de campo.
