# Arquitetura — Sr. Rotas 0.3 Alpha

## Android nativo

`MediaProjectionOcrService` recebe a autorização do Android, mantém uma única sessão de MediaProjection por jornada, processa aproximadamente um frame por segundo e usa ML Kit Text Recognition local.

`SpatialOfferParser` continua sendo o parser de produção do Alpha. Ele não foi recalibrado nesta atualização: aguardamos amostras reais do Uber para evitar ajustes por suposição.

`OfferDispatcher` grava primeiro em `LocalStore` e só depois tenta sincronizar. Assim uma falha de internet não elimina a oferta observada.

`LocalStore` usa SQLite do próprio Android, sem nova dependência, e guarda jornadas, ofertas estruturadas e OCR bruto apenas localmente para diagnóstico.

## Jornadas

Uma jornada nasce somente após a autorização de MediaProjection e é encerrada ao parar o serviço/compartilhamento. Ela agrupa ofertas observadas. Não representa automaticamente tempo de corrida, corridas aceitas ou receita realizada.

## Backend

Next.js em `backend/`, publicado no Vercel. O aparelho se autentica com token opaco; no Supabase fica somente o hash. Rotas principais:

- `POST /api/v1/pair`
- `GET|POST /api/v1/preferences`
- `GET|POST /api/v1/journeys`
- `GET|POST /api/v1/offers`
- `POST /api/v1/ask`
- `/mcp`
- `/api/health`

## Supabase

RLS fica habilitado e sem policy pública no Alpha. O acesso atual é exclusivamente pelo backend com `service_role`.

## IA e MCP

A IA recebe dados estruturados, estratégia e contexto de jornada. As instruções exigem distinguir oferta observada de ganho real. O MCP mantém ferramentas somente leitura e registra auditoria das chamadas.
