# Aplicar — Sr. Rotas 0.16.0-beta

## Objetivo desta versão

0.16 é a versão **Performance + Launcher Icon Fix**.

Ela corrige duas observações do teste de campo da 0.15:

1. launcher Android grande demais / sujeito à máscara do fabricante;
2. regressão de latência no caminho OCR após a inclusão de jornada/exposição.

## O que NÃO muda

- OfferParser
- SpatialOfferParser
- CardStabilizer
- OfferDeduplicator
- fórmulas financeiras
- thresholds
- `FRAME_SAMPLE_INTERVAL_MS = 250`
- `OCR_MAX_LONG_EDGE = 2100`
- logo Web/PWA
- `logo_srrotas.png` usado nos headers/mascote

## Performance

A 0.15 consultava SQLite várias vezes em `canObserveOffers()`:
- estado da jornada;
- corrida atual;
- última oferta.

Esse método é chamado no caminho do dispatcher.

Na 0.16:
- o estado operacional é hidratado uma vez e mantido em memória;
- `canObserveOffers()` passa a ser O(1);
- criação de `OFFERED`, corte/reabertura de exposição e sync da jornada são pós-processados em thread dedicada;
- atualização do mascote após oferta só redesenha o painel quando ele está expandido;
- pós-processamento operacional recebe atraso técnico de 180 ms, preservando o timestamp real da oferta;
- localização regional passa a preferir localização passiva recente e usa amostragem 45 s / 180 m;
- nova telemetria mede `dispatch_médio`, `dispatch_máx` e `dispatch_lento`.

## Launcher Android

Somente os recursos `ic_launcher*` são substituídos.
`logo_srrotas.png` NÃO faz parte deste ZIP.

O launcher fica com fundo preto e arte central menor. O conteúdo foi colocado com
18/108 de margem por lado na camada, seguindo a zona segura de ícones adaptativos.

## Banco

**Nenhum SQL novo.**

A migration 0.15 continua sendo a base atual.

## Ordem

1. envie todo o conteúdo deste ZIP para a raiz do repositório;
2. substitua arquivos existentes;
3. aguarde o GitHub Actions;
4. confirme:
   - `Unit tests` verde;
   - `Build debug APK` verde;
   - `Build field release APK` verde;
5. para teste do motorista, baixe preferencialmente:
   - `sr-rotas-field-release-apk`
6. o APK esperado deve informar:
   - `0.16.0-beta` no field release;
   - `0.16.0-beta-debug` no debug.

## Teste de campo recomendado

Rodar a mesma rota/situação em que a 0.15 pareceu mais lenta.

Ao encerrar a jornada, o diagnóstico local deve conter uma linha semelhante a:

`DESEMPENHO OCR ... ocr_médio=...ms ... dispatch_médio=...ms ... dispatch_máx=...ms ...`

Comparar principalmente:
- ofertas percebidas pelo motorista x ofertas reconhecidas;
- `substituídos`;
- `ocr_médio`;
- `dispatch_médio`;
- `dispatch_máx`.

Se o OCR continuar lento mas `dispatch_médio` estiver baixo, o gargalo está no ML Kit/captura.
Se `dispatch_médio` estiver alto, ainda existe trabalho de app bloqueando o callback.

## Próximas fases

Para manter a validação consolidada na 0.19:
- 0.17: importação histórica + Motor Estatístico v1;
- 0.18: custos pessoais / Lucro est.*;
- 0.19: validação completa de campo.
