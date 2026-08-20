# Aplicar — Sr. Rotas 0.18.0-beta

Base usada:
`658c6fe11cf1b306db189c66c871c39926a22538`

## Ordem de aplicação

1. **Mantenha o APK que está em teste de campo normalmente.**
2. No Supabase SQL Editor, execute:
   `supabase/migrations/20260819_cost_profile_profit_memory_018.sql`
3. Confira as quatro verificações no final do SQL.
4. Depois envie o conteúdo inteiro deste ZIP para a raiz do GitHub.
5. Aguarde Vercel e GitHub Actions.
6. Não é necessário instalar o APK 0.18 no motorista de campo agora; a rodada consolidada permanece na 0.19, salvo se CI revelar uma dúvida exclusiva de aparelho.

## Resultado esperado do SQL

### Tabelas
Devem existir:
- `driver_cost_profiles`
- `driver_cost_profile_revisions`

### ride_offers
Devem existir:
- `cost_per_km_used`
- `cost_source`
- `cost_profile_version`
- `cost_profile_updated_at`

### ACL
Esperado:
- `anon_select = false`
- `authenticated_select = false`
- `service_role_select = true`

### Backfill
A consulta final mostra quantas ofertas antigas tiveram custo/km reconstruído.

As ofertas anteriores à 0.18 são marcadas como:
- `legacy_reconstructed` quando `estimated_cost / total_km` permite reconstrução;
- `historical_revaluation` para importações históricas quando aplicável;
- `legacy_unknown` quando não existe informação suficiente;
- `runtime_reconstructed` quando a aritmética da própria oferta mostra que o custo usado pelo parser difere do perfil que ficou ativo instantes depois.

## O que muda no Android

### Configuração rápida
Novo fluxo “Meus custos”:
- tipo do veículo;
- situação do veículo;
- energia/combustível;
- preço;
- consumo;
- custo fixo principal;
- km de trabalho/mês ou “Não sei”.

### “Não sei”
Não bloqueia.

Quando o motorista não sabe km/mês:
- usa referência configurável;
- default inicial: 3.000 km/mês;
- origem registrada como `estimated`.

Quando informa o próprio km/mês:
- origem registrada como `userProvided`.

O valor de 3.000 km **não é tratado como verdade universal**. É somente uma referência inicial editável para permitir o rateio.

### Avançado opcional
- seguro;
- manutenção média mensal;
- pneus em média mensal;
- outros custos mensais;
- jornada média;
- horas de trabalho/mês.

### Fórmula v1

Combustível líquido:

`custo líquido/km = preço por unidade / km por unidade`

Eletricidade:

`custo elétrico/km = preço kWh × kWh/100km / 100`

Custos mensais:

`fixos/km = custos mensais / km de trabalho por mês`

Final:

`custo operacional estimado/km = variável/km + fixos/km`

Oferta:

`custo est. da oferta = km totais × custo operacional estimado/km`

`Lucro est.* = valor da oferta - custo est. da oferta`

## Memória do cálculo

O app mostra:
- cada componente conhecido;
- fórmula;
- valor;
- se a base de km/mês foi informada ou estimada;
- campos ausentes;
- custo operacional final.

O Supabase mantém:
- perfil atual;
- revisões do perfil;
- snapshot do custo usado em cada oferta.

Assim, mudar o perfil amanhã não altera silenciosamente o significado da estimativa registrada ontem.

## Proteção do Offer Engine

A 0.18 **não altera**:
- `OfferParser`;
- `UberOfferDetector`;
- `SpatialOfferParser`;
- `CardStabilizer`;
- `OfferDeduplicator`;
- fórmulas de tarifa/km/minutos;
- thresholds de verdict;
- sampling OCR;
- limite de bitmap OCR.

O parser continua recebendo apenas `settings.costPerKm`.

A diferença é que `settings.costPerKm` agora pode ser derivado do perfil 0.18, e `OfferDispatcher` registra o snapshot usado.

## Compatibilidade

A migration é aditiva.

APK 0.16/0.17:
- continua enviando ofertas;
- backend 0.18 reconstrói `cost_per_km_used` quando possível;
- não precisa ser interrompido para aplicar esta migration.

## Versão Android
- versionCode: 25
- versionName: `0.18.0-beta`
