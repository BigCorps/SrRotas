# Correção de build — Sr. Rotas 0.5 Alpha

Patch incremental sobre a 0.5 já aplicada.

## O que corrige

1. `BRUberRadarParser.kt`: o parser Radar passa a ser `internal`, compatível com `SpatialOcrLine`, também interno. Isso corrige o erro Kotlin do run #6.
2. `.github/workflows/android-debug.yml`: adiciona de fato `:app:testDebugUnitTest` antes de `:app:assembleDebug`.

## Aplicação

Extraia na raiz do repositório, substitua os dois arquivos e faça commit na `main`.
Não há SQL, variável Vercel ou mudança de versão adicional.

Após o commit, aguarde o workflow Android Debug APK. O APK continua sendo `0.5.0-alpha-debug` / versionCode 5.
