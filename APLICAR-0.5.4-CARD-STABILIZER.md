# Aplicar — Sr. Rotas 0.5.4 Alpha

Patch incremental sobre a 0.5.3.

## Objetivo

A 0.5.3 derrubou o OCR médio de ~611 ms para ~242 ms no aparelho de teste. A 0.5.4 mantém essa velocidade e corrige a oscilação do mesmo card em frames consecutivos.

Casos reais: `36,00 -> 36,58 -> 30,00`, `18,90 -> 18,00 -> 18,99`, `3,00 parcial -> 14,73 completo` e `14,00 parcial -> 15,77 completo`.

## Mudanças

- versionCode 9 / versionName `0.5.4-alpha`;
- parser `sr-rotas-v0.5.4`;
- novo CardStabilizer com janela máxima de 750 ms;
- HUD continua imediato;
- histórico/backend recebem apenas o melhor frame estabilizado;
- agrupamento usa geometria e proximidade, não tarifa;
- leitura parcial fraca não pisca no HUD;
- card visto uma única vez continua sendo salvo;
- último card é forçado ao encerrar a jornada;
- log `CARD ESTABILIZADO` mostra amostras/melhorias.

## Aplicar

1. Extrair na raiz do repositório.
2. Substituir/adicionar os arquivos.
3. Commit/push na main.
4. Aguardar `Android Debug APK`.
5. Confirmar Test parser, Build debug APK e Upload APK.

Depois do teste, enviar a linha `DESEMPENHO OCR`, as linhas `CARD ESTABILIZADO`, o resumo da jornada e qualquer corrida percebida como perdida/errada.
