# Sr. Rotas — arquivos PWA/TWA

O motor de captura do Sr. Rotas **continua sendo Android nativo**. Uma TWA não substitui MediaProjection, Foreground Service, OCR nem Overlay.

Esta pasta deixa `srrotas.com` pronto para uma TWA separada do painel/site, caso você queira publicar essa interface web em Android futuramente.

## O que já está pronto

Os arquivos públicos ficam em `backend/public/`:

- `manifest.webmanifest`
- `sw.js`
- `favicon.ico`
- `apple-touch-icon.png`
- `icons/icon-192.png`
- `icons/icon-512.png`
- `icons/icon-maskable-512.png`
- `logo-srrotas.png`
- `og-srrotas.png`

## Package IDs

- App Android nativo: `com.srrotas.app`
- TWA opcional do site/painel: use `com.srrotas.web`

Não use o mesmo package ID nos dois aplicativos.

## Bubblewrap

Depois que `https://srrotas.com/manifest.webmanifest` estiver publicado:

```bash
npm i -g @bubblewrap/cli
bubblewrap init --manifest=https://srrotas.com/manifest.webmanifest
```

Ao preencher o projeto TWA, use `com.srrotas.web`.

Depois de definir a chave de assinatura, gere o `assetlinks.json` com:

```bash
SHA256_FINGERPRINT='AA:BB:CC:...' node twa/render-assetlinks.mjs
```

O script grava o arquivo correto em:

`backend/public/.well-known/assetlinks.json`

Se o Google Play App Signing usar outra chave, troque o fingerprint pelo certificado de assinatura do Google Play antes de publicar.
