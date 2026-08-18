# Changelog — Sr. Rotas 0.11.0 Alpha

## OneSignal
- SDK Android 5.9.8;
- inicialização via `SrRotasApplication`;
- External ID = UUID do motorista;
- tags operacionais;
- solicitação de permissão sob ação do usuário;
- preferências individuais;
- teste de push.

## Backend
- `GET/PATCH /api/v1/notifications/preferences`;
- `POST /api/v1/notifications/test`;
- resumo de jornada após encerramento.

## Supabase
- `notification_preferences`;
- `notification_deliveries`;
- Edge `srrotas-send-push`.

## Privacidade
- sem OCR bruto em push;
- App API Key somente server-side;
- novidades do produto desligadas por padrão.

## Motor
Offer Engine continua congelado em `sr-rotas-v0.5.4`.
