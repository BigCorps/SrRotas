# Aplicar Sr. Rotas 0.24.0-beta

Este é o ZIP **consolidado** das Partes 1–4 e é o pacote recomendado para
upload no GitHub sobre a base 0.23.6-beta / commit
`6a0d68c5ddbb22e450e92a0434f6e3548786ef5c`.

## Aplicação

1. Extraia o ZIP.
2. Faça upload do conteúdo preservando os caminhos desde a raiz do repositório.
3. Confirme a substituição dos arquivos existentes.
4. Faça um único commit, por exemplo:
   `Sr. Rotas 0.24.0-beta — UI, HUD, estabilidade e acabamento`.
5. Aguarde o GitHub Actions `Android - Build APK`.

## O workflow deve validar

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:assembleRelease`
- certificado do release quando os secrets de assinatura estiverem disponíveis
- artifacts Debug e Release.

## Importante

- não há SQL nesta versão;
- não há migration de Supabase;
- não há alteração de launcher/mipmap;
- `versionCode` passa de 42 para **43**;
- `versionName` passa de `0.23.6-beta` para **0.24.0-beta**.

As quatro partes intermediárias não precisam ser aplicadas separadamente se
você usar este ZIP consolidado.
