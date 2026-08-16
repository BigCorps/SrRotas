# Validação — Sr. Rotas 0.4 Alpha

## Automatizada

- `Android Debug APK`: executa `:app:testDebugUnitTest` antes de gerar `app-debug.apk`.
- `Backend CI`: deve passar TypeScript + Next build.
- Vercel: `/`, `/api/health`, páginas institucionais e APIs devem publicar sem erro.

## Banco

Após executar `20260816_parser_strategy_04.sql`, confirmar as novas colunas de métricas em `ride_offers` e as faixas Cherry Picker em `driver_preferences`.

## Fixtures do parser

O teste automatizado deve aceitar os exemplos equivalentes a:

- R$ 30,98 / 18,0 km / 36 min;
- R$ 15,20 / 7,0 km / 21 min;
- R$ 6,18 / 4,3 km / 12 min;
- Priority com `+R$ ... incluído`, mantendo o preço principal;
- Radar com `Selecionar`;
- rejeição de `Registro de viagens R$ 260,76`;
- rejeição de `+R$ 1,25` e faixas de mapa `1-4 min`.

## Android funcional

Validar pareamento, jornada, MediaProjection, OCR, HUD configurável, preview, persistência local, sincronização offline, diagnóstico e Pesquisa IA.

A precisão final continua dependente de testes reais em diferentes versões/layouts do Uber.
