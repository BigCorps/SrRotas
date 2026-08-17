# Como aplicar — Sr. Rotas 0.5 Alpha

Este ZIP é **incremental**. Ele contém somente arquivos novos ou alterados em relação à 0.4 aplicada no repositório.

## Aplicação

1. Extraia o ZIP na raiz de `BigCorps/SrRotas`, preservando os caminhos e substituindo os arquivos existentes.
2. Não apague arquivos que não aparecem neste ZIP.
3. Faça commit na `main`.
4. Aguarde o workflow **Android Debug APK**.
5. O workflow agora executa `:app:testDebugUnitTest` antes de `:app:assembleDebug`.
6. Baixe o artifact `sr-rotas-debug-apk` somente se os testes e o build passarem.

## Banco / Vercel

Não há migration Supabase nova e não há variável Vercel nova nesta atualização.

## Android

- `versionCode = 5`
- `versionName = 0.5.0-alpha`
- package permanece `com.srrotas.app`

## O que validar no aparelho

- HUD maior e sem as bordas internas/“traço” da 0.4;
- R$/min aparece antes de R$/km por padrão;
- a mesma oferta não deve reaparecer em sequência A → B → A;
- `unknown → comfort/black` do mesmo card não deve criar uma nova oferta;
- Radar com vários cards deve associar tempo/distância ao card correto;
- leituras economicamente implausíveis do Radar devem ser descartadas em vez de persistidas;
- ao voltar para a tela do Sr. Rotas durante a jornada, o OCR não deve criar o ciclo de diagnóstico sobre diagnóstico;
- `R$ 260,76`, promoções `+R$` e valores da Home continuam sem virar oferta.

## Observação de instalação

Se o Android não permitir instalar por cima do APK Debug anterior por diferença de assinatura do GitHub Actions, sincronize as pendências antes de desinstalar. Depois reinstale e faça o pareamento novamente.

Sr. Rotas — desenvolvido pela BigCorps
