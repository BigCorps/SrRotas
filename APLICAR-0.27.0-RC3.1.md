# Sr. Rotas 0.27.0 RC3.1 — correção focada na identificação da 99

## Base obrigatória
Aplicar sobre a `main` que contém a RC3:

- commit base verificado: `3b7508398ff14de446dde1aefaf46b37641590bc`
- versionCode anterior: `58`
- versionName anterior: `0.27.0-rc3`
- GitHub Actions da RC3: sucesso

## Nova versão
- versionCode: `59`
- versionName: `0.27.0-rc3.1`

## Arquivos de código alterados
1. `android/app/build.gradle.kts`
2. `android/app/src/main/java/com/bigcorps/driveraimvp/DriverPlatformOfferRouter.kt`
3. `android/app/src/test/java/com/srrotas/app/MultiplatformOfferParserTest.kt`

## Por que esta correção existe
O diagnóstico RC3 mostrou frames em que:

- tarifa estava presente;
- cluster espacial estava presente;
- havia duas geometrias válidas;
- mas a identidade textual da 99 (`99Plus`, `99Pop`, `Escolher`, etc.) não apareceu naquele frame.

Nessa situação o card era descartado antes do parser financeiro da 99.

## Correção RC3.1
A RC3.1 adiciona memória temporal e espacial do painel da 99 em tela dividida:

- quando um frame identifica a 99 com segurança, o app memoriza somente a posição horizontal normalizada do painel;
- a memória dura no máximo 30 segundos e é renovada quando o painel volta a produzir uma oferta válida;
- se um frame seguinte perder os textos identificadores da 99, mas tarifa + duas geometrias continuarem no mesmo painel, o parser da 99 pode continuar;
- se aparecer uma âncora explícita Uber no painel memorizado, a memória é invalidada imediatamente;
- mudança de orientação também invalida a memória;
- o caminho Uber (`UberSpatialParser0221` / `OfferParser`) não foi alterado.

A memória não grava OCR bruto, endereço nem coordenadas.

## O que NÃO foi alterado
- `OfferParser`
- `UberSpatialParser0221`
- `UberOfferDetector`
- `FrameChangeDetector`
- `MediaProjectionOcrService`
- sampling / resolução OCR
- `OfferDeduplicator`
- fórmulas, custos ou thresholds
- HUD
- backend
- Supabase / SQL
- Vercel

## Aplicação
Extraia o ZIP na raiz do repositório, preservando os caminhos, e envie os arquivos alterados para a `main` como nas rodadas anteriores.

Depois, validar pelo GitHub Actions antes de instalar o APK.
