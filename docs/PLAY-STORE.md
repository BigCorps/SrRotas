# Preparação para Play Store

Identidade definida:

- App nativo: `com.srrotas.app`
- Marca: `Sr. Rotas`
- Domínio previsto: `srrotas.com`
- TWA complementar: `com.srrotas.web`

Antes de publicação pública:

1. Registrar e configurar `srrotas.com`.
2. Revisar política de privacidade e termos com a operação definitiva.
3. Definir keystore/Play App Signing e armazenar credenciais apenas em Secrets.
4. Produzir AAB release assinado; nunca commitar keystore em repositório público.
5. Validar declarações de Data Safety.
6. Revisar a presença do AccessibilityService e as declarações/consentimentos exigidos pela Play para o caso de uso efetivamente lançado.
7. Configurar `assetlinks.json` somente com a fingerprint real.
8. Testar target SDK 36 em aparelhos reais.
9. Criar fluxo definitivo de exclusão de conta/dados quando existir cadastro público.

O Android nativo é o produto principal. A TWA é apenas uma superfície web complementar.

## Workflow já preparado

`.github/workflows/android-release-aab.yml` é manual e só funciona depois que estes GitHub Secrets existirem:

- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Ele nunca cria uma chave nova e nunca publica automaticamente na Play Store; apenas gera o AAB assinado como artifact.
