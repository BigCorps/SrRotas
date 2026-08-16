# Variáveis de ambiente

## Obrigatórias no Vercel

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `PAIRING_CODE`
- `MCP_API_TOKEN`

## IA

- `OPENAI_API_KEY`
- `OPENAI_MODEL=gpt-5.6`
- `DEFAULT_TIMEZONE=America/Sao_Paulo`

## Site

Durante os testes:

- `NEXT_PUBLIC_SITE_URL=https://sr-rotas.vercel.app`
- `NEXT_PUBLIC_INDEX_SITE=false`
- `NEXT_PUBLIC_SUPPORT_EMAIL=contato@bigcorps.com.br` (pode aguardar o domínio/e-mail existir)

Após registrar o domínio:

- trocar `NEXT_PUBLIC_SITE_URL=https://srrotas.com`;
- só colocar `NEXT_PUBLIC_INDEX_SITE=true` quando quiser indexação pública.

## TWA — deixar sem fingerprint por enquanto

- `TWA_PACKAGE_NAME=com.srrotas.web`
- `TWA_SHA256_FINGERPRINTS=`

Não invente fingerprint. O endpoint de Asset Links deve continuar 404 até existir a chave real.
