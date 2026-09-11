# Aplicar — Sr. Rotas 0.27.0-RC2

## Base obrigatória

Aplicar sobre `main` no commit:

`9bb226deaeab04044d8a7da00750f5a1b7a19ed4`

Versão esperada após o upload:

- `versionCode 56`
- `versionName 0.27.0-rc2`

## GitHub

1. Extraia o ZIP.
2. No repositório `BigCorps/SrRotas`, branch `main`, envie **o conteúdo da raiz do ZIP**, preservando os caminhos.
3. Os três arquivos de código do pacote devem substituir os caminhos existentes.
4. Faça um único commit, por exemplo: `Sr. Rotas 0.27.0-RC2`.
5. Aguarde o GitHub Actions concluir.
6. Instale o APK release gerado **por cima da RC1**, sem limpar os dados do app.

## Vercel

O pacote altera `backend/app/api/v1/offers/report-selection/route.ts`. Portanto, depois do commit, aguarde também o deployment Production do Vercel ficar `READY` antes de validar a seleção múltipla remota.

## Supabase

**Nenhum SQL e nenhuma alteração manual no Supabase.**

A correção de seleção múltipla usa os campos já existentes e apenas remove o comportamento do endpoint que desmarcava as outras ofertas da jornada.

## Leitor/OCR

Esta RC **não altera**:

- `MediaProjectionOcrService.kt`;
- `FrameChangeDetector.kt`;
- parsers Uber/99;
- thresholds;
- deduplicação;
- fórmulas do Offer Engine.

A estabilidade visual foi corrigida sem tocar no motor de leitura. Se o HUD falhar, seguir `TESTE-0.27.0-RC2.md`, registrar a falha e compartilhar o Diagnóstico de leitura.
