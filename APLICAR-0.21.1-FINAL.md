# APLICAR — Sr. Rotas 0.21.1-beta

Base usada: `main` no commit `78816316e6162075b3c7ec07f8691d83255dbfba` (`0.21.0-beta`), exatamente a versão avaliada em campo antes deste acabamento.

## GitHub Online

1. Abra `BigCorps/SrRotas` na branch `main`.
2. Extraia o ZIP final `SrRotas-0.21.1-final.zip`.
3. Em **Add file → Upload files**, envie **o conteúdo da raiz do ZIP**, preservando as pastas `android/`, `backend/`, `supabase/` e os arquivos da raiz.
4. Confirme a substituição dos caminhos existentes.
5. Faça um único commit, por exemplo: `Sr. Rotas 0.21.1-beta`.
6. Aguarde GitHub Actions e Vercel antes de instalar o APK.

Não altere os secrets de assinatura. A 0.21.1 continua usando a mesma chave estável:

- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Supabase

**Não execute manualmente os SQLs deste ZIP em produção.** Todas as migrations do pacote já foram aplicadas via MCP no projeto de produção:

- `20260823214228_strategy_region_now_021.sql`
- `20260823214458_trial_first_offer_021.sql`
- `20260823215507_region_alias_cleanup_021.sql`
- `20260823221515_web_handoff_021.sql`
- `20260823222211_region_privacy_cleanup_021.sql`
- `20260825011346_report_selection_0211.sql`

A última migration adiciona `report_selected` e `report_selected_at` às ofertas. O ✓ da 0.21.1 usa esses campos somente para relatórios; ele não altera outcome, jornada, exposição ou OCR.

Os SQLs permanecem no ZIP apenas para manter GitHub e banco reproduzíveis.

## Depois do upload

O esperado é:

- Android: `versionCode 32`, `0.21.1-beta`;
- Vercel: deployment Production `READY`;
- GitHub Actions: build verde e APK release assinado com a mesma chave;
- instalação **por cima da 0.21.0**, sem desinstalar e sem limpar os dados.

## Guardrails desta correção

A 0.21.1 não contém nem altera os arquivos congelados do Offer Engine:

- `OfferParser.kt`
- `SpatialOfferParser.kt`
- `UberOfferDetector.kt`
- `CardStabilizer.kt`
- `OfferDeduplicator.kt`

Também preserva a lógica de sync/ownership da 0.20.3. O que muda é a experiência final, a apresentação de Buscar, a separação entre “selecionar para relatório” e “estado operacional de corrida” e a apresentação assíncrona da continuidade no destino.

## Ícone da bolha/menu flutuante

O ZIP inclui `android/app/src/main/res/drawable-nodpi/srrotas_bubble_icon.png`. Apenas `JourneyBubbleController` foi alterado para usar esse recurso. `AndroidManifest.xml` permanece com `@mipmap/ic_launcher` e `@mipmap/ic_launcher_round`, portanto nenhum ícone de instalação/launcher é substituído por esta arte.

## Continuidade no destino

A 0.21.1 final ativa a funcionalidade prevista no roadmap sem alterar o Offer Engine:

- após uma oferta válida ser persistida, o Context Engine/geocoder resolve destino, célula e ETA;
- o Android consulta `/api/v1/intelligence/destination` fora do hot-path do OCR;
- com pelo menos 20 intervalos elegíveis de exposição observada, o HUD/card mostra **P10 real** (nova oferta em até 10 min) + Alta/Média/Baixa;
- sem denominador observado suficiente, a **Base Sr. Rotas histórica** usa somente os registros validados agregados para classificar recorrência como **Alta/Média/Baixa**;
- densidade de prints históricos nunca é convertida em percentual artificial;
- dia da semana e faixa de 3 horas têm prioridade; se a amostra for pequena, o cálculo amplia de forma controlada para mesma faixa em outros dias e depois para a região em geral.

Nenhuma migration nova foi necessária para este adendo; ele reutiliza `zone_exposures`, `sr_collective_region_hour_v1` e `sr_region_seed_v1`.

## Limpeza Alpha

A interface normal não exibe mais feedback Beta nem pareamento Alpha legado. O login/criação de conta atual continua sendo o fluxo oficial. Alguns arquivos internos de diagnóstico/compatibilidade podem permanecer no repositório até o hardening final da 1.0, mas não participam da navegação 0.21.1.
