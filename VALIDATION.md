# Validação — Sr. Rotas 0.3 Alpha

## Automatizada

- GitHub Actions `Android Debug APK`: deve gerar `app-debug.apk`.
- GitHub Actions `Backend CI`: deve passar `tsc --noEmit` e `next build`.
- Vercel: `/`, `/api/health`, páginas institucionais e APIs devem publicar sem erro.

## Banco

Após executar `20260816_journeys_history_security.sql`, confirmar:

- `driver_journeys` existe e tem RLS habilitado;
- `ride_offers.journey_id` existe;
- `driver_preferences` possui `min_fare`, `max_pickup_km`, `min_profit`;
- índices de FKs foram criados;
- clientes anon/authenticated não executam `rls_auto_enable()`.

## Android funcional

- pareamento;
- início/encerramento de jornada;
- MediaProjection;
- OCR;
- HUD;
- persistência local;
- sincronização após ficar offline;
- compartilhamento explícito de diagnóstico;
- Pesquisa IA depois de existir histórico sincronizado.

## Não validar por suposição

A precisão de parsing do Uber deve ser validada com ofertas reais e diagnóstico do aparelho de teste.
