# Sr. Rotas 0.17 — Importação histórica + Motor Estatístico v1

## Base de implementação

GitHub main:
`a6718d7cc6f2e74407cbdcdbf158ecaa63670dd5`

## Banco observado antes da 0.17

Leitura read-only em 19/08/2026:

- 819 ofertas;
- 16 ofertas com `destination_cell`;
- 9 ofertas com `pickup_cell`;
- 30 exposições fechadas;
- 25 outcomes;
- 1 corrida concluída confirmada.

Isso é suficiente para provar que a infraestrutura de exposição está
funcionando, mas ainda é pouco para exibir probabilidade robusta na maioria
das regiões.

A UI deve mostrar `DADOS INSUF.` quando não houver amostra mínima.

## Correção de rajadas do Radar

A auditoria dos 30 intervalos reais encontrou vários `offer_observed` de 0–5 s
na mesma célula. Eles são cards/ofertas quase simultâneos de uma mesma rajada,
não novas esperas independentes.

Aplicando a regra conservadora de coalescência de 15 s aos dados atuais:
- 30 intervalos brutos → 20 intervalos estatísticos;
- a célula com 23 intervalos brutos cai para 11 amostras estatísticas;
- portanto ela volta corretamente para `DADOS INSUF.` em P(10 min), em vez de
  exibir um percentual prematuro.

A 0.17 corrige os dois lados:
- futuros registros: `JourneyCoordinator` corta a exposição uma vez por burst;
- dados antigos: motor local/backend removem bursts antes do cálculo.

Todas as ofertas financeiras continuam preservadas.

## Regra estatística principal

A 0.17 não calcula:

`ofertas / quantidade arbitrária`

Ela usa exposição regional observada e trata encerramentos precoces como
censura.

Isso evita transformar pausa, troca de célula ou início de corrida em
"não recebeu oferta" quando o motorista nem permaneceu disponível pelo
horizonte completo.

## Screenshots históricos

São eventos positivos.

Eles aumentam:
- histórico de ofertas;
- contexto;
- geocoding;
- destinos;
- médias.

Eles não aumentam:
- tempo disponível;
- número de exposições;
- denominador de probabilidade.

## Offer Engine

Não alterado:
- OfferParser;
- UberOfferDetector;
- SpatialOfferParser;
- CardStabilizer;
- OfferDeduplicator;
- fórmulas;
- thresholds;
- sampling OCR;
- tamanho máximo do OCR.

## Próxima fase

0.18:
- custos pessoais;
- `userProvided` x `estimated`;
- Lucro est.*;
- memória de cálculo.

0.19:
- validação completa de campo do núcleo 0.14–0.18.
