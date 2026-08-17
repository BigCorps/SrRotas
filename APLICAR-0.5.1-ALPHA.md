# Aplicar — Sr. Rotas 0.5.1 Alpha

Patch incremental sobre a 0.5 já instalada no repositório.

## Objetivo

Esta calibração vem da jornada real da 0.5 e ataca duas frentes: **latência/perda de ofertas** e **leituras parciais/duplicadas**.

## Aplicação

1. Extraia o ZIP na raiz do repositório `BigCorps/SrRotas`.
2. Substitua os arquivos existentes e mantenha os novos arquivos de teste/documentação.
3. Faça commit/push na `main`.
4. Aguarde o workflow **Android Debug APK**. Ele deve executar `Test parser` antes de `Build debug APK`.
5. Use somente o artifact produzido pelo novo run.

## Versão esperada

- `versionCode = 6`
- `versionName = 0.5.1-alpha`
- Debug exibido no aparelho: `0.5.1-alpha-debug`
- Parser: `sr-rotas-v0.5.1`

## Não há

- SQL novo;
- migration Supabase;
- variável nova no Vercel;
- alteração de domínio;
- mudança de package (`com.srrotas.app` continua igual).

## Teste de campo

Observe principalmente: velocidade para aparecer o HUD, ofertas curtas que antes passavam sem leitura, Radar com vários cards, ausência de R$/h absurdo, ausência de duplicata quando rating/categoria oscila e o caso R$17,99 com pickup parcial.
