# APLICAR — Sr. Rotas 0.21.0-beta

Base usada: `main` no commit `dbbaff921a6a5a27331d4c6f62d063ea69075d53` (`0.20.3-beta`).

## GitHub Online

1. Abra o repositório `BigCorps/SrRotas` na branch `main`.
2. Extraia o ZIP deste pacote no computador/celular.
3. Em **Add file → Upload files**, arraste **o conteúdo da raiz do ZIP**, preservando exatamente as pastas `android/`, `backend/`, `supabase/` e os arquivos Markdown/JSON da raiz.
4. Confirme a substituição dos arquivos com o mesmo caminho.
5. Faça um único commit, por exemplo: `Sr. Rotas 0.21.0-beta`.
6. Aguarde GitHub Actions e o deploy automático da Vercel.

Não é necessário alterar secrets de assinatura. Continue usando a mesma chave estável configurada em:

- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Supabase

**Não execute manualmente os SQLs deste ZIP em produção.** As cinco migrations 0.21 já foram aplicadas por MCP no projeto de produção:

- `20260823214228_strategy_region_now_021.sql`
- `20260823214458_trial_first_offer_021.sql`
- `20260823215507_region_alias_cleanup_021.sql`
- `20260823221515_web_handoff_021.sql`
- `20260823222211_region_privacy_cleanup_021.sql`

Elas estão no ZIP apenas para manter GitHub e banco reproduzíveis.

Validação pós-migration feita em produção:

- `sr_region_seed_v1`: 4.090 grupos;
- 37.495 amostras históricas válidas classificadas no seed regional;
- 1.276 registros válidos ficaram de fora por não permitirem região segura após a limpeza final;
- `driver_trials`: 0 antes do primeiro teste 0.21, como esperado;
- base coletiva: 0 grupos no momento porque ainda não existem 3 contribuidores opt-in suficientes;
- seed e trial continuam restritos a `service_role`/postgres, sem grants para `anon`/`authenticated`.

## Depois do upload

O esperado é:

- Android: `versionCode 31`, `0.21.0-beta`;
- Vercel: novo deployment Production em estado `READY`;
- GitHub Actions: build verde e APK release assinado com a mesma chave;
- instalação por cima da `0.20.3`, sem desinstalar nem limpar dados.

O Offer Engine permanece congelado em `sr-rotas-v0.5.4`. O pacote não contém `OfferParser.kt`, `SpatialOfferParser.kt`, `UberOfferDetector.kt`, `CardStabilizer.kt` nem `OfferDeduplicator.kt`.
