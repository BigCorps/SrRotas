# QA — Sr. Rotas 0.31.0 Field · Reader 2 Parallel

## Objetivo

Validar o Reader 2.0 realmente paralelo, com M1 ainda oficial.

A pergunta principal desta versão é:

> Quando o M1 lê errado ou rejeita um card, o Reader 2 consegue montar um candidato estruturalmente melhor usando o mesmo OCR?

## 1. Instalação

- instalar por cima da versão existente;
- não desinstalar;
- não limpar dados;
- confirmar `versionName = 0.31.0-field` e `versionCode = 73`;
- manter M1 como leitor oficial;
- não selecionar M2 isolado para este QA.

Para o segundo aparelho/usuário que ainda estava na `0.27.0-rc3.7.2`, atualizar primeiro para esta versão antes de comparar bugs atuais.

## 2. Duração e amostra

- mínimo recomendado: 2 horas;
- meta: 40+ ofertas Uber;
- ideal: testar em pelo menos dois aparelhos;
- não encerrar a jornada por erro pontual de leitura, salvo se o app ficar inutilizável.

## 3. Cards longos — prioridade máxima

Quando aparecer oferta com endereço longo, observação adicional (`Acesso ao...`) ou muitas linhas:

- guardar screenshot quando possível;
- anotar horário aproximado;
- observar se o HUD M1 mostrou a oferta;
- mesmo se o M1 não mostrar, continuar a jornada para o Reader 2 registrar o frame em shadow.

Especialmente observar layouts em que o OCR possa quebrar:

```text
tempo até embarque
km até embarque
endereço de embarque / observação longa

tempo da corrida
km da corrida
destino
```

O Reader 2 0.31 foi criado para reconstruir esse cenário sem exigir que tempo e km estejam na mesma linha.

## 4. Turbo Mais — regressão congelada

Continuar observando promoções naturalmente.

Esperado:

- tarifa principal continua correta;
- Turbo Mais não vira tarifa;
- não surge card artificial;
- Reader 2 também deve escolher a tarifa principal.

Caso conhecido: `R$ 34,15` principal + `Turbo Mais R$ 6,57` deve continuar em `R$ 34,15`.

## 5. Cinco campos/core

Nos casos problemáticos, comparar primeiro:

1. horário da oferta;
2. local de embarque;
3. minutos/km até embarque;
4. minutos/km da corrida;
5. local de destino;
6. tempo total.

Tarifa e métricas financeiras também devem ser observadas, mas não substituir a conferência do core temporal/geográfico.

## 6. O que continua oficial

- HUD usa M1;
- Histórico recebe somente fluxo oficial M1;
- backend recebe somente fluxo oficial M1;
- Reader 2 não corrige nem substitui a oferta nesta versão.

Portanto, é esperado que um caso `reader2_only` ainda não apareça no HUD. O objetivo é medir se o Reader 2 poderia tê-lo recuperado.

## 7. Diagnóstico obrigatório

Exportar o diagnóstico combinado ao final sem limpar dados/reiniciar deliberadamente o app.

### `reader2_parallel_031`

Confirmar:

- `mode = parallel_shadow`;
- `input_stage = pre_m1_offer_from_shared_spatial_ocr`;
- `independent_candidate_builder = true`;
- `m1_required_for_reader2 = false`;
- `can_observe_m1_rejected_frames = true`;
- `second_ocr = false`;
- `official_persistence = false`;
- `backend_effect = false`;
- `hud_effect = false`;
- `admission_influence = false`.

Analisar principalmente:

- `reader2_candidates`;
- `reader2_core_complete`;
- `reader2_only_candidates`;
- `reader2_only_core_complete`;
- `m1_offers_seen`;
- `m1_only_offers`;
- `matched_candidates`;
- `frames_with_disagreement`;
- `reader2_core_ahead_frames`;
- `m1_core_ahead_frames`;
- `split_geometry_candidates`;
- `long_card_frames`;
- `long_card_candidates`;
- `long_card_reader2_only`;
- divergências por campo.

### Shadows anteriores

`reader2_shadow_030` e `reader2_money_shadow_030_field2` continuam no diagnóstico para continuidade, mas o foco desta rodada passa a ser `reader2_parallel_031`.

## 8. Interrupção da captura

A correção de resiliência MediaProjection está registrada para a próxima etapa `0.31.1`.

Nesta build apenas registrar, se acontecer:

- horário aproximado;
- se o ícone de gravação sumiu;
- se a jornada continuou visualmente ativa;
- se apareceu pedido de autorização novamente;
- tempo aproximado desde o início da captura.

Não considerar resolvido nesta versão.

## 9. Dark Mode / screenshots / UI

Também continuam registrados para etapas próprias. Se houver falha evidente, guardar print, mas não misturar com a aprovação do Reader 2 Parallel.

## 10. Histórico

Abrir antes/durante/depois. Qualquer regressão funcional é P1 porque o Histórico não foi alterado nesta etapa.

## 11. Critérios de aprovação da 0.31.0

- CI/build verde;
- M1 continua funcional;
- nenhuma regressão Turbo Mais;
- Reader 2 produz candidatos sem segundo OCR;
- `reader2_only_candidates` consegue aparecer quando houver frame M1 rejeitado mas estruturalmente recuperável;
- cards longos não provocam crash/ciclo de reset;
- divergências são registradas por campo;
- Histórico permanece estável;
- nenhum efeito oficial do Reader 2.

## 12. O que enviar

1. diagnóstico final;
2. aparelho e versão Android;
3. duração da jornada;
4. quantidade aproximada de ofertas;
5. screenshots/horários de cards longos ou leituras erradas;
6. dizer se cada erro apareceu no HUD M1;
7. informar qualquer interrupção de captura e horário aproximado.
