# QA — Sr. Rotas 0.30.0 Field2 · Money Roles

## Objetivo

Validar somente a correção de papéis monetários no Core Reader, sem misturar UI, Radar, janela flutuante ou o próximo bug de armazenamento de screenshots.

Confirmar:

- tarifa principal correta em cards com Turbo Mais;
- valores promocionais não viram tarifa/card;
- cards comuns continuam funcionando;
- múltiplos cards reais continuam separados;
- Reader 2 money shadow funciona sem interferir;
- cinco campos patrimoniais continuam confiáveis.

## 1. Instalação

- instalar por cima da versão atual;
- **não desinstalar**;
- **não limpar dados**;
- confirmar:
  - `versionName = 0.30.0-field2`;
  - `versionCode = 72`;
- leitor oficial: **M1**;
- Reader 2 continua em shadow automático.

## 2. Teste obrigatório do Turbo Mais

Quando aparecer uma oferta promocional, conferir no card:

1. categoria;
2. tarifa principal;
3. R$/km aproximado, se houver;
4. nota do passageiro;
5. “Verificado”, se houver;
6. Turbo Mais/promoção e seu valor;
7. km/min até embarque;
8. km/min da viagem;
9. destino.

Esperado:

- HUD usa a **tarifa principal**, nunca o Turbo Mais;
- R$/km, R$/min, R$/h e lucro derivam da tarifa principal;
- o valor Turbo Mais não aparece como uma segunda oferta;
- o card não é cortado em dois blocos por causa do segundo `R$`.

Caso de regressão obrigatório:

`Electric | R$ 34,15 | R$/km aprox. | nota | Verificado | Turbo Mais | R$ 6,57 | rota`

Esperado: **tarifa = R$ 34,15**, nunca R$ 6,57.

## 3. Cards sem promoção

Observar pelo menos 15 ofertas normais.

Confirmar:

- tarifa correta;
- nenhuma perda de leitura causada pelo resolver;
- pickup/destino continuam associados ao card certo;
- cálculos financeiros continuam iguais quando as entradas são iguais.

## 4. Múltiplos cards / Radar

Se ocorrer naturalmente Radar de Viagens ou mais de um card:

- cards legítimos continuam separados;
- uma geometria/ação real entre duas tarifas permite dois cards;
- promoção dentro de um card não cria card adicional.

## 5. Cinco campos patrimoniais

Para qualquer oferta problemática conferir primeiro:

1. horário da oferta;
2. local de embarque;
3. tempo até embarque;
4. local de destino;
5. tempo total.

Esses campos são o core da inteligência e não podem regredir por causa da correção monetária.

## 6. Duração / amostra

- mínimo recomendado: **2 horas**;
- meta: **40+ ofertas**;
- ideal: pelo menos **3 cards com Turbo Mais/promoção**, se a Uber os apresentar naturalmente.

Se Turbo Mais não aparecer, o teste valida regressão geral, mas o caso promocional permanece pendente de evidência real.

## 7. Se houver erro

Quando possível:

- Registrar falha imediatamente;
- guardar screenshot do card;
- anotar horário aproximado;
- não reiniciar a jornada;
- continuar o teste, salvo se o app ficar inutilizável.

Preservar no print todos os valores monetários do card.

## 8. Diagnóstico obrigatório

Exportar o diagnóstico combinado no fim da jornada.

### `reader2_money_shadow_030_field2`

Confirmar:

- `mode = shadow`;
- `second_ocr = false`;
- `official_persistence = false`;
- `backend_effect = false`;
- `hud_effect = false`;
- `admission_influence = false`;
- `observations > 0`;
- `multi_money_cards` aumenta quando houver mais de um valor `R$` no card;
- `promotion_cards` aumenta quando Turbo Mais/bônus for reconhecido;
- `fare_matches` deve predominar;
- qualquer `fare_disagreements` deve ser cruzado com print/horário.

### `reader2_shadow_030`

Continuar verificando:

- `m1_core_complete`;
- `shadow_core_complete`;
- divergências por campo;
- nenhum segundo OCR.

### `offer_admission_030`

Continua válido para conflitos x10. Field2 não substitui esse gate.

## 9. Histórico

Abrir antes/durante/depois. Qualquer regressão é **P1**, pois Histórico não foi alterado por contrato.

## 10. Screenshots — já registrado, mas NÃO corrigido neste APK

O excesso de screenshots passa a ser a próxima etapa **0.30.1 Field — Screenshot Storage Guard**.

Nesta build apenas anotar, se fácil:

- quantidade aproximada de imagens geradas por oferta;
- tamanho aproximado de alguns arquivos.

Não misturar esse bug com a aprovação do Money Roles Field2.

## 11. Critérios de aprovação

- nenhuma promoção secundária vira tarifa principal;
- caso Turbo Mais real passa;
- cards comuns não regrediram;
- múltiplos cards legítimos continuam separados;
- cinco campos patrimoniais continuam confiáveis;
- Reader 2 money shadow permanece sem side effects;
- Histórico continua normal;
- nenhum P0/P1 do Core Reader.

## 12. O que enviar

1. diagnóstico final;
2. duração da jornada;
3. quantidade aproximada de ofertas;
4. quantidade de ofertas com Turbo Mais;
5. prints dos casos promocionais;
6. horário aproximado de qualquer erro;
7. dizer se o erro apareceu no HUD, Histórico ou apenas diagnóstico.
