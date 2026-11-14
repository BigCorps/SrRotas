# Assinatura estável do APK de campo

## Por que

O APK precisa manter a mesma assinatura para o Android aceitar uma atualização por cima da versão anterior.

O `debug.keystore` criado automaticamente no GitHub Actions não deve ser usado como identidade permanente de testes porque runners diferentes podem criar chaves diferentes.

## Secrets usados pela 0.19

Em:

`GitHub → Settings → Secrets and variables → Actions`

crie:

- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Nunca envie o arquivo `.jks` ao repositório público.

## Se já existe uma chave estável Sr. Rotas

Prefira continuar usando a mesma.

Converta o arquivo para Base64 e salve somente em Secret.

### PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("srrotas-field.jks"))
```

### Linux / Codespace / Git Bash

```bash
base64 -w 0 srrotas-field.jks
```

Cole a saída em `ANDROID_KEYSTORE_BASE64`.

## Se ainda não existe chave estável

Crie uma fora do repositório e faça backup seguro.

Exemplo com o JDK:

```bash
keytool -genkeypair -v \
  -keystore srrotas-field.jks \
  -alias srrotas \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Depois transforme em Base64 e cadastre os Secrets.

Não commite:
- `.jks`;
- `.keystore`;
- senhas;
- Base64 da chave.

## Workflow

Quando todos os Secrets existem, o Actions publica:

`sr-rotas-field-release-apk`

Quando faltam Secrets, publica:

`sr-rotas-field-release-UNSTABLE-SIGNING`

O segundo é somente fallback de CI.

## Fingerprint

Cada artifact release inclui:

`field-signing-certificate.txt`

Esse arquivo contém somente a informação pública do certificado, não a chave privada.

Use-o para confirmar que a assinatura permaneceu a mesma entre versões.

## Integração vc114 — entrega padrão com assinatura obrigatória

A issue #18 usa `.github/workflows/field-vc114-integration.yml` na branch
`integration/field-vc114-radar-history-recovery`. Reutiliza os mesmos quatro Secrets e a configuração
`releaseEnv` existente; gera exclusivamente o instalável operacional por `:app:assembleRelease`.
Não aceita o fallback debug mencionado no workflow histórico acima. Não gera/entrega APK de laboratório.

Depois das regressões operacional/R3-Lab e QA nativa, `android/scripts/verify-field-release.sh`
confere o APK real com `apksigner` e `aapt2`: pacote `com.srrotas.app`, vc114/`0.33.37-field`,
sem debuggable, um único signatário e `R3_LAB=false`. Exige o certificado estável público SHA-256:
`cc4476c4eb3ac725b692914488282f0ac70a83b3682ca6b7ac0f841f9de2117c`.
Esse fingerprint foi reconferido nos logs reais do APK vc113, Actions `43443953340`.

O artifact privado `sr-rotas-field-vc114-release-apk` contém somente:

- `app-release.apk`;
- `app-release.apk.sha256` (hash do APK, não do ZIP);
- `field-signing-certificate.txt`;
- `apk-badging.txt`;
- `release-verification.json` (commit, pacote, versão, tamanho, hash e certificado públicos).

Se faltar Secret, houver falha crítica ou certificado/identidade divergente, o job falha e não publica
um instalável substituto. Nenhum segredo/keystore é incluído no artifact; arquivo temporário removido
no encerramento do job. Download requer acesso ao repositório; retenção de 30 dias.

Esta é uma candidata para homologação física, não release público/Play Store. A atualização deve
ser por cima, sem apagar dados; assinatura e versionCode são gates técnicos, não prova de campo.
