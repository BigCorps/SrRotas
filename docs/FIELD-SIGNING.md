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
