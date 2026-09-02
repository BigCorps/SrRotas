# Sr.Rotas 0.25.0 — ZIP 01/04 — Parser + Radar

Base analisada: commit `0ce5f984e5bf8082f11fc36eb9982f811e286fa9` (0.24.2-beta / versionCode 45).

## Objetivo desta rodada

Corrigir somente os dois primeiros bloqueadores do relatório de campo, sem alterar UI, HUD, jornada, MediaProjection, Supabase, Vercel ou versão do APK.

### 1. Duração acima de 1 hora

O parser antigo reconhecia apenas `N min`. Uma linha como `1 h 29 min` podia deixar a parcela `29 min` ser tratada isoladamente em fallback, gerando totais incorretos e métricas como R$/min superestimadas.

Esta rodada passa a reconhecer, de forma conservadora:

- `34 min`, `34 minutos`;
- `1h29`, `1h29min`;
- `1 h 29 min`;
- `1 hora e 29 minutos`;
- `2 horas`;
- equivalentes com pequenos erros OCR numéricos (`I`, `l`, `L`, `O`, `S`).

O limite plausível permanece entre 1 e 360 minutos.

### 2. Radar cinza que não abre HUD

O gate antigo exigia simultaneamente:

- geometria no formato exato `N min (N km)`; e
- `R$/km` reconhecido pelo OCR.

Se parênteses ou uma dessas âncoras sumissem, o card era descartado antes mesmo de o parser numérico tentar validá-lo.

O novo gate mantém o isolamento espacial já existente e permite avançar quando há evidência suficiente:

- `R$/km` + pelo menos uma duração e uma distância; ou
- dois pares completos de duração/distância; ou
- duas durações + duas distâncias com âncora de serviço/`Selecionar`.

Preço isolado, duração sem distância e geometria insuficiente continuam rejeitados. As validações numéricas de coerência do `OfferParser` também continuam valendo, inclusive os casos já testados de mistura entre cards.

## Arquivos desta rodada

### Novos
- `android/app/src/main/java/com/bigcorps/driveraimvp/UberDurationParser025.kt`
- `android/app/src/test/java/com/srrotas/app/UberDurationParser025Test.kt`
- `android/app/src/test/java/com/srrotas/app/BRUberRadarParser025Test.kt`

### Substituir integralmente
- `android/app/src/main/java/com/bigcorps/driveraimvp/UberOfferDetector.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/BRUberRadarParser.kt`
- `android/app/src/test/java/com/srrotas/app/OfferParserTest.kt`

## O que NÃO foi alterado

- `android/app/build.gradle.kts` continua em 0.24.2-beta / versionCode 45 nesta rodada;
- MediaProjection/OCR contínuo (ZIP 02);
- Base Coletiva e Nova corrida no destino (ZIP 03);
- acabamento visual e bump 0.25.0 (ZIP 04);
- Supabase;
- Vercel;
- Busca e Destino;
- regras atuais de custo, lucro, verdict, deduplicação e contexto espacial.

## Aplicação

Envie o conteúdo deste ZIP para a raiz do repositório, preservando os caminhos. Arquivos existentes devem ser substituídos pelos arquivos completos desta rodada; arquivos novos devem ser adicionados.

Não é necessário gerar APK após este ZIP. O plano acordado é gerar o APK somente após o ZIP 04 e testar a 0.25.0 completa de uma vez.
