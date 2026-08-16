# Sr. Rotas 2.0 Alpha — como aplicar este patch

Este ZIP foi feito **para ser aplicado por cima do primeiro `driver-ai-mvp.zip`**.

## Ordem

1. Extraia `driver-ai-mvp.zip`.
2. Extraia `sr-rotas-2.0-alpha-patch.zip` no mesmo local.
3. Quando o sistema perguntar, escolha **substituir/mesclar os arquivos existentes**.
4. Não apague as pastas que vieram do primeiro ZIP; este pacote contém somente arquivos novos ou alterados.

A pasta final continua se chamando `driver-ai-mvp` para a sobreposição funcionar sem ajuste manual.

## O que este patch muda

- Marca definitiva: **Sr. Rotas** / `srrotas.com`.
- Android package definitivo: `com.srrotas.app`.
- Ícones Android e assets web/TWA derivados do logo oficial.
- MediaProjection + VirtualDisplay + ImageReader como motor principal do Alpha.
- Foreground Service `mediaProjection`.
- OCR local ML Kit em aproximadamente 1 frame/s.
- Parser espacial para diferenciar oferta exclusiva e múltiplos cards/Radar.
- Parser numérico tolerante a erros OCR `O→0` e `S→5` somente em tokens numéricos.
- HUD via `TYPE_APPLICATION_OVERLAY`, sem capturar toques e marcado `FLAG_SECURE` para não entrar no próprio OCR.
- AccessibilityService passa a ser **auxiliar**, não o motor principal.
- Novas metas: valor mínimo, máximo até o passageiro e lucro mínimo.
- Texto OCR bruto deixa de ser enviado ao backend por padrão.
- Backend/MCP renomeados para Sr. Rotas.
- PWA/TWA pronta no site, com manifest, service worker e imagens.
- Migration incremental do Supabase.

## Supabase

Depois da migration do primeiro ZIP, execute também:

`supabase/migrations/20260815_srrotas_alpha_patch.sql`

## Android — teste rápido

```bash
cd android
./gradlew :app:assembleDebug
```

APK:

`android/app/build/outputs/apk/debug/app-debug.apk`

Ao abrir o app:

1. autorize o HUD;
2. toque em **Iniciar jornada**;
3. aceite o compartilhamento de tela do Android;
4. abra o Uber Driver;
5. espere uma oferta;
6. volte ao Sr. Rotas e copie o diagnóstico se algum campo vier errado.

## AAB

```bash
./gradlew :app:bundleRelease
```

O release usa as mesmas variáveis de assinatura do primeiro ZIP.

## Importante sobre TWA

A TWA incluída é para `srrotas.com`/painel web. Ela **não substitui o Android nativo**, porque o motor do motorista depende de MediaProjection, Foreground Service e Overlay.
