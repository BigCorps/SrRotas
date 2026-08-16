# Como aplicar — Sr. Rotas 0.3 Alpha

Este ZIP é um **patch incremental completo da atualização 0.3**. Extraia por cima do repositório `BigCorps/SrRotas`, preservando os caminhos. Não apague arquivos que não aparecem no ZIP.

## 1. Supabase primeiro

No SQL Editor do projeto SrRotas, execute inteiro:

`supabase/migrations/20260816_journeys_history_security.sql`

Ele cria `driver_journeys`, relaciona ofertas a jornadas, amplia preferências, adiciona índices e restringe execução pública da função auxiliar `rls_auto_enable()` quando ela existir.

## 2. GitHub

Extraia todos os arquivos deste ZIP na raiz do repositório e faça commit na `main`.

O Android passa para `0.3.0-alpha` / versionCode 3. O package permanece `com.srrotas.app`.

## 3. Vercel

Mantenha as variáveis que já existem. Adicione/ajuste:

```env
NEXT_PUBLIC_SITE_URL=https://sr-rotas.vercel.app
NEXT_PUBLIC_INDEX_SITE=false
NEXT_PUBLIC_SUPPORT_EMAIL=contato@srrotas.com
```

`NEXT_PUBLIC_SUPPORT_EMAIL` pode ficar com o endereço previsto mesmo antes de o e-mail ser criado, mas não divulgue o site publicamente como canal de suporte até o endereço existir.

Para TWA, pode deixar preparado:

```env
TWA_PACKAGE_NAME=com.srrotas.web
TWA_SHA256_FINGERPRINTS=
```

**Não preencha fingerprint inventada.** A rota de Asset Links responde 404 de propósito até existir uma chave real.

Quando registrar o domínio, troque `NEXT_PUBLIC_SITE_URL` para `https://srrotas.com` e associe o domínio no Vercel.

## 4. Builds automáticos

Devem rodar:

- `Android Debug APK` (workflow já existente);
- `Backend CI` (novo workflow deste patch);
- deploy Vercel.

A primeira execução do Backend CI usa `npm install`, então não depende de package-lock.

## 5. Teste

Seu irmão pode terminar os testes do APK 0.2 atual. Não é necessário interrompê-los.

Depois, gere o APK 0.3 pelo GitHub Actions. Ele terá persistência offline, jornada e botão **Compartilhar diagnóstico**, o que facilita muito a próxima calibração.

## O que deliberadamente NÃO foi alterado

- heurísticas do `SpatialOfferParser`;
- posições/layout definitivo do HUD;
- tentativa de identificar automaticamente corrida aceita/concluída;
- TWA assinada/publicada;
- AAB release/keystore.

Esses itens dependem de teste real, chave definitiva ou decisão de publicação.
