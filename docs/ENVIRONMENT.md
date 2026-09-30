# Variáveis de ambiente

## Obrigatórias no Vercel

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `PAIRING_CODE`
- `CRON_SECRET`

## MCP

- `MCP_PUBLIC_BASE_URL=https://srrotas.com` — opcional, mas recomendado em produção para fixar o issuer/resource canônico.

Não existe mais `MCP_API_TOKEN` global. O MCP usa:
- OAuth 2.1 por usuário, com códigos/tokens em hash no Supabase; ou
- chave manual `srmcp_...` criada pelo próprio motorista em `/app/mcp`.

Nenhum client secret, access token, refresh token ou chave manual deve ser colocado em variável `NEXT_PUBLIC_*`.

## IA

- `OPENAI_API_KEY`
- `OPENAI_MODEL=gpt-5.6`
- `DEFAULT_TIMEZONE=America/Sao_Paulo`

## Site

Produção:

- `NEXT_PUBLIC_SITE_URL=https://srrotas.com`
- `NEXT_PUBLIC_INDEX_SITE=false` enquanto o lançamento público não estiver liberado.
- `NEXT_PUBLIC_SUPPORT_EMAIL=contato@bigcorps.com.br`

Quando a publicação pública for aprovada:
- manter `NEXT_PUBLIC_SITE_URL=https://srrotas.com`;
- alterar `NEXT_PUBLIC_INDEX_SITE=true` somente quando quiser indexação pública.

## Android / Digital Asset Links

- `TWA_PACKAGE_NAME=com.srrotas.web`
- `TWA_SHA256_FINGERPRINTS=`

Não inventar fingerprint. Preencher somente com o SHA-256 real da assinatura final aprovada.
