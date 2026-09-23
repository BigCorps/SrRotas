# Reader 2.0 — Parallel Contract 0.31

## Objetivo

Transformar o Reader 2 de um shadow dependente da `RideOffer` M1 em um leitor paralelo capaz de interpretar a mesma evidência espacial OCR antes de o M1 aceitar ou rejeitar o card.

## Fluxo 0.31

```text
MediaProjection
  -> OCR M1 único
  -> linhas espaciais
      -> Reader2Parallel031 (candidate builder independente, shadow)
      -> M1 / UberSpatialParser / OfferParser (oficial)
  -> comparação Reader 2 x M1
  -> somente M1 segue para integridade/admissão/HUD/persistência
```

## Hard gates

Na 0.31.0:

- `reader2_official_persistence=false`
- `reader2_backend_effect=false`
- `reader2_hud_effect=false`
- `reader2_admission_influence=false`
- `reader2_second_ocr=false`
- M1 permanece oficial
- nenhum schema de banco muda
- nenhum contrato do Histórico muda

## Independência mínima exigida

O candidate builder 0.31 não pode usar como decisão:

- `OfferParser.parse`
- `UberOfferDetector.detect`
- `MoneyRoleResolver030`
- `Reader2ShadowRules030.fromSpatial`

Ele pode compartilhar apenas a evidência bruta já reconhecida pelo OCR (`SpatialOcrLine`) e tipos de domínio necessários para comparação diagnóstica.

## Campos Reader 2

Cada candidato tenta produzir:

- horário de observação (presença do frame);
- tarifa;
- km até embarque;
- minutos até embarque;
- km da corrida;
- minutos da corrida;
- km total;
- minutos totais;
- local de embarque;
- local de destino;
- confiança estrutural;
- indicação de geometria reconstruída entre linhas separadas.

## Cards longos

O Reader 2 não exige que `tempo + km` estejam na mesma linha. Se o OCR separar, por exemplo:

```text
4 min
1,2 km
Rua de embarque...
...
33 min
7,9 km
Rua de destino...
```

o candidate builder associa duração e distância por ordem/proximidade espacial dentro do card.

## Comparação

O diagnóstico `reader2_parallel_031` precisa permitir responder:

- quantos candidatos Reader 2 existiram;
- quantos ficaram core-complete;
- quantos existiram sem qualquer oferta M1 (`reader2_only_candidates`);
- quantos `reader2_only` ficaram core-complete;
- quantas ofertas ficaram somente no M1;
- quantos candidatos puderam ser pareados;
- em quais campos houve divergência;
- quantos cards longos foram vistos;
- quantos cards longos foram recuperados somente pelo Reader 2;
- em quantos frames Reader 2 teve mais cobertura core que o M1 e vice-versa.

## Privacidade

A telemetria não persiste:

- OCR bruto;
- endereços;
- coordenadas;
- screenshots.

Persistem apenas contadores, flags e nomes dos campos divergentes.

## Critério de promoção futura

Reader 2 só poderá influenciar a oferta oficial após versão própria e decisão explícita, apoiada por QA em múltiplos aparelhos e revisão dos casos de divergência/`reader2_only`.
