# Sr. Rotas 0.27.0-RC2-DIAG1 — Aplicação

## Objetivo

Rodada exclusivamente de instrumentação para localizar por que alguns cards visíveis na Uber/99 não chegam ao HUD.

Esta rodada **não altera o comportamento do leitor**: não modifica MediaProjection, sampling, resolução OCR, ML Kit, FrameChangeDetector, parsers Uber/99, thresholds, fórmulas, CardStabilizer, OfferDeduplicator, HUD renderer, Supabase ou backend.

## Base obrigatória

- branch: `main`
- commit base: `144101ddf59030912705c7027c63a66aff6e8f6a`
- versão base: `0.27.0-rc2` / versionCode 56

## Versão resultante

- versionName: `0.27.0-rc2-diag1`
- versionCode: `57`

## Arquivos de código alterados

1. `android/app/build.gradle.kts`
2. `android/app/src/main/java/com/bigcorps/driveraimvp/RadarHudTrace024.kt`
3. `android/app/src/main/java/com/bigcorps/driveraimvp/DiagnosticBundle.kt`

## O que muda

### RadarHudTrace024

- aumenta apenas a retenção do trace técnico anonimizado;
- continua sem OCR bruto, screenshot, endereço ou coordenada;
- agrega contagens por estágio;
- agrega motivos de `SCREEN_CLASSIFIED` e `PARSE_REJECTED`;
- resume sinais espaciais (`fare_lines`, `clusters`, geometria, âncora Uber, ruído de navegação);
- permite reconstruir uma janela de aproximadamente 20 s antes e 12 s depois do último `manual_failure_mark` registrado pelo sistema de confiabilidade.

### DiagnosticBundle

- schema passa de `sr-rotas-diagnostic-v3` para `sr-rotas-diagnostic-v4`;
- adiciona a chave `radar_hud_trace_024`;
- mantém o compartilhamento padrão sem OCR bruto/log local/screenshot/endereço/coordenada.

## Aplicação

Copiar o conteúdo deste ZIP sobre a raiz do repositório, preservando os caminhos.

Não executar SQL.
Não alterar Supabase.
Não alterar Vercel manualmente.
Não alterar backend.

Após upload/commit, aguardar o GitHub Actions gerar o APK de campo normalmente.
