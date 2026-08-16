# TWA — Sr. Rotas

A TWA é complementar ao aplicativo Android nativo. Ela não substitui MediaProjection, OCR ou HUD.

## Identidade prevista

- Marca: **Sr. Rotas**
- Domínio definitivo: `https://srrotas.com`
- Package TWA: `com.srrotas.web`
- Package Android nativo: `com.srrotas.app`

## Antes de ativar

1. Registrar e apontar `srrotas.com` para o projeto Vercel.
2. Trocar `NEXT_PUBLIC_SITE_URL` para `https://srrotas.com`.
3. Ter a chave real que assina a TWA/Play Store.
4. Configurar no Vercel:
   - `TWA_PACKAGE_NAME=com.srrotas.web`
   - `TWA_SHA256_FINGERPRINTS=AA:BB:...`
5. Confirmar que `https://srrotas.com/.well-known/assetlinks.json` responde com a fingerprint correta.

A rota `backend/app/.well-known/assetlinks.json/route.ts` retorna 404 enquanto package/fingerprint não estiverem configurados. Isso é intencional para não publicar uma associação falsa que possa ser cacheada pelo Chrome.

O fluxo nativo do Sr. Rotas deve ser validado antes de investir na publicação TWA.
